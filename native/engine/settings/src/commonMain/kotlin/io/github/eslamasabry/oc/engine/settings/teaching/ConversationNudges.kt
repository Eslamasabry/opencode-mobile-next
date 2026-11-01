package io.github.eslamasabry.oc.engine.settings.teaching

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Everything the conversation nudges need, read from existing state by the conversation's ViewModel. Plain values,
 * so the trigger rules are testable without a screen.
 */
public data class ConversationNudgeFacts(
    /** The conversation has a run in progress. */
    val busy: Boolean,
    /** The transcript holds at least one assistant message. */
    val hasAssistantReply: Boolean,
    /** Requests waiting on the person: request id to permission kind. */
    val pendingPermissions: ImmutableMap<String, String> = persistentMapOf(),
    /** The Approvals control exists here and is not already automatic. */
    val approvalsAvailable: Boolean = false,
    /** The latest run completed a file-writing tool call (the transcript engine computes it). */
    val runChangedFiles: Boolean = false,
    /** The server can show the conversation's changes. */
    val reviewAvailable: Boolean = false,
    /** Fraction of the context window in use; null when unknown. */
    val contextUsage: Double? = null,
    /** The server can compact and a compact would be accepted now. */
    val compactAvailable: Boolean = false,
    /** A finished-run notification would really reach the person right now. */
    val finishedRunNotificationsReady: Boolean = false,
    /** Another inline card owns the space above the composer: nothing is offered, nothing is consumed. */
    val suppressed: Boolean = false,
)

/**
 * Turns a conversation's facts into offers on the [NudgeRegistry] (B-NAV-039). One per open conversation, driven from
 * one thread (its ViewModel); the registry decides whether an offer shows. The wait timer runs in [scope].
 */
public class ConversationNudgeWatcher(
    private val registry: NudgeRegistry,
    private val conversation: String,
    private val scope: CoroutineScope,
) {
    private var facts: ConversationNudgeFacts? = null
    private var waitTimer: Job? = null
    private var waitedLong = false
    private var disposed = false

    public fun observe(next: ConversationNudgeFacts) {
        if (disposed) return
        val wasBusy = facts?.busy ?: next.busy
        facts = next
        for ((requestId, kind) in next.pendingPermissions) {
            registry.notePermissionRequest(conversation, kind, requestId)
        }
        syncWaitTimer(next.busy)

        if (!registry.firstReplySeen) {
            // The observation that sees the first reply only records it: tips start with the next trigger.
            if (!next.busy && next.hasAssistantReply) scope.launch { registry.markFirstReplySeen() }
            return
        }

        retireStale(next)
        if (next.suppressed) return

        val runFinished = wasBusy && !next.busy
        val repeated = registry.repeatedPermissionKind(conversation)
        if (repeated != null && next.approvalsAvailable && next.pendingPermissions.isEmpty()) {
            // After the third request is answered, not beside it.
            registry.offer(NudgeId.Approvals, conversation, detail = repeated)
        }
        if (runFinished && next.runChangedFiles && next.reviewAvailable) {
            registry.offer(NudgeId.ReviewChanges, conversation)
        }
        if (waitedLong && next.busy && next.finishedRunNotificationsReady) {
            registry.offer(NudgeId.LeaveAndBeTold, conversation)
        }
        val usage = next.contextUsage
        if (usage != null && usage >= CONTEXT_THRESHOLD && next.compactAvailable && !next.busy) {
            registry.offer(NudgeId.Compact, conversation, detail = (usage * 100).coerceIn(0.0, 100.0).roundToInt().toString())
        }
    }

    /** The conversation closed: its card leaves so it cannot block every other nudge. */
    public fun dispose() {
        disposed = true
        waitTimer?.cancel()
        registry.releaseScope(conversation)
    }

    /** A card whose sentence stopped being true leaves on its own. */
    private fun retireStale(next: ConversationNudgeFacts) {
        val active = registry.activeFor(conversation) ?: return
        val stale = when (active.id) {
            NudgeId.LeaveAndBeTold -> !next.busy || !next.finishedRunNotificationsReady
            NudgeId.Approvals -> !next.approvalsAvailable
            NudgeId.Compact -> !next.compactAvailable || (next.contextUsage ?: 0.0) < CONTEXT_THRESHOLD
            NudgeId.ReviewChanges -> !next.reviewAvailable
            NudgeId.PinConversations -> false
        }
        if (stale) registry.release(active.id)
    }

    private fun syncWaitTimer(busy: Boolean) {
        if (!busy) {
            waitTimer?.cancel()
            waitTimer = null
            waitedLong = false
            return
        }
        // Measured while this conversation is open and busy: exactly the time the person spent watching it.
        if (waitTimer == null) {
            waitTimer = scope.launch {
                delay(LONG_WAIT)
                if (disposed) return@launch
                waitedLong = true
                facts?.let(::observe)
            }
        }
    }

    public companion object {
        /** How long a run must keep the person waiting before leaving is suggested. */
        public val LONG_WAIT: Duration = 60.seconds

        /** Context use from which compacting is worth suggesting. */
        public const val CONTEXT_THRESHOLD: Double = 0.8
    }
}
