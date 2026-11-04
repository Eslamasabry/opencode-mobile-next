package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.AlertShown
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionAlertDelivery
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscriptionsEngine
import java.util.Calendar
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A collector's periodic read needs visible use or Android's confirmed background runtime. */
@SingleIn(AppScope::class)
@Inject
class QuotaCollectionPolicy(
    private val app: Application,
    private val stores: PreviewStores,
    background: BackgroundHub,
    activities: ResumedActivities,
    appScope: AppCoroutineScope,
) {
    val foreground = MutableStateFlow(activities.current != null)
    @Volatile
    var runtimeEpoch: Long = 0
        private set

    @Volatile
    var backgroundAllowed: Boolean = background.live.value.active
        private set

    val runningAllowed: Boolean
        get() = foreground.value || backgroundAllowed

    init {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var started = if (foreground.value) 1 else 0

                override fun onActivityStarted(activity: Activity) {
                    started++
                    foreground.value = true
                }

                override fun onActivityStopped(activity: Activity) {
                    started = (started - 1).coerceAtLeast(0)
                    foreground.value = started > 0 || activity.isChangingConfigurations
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
                    Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
        appScope.launch {
            combine(foreground, background.live) { visible, live -> visible to live.active }
                .collect { (_, active) ->
                    backgroundAllowed = active
                    runtimeEpoch++
                }
        }
    }

    fun onWifi(): Boolean? {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return null
        return manager
            .getNetworkCapabilities(manager.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun wifiOnly(legacy: Boolean = false): Boolean =
        stores.prefs.bool("oc.notify.wifiOnly") ?: legacy

    fun quiet(legacyStart: Int? = null, legacyEnd: Int? = null): Boolean {
        val start = stores.prefs.long("oc.notify.quietStart")?.toInt() ?: legacyStart
        val end = stores.prefs.long("oc.notify.quietEnd")?.toInt() ?: legacyEnd
        if (start == null || end == null || start !in 0..1439 || end !in 0..1439) return false
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return start == end ||
            if (start < end) minute in start until end else minute >= start || minute < end
    }

    fun deliveryAllowed(): Boolean =
        !foreground.value && !quiet() && (!wifiOnly() || onWifi() == true)
}

/** Runtime adapter for the protocol-neutral subscription alert owner. */
@SingleIn(AppScope::class)
@Inject
class AndroidSubscriptionAlerts(
    stores: PreviewStores,
    clock: Clock,
    policy: QuotaCollectionPolicy,
    publisher: QuotaAlertPublisher,
) {
    val delivery =
        SubscriptionAlertDelivery(
            stores.prefs,
            stores.lanes.app,
            { clock.now() },
            { policy.deliveryAllowed() },
            { note ->
                publisher.show(
                    CodingAlert(
                        if (note.reset) AlertKind.QuotaReset else AlertKind.Quota,
                        "quota",
                        note.key,
                        profileId = note.profileId,
                        monitorToken = note.token,
                        agentName = note.providerName,
                    )
                ) == AlertShown.Shown
            },
            { publisher.dismiss(it) },
        )
}

/** Only a current opaque consent or subscription token can open Plans from a quota notification. */
@SingleIn(AppScope::class)
@Inject
class QuotaAlertTargets(
    private val stores: PreviewStores,
    private val collector: CollectorUsageSource,
) {
    suspend fun accepts(profile: String, token: String): Boolean {
        if (!Regex("^[a-f0-9]{64}$").matches(token) || stores.warm() !is Outcome.Ok) return false
        val id = ProfileId(profile)
        if (stores.profiles.profile(id) == null && profile != "phone") return false
        if (!collector.acceptsProfile(profile)) return false
        if (collector.acceptsAlert(profile, token)) return true
        val cached =
            DartJson.decodeOrNull(
                stores.prefs.string(UsageSubscriptionsEngine.lastKey(profile)).orEmpty()
            ) as? List<*> ?: return false
        return cached.any { value ->
            val row = value as? Map<*, *> ?: return@any false
            val provider = row["provider"] as? String ?: return@any false
            val account = row["account"] as? String
            val key =
                io.github.eslamasabry.oc.engine.integrations.usage.usageSubscriptionKey(
                    provider,
                    account,
                    profile,
                )
            SubscriptionAlertDelivery.tokenFor(key) == token
        }
    }
}
