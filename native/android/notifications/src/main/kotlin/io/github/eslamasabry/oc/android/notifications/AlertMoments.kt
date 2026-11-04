package io.github.eslamasabry.oc.android.notifications

/**
 * Which coding alerts a profile's sessions deserve, and when they go (B-ANDR-011, B-ANDR-040). Port of the Flutter
 * `_syncInputAlerts` / `_markSessionAttentionActive` / `_settleSessionAttention` / `_dismissAllCodingAlerts`
 * (lib/state/connection/alerts.dart), as a pure tracker: it answers with [Step]s, and the caller words each [Step.Post]
 * into a [CodingAlert] (masked detail, ask, options), routes it through [AlertPolicy] and hands it to the [AlertShade].
 *
 * - Permission and question requests of one session share **one** alert (`input:<profile>:<session>`): a permission
 *   wins, then the oldest question; answering one shows the next in place, the last one dismisses it.
 * - Nothing is posted while the app is on screen; a request already visible is posted when the app goes to the
 *   background; coming back dismisses every outstanding alert.
 * - A run that ends alerts once (`status:<profile>:<session>`), whatever order the ending events arrive in; an error
 *   replaces the finished alert; sub-agent sessions stay quiet; a finished reply waits out quiet hours (B-ANDR-039).
 * - Opening the chat, or a new run in it, clears its finished alert.
 *
 * As amended by plan 11 §10.4: keys carry the profile (`input:p1:ses_1`, Flutter `input:ses_1`).
 */
public class AlertMoments(private val profileId: String) {
    /** One pending request, in arrival order. */
    public data class Pending(val sessionId: String, val requestId: String, val kind: AlertKind)

    public sealed interface Step {
        /** Post (or replace in place) the alert for [key]. [requestId] is empty for a finished or stopped run. */
        public data class Post(
            val kind: AlertKind,
            val sessionId: String,
            val key: String,
            val requestId: String = "",
            val reason: StopReason = StopReason.Other,
            val quiet: Boolean = false,
        ) : Step

        public data class Dismiss(val key: String) : Step
    }

    private var visible = true
    private var pending: List<Pending> = emptyList()
    private val alertedInput = mutableMapOf<String, Pair<AlertKind, String>>()
    private val alertedStatus = mutableSetOf<String>()
    private val active = mutableSetOf<String>()

    /** The current pending requests of this profile; answers the alert changes. */
    public fun requests(now: List<Pending>, quiet: Boolean = false): List<Step> {
        pending = now.filter { it.sessionId.isNotBlank() && it.requestId.isNotBlank() && it.kind.isRequest }
        return sync(quiet)
    }

    /** The app came on screen ([isVisible]) or left it. */
    public fun appVisible(isVisible: Boolean, quiet: Boolean = false): List<Step> {
        val was = visible
        visible = isVisible
        return when {
            isVisible && !was -> dismissAll()
            !isVisible && was -> sync(quiet)
            else -> emptyList()
        }
    }

    /** A run started (busy or retry): a new run clears the session's old finished alert. */
    public fun runStarted(sessionId: String): List<Step> {
        if (sessionId.isBlank()) return emptyList()
        active += sessionId
        return if (alertedStatus.remove(sessionId)) listOf(Step.Dismiss(AlertKeys.status(profileId, sessionId))) else emptyList()
    }

    /**
     * A run ended: [AlertKind.Complete] for a normal finish, [AlertKind.Error] for a failure (never the person's own
     * stop, which the caller does not report). Only the first ending of a watched run alerts.
     */
    public fun runEnded(
        sessionId: String,
        kind: AlertKind,
        subAgent: Boolean = false,
        reason: StopReason = StopReason.Other,
        quiet: Boolean = false,
    ): List<Step> {
        require(kind == AlertKind.Complete || kind == AlertKind.Error) { "a run ends complete or error" }
        if (sessionId.isBlank() || !active.remove(sessionId)) return emptyList()
        if (visible || subAgent) return emptyList()
        // A finished reply can wait for the morning; a failure posts, silently.
        if (quiet && kind == AlertKind.Complete) return emptyList()
        if (!alertedStatus.add(sessionId)) return emptyList()
        return listOf(Step.Post(kind, sessionId, AlertKeys.status(profileId, sessionId), reason = reason, quiet = quiet))
    }

    /** The person opened [sessionId]: its finished or stopped alert has done its job. */
    public fun chatOpened(sessionId: String): List<Step> =
        if (alertedStatus.remove(sessionId)) listOf(Step.Dismiss(AlertKeys.status(profileId, sessionId))) else emptyList()

    /** The shade did not post (blocked channel, no permission): the next sync may try again. */
    public fun notShown(step: Step.Post) {
        if (step.requestId.isEmpty()) {
            alertedStatus -= step.sessionId
        } else if (alertedInput[step.sessionId] == (step.kind to step.requestId)) {
            alertedInput -= step.sessionId
        }
    }

    private fun sync(quiet: Boolean): List<Step> {
        val steps = mutableListOf<Step>()
        // Front request per session: the first permission, else the first question.
        val front = linkedMapOf<String, Pending>()
        for (p in pending) {
            val held = front[p.sessionId]
            if (held == null || (held.kind != AlertKind.Permission && p.kind == AlertKind.Permission)) front[p.sessionId] = p
        }
        for (session in alertedInput.keys.toList()) {
            if (session in front) continue
            alertedInput -= session
            steps += Step.Dismiss(AlertKeys.input(profileId, session))
        }
        if (visible) return steps
        for ((session, p) in front) {
            val alerted = p.kind to p.requestId
            if (alertedInput[session] == alerted) continue
            alertedInput[session] = alerted
            steps += Step.Post(p.kind, session, AlertKeys.input(profileId, session), p.requestId, quiet = quiet)
        }
        return steps
    }

    private fun dismissAll(): List<Step> {
        val steps =
            alertedInput.keys.map { Step.Dismiss(AlertKeys.input(profileId, it)) } +
                alertedStatus.map { Step.Dismiss(AlertKeys.status(profileId, it)) }
        alertedInput.clear()
        alertedStatus.clear()
        active.clear()
        return steps
    }
}
