package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.notifications.AlertFacts
import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.AlertPolicy
import io.github.eslamasabry.oc.android.notifications.AlertRoute
import io.github.eslamasabry.oc.android.notifications.AndroidAlertShade
import io.github.eslamasabry.oc.android.notifications.ChannelRegistry
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.engine.team.TeamAlertKind
import io.github.eslamasabry.oc.engine.team.TeamAlertTracker
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AI Team notifications (Flutter `_syncTeamAlerts`): while the app is away, each phone team is read every
 * [POLL_MS] and a new decision, a task that failed, work ready to review and a finished project each raise one
 * notification. Ids only (the copy is fixed in the notifications module), under the same rules as every other coding
 * alert: not while the app is on screen, quiet hours post decisions and failures silently and hold the rest, and the
 * requests / finished-runs switches in Settings apply. Nothing here answers anything.
 */
@SingleIn(AppScope::class)
@Inject
class TeamAlertsWatcher(
    application: Application,
    private val scope: AppCoroutineScope,
    private val engine: TeamEngine,
    private val stores: PreviewStores,
    private val policy: QuotaCollectionPolicy,
    private val background: BackgroundHub,
    preferences: AppPreferences,
) {
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val shade =
        AndroidAlertShade(
            application,
            words,
            ChannelRegistry(application.getSystemService(android.app.NotificationManager::class.java), words),
        )
    private val trackers = mutableMapOf<String, TeamAlertTracker>()
    private var started = false

    /** Called once from the platform hub's start. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                runCatching { tick() }
                delay(POLL_MS)
            }
        }
    }

    private suspend fun tick() {
        if (stores.warm() !is io.github.eslamasabry.oc.core.common.Outcome.Ok) return
        val teams =
            stores.profiles.profiles.value.filter {
                TeamConfig.parse(it.profile.orchestration?.encoded)?.provider == TeamProvider.PhoneEngine
            }
        trackers.keys.retainAll(teams.map { it.id.raw }.toSet())
        for (team in teams) {
            val id = team.id.raw
            // On screen the pages poll for themselves; away, this read is the only one.
            if (!policy.foreground.value) engine.refresh(id)
            val tracker = trackers.getOrPut(id) { TeamAlertTracker(id) }
            val diff = tracker.observe(engine.state(id).value.workspace)
            if (diff.isEmpty || policy.foreground.value) continue
            diff.settled.forEach { shade.dismiss(it) }
            for (request in diff.alerts) post(request.kind, request.id, request.key, id, team.profile.name)
        }
    }

    private fun post(kind: TeamAlertKind, id: String, key: String, profile: String, server: String) {
        val alertKind =
            when (kind) {
                TeamAlertKind.Decision -> AlertKind.TeamDecision
                TeamAlertKind.Review -> AlertKind.TeamReview
                TeamAlertKind.RunFailed -> AlertKind.TeamRunFailed
                TeamAlertKind.Completed -> AlertKind.TeamCompleted
            }
        val on =
            stores.prefs.bool(if (kind == TeamAlertKind.Completed) FINISHED_ON else REQUESTS_ON) ?: true
        val live = background.live.value
        val route =
            AlertPolicy.route(
                CodingAlert(alertKind, id, key, profileId = profile, subtext = server, allowActions = false),
                AlertFacts(
                    liveModeActive = live.enabled && live.active,
                    replyWatchActive = false,
                    appVisible = policy.foreground.value,
                    permissionGranted = true,
                    serverAlertsOn = on,
                    quietHours = policy.quiet(),
                    onWifi = null,
                    wifiOnly = false,
                ),
            )
        if (route is AlertRoute.Post) shade.show(CodingAlert(alertKind, id, key, profileId = profile, subtext = server, allowActions = false), route)
    }

    private companion object {
        const val POLL_MS = 20_000L
        const val REQUESTS_ON = "oc.notify.requests"
        const val FINISHED_ON = "oc.notify.finishedRuns"
    }
}
