package io.github.eslamasabry.oc.android.notifications

import android.content.Intent
import android.os.Build
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The body behind the `CodingActionReceiver` and `StatusActionReceiver` shells (plan 11 §10.6, ANDROID-PLATFORM-07;
 * 16 WP-16.8). The shells run it inside `goAsync` after the migration gate (plan 11 §2.2).
 *
 * Rules it keeps:
 * - A decision is bound to the **exact** `requestID`; an empty id refreshes the alert instead of answering any other
 *   request (B-AGENT-031, B-SEC-183). A stale id (the answerer says `StaleScope`) does the same (B-PROTO-046).
 * - On API 26–30 a decision that needs unlock (Allow once, legacy reply and options) answers nothing while the phone is
 *   locked and reposts the alert with Open only, whose tap opens the app at that request (CR16-7, B-SEC-184). On 31+
 *   Android itself asks for unlock (`setAuthenticationRequired`). Reject never asks.
 * - An empty legacy reply is refused before any network call (B-AGENT-032).
 * - Two taps for one request send one answer (B-CHAT-074); an answer for a request that is already gone dismisses the
 *   alert quietly (B-CHAT-076).
 * - Any other failure, or no answer within [answerDeadline], reposts the alert in plain words with no buttons.
 * - **No activity is ever started from here** (no trampoline; `NoTrampolineTest`).
 */
public class NotificationActionBody(
    private val shade: AlertShade,
    private val answerer: ShadeAnswerer,
    private val stopper: ReplyStopper,
    private val lock: DeviceLock,
    private val answerDeadline: Duration = 15.seconds,
    /** The person swiped the status row away (S11-9): reply watch ends for that run only. */
    private val onRowDismissed: (session: String) -> Unit = {},
) {
    private val inFlight = mutableSetOf<String>()

    /** Reads the frozen extras and handles the tap. */
    public suspend fun onCodingAction(intent: Intent): ShadeOutcome {
        val tap = ShadeTap.read(intent) ?: return ShadeOutcome.Ignored
        return onCodingAction(tap)
    }

    public suspend fun onCodingAction(tap: ShadeTap): ShadeOutcome {
        // Open buttons are activity PendingIntents; a stray "open" broadcast does nothing (and never starts one).
        if (tap.decision == ShadeDecision.Open) return ShadeOutcome.Ignored
        if (!tap.kind.isRequest) return ShadeOutcome.Ignored
        val reply = replyOf(tap) ?: return ShadeOutcome.Ignored
        if (tap.requestId.isBlank() || tap.profileId.isBlank()) return repost(tap)
        if (tap.decision.needsUnlock && lock.sdkInt < Build.VERSION_CODES.S && lock.isLocked()) return repost(tap)
        if (!claim(tap.requestId)) return ShadeOutcome.IgnoredDuplicate
        try {
            val answer = ShadeAnswer(ProfileId(tap.profileId), SessionId(tap.sessionId), RequestId(tap.requestId), tap.kind, reply)
            val outcome = withTimeoutOrNull(answerDeadline) { answerer.answer(answer) }
            return when {
                outcome is Outcome.Ok -> {
                    shade.dismiss(tap.key)
                    ShadeOutcome.Answered
                }
                outcome is Outcome.Failed && outcome.failure.meansGone() -> {
                    shade.dismiss(tap.key)
                    ShadeOutcome.DismissedGone
                }
                else -> repost(tap)
            }
        } finally {
            release(tap.requestId)
        }
    }

    /** **Stop the reply** on the status row (B-ANDR-040): stops exactly the chat the row named, ignores other actions. */
    public suspend fun onStatusAction(intent: Intent): ShadeOutcome {
        if (intent.action != FrozenExtras.ACTION_STATUS) return ShadeOutcome.Ignored
        if (intent.getStringExtra(FrozenExtras.EXTRA_STATUS_ACTION) == StatusRenderer.DISMISSED_ACTION) {
            onRowDismissed(intent.getStringExtra(FrozenExtras.EXTRA_STATUS_SESSION).orEmpty())
            return ShadeOutcome.RowDismissed
        }
        if (intent.getStringExtra(FrozenExtras.EXTRA_STATUS_ACTION) != StatusAction.StopReply.wire) return ShadeOutcome.Ignored
        val session = intent.getStringExtra(FrozenExtras.EXTRA_STATUS_SESSION).orEmpty()
        if (session.isBlank()) return ShadeOutcome.Ignored
        val outcome = withTimeoutOrNull(answerDeadline) { stopper.stop(SessionId(session)) }
        // On failure the row stays; its content tap (an activity PendingIntent) opens that chat.
        return if (outcome is Outcome.Ok || (outcome as? Outcome.Failed)?.failure is Failure.NotFound) {
            ShadeOutcome.StopSent
        } else {
            ShadeOutcome.StopFailed
        }
    }

    private fun replyOf(tap: ShadeTap): ShadeReply? =
        when (tap.decision) {
            ShadeDecision.Allow -> if (tap.kind == AlertKind.Permission) ShadeReply.AllowOnce else null
            ShadeDecision.Deny -> ShadeReply.Reject
            ShadeDecision.Reply, ShadeDecision.Option ->
                tap.replyText?.trim()?.takeIf { it.isNotEmpty() }?.let { ShadeReply.Text(it) }
            ShadeDecision.Open -> null
        }

    private fun repost(tap: ShadeTap): ShadeOutcome {
        shade.showOpenOnly(tap)
        return ShadeOutcome.RepostedOpen
    }

    private fun claim(request: String): Boolean = synchronized(inFlight) { inFlight.add(request) }

    private fun release(request: String) {
        synchronized(inFlight) { inFlight.remove(request) }
    }
}
