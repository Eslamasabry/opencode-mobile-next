package io.github.eslamasabry.opencode_mobile.graph

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.github.eslamasabry.oc.android.tiles.SharedStash
import io.github.eslamasabry.oc.android.tiles.StashLimits
import io.github.eslamasabry.oc.android.tiles.StashRefusal
import io.github.eslamasabry.oc.android.tiles.StashSlot
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.DraftSession
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.media.attach.AttachmentIntake
import io.github.eslamasabry.oc.engine.media.attach.AttachmentLimits
import io.github.eslamasabry.oc.engine.media.attach.ShareInFacts
import io.github.eslamasabry.oc.engine.media.attach.ShareInProblem
import io.github.eslamasabry.oc.engine.media.attach.ShareInResult
import io.github.eslamasabry.oc.engine.media.attach.SharedInput
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Only the phase leaves this owner. Shared words never appear in diagnostics or UI state. */
internal enum class IncomingSharePhase {
    Empty,
    Waiting,
    Opening,
    Failed,
    FailedAgain,
    Discarding,

    /** Files were shared in and something was left out: the status line says how many were added and why. */
    Noted,
}

/** What the "Noted" line says. Counts and a reason only; never a name or address. */
internal data class ShareInNote(val added: Int, val total: Int, val problem: ShareInProblem)

/** One incoming share, owned by the existing platform router. No prompt is sent automatically. */
internal class IncomingShare(
    private val context: Context,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val drafts: Set<ConversationDrafts>,
    private val navigator: AppNavigator,
    private val stash: SharedStash,
    private val intake: () -> AttachmentIntake?,
    /** `share_in`: a count range and image/file, through the opt-in telemetry. */
    private val report: (count: String, kind: String) -> Unit,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val io = dispatchers.io
    private val mutableNote = MutableStateFlow<ShareInNote?>(null)
    val note: StateFlow<ShareInNote?> = mutableNote
    private var slots: List<StashSlot>? = null
    private var converted: ShareInResult? = null
    private var attachmentsSaved = false
    private val mutablePhase = MutableStateFlow(IncomingSharePhase.Empty)
    val phase: StateFlow<IncomingSharePhase> = mutablePhase
    private var text: String? = null
    private var version = 0L
    private var opening = false
    private var failures = 0
    private var target: ConversationRef? = null
    private var draft: DraftSession? = null
    private var prepared: String? = null
    private var beforeDiscard = IncomingSharePhase.Failed

    init {
        // Copies left by a process that died mid-share belong to nobody; this runs before any share can arrive.
        stash.clear()
        scope.launch {
            combine(registry.live, runtime.busy, stores.profiles.activeId) { _, _, _ -> Unit }
                .collect { openIfReady() }
        }
    }

    /**
     * Pictures or files arrived. The copy into private storage starts at once (the read grant is temporary);
     * nothing is sent, and the attachments land in a new conversation's draft like shared text does.
     */
    fun arrivedStreams(shared: io.github.eslamasabry.oc.android.tiles.SharedStreams) {
        version++
        val generation = version
        text = shared.text
        slots = null
        converted = null
        attachmentsSaved = false
        target = null
        draft = null
        prepared = null
        failures = 0
        mutableNote.value = null
        mutablePhase.value = IncomingSharePhase.Waiting
        scope.launch {
            val copied =
                withContext(io) {
                    stash.copyIn(
                        shared.uris,
                        StashLimits(
                            AttachmentLimits.MAX_COUNT,
                            AttachmentLimits.MAX_BYTES,
                            AttachmentLimits.MAX_TOTAL_BYTES,
                        ),
                    ) + List(shared.refused) { StashSlot.Refused(StashRefusal.Unreadable) }
                }
            if (version != generation) {
                withContext(io) { stash.clear() }
                return@launch
            }
            slots = copied
            openIfReady()
        }
    }

    fun dismissNote() {
        if (mutablePhase.value != IncomingSharePhase.Noted) return
        mutableNote.value = null
        mutablePhase.value = IncomingSharePhase.Empty
    }

    fun arrived(words: String) {
        version++
        slots = null
        converted = null
        attachmentsSaved = false
        mutableNote.value = null
        text = words
        target = null
        draft = null
        prepared = null
        failures = 0
        mutablePhase.value = IncomingSharePhase.Waiting
        openIfReady()
    }

    fun retry() {
        if (mutablePhase.value !in setOf(IncomingSharePhase.Failed, IncomingSharePhase.FailedAgain))
            return
        mutablePhase.value = IncomingSharePhase.Waiting
        openIfReady()
    }

    fun copy() {
        val words = text ?: return
        context
            .getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("", words))
    }

    fun discard() {
        if (opening || (text == null && slots == null)) return
        val now = mutablePhase.value
        if (now == IncomingSharePhase.Discarding || now == IncomingSharePhase.Empty) return
        beforeDiscard = now
        mutablePhase.value = IncomingSharePhase.Discarding
    }

    /** Undo restores what was shown before Discard; it is not counted as another failure. */
    fun undoDiscard() {
        if (mutablePhase.value != IncomingSharePhase.Discarding) return
        mutablePhase.value = beforeDiscard
        if (beforeDiscard == IncomingSharePhase.Waiting) openIfReady()
    }

    fun commitDiscard() {
        if (mutablePhase.value != IncomingSharePhase.Discarding) return
        version++
        text = null
        slots = null
        converted = null
        scope.launch { withContext(io) { stash.clear() } }
        target = null
        draft = null
        prepared = null
        mutablePhase.value = IncomingSharePhase.Empty
    }

    private fun failed() {
        failures++
        mutablePhase.value =
            if (failures > 1) IncomingSharePhase.FailedAgain else IncomingSharePhase.Failed
    }

    private fun openIfReady() {
        if (opening || mutablePhase.value != IncomingSharePhase.Waiting || runtime.busy.value)
            return
        val words = text
        if (words == null && slots == null) return
        val profile = target?.profileId ?: stores.profiles.activeId.value ?: return
        if (stores.profiles.activeId.value != profile) return
        val graph = registry.live.value[profile] ?: return
        val saved = stores.profiles.profile(profile)?.profile ?: return
        val location = graph.session.server.location.value
        val generation = version
        fun current(): Boolean =
            version == generation &&
                registry.live.value[profile] === graph &&
                stores.profiles.activeId.value == profile &&
                stores.profiles.profile(profile)?.profile == saved &&
                graph.session.server.location.value == location
        opening = true
        mutablePhase.value = IncomingSharePhase.Opening
        scope.launch {
            try {
                val result =
                    attempt({ Failure.Unexpected() }) {
                        if (!current()) return@attempt false
                        val shared = slots
                        if (shared != null && converted == null) {
                            val result = convert(shared) ?: return@attempt false
                            if (!current()) return@attempt false
                            converted = result
                            if (result.added.isEmpty() && words == null) {
                                // Nothing can be attached and there are no words: do not open an empty conversation.
                                finishWithNote(result)
                                return@attempt true
                            }
                        }
                        val ref =
                            target
                                ?: when (
                                    val created = graph.session.server.createSession(NewSession())
                                ) {
                                    is Outcome.Failed -> return@attempt false
                                    is Outcome.Ok ->
                                        ConversationRef(profile, created.value.id).also {
                                            if (current()) target = it
                                        }
                                }
                        if (!current()) return@attempt false
                        val owner = drafts.singleOrNull() ?: return@attempt false
                        val session = draft ?: owner.open(ref).also { draft = it }
                        val ready =
                            withTimeoutOrNull(10_000) { session.state.first { it.ready } }
                                ?: return@attempt false
                        if (!current()) return@attempt false
                        val added = converted?.added.orEmpty()
                        if (added.isNotEmpty() && !attachmentsSaved) {
                            val next = converted?.draftWith(ready.attachments)
                            if (next != null && session.replaceAttachments(next) !is Outcome.Ok)
                                return@attempt false
                            attachmentsSaved = true
                        }
                        val previous = prepared
                        if (words == null) {
                            // Files only: the draft text is left exactly as it was.
                        } else if (previous == null) {
                            val merged =
                                if (ready.text.isBlank()) words
                                else ready.text.trimEnd() + "\n\n" + words
                            prepared = merged
                            session.changed(merged)
                        } else if (ready.text != previous) {
                            // The person edited the draft after a failed save. Never overwrite that
                            // edit or append twice.
                            return@attempt false
                        }
                        if (session.flush() !is Outcome.Ok || !current()) return@attempt false
                        val summary = converted
                        text = null
                        target = null
                        draft = null
                        prepared = null
                        slots = null
                        converted = null
                        attachmentsSaved = false
                        withContext(io) { stash.clear() }
                        if (summary != null && summary.problem != null) noted(summary)
                        else mutablePhase.value = IncomingSharePhase.Empty
                        navigator.openChat(ref)
                        true
                    }
                if (version == generation && (result !is Outcome.Ok || !result.value)) failed()
            } finally {
                opening = false
                // A newer share that arrived during a call owns the next attempt.
                if (version != generation) openIfReady()
            }
        }
    }

    /** Reads the private copies and lets the composer's own intake decide what fits (limits, types, pictures scaled). */
    private suspend fun convert(shared: List<StashSlot>): ShareInResult? {
        val files = intake() ?: return null
        val inputs =
            withContext(io) {
                shared.map { slot ->
                    when (slot) {
                        is StashSlot.Refused ->
                            SharedInput.Refused(
                                when (slot.why) {
                                    StashRefusal.OverCount -> ShareInProblem.OverLimit
                                    StashRefusal.TooLarge -> ShareInProblem.TooLarge
                                    StashRefusal.Unreadable -> ShareInProblem.Unreadable
                                }
                            )
                        is StashSlot.Kept ->
                            try {
                                SharedInput.Ready(slot.stashed.name, slot.stashed.mime, slot.stashed.file.readBytes())
                            } catch (_: IOException) {
                                SharedInput.Refused(ShareInProblem.Unreadable)
                            }
                    }
                }
            }
        report(
            ShareInFacts.countRange(inputs.size),
            if (ShareInFacts.picturesOnly(inputs)) "image" else "file",
        )
        return files.fromShared(emptyList(), inputs)
    }

    private fun finishWithNote(result: ShareInResult) {
        slots = null
        converted = null
        text = null
        attachmentsSaved = false
        scope.launch { withContext(io) { stash.clear() } }
        noted(result)
    }

    private fun noted(result: ShareInResult) {
        val problem = result.problem ?: return
        mutableNote.value = ShareInNote(result.added.size, result.total, problem)
        mutablePhase.value = IncomingSharePhase.Noted
        val shown = mutableNote.value
        scope.launch {
            delay(NOTE_MILLIS)
            if (mutableNote.value === shown) dismissNote()
        }
    }

    private companion object {
        const val NOTE_MILLIS = 9_000L
    }
}
