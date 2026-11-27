package io.github.eslamasabry.oc.android.notifications

import android.content.Intent
import android.os.Build
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The body behind the `CodingActionReceiver` and `StatusActionReceiver` shells (plan 11 §10.6,
 * ANDROID-PLATFORM-07; 16 WP-16.8). The shells run it inside `goAsync` after the migration gate
 * (plan 11 §2.2).
 *
 * Rules it keeps:
 * - A decision is bound to the **exact** `requestID`; an empty id refreshes the alert instead of
 *   answering any other request (B-AGENT-031, B-SEC-183). A stale id (the answerer says
 *   `StaleScope`) does the same (B-PROTO-046).
 * - On API 26–30 a decision that needs unlock (Allow once, legacy reply and options) answers
 *   nothing while the phone is locked and reposts the alert with Open only, whose tap opens the app
 *   at that request (CR16-7, B-SEC-184). On 31+ Android itself asks for unlock
 *   (`setAuthenticationRequired`). Reject never asks.
 * - An empty legacy reply is refused before any network call (B-AGENT-032).
 * - Two taps for one request send one answer (B-CHAT-074); an answer for a request that is already
 *   gone dismisses the alert quietly (B-CHAT-076).
 * - Any other failure, or no answer within [answerDeadline], reposts the alert in plain words with
 *   no buttons.
 * - **Pause this automation** (B-620) is bound the same way: the tapped alert's token authorizes one call to the
 *   server's API. A pause that is refused, failed or timed out reposts the alert with Open only, never "paused"; a
 *   pause that took clears the automation's whole group. It needs the unlocked phone, as Allow once does.
 * - **No activity is ever started from here** (no trampoline; `NoTrampolineTest`).
 */
public class NotificationActionBody(
    private val shade: AlertShade,
    private val answerer: ShadeAnswerer,
    private val stopper: ReplyStopper,
    private val lock: DeviceLock,
    /** The person swiped the status row away (S11-9): reply watch ends for that run only. */
    private val onRowDismissed: (run: ReplyRun) -> Unit = {},
    /** B-620: Pause this automation through the server's API; unbound, it fails and the alert stays. */
    private val automationPauser: AutomationPauser = AutomationPauser { _, _, _ ->
        Outcome.Failed(Failure.Unavailable(FeatureId("serverAutomations")))
    },
) {
    /** Told when a Stop is tapped and when its call ends, so the row can say "Stopping…" and settle. */
    public var onStop: StopObserver = StopObserver.None

    /** How long an answer, a stop or a pause may take before the alert is reposted with Open only. */
    public var answerDeadline: Duration = DEFAULT_DEADLINE

    private data class AnswerClaim(
        val profile: String,
        val session: String,
        val kind: AlertKind,
        val request: String,
        val actionToken: String,
    )

    private val inFlight = mutableSetOf<AnswerClaim>()

    /** Reads the frozen extras and handles the tap. */
    public suspend fun onCodingAction(intent: Intent): ShadeOutcome {
        val tap = ShadeTap.read(intent) ?: return ShadeOutcome.Ignored
        return onCodingAction(tap)
    }

    public suspend fun onCodingAction(tap: ShadeTap): ShadeOutcome {
        // Open buttons are activity PendingIntents; a stray "open" broadcast does nothing (and
        // never starts one).
        if (tap.decision == ShadeDecision.Open) return ShadeOutcome.Ignored
        if (tap.decision == ShadeDecision.PauseAutomation) return pauseAutomation(tap)
        if (!tap.kind.isRequest) return ShadeOutcome.Ignored
        val reply = replyOf(tap) ?: return ShadeOutcome.Ignored
        if (tap.requestId.isBlank() || tap.profileId.isBlank()) return repost(tap)
        if (tap.decision.needsUnlock && lock.sdkInt < Build.VERSION_CODES.S && lock.isLocked())
            return repost(tap)
        // The exact tapped action (its token included) is the duplicate key; a repost carries a new
        // token, so it is answerable again.
        val claimKey =
            AnswerClaim(tap.profileId, tap.sessionId, tap.kind, tap.requestId, tap.actionToken)
        if (!claim(claimKey)) return ShadeOutcome.IgnoredDuplicate
        try {
            val answer =
                ShadeAnswer(
                    ProfileId(tap.profileId),
                    SessionId(tap.sessionId),
                    RequestId(tap.requestId),
                    tap.kind,
                    reply,
                    tap.actionToken,
                )
            val outcome = withTimeoutOrNull(answerDeadline) { answerer.answer(answer) }
            return when {
                outcome is Outcome.Ok -> {
                    shade.dismissIfCurrent(tap)
                    ShadeOutcome.Answered
                }
                outcome is Outcome.Failed && outcome.failure.meansGone() -> {
                    shade.dismissIfCurrent(tap)
                    ShadeOutcome.DismissedGone
                }
                else -> repost(tap)
            }
        } finally {
            release(claimKey)
        }
    }

    /**
     * **Stop the reply** on the status row (B-ANDR-040): stops exactly the chat the row named,
     * ignores other actions.
     */
    public suspend fun onStatusAction(intent: Intent): ShadeOutcome {
        if (intent.action != FrozenExtras.ACTION_STATUS) return ShadeOutcome.Ignored
        if (
            intent.getStringExtra(FrozenExtras.EXTRA_STATUS_ACTION) ==
                StatusRenderer.DISMISSED_ACTION
        ) {
            val run = intent.statusRun() ?: return ShadeOutcome.Ignored
            onRowDismissed(run)
            return ShadeOutcome.RowDismissed
        }
        if (intent.getStringExtra(FrozenExtras.EXTRA_STATUS_ACTION) != StatusAction.StopReply.wire)
            return ShadeOutcome.Ignored
        val conversation = intent.statusConversation() ?: return ShadeOutcome.Ignored
        // Stop needs the phone unlocked, as Allow once does: Android 12+ asks through the action's
        // setAuthenticationRequired; below that nothing is stopped while locked (the row stays, its tap opens the chat).
        if (lock.sdkInt < Build.VERSION_CODES.S && lock.isLocked()) return ShadeOutcome.Ignored
        // Show the answer to the tap first: the person sees "Stopping…" while the call is made.
        onStop.requested(conversation)
        val outcome =
            try {
                withTimeoutOrNull(answerDeadline) { stopper.stop(conversation) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                onStop.settled(conversation, ShadeOutcome.StopFailed)
                throw e
            }
        // On failure the row stays; its content tap (an activity PendingIntent) opens that chat.
        val result =
            if (
                outcome is Outcome.Ok || (outcome as? Outcome.Failed)?.failure is Failure.NotFound
            ) {
                ShadeOutcome.StopSent
            } else {
                ShadeOutcome.StopFailed
            }
        onStop.settled(conversation, result)
        return result
    }

    private fun replyOf(tap: ShadeTap): ShadeReply? =
        when (tap.decision) {
            ShadeDecision.Allow ->
                if (tap.kind == AlertKind.Permission) ShadeReply.AllowOnce else null
            ShadeDecision.Deny -> ShadeReply.Reject
            ShadeDecision.Reply,
            ShadeDecision.Option ->
                tap.replyText?.trim()?.takeIf { it.isNotEmpty() }?.let { ShadeReply.Text(it) }
            ShadeDecision.Open,
            ShadeDecision.PauseAutomation -> null
        }

    /** **Pause this automation** (B-620): the tap's token authorizes one call to the server. */
    private suspend fun pauseAutomation(tap: ShadeTap): ShadeOutcome {
        if (!canPauseFromShade(tap)) return repost(tap)
        val paused =
            withTimeoutOrNull(answerDeadline) {
                automationPauser.pause(ProfileId(tap.profileId), tap.requestId, tap.actionToken)
            }
        return finishPause(tap, paused is Outcome.Ok)
    }

    /** A pause that was refused or failed leaves the alert, as Open only; a pause that took clears the group. */
    private fun finishPause(tap: ShadeTap, paused: Boolean): ShadeOutcome {
        if (!paused) return repost(tap)
        shade.dismissGroup(tap.automationGroup.ifBlank { tap.key })
        return ShadeOutcome.AutomationPaused
    }

    /**
     * The tap names an automation, a server and the exact posted alert, and the phone is unlocked where Android
     * does not ask for the unlock itself (CR16-7).
     */
    private fun canPauseFromShade(tap: ShadeTap): Boolean =
        tap.namesAnAutomation() && !(lock.sdkInt < Build.VERSION_CODES.S && lock.isLocked())

    private fun ShadeTap.namesAnAutomation(): Boolean =
        kind.isAutomation && requestId.isNotBlank() && profileId.isNotBlank() && actionToken.isNotBlank()

    private fun repost(tap: ShadeTap): ShadeOutcome {
        shade.showOpenOnlyIfCurrent(tap)
        return ShadeOutcome.RepostedOpen
    }

    private fun claim(request: AnswerClaim): Boolean =
        synchronized(inFlight) { inFlight.add(request) }

    private fun release(request: AnswerClaim) {
        synchronized(inFlight) { inFlight.remove(request) }
    }

    private companion object {
        val DEFAULT_DEADLINE: Duration = 15.seconds
    }
}

/** The status row's side of a Stop tap: [requested] fires before the stop call, [settled] after it. */
public interface StopObserver {
    public fun requested(conversation: ConversationRef)

    public fun settled(conversation: ConversationRef, outcome: ShadeOutcome)

    public object None : StopObserver {
        override fun requested(conversation: ConversationRef): Unit = Unit

        override fun settled(conversation: ConversationRef, outcome: ShadeOutcome): Unit = Unit
    }
}
