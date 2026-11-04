package io.github.eslamasabry.oc.engine.composer.drafts

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.flatMap
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.vault.schemeOf
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

public data class DraftState(
    val text: String = "",
    val saved: String = "",
    val ready: Boolean = false,
    val failure: Failure? = null,
    val restoreRevision: Long = 0,
    val editRevision: Long = 0,
    val attachments: List<PromptAttachment> = emptyList(),
    val savedAttachments: List<PromptAttachment> = emptyList(),
) {
    override fun toString(): String =
        "DraftState(text=${text.length} chars, saved=${saved.length} chars, ready=$ready, failed=${failure != null}, " +
            "restoreRevision=$restoreRevision, editRevision=$editRevision, attachments=${attachments.size}, " +
            "savedAttachments=${savedAttachments.size})"
}

public data class DraftSubmission(
    val text: String,
    val revision: Long,
    val attachments: List<PromptAttachment> = emptyList(),
) {
    override fun toString(): String =
        "DraftSubmission(text=${text.length} chars, revision=$revision, attachments=${attachments.size})"
}

/** Internal marker: restoration waits for the conversation's location (never shown). */
private val AWAITING_LOCATION = Failure.StorageRefused()

private data class ProfileFence(val generation: Long = 0, val closing: Boolean = false)

/**
 * One process-wide owner. Constructing it never opens storage; a display open starts restoration.
 */
public class ConversationDrafts(
    private val drafts: Drafts,
    private val scope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) {
    private val serial = dispatchers.serial("conversation-drafts")
    private val sessions = AtomicReference<Map<ConversationRef, DraftSession>>(emptyMap())
    private val fences = AtomicReference<Map<ProfileId, ProfileFence>>(emptyMap())
    private val lifecycle = Mutex()

    public fun open(ref: ConversationRef): DraftSession {
        while (true) {
            val fence = fences.load()[ref.profileId] ?: ProfileFence()
            val before = sessions.load()
            val existing = before[ref]
            if (existing != null) {
                if (existing.generation == fence.generation && existing.reopen()) return existing
                existing.fenceProfile()
                sessions.compareAndSet(before, before - ref)
                continue
            }
            val session = DraftSession(this, ref, drafts, scope, serial, fence.generation)
            if (sessions.compareAndSet(before, before + (ref to session))) {
                session.start()
                return session
            }
        }
    }

    internal fun active(profile: ProfileId, generation: Long): Boolean =
        (fences.load()[profile] ?: ProfileFence()).let {
            !it.closing && it.generation == generation
        }

    internal fun releaseEmpty(ref: ConversationRef, session: DraftSession) {
        while (true) {
            val before = sessions.load()
            if (before[ref] !== session || !session.retireIfEmpty()) return
            if (sessions.compareAndSet(before, before - ref)) return
        }
    }

    public suspend fun closeProfile(profileId: ProfileId): Outcome<Unit> {
        // This fence precedes dispatcher admission and every suspension, including storage waits.
        while (true) {
            val before = fences.load()
            val previous = before[profileId] ?: ProfileFence()
            if (
                fences.compareAndSet(
                    before,
                    before + (profileId to ProfileFence(previous.generation + 1, true)),
                )
            )
                break
        }
        sessions
            .load()
            .filterKeys { it.profileId == profileId }
            .values
            .forEach { it.fenceProfile() }
        return withContext(serial) {
            lifecycle.withLock {
                attempt({ Failure.StorageRefused() }) {
                        if (drafts.closeProfile(profileId.raw)) Unit.ok()
                        else Failure.StorageRefused().failed()
                    }
                    .flatMap { it }
            }
        }
    }

    /** Explicit recovery only after a refused removal; old handles never regain write authority. */
    public suspend fun reopenProfile(profileId: ProfileId): Outcome<Unit> =
        withContext(serial) {
            lifecycle.withLock {
                val fence = fences.load()[profileId] ?: ProfileFence()
                val readable =
                    attempt({ Failure.StorageRefused() }) {
                            when {
                                !drafts.hasProfile(profileId.raw) ->
                                    Failure.Refused(RefusalReason.ProfileDeleted).failed()
                                !drafts.readable -> Failure.StorageRefused().failed()
                                else -> Unit.ok()
                            }
                        }
                        .flatMap { it }
                if (readable is Outcome.Failed) return@withLock readable
                if (!fence.closing) return@withLock Unit.ok()
                sessions
                    .load()
                    .filterKeys { it.profileId == profileId }
                    .values
                    .forEach { it.fenceProfile() }
                drafts.reopenProfile(profileId.raw)
                while (true) {
                    val before = fences.load()
                    if (before[profileId] != fence)
                        return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
                    if (
                        fences.compareAndSet(
                            before,
                            before + (profileId to ProfileFence(fence.generation + 1, false)),
                        )
                    )
                        break
                }
                Unit.ok()
            }
        }
}

private data class SessionSnapshot(
    val public: DraftState = DraftState(),
    val revision: Long = 0,
    val closed: Boolean = false,
    val fenced: Boolean = false,
    val retired: Boolean = false,
    val clearPending: Boolean = false,
    val fileCommitPending: Boolean = false,
    val submissions: List<DraftSubmission> = emptyList(),
    /** Where this conversation lives; null until its connection names it. */
    val location: DraftLocation? = null,
    /** Restoration waits for [location]: the saved draft holds a `file:` reference. */
    val awaitingLocation: Boolean = false,
)

/**
 * Direct view: value and capture read the same atomic revision, without another collector or a
 * lagging cache.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class PublicDraftState(private val source: StateFlow<SessionSnapshot>) :
    StateFlow<DraftState> {
    override val value: DraftState
        get() = source.value.public

    override val replayCache: List<DraftState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<DraftState>): Nothing =
        source.collect(
            object : FlowCollector<SessionSnapshot> {
                private var previous: DraftState? = null

                override suspend fun emit(value: SessionSnapshot) {
                    if (previous != value.public) {
                        previous = value.public
                        collector.emit(value.public)
                    }
                }
            }
        )
}

public class DraftSession
internal constructor(
    private val owner: ConversationDrafts,
    private val ref: ConversationRef,
    private val drafts: Drafts,
    private val scope: AppCoroutineScope,
    private val serial: CoroutineDispatcher,
    internal val generation: Long,
) {
    private val snapshots = MutableStateFlow(SessionSnapshot())
    public val state: StateFlow<DraftState> = PublicDraftState(snapshots)
    private val autosave =
        DraftAutosave(
                CoroutineScope(scope.coroutineContext + serial),
                drafts,
                ref.profileId.raw,
                ref.sessionId.raw,
            )
            .apply {
                onAcknowledged = ::acknowledged
                canWriteRevision = {
                    !snapshots.value.fenced && owner.active(ref.profileId, generation)
                }
                currentAttachments = { snapshots.value.public.attachments }
            }

    internal fun start() {
        scope.launch(serial) { restore() }
    }

    internal fun reopen(): Boolean {
        while (true) {
            val before = snapshots.value
            if (before.retired) return false
            if (snapshots.compareAndSet(before, before.copy(closed = false))) {
                if (!owner.active(ref.profileId, generation)) fenceProfile()
                return true
            }
        }
    }

    internal fun retireIfEmpty(): Boolean {
        while (true) {
            val before = snapshots.value
            if (before.retired) return true
            if (
                !before.closed ||
                    before.clearPending ||
                    before.fileCommitPending ||
                    before.submissions.isNotEmpty() ||
                    !before.public.ready ||
                    before.public.text.isNotEmpty() ||
                    before.public.saved.isNotEmpty() ||
                    before.public.failure != null ||
                    before.public.attachments.isNotEmpty() ||
                    before.public.savedAttachments.isNotEmpty()
            )
                return false
            if (snapshots.compareAndSet(before, before.copy(retired = true))) return true
        }
    }

    private fun deleted(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))

    private fun unavailable(): Outcome.Failed = Outcome.Failed(Failure.StorageRefused())

    private suspend fun restore(): Outcome<Unit> {
        if (!owner.active(ref.profileId, generation) || snapshots.value.fenced) {
            fenceProfile()
            return deleted()
        }
        if (snapshots.value.clearPending) return unavailable()
        if (snapshots.value.public.ready) return Unit.ok()
        val loaded =
            attempt({ Failure.StorageRefused() }) {
                    if (!drafts.profileAvailable(ref.profileId.raw)) return@attempt deleted()
                    if (!drafts.readable) return@attempt unavailable()
                    val saved = drafts.draft(ref.profileId.raw, ref.sessionId.raw)
                    val restored =
                        drafts.restoreFor(ref.profileId.raw, ref.sessionId.raw, emptyList())
                    // A draft that waits as an unconfirmed send keeps its files back too: putting
                    // them one tap away would invite a duplicate send.
                    val waiting = restored.text == null && !saved?.text.isNullOrEmpty()
                    val place = snapshots.value.location
                    val files =
                        if (!waiting && saved?.attachments?.isNotEmpty() == true) {
                            // A `file:` reference means something only where it was saved: wait for
                            // the conversation's own location instead of guessing it.
                            if (
                                place == null &&
                                    saved.attachments.any { schemeOf(it.url.orEmpty()) == "file" }
                            )
                                return@attempt AWAITING_LOCATION.failed()
                            val recovery =
                                drafts.restoreAttachments(
                                    ref.profileId.raw,
                                    ref.sessionId.raw,
                                    place ?: DraftLocation(null, null),
                                )
                            // A payload that cannot be recovered blocks the session: half a draft
                            // must never overwrite the saved one.
                            if (recovery.unavailable.isNotEmpty()) return@attempt unavailable()
                            recovery.attachments
                        } else {
                            emptyList()
                        }
                    Triple(restored.text.orEmpty(), saved?.text.orEmpty(), files).ok()
                }
                .flatMap { it }
        snapshots.update { before ->
            if (
                before.fenced ||
                    !owner.active(ref.profileId, generation) ||
                    before.public.ready ||
                    before.clearPending
            )
                before
            else
                when (loaded) {
                    is Outcome.Failed ->
                        if (loaded.failure === AWAITING_LOCATION)
                            before.copy(awaitingLocation = true)
                        else
                            before.copy(
                                public = before.public.copy(failure = loaded.failure),
                                awaitingLocation = false,
                            )
                    is Outcome.Ok ->
                        before.copy(
                            public =
                                DraftState(
                                    text = loaded.value.first,
                                    saved = loaded.value.second,
                                    ready = true,
                                    attachments = loaded.value.third,
                                    savedAttachments = loaded.value.third,
                                    restoreRevision = before.public.restoreRevision + 1,
                                    editRevision = before.revision + 1,
                                ),
                            revision = before.revision + 1,
                            awaitingLocation = false,
                        )
                }
        }
        if (loaded is Outcome.Failed && loaded.failure === AWAITING_LOCATION) return unavailable()
        return if (snapshots.value.fenced || !owner.active(ref.profileId, generation)) deleted()
        else
            loaded.let {
                when (it) {
                    is Outcome.Ok -> Unit.ok()
                    is Outcome.Failed -> it
                }
            }
    }

    /**
     * The conversation's directory/workspace as its connection names it. Whole-state writes record
     * it; a draft holding a `file:` reference restores only once it is known, and only in the same
     * place (elsewhere it fails closed and the saved draft is kept).
     */
    public fun bindLocation(location: DraftLocation) {
        while (true) {
            val before = snapshots.value
            if (before.location == location) return
            if (snapshots.compareAndSet(before, before.copy(location = location))) break
        }
        if (snapshots.value.awaitingLocation) scope.launch(serial) { restore() }
    }

    public fun changed(text: String) {
        changeText(text)
    }

    /**
     * Installs explicitly reviewed context only while the captured draft is unchanged. Advancing
     * the restore revision asks a still-mounted field to adopt this acknowledged external edit.
     */
    public fun replaceReviewedText(expectedEditRevision: Long, text: String): Boolean =
        changeText(text, expectedEditRevision)

    private fun changeText(text: String, expectedEditRevision: Long? = null): Boolean {
        while (true) {
            val before = snapshots.value
            if (
                before.fenced ||
                    (expectedEditRevision != null &&
                        before.public.editRevision != expectedEditRevision) ||
                    before.closed ||
                    before.retired ||
                    !owner.active(ref.profileId, generation) ||
                    !before.public.ready ||
                    text == before.public.text
            )
                return false
            if (
                snapshots.compareAndSet(
                    before,
                    before.copy(
                        public =
                            before.public.copy(
                                text = text,
                                editRevision = before.revision + 1,
                                restoreRevision =
                                    before.public.restoreRevision +
                                        if (expectedEditRevision != null) 1 else 0,
                            ),
                        revision = before.revision + 1,
                        submissions = emptyList(),
                    ),
                )
            )
                break
        }
        scope.launch(serial) {
            val current = snapshots.value
            if (
                !current.fenced &&
                    !current.closed &&
                    current.public.ready &&
                    owner.active(ref.profileId, generation) &&
                    !(current.clearPending && current.public.text.isEmpty())
            ) {
                autosave.changedRevision(current.public.text, current.revision)
            }
        }
        return true
    }

    private fun acknowledged(revision: Long, text: String, result: Outcome<Unit>) {
        snapshots.update { before ->
            if (
                before.fenced ||
                    !owner.active(ref.profileId, generation) ||
                    revision > before.revision
            )
                before
            else
                when (result) {
                    is Outcome.Ok ->
                        before.copy(
                            public =
                                before.public.copy(
                                    saved = text,
                                    savedAttachments = before.public.attachments,
                                    failure = null,
                                    ready = true,
                                ),
                            clearPending = false,
                            fileCommitPending = false,
                        )
                    is Outcome.Failed ->
                        before.copy(public = before.public.copy(failure = result.failure))
                }
        }
        owner.releaseEmpty(ref, this)
    }

    public suspend fun flush(): Outcome<Unit> =
        withContext(serial) {
            if (!owner.active(ref.profileId, generation) || snapshots.value.fenced)
                return@withContext deleted()
            if (
                snapshots.value.clearPending &&
                    snapshots.value.public.text.isEmpty() &&
                    snapshots.value.public.attachments.isEmpty()
            )
                return@withContext retryClear()
            if (snapshots.value.clearPending && snapshots.value.public.text.isEmpty())
                return@withContext commitAttachments(snapshots.value.public.attachments)
            if (!snapshots.value.public.ready) {
                val restored = restore()
                if (restored is Outcome.Failed) return@withContext restored
            }
            val current = snapshots.value
            if (
                current.public.text == current.public.saved &&
                    current.public.attachments == current.public.savedAttachments &&
                    current.public.failure == null &&
                    !current.fileCommitPending
            )
                return@withContext Unit.ok()
            if (
                current.public.attachments != current.public.savedAttachments ||
                    current.fileCommitPending
            )
                return@withContext commitAttachments(current.public.attachments)
            autosave.changedRevision(current.public.text, current.revision)
            autosave.flush()
        }

    /**
     * Replaces the composer's files with an immutable snapshot of [files] (full composer,
     * 2026-11-02). The whole state — the current text and the files — is persisted through the
     * vault and the index before the accepted chips are published; a refusal keeps the previous
     * chips, the stored draft and newer text edits untouched. A committed edit invalidates old
     * submission tokens, and delayed results obey the profile fence.
     */
    public suspend fun replaceAttachments(files: List<PromptAttachment>): Outcome<Unit> {
        val requested = files.toList()
        return withContext(serial) {
            if (!owner.active(ref.profileId, generation) || snapshots.value.fenced)
                return@withContext deleted()
            if (!snapshots.value.public.ready && !snapshots.value.clearPending) {
                when (val restored = restore()) {
                    is Outcome.Failed -> return@withContext restored
                    is Outcome.Ok -> Unit
                }
            }
            if (snapshots.value.closed || snapshots.value.retired) return@withContext unavailable()
            commitAttachments(requested)
        }
    }

    /**
     * The one whole-state writer (file edits, their retries, the pending-clear supersede). It runs
     * on the serial lane inside the autosave writer fence, so a text save can never interleave with
     * it and the file snapshot a revision save keeps is always fresh.
     */
    private suspend fun commitAttachments(requested: List<PromptAttachment>): Outcome<Unit> =
        autosave.exclusive {
            if (!owner.active(ref.profileId, generation) || snapshots.value.fenced)
                return@exclusive deleted()
            val before = snapshots.value
            if (
                before.public.attachments == requested &&
                    before.public.savedAttachments == requested &&
                    !before.fileCommitPending &&
                    before.public.failure == null
            )
                return@exclusive Unit.ok()
            val text = before.public.text
            val result =
                attempt({ Failure.StorageRefused() }) {
                        drafts.saveRevision(
                            ref.profileId.raw,
                            ref.sessionId.raw,
                            text,
                            requested,
                            snapshots.value.location,
                        ) {
                            !snapshots.value.fenced && owner.active(ref.profileId, generation)
                        }
                    }
                    .flatMap { it }
            snapshots.update { state ->
                if (state.fenced || !owner.active(ref.profileId, generation)) state
                else
                    when (result) {
                        is Outcome.Ok ->
                            state.copy(
                                public =
                                    state.public.copy(
                                        attachments = requested,
                                        savedAttachments = requested,
                                        saved = text,
                                        ready = true,
                                        failure = null,
                                        editRevision = state.revision + 1,
                                    ),
                                revision = state.revision + 1,
                                clearPending = false,
                                fileCommitPending = false,
                                submissions = emptyList(),
                            )
                        is Outcome.Failed ->
                            state.copy(
                                public = state.public.copy(failure = result.failure),
                                fileCommitPending = true,
                            )
                    }
            }
            owner.releaseEmpty(ref, this@DraftSession)
            if (snapshots.value.fenced || !owner.active(ref.profileId, generation)) deleted()
            else result
        }

    public fun captureSubmission(): DraftSubmission? {
        while (true) {
            val before = snapshots.value
            if (
                !before.public.ready ||
                    before.public.failure != null ||
                    before.closed ||
                    before.fenced ||
                    before.clearPending ||
                    !owner.active(ref.profileId, generation) ||
                    (before.public.text.isEmpty() && before.public.attachments.isEmpty())
            )
                return null
            before.submissions
                .firstOrNull {
                    it.revision == before.revision &&
                        it.text == before.public.text &&
                        it.attachments == before.public.attachments
                }
                ?.let {
                    return it
                }
            val token =
                DraftSubmission(before.public.text, before.revision, before.public.attachments)
            if (
                snapshots.compareAndSet(
                    before,
                    before.copy(submissions = before.submissions + token),
                )
            )
                return token
        }
    }

    public suspend fun clearAfterSend(submission: DraftSubmission): Outcome<Unit> =
        withContext(serial) {
            if (!owner.active(ref.profileId, generation) || snapshots.value.fenced)
                return@withContext deleted()
            autosave.exclusive {
                while (true) {
                    val before = snapshots.value
                    if (before.submissions.none { it === submission }) return@exclusive Unit.ok()
                    if (before.fenced || !owner.active(ref.profileId, generation))
                        return@exclusive deleted()
                    val current =
                        before.revision == submission.revision &&
                            before.public.text == submission.text &&
                            before.public.attachments == submission.attachments
                    val next =
                        if (!current)
                            before.copy(
                                submissions = before.submissions.filterNot { it === submission }
                            )
                        else
                            before.copy(
                                public =
                                    before.public.copy(
                                        text = "",
                                        attachments = emptyList(),
                                        restoreRevision = before.public.restoreRevision + 1,
                                        editRevision = before.revision + 1,
                                    ),
                                revision = before.revision + 1,
                                clearPending = true,
                                submissions = before.submissions.filterNot { it === submission },
                            )
                    if (snapshots.compareAndSet(before, next)) {
                        if (!current) return@exclusive Unit.ok()
                        break
                    }
                }
                autosave.cancelPending()
                writeClear()
            }
        }

    private suspend fun retryClear(): Outcome<Unit> = autosave.exclusive {
        if (!owner.active(ref.profileId, generation) || snapshots.value.fenced)
            return@exclusive deleted()
        if (
            !snapshots.value.clearPending ||
                snapshots.value.public.text.isNotEmpty() ||
                snapshots.value.public.attachments.isNotEmpty()
        )
            return@exclusive Unit.ok()
        autosave.cancelPending()
        writeClear()
    }

    /**
     * Call only inside autosave's writer mutex; changes during suspension remain separate unsent
     * revisions.
     */
    private suspend fun writeClear(): Outcome<Unit> {
        val result =
            attempt({ Failure.StorageRefused() }) {
                    drafts.saveRevision(
                        ref.profileId.raw,
                        ref.sessionId.raw,
                        "",
                        emptyList(),
                        snapshots.value.location,
                    ) {
                        !snapshots.value.fenced && owner.active(ref.profileId, generation)
                    }
                }
                .flatMap { it }
        snapshots.update { before ->
            if (before.fenced || !owner.active(ref.profileId, generation)) before
            else
                when (result) {
                    is Outcome.Ok ->
                        before.copy(
                            public =
                                before.public.copy(
                                    saved = "",
                                    savedAttachments = emptyList(),
                                    ready = true,
                                    failure = null,
                                ),
                            clearPending = false,
                            fileCommitPending = false,
                        )
                    is Outcome.Failed ->
                        before.copy(
                            public =
                                before.public.copy(
                                    ready = before.public.text.isNotEmpty(),
                                    failure = result.failure,
                                )
                        )
                }
        }
        owner.releaseEmpty(ref, this)
        return result
    }

    internal fun fenceProfile() {
        snapshots.update { before ->
            before.copy(
                public =
                    before.public.copy(
                        ready = false,
                        failure = Failure.Refused(RefusalReason.ProfileDeleted),
                    ),
                fenced = true,
                closed = true,
                submissions = emptyList(),
            )
        }
        scope.launch(serial) { autosave.cancelPending() }
    }

    public fun close() {
        snapshots.update { it.copy(closed = true) }
        // Independent app-scope work survives disposal. The display's wait is bounded; a timeout
        // does not
        // cancel a platform write which was already admitted.
        scope.launch(serial) {
            val saving = scope.async(serial) { flush() }
            if (withTimeoutOrNull(5_000) { saving.await() } == null) {
                snapshots.update { before ->
                    if (before.fenced) before
                    else before.copy(public = before.public.copy(failure = Failure.TimedOut()))
                }
            }
            owner.releaseEmpty(ref, this@DraftSession)
        }
    }
}
