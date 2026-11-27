package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.notifications.AlertFacts
import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.AlertPolicy
import io.github.eslamasabry.oc.android.notifications.AlertRoute
import io.github.eslamasabry.oc.android.notifications.AlertShade
import io.github.eslamasabry.oc.android.notifications.AlertShown
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.transcript.MessageAutomation
import io.github.eslamasabry.oc.engine.attention.automation.AutomationAlertDiff
import io.github.eslamasabry.oc.engine.attention.automation.AutomationAlertReason
import io.github.eslamasabry.oc.engine.attention.automation.AutomationAlertTracker
import io.github.eslamasabry.oc.engine.attention.automation.AutomationNotification
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.entry.AutomationAlertSession
import io.github.eslamasabry.oc.feature.settings.presentation.ServerAutomationsSource
import java.util.UUID
import kotlinx.collections.immutable.ImmutableList

private const val MAX_KEPT_PAUSES = 256

/** Live server automation controls wired for a saved server profile (B-605). */
@ContributesBinding(AppScope::class, binding = binding<ServerAutomationsSource>())
@SingleIn(AppScope::class)
@Inject
class NativeServerAutomations(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
) : ServerAutomationsSource {
    override suspend fun automations(
        profile: ProfileId
    ): Outcome<ImmutableList<ServerAutomation>> =
        withAutomations(profile) { it.listAutomations() }

    override suspend fun pauseAutomation(
        profile: ProfileId,
        id: String,
    ): Outcome<ServerAutomation> = withAutomations(profile) { it.pauseAutomation(id) }

    override suspend fun resumeAutomation(
        profile: ProfileId,
        id: String,
    ): Outcome<ServerAutomation> = withAutomations(profile) { it.resumeAutomation(id) }

    override suspend fun deleteAutomation(profile: ProfileId, id: String): Outcome<Unit> =
        withAutomations(profile) { it.deleteAutomation(id) }

    private fun liveGraph(profile: ProfileId): Outcome<ConnectionGraph> {
        val loaded = stores.profiles.profile(profile)
        val graph = registry.live.value[profile]
        return when {
            loaded == null -> Failure.Refused(RefusalReason.ProfileDeleted).failed()
            graph == null -> Failure.Network().failed()
            loaded.toServerProfile() != graph.profile ->
                Failure.Refused(RefusalReason.StaleScope).failed()
            else -> graph.ok()
        }
    }

    private fun current(profile: ProfileId, graph: ConnectionGraph): Boolean =
        registry.live.value[profile] === graph &&
            stores.profiles.profile(profile)?.toServerProfile() == graph.profile

    private suspend fun <T> withAutomations(
        profile: ProfileId,
        block: suspend (ServerOperationsGateway) -> Outcome<T>,
    ): Outcome<T> {
        val warm = stores.warm()
        val ready = if (warm is Outcome.Failed) warm else liveGraph(profile)
        return when (ready) {
            is Outcome.Failed -> ready
            is Outcome.Ok -> {
                val graph = ready.value
                if (!graph.session.capabilities.value.serverAutomations) {
                    Failure.Unavailable(FeatureId("serverAutomations")).failed()
                } else {
                    val result = block((graph as ServerSettingsConnection).serverSettingsOperations)
                    if (current(profile, graph)) result
                    else Failure.Refused(RefusalReason.StaleScope).failed()
                }
            }
        }
    }
}

// ---- B-620: the alerts of the automations the client above reads ----

/** One saved server's tracker. */
private class Tracked(val tracker: AutomationAlertTracker)

/** A pause a posted alert may still ask for: the automation it pauses, and the group it clears. */
private class Pause(val automationId: String, val group: String)

/** One saved server's tracker, made on first use. */
private fun MutableMap<ProfileId, Tracked>.tracked(profile: ProfileId): Tracked =
    getOrPut(profile) { Tracked(AutomationAlertTracker(profileId = profile.raw)) }

/** The action tokens of posted alerts that may still ask for a pause; each is used once. */
private class Pauses {
    private val lock = Any()
    private val byServer = mutableMapOf<ProfileId, LinkedHashMap<String, Pause>>()

    /** Remembers a token while its alert shows; past [MAX_KEPT_PAUSES] on a server the oldest go first. */
    fun offer(profile: ProfileId, alert: CodingAlert, group: String) {
        if (!alert.allowActions || alert.requestId.isBlank()) return
        synchronized(lock) {
            val pauses = byServer.getOrPut(profile) { LinkedHashMap() }
            pauses[alert.actionToken] = Pause(alert.requestId, group)
            while (pauses.size > MAX_KEPT_PAUSES) pauses.remove(pauses.keys.first())
        }
    }

    /** The pause a token authorizes, once: null for a blank or unknown token, a used one, or another automation. */
    fun take(profile: ProfileId, automationId: String, actionToken: String): Pause? =
        synchronized(lock) {
            val pause = if (actionToken.isBlank()) null else byServer[profile]?.remove(actionToken)
            if (pause != null && pause.automationId != automationId) null else pause
        }

    fun forget(profile: ProfileId) {
        synchronized(lock) { byServer.remove(profile) }
    }

    fun clear() {
        synchronized(lock) { byServer.values.forEach { it.clear() } }
    }
}

/**
 * Server automations' alerts (B-620), one [AutomationAlertTracker] per saved server. The tracker decides what is news;
 * this posts each alert through the same shade and rules as every coding alert ([AlertPolicy] over the posting facts),
 * and binds **Pause this automation** to the exact posted alert: its action token authorizes one call to the B-605
 * client above, which pauses through the server's live connection and nothing else. Offline, the read and the pause
 * fail, and nothing pretends otherwise.
 *
 * Calls arrive on the hub's main lane; only the pause tokens are kept under a lock.
 */
class AutomationAlertsCoordinator(
    private val shade: AlertShade,
    private val source: ServerAutomationsSource,
    private val serverName: (ProfileId) -> String,
    private val alertFacts: () -> AlertFacts,
    private val redact: (String) -> String,
) {
    private val servers = mutableMapOf<ProfileId, Tracked>()
    private val pauses = Pauses()

    /** The server's event stream is up, or down: nothing is read or told while it is down. */
    fun setConnected(profile: ProfileId, connected: Boolean) {
        servers.tracked(profile).tracker.setConnected(connected)
    }

    /**
     * Reads the server's automations through the B-605 client and posts what changed. A read that fails leaves the
     * tracker as it was. With [baseline] the read only records what is there: the person just left its rows, and what
     * could be seen never notifies.
     */
    suspend fun pollAutomations(profile: ProfileId, baseline: Boolean = false) {
        val tracker = servers.tracked(profile).tracker
        if (!tracker.isConnected) return
        if (baseline) tracker.rebase()
        val listed = source.automations(profile)
        if (listed !is Outcome.Ok) return
        post(profile, tracker.observeAutomations(listed.value))
    }

    /** The live stream showed a server automation's prompt in [sessionId]. */
    fun onAutomationMessage(profile: ProfileId, sessionId: String, automation: MessageAutomation) {
        servers.tracked(profile).tracker.onAutomationMessage(sessionId, automation)
    }

    /** True while [sessionId] runs an automation's prompt: the caller reads the reply when the run ends. */
    fun isAutomationSession(profile: ProfileId, sessionId: String): Boolean =
        servers.tracked(profile).tracker.isAutomationSession(sessionId)

    /** A run ended in [sessionId]; [replyExcerpt] is the reply's first line, as the finished alert reads it. */
    fun onSessionRunEnded(
        profile: ProfileId,
        sessionId: String,
        failed: Boolean,
        interrupted: Boolean,
        replyExcerpt: String,
    ) {
        val tracker = servers.tracked(profile).tracker
        post(profile, tracker.onSessionRunEnded(sessionId, failed, interrupted, replyExcerpt))
    }

    /**
     * **Pause this automation** (B-620), without opening the app: the token of the exact posted alert authorizes one
     * call to the server. A blank, unknown, used or mismatched token is `Refused(StaleScope)` (B-SEC-183). A pause the
     * server refused or could not reach comes back as it is, and the alerts stay: nothing pretends paused.
     */
    suspend fun pauseFromShade(profile: ProfileId, automationId: String, actionToken: String): Outcome<Unit> {
        val pause = pauses.take(profile, automationId, actionToken)
            ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        return pauseOnServer(profile, pause)
    }

    /** The app came to the screen: what the alerts said is on screen now (B-ANDR-011), and every group counts anew. */
    fun alertsSeen() {
        val groups = mutableSetOf<String>()
        for (entry in servers.values) {
            groups += entry.tracker.groups()
            entry.tracker.alertsCleared()
        }
        pauses.clear()
        groups.forEach(shade::dismissGroup)
    }

    /** The server's profile is gone: nothing of it is kept, and its alerts go. */
    fun forget(profile: ProfileId) {
        pauses.forget(profile)
        servers.remove(profile)?.tracker?.groups().orEmpty().forEach(shade::dismissGroup)
    }

    /** The server's own answer; a pause that took also clears the group here and now. */
    private suspend fun pauseOnServer(profile: ProfileId, pause: Pause): Outcome<Unit> {
        val answer = source.pauseAutomation(profile, pause.automationId)
        if (answer is Outcome.Failed) return answer
        post(profile, servers.tracked(profile).tracker.markPausedLocally(pause.automationId))
        shade.dismissGroup(pause.group)
        return Outcome.Ok(Unit)
    }

    /** Posts a tracker diff: dismissals first, then each alert under a fresh one-shot token for its Pause action. */
    private fun post(profile: ProfileId, diff: AutomationAlertDiff) {
        diff.dismissKeys.forEach(shade::dismiss)
        diff.dismissGroups.forEach(shade::dismissGroup)
        for (notification in diff.post) {
            val alert = alertOf(profile, notification, redact(serverName(profile)), redact)
            val route = AlertPolicy.route(alert, alertFacts())
            if (route is AlertRoute.Post && shade.show(alert, route) == AlertShown.Shown) {
                pauses.offer(profile, alert, notification.groupKey)
            }
        }
    }

    private companion object {
        /** The shade's alert for a tracker notification; [subtext] names the server and [mask] hides secrets. */
        fun alertOf(
            profile: ProfileId,
            notification: AutomationNotification,
            subtext: String,
            mask: (String) -> String,
        ): CodingAlert =
            CodingAlert(
                kind = kindOf(notification.reason),
                // An alert without a chat still needs a session id to be a valid alert and a safe tap.
                sessionId = notification.sessionId.ifBlank { AutomationAlertSession.of(notification.automationId) },
                key = notification.slotKey,
                requestId = notification.automationId,
                profileId = profile.raw,
                subtext = subtext,
                chatTitle = mask(notification.automationName),
                detail = mask(notification.detail),
                allowActions = notification.canPause,
                actionToken = UUID.randomUUID().toString().replace("-", ""),
                automationGroup = notification.groupKey,
                automationCoveredRuns = notification.coveredRuns,
                automationSilentUpdate = notification.capped,
            )

        fun kindOf(reason: AutomationAlertReason): AlertKind =
            when (reason) {
                AutomationAlertReason.Message -> AlertKind.AutomationMessage
                AutomationAlertReason.Failed -> AlertKind.AutomationFailed
                AutomationAlertReason.Paused -> AlertKind.AutomationPaused
            }
    }
}

/** The B-605 automations client for the platform hub, which takes it once the graph exists. */
@ContributesTo(AppScope::class)
interface ServerAutomationsAccessors {
    val serverAutomations: ServerAutomationsSource
}

/** A tap on an automation's alert (B-620): the chat of its run, or the server list when it has none. */
internal data class AutomationAlertTap(val profile: ProfileId, val session: SessionId?)

/**
 * The automation alert this launch carries, or null for any other alert. [session] is the raw session extra: a real
 * session opens that chat; a pause, or an alert that carries no chat ([AutomationAlertSession]), opens Servers, where
 * the automation's row is. The entry decider keeps the same rule for the router.
 */
internal fun automationAlertTap(kind: AlertKind?, session: String?, profile: String?): AutomationAlertTap? =
    profile
        ?.takeIf { kind?.isAutomation == true && it.isNotBlank() }
        ?.let { owner ->
            val chat = session?.takeIf { it.isNotBlank() && it.isChat() && kind != AlertKind.AutomationPaused }
            AutomationAlertTap(ProfileId(owner), chat?.let(::SessionId))
        }

private fun String.isChat(): Boolean = !AutomationAlertSession.isNoChat(this)
