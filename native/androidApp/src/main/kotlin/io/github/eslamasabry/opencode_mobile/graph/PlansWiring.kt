package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.opencode_mobile.UsageWidgetProvider
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One owner for Plans, its widget and its alerts. Reads run only in front or under an existing live
 * service.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlansSource>())
@Inject
class AndroidPlansSource(
    private val stores: PreviewStores,
    private val application: Application,
    private val clock: Clock,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val readers: AndroidPlansReaders,
    private val legacy: PlansLegacyCollectors,
    private val alerts: AndroidPlansAlerts,
    private val background: BackgroundHub,
    activities: ResumedActivities,
) : PlansSource, PlansPreferences {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val mutableSnapshot = MutableStateFlow(UsageSnapshot())
    override val snapshot: StateFlow<UsageSnapshot> = mutableSnapshot.asStateFlow()
    private val mutablePreferences = MutableStateFlow(PlanAlertPreferences())
    override val preferences: StateFlow<PlanAlertPreferences> = mutablePreferences.asStateFlow()
    private val creating = Mutex()
    private var engine: UsageSubscriptionsEngine? = null
    private val foreground = MutableStateFlow(activities.current != null)
    private val shown =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>()).apply {
            activities.current?.let(::add)
        }

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    shown.add(activity)
                    foreground.value = true
                }

                override fun onActivityStopped(activity: Activity) {
                    shown.remove(activity)
                    foreground.value = shown.isNotEmpty()
                }

                override fun onActivityDestroyed(activity: Activity) {
                    shown.remove(activity)
                    foreground.value = shown.isNotEmpty()
                }

                override fun onActivityCreated(activity: Activity, saved: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, saved: Bundle) = Unit
            }
        )
        scope.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            val e = engine() ?: return@launch
            launch {
                var known = stores.profiles.profiles.value.associateBy { it.id }
                stores.profiles.profiles.collect { profiles ->
                    val next = profiles.associateBy { it.id }
                    for ((id, before) in known) if (next[id] != before) e.removeProfile(id.raw)
                    known = next
                }
            }
            launch {
                var phoneOwner = stores.prefs.string("oc.builtinServerOwner")
                stores.prefs.changes.collect { keys ->
                    if ("oc.builtinServerOwner" in keys) {
                        val next = stores.prefs.string("oc.builtinServerOwner")
                        if (next != phoneOwner) e.removeProfile(UsageConnection.PHONE_ID)
                        phoneOwner = next
                    }
                    if (
                        keys.any {
                            it.startsWith("oc.notify.") ||
                                it == UsageSubscriptionsEngine.ALERTS_KEY ||
                                it == UsageWidgetSettings.KEY
                        }
                    )
                        project(e)
                }
            }
            combine(foreground, background.live) { front, live -> front to live.active }
                .collectLatest { (front, live) ->
                    e.setForeground(front)
                    if (front || live)
                        while (currentCoroutineContext().isActive) {
                            refresh()
                            delay(if (front) 60_000 else 300_000)
                        }
                }
        }
    }

    private suspend fun engine(): UsageSubscriptionsEngine? = creating.withLock {
        engine?.let {
            return@withLock it
        }
        if (stores.warm() !is Outcome.Ok) return@withLock null
        RetiredQuotaBudgets.dropAll(stores.prefs, stores.lanes.app)
        val widget =
            UsageWidgetFeed(
                stores.prefs,
                stores.lanes.app,
                true,
                refreshNative = { UsageWidgetProvider.refreshAll(application) },
                clock = { clock.now() },
            )
        UsageSubscriptionsEngine(
                store = stores.prefs,
                lane = stores.lanes.app,
                connections = readers::connections,
                sources = listOf(legacy, readers.signInSource, readers),
                alertsOnRule = { stores.prefs.bool(AndroidPlansAlerts.ALERTS) == true },
                setQuotaAlerts = { value ->
                    stores.prefs.commit(stores.lanes.app) {
                        putBool(AndroidPlansAlerts.ALERTS, value)
                    }
                },
                widget = widget,
                clock = { clock.now() },
                legacyRules = legacy::legacyRules,
            )
            .also { created ->
                engine = created
                project(created)
                scope.launch {
                    created.snapshot.collect { value ->
                        mutableSnapshot.value = value
                        alerts.reconcile(value)
                    }
                }
            }
    }

    private fun project(e: UsageSubscriptionsEngine) {
        mutablePreferences.value =
            PlanAlertPreferences(
                enabled = e.alertsOn,
                threshold = e.defaultThreshold.roundToInt(),
                notifyReset = e.notifyOnResetDefault,
                quietStart =
                    stores.prefs
                        .long(AndroidPlansAlerts.QUIET_START)
                        ?.takeIf { it in 0..1439 }
                        ?.toInt(),
                quietEnd =
                    stores.prefs
                        .long(AndroidPlansAlerts.QUIET_END)
                        ?.takeIf { it in 0..1439 }
                        ?.toInt(),
                wifiOnly = stores.prefs.bool(AndroidPlansAlerts.WIFI) == true,
                widget = e.widgetSettings,
            )
    }

    override suspend fun refresh() {
        val e = engine() ?: return
        e.setForeground(foreground.value)
        e.refresh()
        project(e)
    }

    private suspend fun save(write: suspend (UsageSubscriptionsEngine) -> Boolean): Outcome<Unit> {
        val e = engine() ?: return Failure.StorageRefused().failed()
        val saved = write(e)
        project(e)
        if (saved) alerts.reconcile(e.snapshot.value)
        return if (saved) Unit.ok() else Failure.StorageRefused().failed()
    }

    override suspend fun setAlerts(on: Boolean): Outcome<Unit> = save { e ->
        e.setAlertsOn(on)
        e.alertsOn == on
    }

    override suspend fun setDefaultThreshold(level: Int): Outcome<Unit> = save {
        it.setDefaultThreshold(level.toDouble())
    }

    override suspend fun setDefaultReset(on: Boolean): Outcome<Unit> = save {
        it.setNotifyOnResetDefault(on)
    }

    override suspend fun setThreshold(key: String, level: Int?): Outcome<Unit> = save {
        if (it.subscription(key) == null) false else it.setThresholdFor(key, level?.toDouble())
    }

    override suspend fun setReset(key: String, on: Boolean): Outcome<Unit> = save {
        if (it.subscription(key) == null) false else it.setNotifyOnReset(key, on)
    }

    override suspend fun setQuiet(start: Int?, end: Int?): Outcome<Unit> = save {
        if (
            (start == null) != (end == null) ||
                (start != null && start !in 0..1439) ||
                (end != null && end !in 0..1439)
        )
            false
        else
            stores.prefs.commit(stores.lanes.app) {
                if (start == null || end == null) {
                    remove(AndroidPlansAlerts.QUIET_START)
                    remove(AndroidPlansAlerts.QUIET_END)
                } else {
                    putLong(AndroidPlansAlerts.QUIET_START, start.toLong())
                    putLong(AndroidPlansAlerts.QUIET_END, end.toLong())
                }
            } is Outcome.Ok
    }

    override suspend fun setWifiOnly(on: Boolean): Outcome<Unit> = save {
        stores.prefs.commit(stores.lanes.app) { putBool(AndroidPlansAlerts.WIFI, on) } is Outcome.Ok
    }

    override suspend fun setWidget(settings: UsageWidgetSettings): Outcome<Unit> = save {
        it.setWidgetSettings(settings)
    }

    suspend fun removeProfile(profile: ProfileId): Boolean {
        val e = engine() ?: return false
        var acknowledged = e.removeProfile(profile.raw)
        if (stores.prefs.string("oc.builtinServerOwner") == profile.raw)
            acknowledged = e.removeProfile(UsageConnection.PHONE_ID) && acknowledged
        project(e)
        alerts.reconcile(e.snapshot.value)
        return acknowledged
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object PlansPreferenceBindings {
    @Provides fun preferences(source: AndroidPlansSource): PlansPreferences = source
}

@ContributesTo(AppScope::class)
interface PlansGraph {
    val plansSource: AndroidPlansSource
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class UsagePlansDeletionCleaner(private val plans: AndroidPlansSource) : ProfileResourceCleaner {
    override val owner: String = ":engine:integrations-usage (plans)"
    override val order: Int = DeletionStep.DEVICE_SURFACES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        if (plans.removeProfile(profileId)) 1.ok() else Failure.StorageRefused().failed()
}
