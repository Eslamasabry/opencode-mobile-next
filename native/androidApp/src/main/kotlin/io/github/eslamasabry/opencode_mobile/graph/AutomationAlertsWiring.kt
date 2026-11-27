package io.github.eslamasabry.oc.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import io.github.eslamasabry.oc.android.notifications.AlertFacts
import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.AlertPolicy
import io.github.eslamasabry.oc.android.notifications.AlertRoute
import io.github.eslamasabry.oc.android.notifications.AlertShade
import io.github.eslamasabry.oc.android.notifications.AlertShown
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.transcript.MessageAutomation
import io.github.eslamasabry.oc.engine.attention.automation.AutomationAlertDiff
import io.github.eslamasabry.oc.engine.attention.automation.AutomationAlertReason
import io.github.eslamasabry.oc.engine.attention.automation.AutomationAlertTracker
import io.github.eslamasabry.oc.engine.attention.automation.AutomationNotification
import io.github.eslamasabry.oc.engine.entry.AutomationAlertSession
import io.github.eslamasabry.oc.feature.settings.presentation.ServerAutomationsSource
import java.util.UUID

private const val MAX_KEPT_PAUSES = 256

/**
 * Server automations' alerts (B-620), one [AutomationAlertTracker] per saved server. The tracker decides what is news;
 * this posts each alert through the same shade and rules as every coding alert ([AlertPolicy] over the posting facts),
 * and binds **Pause this automation** to the exact posted alert: its action token authorizes one call to the B-605
 * client, which pauses through the server's live connection and nothing else. Offline, the read and the pause fail,
 * and nothing pretends otherwise.
 *
 * Calls arrive on the hub's main lane; only the pause tokens are guarded by a lock.
 */
class AutomationAlertsCoordinator(
    private val shade: AlertShade,
    private val source: ServerAutomationsSource,
    private val serverName: (ProfileId) -> String,
    private val alertFacts: () -> AlertFacts,
    private val redact: (String) -> String,
) {
    private class Server(val tracker: AutomationAlertTracker) {
        /** The pauses posted alerts may still ask for, by action token; each token is used once. */
        val pauses = LinkedHashMap<String, Pause>()
    }

    private class Pause(val automationId: String, val group: String)

    private val servers = mutableMapOf<ProfileId, Server>()
    private val lock = Any()

    /** The server's event stream is up, or down: nothing is read or told while it is down. */
    fun setConnected(profile: ProfileId, connected: Boolean) {
        server(profile).tracker.setConnected(connected)
    }

    /** True while the server's stream is up: the caller reads its automations only then. */
    fun isConnected(profile: ProfileId): Boolean = server(profile).tracker.isConnected

    /**
     * Reads the server's automations through the B-605 client and posts what changed. A read that fails leaves the
     * tracker as it was. With [baseline] the read only records what is there: the person just left its rows, and what
     * could be seen never notifies.
     */
    suspend fun pollAutomations(profile: ProfileId, baseline: Boolean = false) {
        val tracker = server(profile).tracker
        if (!tracker.isConnected) return
        if (baseline) tracker.rebase()
        val listed = source.automations(profile)
        if (listed !is Outcome.Ok) return
        post(profile, tracker.observeAutomations(listed.value))
    }

    /** The live stream showed a server automation's prompt in [sessionId]. */
    fun onAutomationMessage(profile: ProfileId, sessionId: String, automation: MessageAutomation) {
        server(profile).tracker.onAutomationMessage(sessionId, automation)
    }

    /** True while [sessionId] runs an automation's prompt: the caller reads the reply when the run ends. */
    fun isAutomationSession(profile: ProfileId, sessionId: String): Boolean =
        server(profile).tracker.isAutomationSession(sessionId)

    /** A run ended in [sessionId]; [replyExcerpt] is the reply's first line, as the finished alert reads it. */
    fun onSessionRunEnded(
        profile: ProfileId,
        sessionId: String,
        failed: Boolean,
        interrupted: Boolean,
        replyExcerpt: String,
    ) {
        val tracker = server(profile).tracker
        post(profile, tracker.onSessionRunEnded(sessionId, failed, interrupted, replyExcerpt))
    }

    /**
     * **Pause this automation** (B-620), without opening the app: the token of the exact posted alert authorizes one
     * call to the server. A blank, unknown, used or mismatched token is `Refused(StaleScope)` (B-SEC-183). A pause the
     * server refused or could not reach comes back as it is, and the alerts stay: nothing pretends paused.
     */
    suspend fun pauseFromShade(profile: ProfileId, automationId: String, actionToken: String): Outcome<Unit> {
        val pause = take(profile, automationId, actionToken)
            ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        return pauseOnServer(profile, pause)
    }

    /** The app came to the screen: what the alerts said is on screen now (B-ANDR-011), and every group counts anew. */
    fun alertsSeen() {
        val groups = mutableSetOf<String>()
        synchronized(lock) {
            for (entry in servers.values) {
                groups += entry.tracker.groups()
                entry.pauses.clear()
                entry.tracker.alertsCleared()
            }
        }
        groups.forEach(shade::dismissGroup)
    }

    /** The server's profile is gone: nothing of it is kept, and its alerts go. */
    fun forget(profile: ProfileId) {
        val groups = synchronized(lock) { servers.remove(profile)?.tracker?.groups().orEmpty() }
        groups.forEach(shade::dismissGroup)
    }

    private fun server(profile: ProfileId): Server =
        servers.getOrPut(profile) { Server(AutomationAlertTracker(profileId = profile.raw)) }

    /** The server's own answer; a pause that took also clears the group here and now. */
    private suspend fun pauseOnServer(profile: ProfileId, pause: Pause): Outcome<Unit> {
        val answer = source.pauseAutomation(profile, pause.automationId)
        if (answer is Outcome.Failed) return answer
        post(profile, server(profile).tracker.markPausedLocally(pause.automationId))
        shade.dismissGroup(pause.group)
        return Outcome.Ok(Unit)
    }

    /** The pause a token authorizes, once: null for a blank or unknown token, a used one, or another automation. */
    private fun take(profile: ProfileId, automationId: String, actionToken: String): Pause? =
        synchronized(lock) {
            val pause = if (actionToken.isBlank()) null else server(profile).pauses.remove(actionToken)
            if (pause != null && pause.automationId != automationId) null else pause
        }

    /** Posts a tracker diff: dismissals first, then each alert under a fresh one-shot token for its Pause action. */
    private fun post(profile: ProfileId, diff: AutomationAlertDiff) {
        diff.dismissKeys.forEach(shade::dismiss)
        diff.dismissGroups.forEach(shade::dismissGroup)
        for (notification in diff.post) {
            val alert = alert(profile, notification)
            val route = AlertPolicy.route(alert, alertFacts())
            if (route is AlertRoute.Post && shade.show(alert, route) == AlertShown.Shown) {
                offer(profile, alert, notification.groupKey)
            }
        }
    }

    private fun alert(profile: ProfileId, notification: AutomationNotification): CodingAlert =
        CodingAlert(
            kind = kindOf(notification.reason),
            // An alert without a chat still needs a session id to be a valid alert and a safe tap.
            sessionId = notification.sessionId.ifBlank { AutomationAlertSession.of(notification.automationId) },
            key = notification.slotKey,
            requestId = notification.automationId,
            profileId = profile.raw,
            subtext = redact(serverName(profile)),
            chatTitle = redact(notification.automationName),
            detail = redact(notification.detail),
            allowActions = notification.canPause,
            actionToken = UUID.randomUUID().toString().replace("-", ""),
            automationGroup = notification.groupKey,
            automationCoveredRuns = notification.coveredRuns,
            automationSilentUpdate = notification.capped,
        )

    private fun kindOf(reason: AutomationAlertReason): AlertKind =
        when (reason) {
            AutomationAlertReason.Message -> AlertKind.AutomationMessage
            AutomationAlertReason.Failed -> AlertKind.AutomationFailed
            AutomationAlertReason.Paused -> AlertKind.AutomationPaused
        }

    /** Remembers the token while its alert shows, so its Pause action can be taken once; the oldest go first. */
    private fun offer(profile: ProfileId, alert: CodingAlert, group: String) {
        if (!alert.allowActions || alert.requestId.isBlank()) return
        synchronized(lock) {
            val pauses = server(profile).pauses
            pauses[alert.actionToken] = Pause(alert.requestId, group)
            while (pauses.size > MAX_KEPT_PAUSES) pauses.remove(pauses.keys.first())
        }
    }
}

/** The B-605 automations client for the platform hub, which takes it once the graph exists. */
@ContributesTo(AppScope::class)
interface ServerAutomationsAccessors {
    val serverAutomations: ServerAutomationsSource
}
