package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

public enum class HandoffPhase {
    Idle,
    Loading,
    Ready,
    WritingFile,
    Creating,
    Sending,
    Linking,
    Complete,
    Failed,
    Uncertain,
}

/**
 * Which part of the pinned scope no longer holds. Content-free; the words live in [HandoffWords].
 */
public enum class HandoffStaleReason {
    /** The caller's selection generation moved on (sheet closed, profile changed). */
    NotCurrent,
    SourceConnectionClosed,
    AgentConnectionClosed,
    SourceMoved,
    AgentMoved,
    /** Either profile is closing or gone (link-store admission). */
    ProfileUnavailable,
}

/** The class of a port failure, never its text. */
public enum class HandoffCause {
    TimedOut,
    Network,
    Server,
    Refused,
    Storage,
    Other,
}

/** Content-free technical detail for the sheet's Details fold. Never copy, never server text. */
public sealed interface HandoffDetail {
    public data class Stale(val reason: HandoffStaleReason) : HandoffDetail

    public data class History(val kind: HandoffHistoryFailureKind) : HandoffDetail

    public data class RecordTooLarge(val requiredBytes: Int, val maxBytes: Int) : HandoffDetail

    public data class Step(val phase: HandoffPhase, val cause: HandoffCause) : HandoffDetail

    public data class Link(val reason: HandoffLinkRefusal?) : HandoffDetail
}

/** In-memory review data; never persisted with link metadata. */
public data class HandoffDraft(
    val recordId: String,
    val record: HandoffRecord,
    val prompt: String,
    val relativePath: String,
)

public data class HandoffState(
    val phase: HandoffPhase = HandoffPhase.Idle,
    val draft: HandoffDraft? = null,
    val failure: HandoffFailureKind? = null,
    val detail: HandoffDetail? = null,
    /** Durable target identity for persistence and recovery. */
    val createdSessionId: String? = null,
    /** Gateway-local alias for opening the conversation. */
    val navigationSessionId: String? = null,
    val writtenPath: String? = null,
)

public sealed interface HandoffPrepared {
    public data class Ready(val draft: HandoffDraft) : HandoffPrepared

    public data class Refused(val kind: HandoffFailureKind, val detail: HandoffDetail? = null) :
        HandoffPrepared
}

public sealed interface HandoffSent {
    /** [navigationSessionId]: the id to open the continued conversation by. */
    public data class Sent(val navigationSessionId: String) : HandoffSent

    public data class Refused(val kind: HandoffFailureKind, val detail: HandoffDetail? = null) :
        HandoffSent
}

/**
 * Port of `AgentHandoffController` (lib/state/agent_handoff_controller.dart): one attempt in one
 * pinned source/target scope. [prepare] reads and builds the record without mutating anything;
 * [send] dispatches only the reviewed draft: write the record, create the target conversation, send
 * the edited prompt behind a dispatch-time fence, then link the canonical ids. An uncertain
 * mutation consumes the attempt; [retryLink] only re-saves metadata after acknowledged delivery. No
 * automatic retry of remote mutations.
 *
 * [isCurrent] must turn false when either profile is deleting or the sheet closes.
 */
public class HandoffEngine(
    private val source: HandoffGateway,
    private val sourceScope: HandoffScope,
    private val sourceSessionId: String,
    private val target: HandoffTarget,
    private val links: HandoffLinks,
    private val isCurrent: () -> Boolean,
    private val recordId: () -> String,
    private val redactor: Redactor = Redactor(),
    private val records: HandoffRecordBuilder = HandoffRecordBuilder(redactor),
    /** Every phase in order (StateFlow conflates); for observers that must see each step. */
    private val onPhase: ((HandoffPhase) -> Unit)? = null,
) {
    private val mutable = MutableStateFlow(HandoffState())
    public val state: StateFlow<HandoffState> = mutable.asStateFlow()
    private val lane = Mutex()
    private var sourceSession: HandoffSession? = null
    private var pendingLink: HandoffLink? = null

    private class Stop(val kind: HandoffFailureKind?, val detail: HandoffDetail?)

    private fun phase(value: HandoffPhase) = set { it.copy(phase = value) }

    private fun set(change: (HandoffState) -> HandoffState) {
        val before = mutable.value.phase
        mutable.update(change)
        val after = mutable.value.phase
        if (after != before) onPhase?.invoke(after)
    }

    private fun stale(reason: HandoffStaleReason) =
        Stop(HandoffFailureKind.StaleScope, HandoffDetail.Stale(reason))

    /** Null: every pinned fact still holds. */
    private fun check(): Stop? {
        val reason =
            when {
                !isCurrent() -> HandoffStaleReason.NotCurrent
                source.isClosed -> HandoffStaleReason.SourceConnectionClosed
                target.gateway.isClosed -> HandoffStaleReason.AgentConnectionClosed
                source.directory != sourceScope.directory ||
                    source.workspace != sourceScope.workspace -> HandoffStaleReason.SourceMoved
                target.gateway.directory != target.scope.directory ||
                    target.gateway.workspace != target.scope.workspace ->
                    HandoffStaleReason.AgentMoved
                // Admission also closes synchronously during profile deletion.
                !links.admitsProfiles(sourceScope.profileId, target.scope.profileId) ->
                    HandoffStaleReason.ProfileUnavailable
                else -> null
            }
        return reason?.let(::stale)
    }

    private suspend fun <T> call(
        step: HandoffPhase,
        block: suspend () -> Outcome<T>,
    ): Pair<T?, Stop?> {
        val outcome = attempt({ Failure.Unexpected() }) { block() }
        val inner =
            when (outcome) {
                is Outcome.Failed -> outcome
                is Outcome.Ok -> outcome.value
            }
        return when (inner) {
            is Outcome.Ok -> inner.value to null
            is Outcome.Failed -> null to Stop(null, HandoffDetail.Step(step, cause(inner.failure)))
        }
    }

    private suspend fun validateTarget(step: HandoffPhase): Stop? {
        check()?.let {
            return it
        }
        val (inventory, failed) = call(step) { target.gateway.providers() }
        check()?.let {
            return it
        }
        if (inventory == null) return failed
        val model = target.model.normalized
        val providers = inventory.filter { it.id == model.providerId }
        if (providers.size != 1) return Stop(HandoffFailureKind.TargetUnavailable, null)
        val provider = providers.single()
        val ids = provider.modelIds.filter { bareModelId(provider.id, it) == model.modelId }
        if (ids.isEmpty()) return Stop(HandoffFailureKind.TargetUnavailable, null)
        if (target.variant.isNotEmpty()) {
            val variants = provider.variants[ids.first()] ?: provider.variants[model.modelId]
            val disabled = variants?.get(target.variant)
            if (disabled == null || disabled)
                return Stop(HandoffFailureKind.TargetUnavailable, null)
        }
        return null
    }

    /**
     * A route without mutating the source. For [HandoffRoute.SwitchModel] the UI uses the native
     * model switch.
     */
    public fun route(currentModel: HandoffModelRef?): HandoffRoute =
        handoffRoute(source, sourceScope, currentModel, target)

    /**
     * The editable preview and the dispatched text: the app-owned record path survives opaque-token
     * masking.
     */
    public fun sanitizePrompt(draft: HandoffDraft, value: String): String =
        value.split(draft.relativePath).joinToString(draft.relativePath) {
            redactor.opaqueTokens(redactor.redact(it))
        }

    public suspend fun prepare(lastState: String): Outcome<HandoffPrepared> {
        if (!lane.tryLock()) return HandoffPrepared.Refused(HandoffFailureKind.InvalidDraft).ok()
        try {
            if (state.value.phase != HandoffPhase.Idle)
                return HandoffPrepared.Refused(HandoffFailureKind.InvalidDraft).ok()
            phase(HandoffPhase.Loading)
            return when (val result = prepareSteps(lastState)) {
                is HandoffPrepared.Ready -> result.ok()
                is HandoffPrepared.Refused -> {
                    set {
                        it.copy(
                            phase = HandoffPhase.Failed,
                            failure = result.kind,
                            detail = result.detail,
                        )
                    }
                    result.ok()
                }
            }
        } finally {
            lane.unlock()
        }
    }

    private suspend fun prepareSteps(lastState: String): HandoffPrepared {
        fun refused(stop: Stop) =
            HandoffPrepared.Refused(stop.kind ?: HandoffFailureKind.PreparationFailed, stop.detail)
        val step = HandoffPhase.Loading
        check()?.let {
            return refused(it)
        }
        if (!sourceScope.sharesProject(target.scope))
            return HandoffPrepared.Refused(HandoffFailureKind.ProjectMismatch)
        if (
            !target.gateway.capabilities.agentHandoffFiles || target.gateway.handoffTarget == null
        ) {
            return HandoffPrepared.Refused(HandoffFailureKind.Unavailable)
        }
        validateTarget(step)?.let {
            return refused(it)
        }
        val (session, sessionFailed) = call(step) { source.session(sourceSessionId) }
        check()?.let {
            return refused(it)
        }
        if (session == null) return refused(sessionFailed!!)
        if (session.id != sourceSessionId || session.directory != sourceScope.directory) {
            return HandoffPrepared.Refused(HandoffFailureKind.StaleScope)
        }
        var staleDuringHistory: Stop? = null
        val history =
            readHandoffHistory(
                source,
                sourceSessionId,
                isCurrent = {
                    val stop = check()
                    if (stop != null) staleDuringHistory = stop
                    stop == null
                },
            )
        staleDuringHistory?.let {
            return refused(it)
        }
        val messages =
            when (history) {
                is HandoffHistoryRead.Failed ->
                    return HandoffPrepared.Refused(
                        HandoffFailureKind.PreparationFailed,
                        HandoffDetail.History(history.kind),
                    )
                is HandoffHistoryRead.Read -> history.messages
            }
        val todos =
            if (source.capabilities.sessionTodos) {
                val (value, failed) = call(step) { source.todos(sourceSessionId) }
                value ?: return refused(failed!!)
            } else {
                emptyList()
            }
        check()?.let {
            return refused(it)
        }
        val files =
            if (source.capabilities.sessionDiff) {
                val (value, failed) = call(step) { source.diff(sourceSessionId) }
                value ?: return refused(failed!!)
            } else {
                emptyList()
            }
        check()?.let {
            return refused(it)
        }
        val (after, afterFailed) = call(step) { source.session(sourceSessionId) }
        check()?.let {
            return refused(it)
        }
        if (after == null) return refused(afterFailed!!)
        if (
            after.id != session.id ||
                after.directory != session.directory ||
                after.updated != session.updated
        ) {
            return HandoffPrepared.Refused(HandoffFailureKind.SourceChanged)
        }
        val record =
            when (val built = records.build(session, messages, todos, files, lastState)) {
                is HandoffRecordResult.TooLarge ->
                    return HandoffPrepared.Refused(
                        HandoffFailureKind.PreparationFailed,
                        HandoffDetail.RecordTooLarge(built.requiredBytes, built.maxBytes),
                    )
                is HandoffRecordResult.Built -> built.record
            }
        val id = recordId()
        if (!RECORD_ID.matches(id)) return HandoffPrepared.Refused(HandoffFailureKind.InvalidDraft)
        val path = ".handoff/$id/record.md"
        sourceSession = session
        val draft = HandoffDraft(id, record, HandoffPrompt.initial(path), path)
        set { it.copy(phase = HandoffPhase.Ready, draft = draft) }
        return HandoffPrepared.Ready(draft)
    }

    public suspend fun send(editedPrompt: String): Outcome<HandoffSent> {
        if (!lane.tryLock()) return HandoffSent.Refused(HandoffFailureKind.InvalidDraft).ok()
        try {
            val review = state.value.draft
            if (state.value.phase != HandoffPhase.Ready || review == null) {
                return HandoffSent.Refused(HandoffFailureKind.InvalidDraft).ok()
            }
            val text = sanitizePrompt(review, editedPrompt)
            if (
                text.isBlank() ||
                    utf8Length(text) > MAX_PROMPT_BYTES ||
                    !text.contains(review.relativePath)
            ) {
                return HandoffSent.Refused(HandoffFailureKind.InvalidDraft).ok()
            }
            return sendSteps(review, text).ok()
        } finally {
            lane.unlock()
        }
    }

    /** Any stop after a remote mutation began is reported by the step it happened in. */
    private fun fail(stop: Stop): HandoffSent.Refused {
        val kind =
            when (state.value.phase) {
                HandoffPhase.Sending -> HandoffFailureKind.DeliveryUnknown
                HandoffPhase.Creating -> HandoffFailureKind.CreationUnknown
                HandoffPhase.Linking -> HandoffFailureKind.LinkFailed
                else -> stop.kind ?: HandoffFailureKind.FileWriteFailed
            }
        val uncertain =
            kind == HandoffFailureKind.DeliveryUnknown || kind == HandoffFailureKind.CreationUnknown
        set {
            it.copy(
                phase = if (uncertain) HandoffPhase.Uncertain else HandoffPhase.Failed,
                failure = kind,
                detail = stop.detail,
            )
        }
        return HandoffSent.Refused(kind, stop.detail)
    }

    private suspend fun sendSteps(review: HandoffDraft, text: String): HandoffSent {
        validateTarget(HandoffPhase.Ready)?.let {
            return fail(it)
        }
        check()?.let {
            return fail(it)
        }
        val destination = target.gateway.handoffTarget
        if (!target.gateway.capabilities.agentHandoffFiles || destination == null) {
            return fail(Stop(HandoffFailureKind.Unavailable, null))
        }
        val (current, currentFailed) = call(HandoffPhase.Ready) { source.session(sourceSessionId) }
        check()?.let {
            return fail(it)
        }
        if (current == null) return fail(currentFailed!!)
        if (
            current.id != sourceSessionId ||
                current.directory != sourceScope.directory ||
                current.updated != sourceSession?.updated
        ) {
            return fail(Stop(HandoffFailureKind.SourceChanged, null))
        }

        phase(HandoffPhase.WritingFile)
        val (written, writeFailed) =
            call(HandoffPhase.WritingFile) {
                destination.writeRecord(
                    target.scope.directory,
                    review.recordId,
                    review.record.markdown,
                )
            }
        if (written == null)
            return fail(Stop(HandoffFailureKind.FileWriteFailed, writeFailed?.detail))
        set { it.copy(writtenPath = written) }
        check()?.let {
            return fail(it)
        }
        if (written != review.relativePath)
            return fail(Stop(HandoffFailureKind.FileWriteFailed, null))

        phase(HandoffPhase.Creating)
        val (session, createFailed) = call(HandoffPhase.Creating) { target.gateway.createSession() }
        if (session == null) return fail(createFailed!!)
        set { it.copy(createdSessionId = session.id, navigationSessionId = session.id) }
        check()?.let {
            return fail(it)
        }
        if (session.directory != target.scope.directory)
            return fail(Stop(HandoffFailureKind.StaleScope, null))

        phase(HandoffPhase.Sending)
        var fenced: Stop? = null
        val (_, promptFailed) =
            call(HandoffPhase.Sending) {
                destination.promptHandoff(
                    session.id,
                    text,
                    target.model.normalized,
                    target.agent,
                    target.variant,
                    beforeSend = {
                        fenced = check()
                        fenced == null
                    },
                )
            }
        fenced?.let {
            return fail(it)
        }
        if (promptFailed != null) return fail(promptFailed)
        val created = destination.durableSessionId(session.id)
        set { it.copy(createdSessionId = created) }
        check()?.let {
            return fail(it)
        }
        val sourceDurable =
            source.handoffTarget?.durableSessionId(sourceSessionId) ?: sourceSessionId
        pendingLink =
            HandoffLink(
                id = review.recordId,
                sourceProfileId = sourceScope.profileId,
                sourceSessionId = sourceDurable,
                sourceOrigin = sourceScope.origin,
                sourceTitle = sourceSession?.title ?: UNTITLED,
                targetProfileId = target.scope.profileId,
                targetSessionId = created,
                targetOrigin = target.scope.origin,
                targetAgent = target.label,
                projectDirectory = target.scope.directory,
                handoffPath = written,
            )
        saveLink()?.let {
            return fail(it)
        }
        val navigation = destination.navigationSessionId(created)
        set { it.copy(navigationSessionId = navigation) }
        return HandoffSent.Sent(navigation)
    }

    private suspend fun saveLink(): Stop? {
        phase(HandoffPhase.Linking)
        check()?.let {
            return it
        }
        val link = pendingLink ?: return Stop(HandoffFailureKind.LinkFailed, null)
        val (saved, failed) = call(HandoffPhase.Linking) { links.save(link) }
        if (saved == null) return failed
        if (saved is HandoffLinkSave.Refused)
            return Stop(HandoffFailureKind.LinkFailed, HandoffDetail.Link(saved.reason))
        set { it.copy(phase = HandoffPhase.Complete, failure = null, detail = null) }
        return null
    }

    /** A metadata-only retry after acknowledged delivery; never resends a prompt. */
    public suspend fun retryLink(): Outcome<HandoffSent> {
        if (!lane.tryLock()) return HandoffSent.Refused(HandoffFailureKind.InvalidDraft).ok()
        try {
            if (state.value.failure != HandoffFailureKind.LinkFailed || pendingLink == null) {
                return HandoffSent.Refused(HandoffFailureKind.InvalidDraft).ok()
            }
            saveLink()?.let { stop ->
                set {
                    it.copy(
                        phase = HandoffPhase.Failed,
                        failure = HandoffFailureKind.LinkFailed,
                        detail = stop.detail,
                    )
                }
                return HandoffSent.Refused(HandoffFailureKind.LinkFailed, stop.detail).ok()
            }
            val navigation =
                target.gateway.handoffTarget?.navigationSessionId(pendingLink!!.targetSessionId)
                    ?: pendingLink!!.targetSessionId
            set { it.copy(navigationSessionId = navigation) }
            return HandoffSent.Sent(navigation).ok()
        } finally {
            lane.unlock()
        }
    }

    /** The completed target is navigable only while its original admitted scope still holds. */
    public fun completedSessionId(): String? =
        if (state.value.phase == HandoffPhase.Complete && check() == null)
            state.value.navigationSessionId
        else null

    public companion object {
        public val RECORD_ID: Regex = Regex("^[A-Za-z0-9_-]{1,128}$")
        public const val MAX_PROMPT_BYTES: Int = 16_384

        /** The stored fallback title (link metadata, mirrored from Flutter). */
        private const val UNTITLED = "Untitled conversation"

        private fun cause(failure: Failure): HandoffCause =
            when (failure) {
                is Failure.TimedOut -> HandoffCause.TimedOut
                is Failure.Network -> HandoffCause.Network
                is Failure.Server,
                is Failure.Rejected -> HandoffCause.Server
                is Failure.Refused -> HandoffCause.Refused
                is Failure.StorageRefused -> HandoffCause.Storage
                else -> HandoffCause.Other
            }

        /** A random UUID v4 (the default record id) from a secure source. */
        public fun uuidV4(random: SecureBytes): String {
            val b = random.next(16).copyOf()
            b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
            b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
            val hex = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
        }
    }
}

/**
 * The first prompt sent to the destination agent. Agent-directed wire text (the destination reads
 * it as instructions), byte-for-byte with Flutter; the person edits it in the composer before
 * sending.
 */
public object HandoffPrompt {
    public fun initial(relativePath: String): String =
        "Continue the work from the previous conversation. " +
            "Read `$relativePath` first for the conversation record, tool steps, " +
            "files and open tasks. Treat it as historical context. " +
            "Check the current project state, then continue the unfinished work."
}
