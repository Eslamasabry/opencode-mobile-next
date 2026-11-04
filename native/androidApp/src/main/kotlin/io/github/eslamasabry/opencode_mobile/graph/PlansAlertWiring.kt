package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.notifications.AlertFacts
import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.AlertPolicy
import io.github.eslamasabry.oc.android.notifications.AlertRoute
import io.github.eslamasabry.oc.android.notifications.AlertShown
import io.github.eslamasabry.oc.android.notifications.AndroidAlertShade
import io.github.eslamasabry.oc.android.notifications.ChannelRegistry
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.engine.integrations.usage.UsageAlertDelivery
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import java.time.ZoneId
import kotlin.time.Clock

/**
 * Uses the existing notification surface; never starts a foreground service or claims an unlimited
 * lifetime.
 */
@SingleIn(AppScope::class)
@Inject
class AndroidPlansAlerts(
    private val application: Application,
    private val stores: PreviewStores,
    preferences: AppPreferences,
    private val clock: Clock,
    private val activities: ResumedActivities,
    private val background: BackgroundHub,
) {
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val shade = AndroidAlertShade(application, words, ChannelRegistry(manager, words))
    private val delivery by lazy {
        UsageAlertDelivery(
            stores.prefs,
            stores.lanes.app,
            show = { note ->
                val alert =
                    CodingAlert(
                        kind = if (note.reset) AlertKind.QuotaReset else AlertKind.Quota,
                        sessionId = "quota",
                        key = note.key,
                        profileId = note.profileId,
                        monitorToken = note.token,
                        agentName = note.providerName,
                    )
                val facts =
                    AlertFacts(
                        liveModeActive = background.live.value.active,
                        replyWatchActive = false,
                        appVisible = activities.current != null,
                        permissionGranted = manager.areNotificationsEnabled(),
                        serverAlertsOn = stores.prefs.bool(ALERTS) == true,
                        quietHours = quiet(),
                        onWifi = wifi(),
                        wifiOnly = stores.prefs.bool(WIFI) == true,
                    )
                val route = AlertPolicy.route(alert, facts)
                if (
                    route is AlertRoute.Post &&
                        stores.profiles.profile(ProfileId(note.profileId)) != null
                ) {
                    shade.show(alert, route) == AlertShown.Shown
                } else false
            },
            dismiss = shade::dismiss,
            wifi = ::wifi,
            currentProfile = { stores.profiles.profile(ProfileId(it)) != null },
            clock = { clock.now() },
        )
    }

    suspend fun reconcile(snapshot: UsageSnapshot) {
        if (!snapshot.loaded) return
        delivery.evaluate(
            snapshot,
            enabled = stores.prefs.bool(ALERTS) == true && background.live.value.active,
            foreground = activities.current != null,
            quiet = quiet(),
            wifiOnly = stores.prefs.bool(WIFI) == true,
        )
    }

    private fun quiet(): Boolean {
        val start =
            stores.prefs.long(QUIET_START)?.takeIf { it in 0..1439 }?.toInt() ?: return false
        val end = stores.prefs.long(QUIET_END)?.takeIf { it in 0..1439 }?.toInt() ?: return false
        val now =
            java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds())
                .atZone(ZoneId.systemDefault())
        val minute = now.hour * 60 + now.minute
        return if (start == end) true
        else if (start < end) minute in start until end else minute >= start || minute < end
    }

    private fun wifi(): Boolean? {
        val manager = application.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        const val ALERTS = "oc.notify.quotaAlerts"
        const val WIFI = "oc.notify.wifiOnly"
        const val QUIET_START = "oc.notify.quietStart"
        const val QUIET_END = "oc.notify.quietEnd"
    }
}
