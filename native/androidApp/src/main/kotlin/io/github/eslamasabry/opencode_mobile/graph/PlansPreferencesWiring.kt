package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetSettings
import io.github.eslamasabry.oc.feature.plans.presentation.PlanAlertPreferences
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPreferences
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plans alert preferences (screens-3 Plans management page) over the app's ONE usage engine
 * ([AndroidPlansSource]): alerts, thresholds, reset notices and the widget go through that engine;
 * quiet hours and Wi-Fi only are plain app preferences under the Flutter keys. Every change re-reads
 * what was actually saved.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlansPreferences>())
@Inject
class AndroidPlansPreferences(
    private val plans: AndroidPlansSource,
    private val stores: PreviewStores,
) : PlansPreferences {
    private val state = MutableStateFlow(read())
    override val preferences: StateFlow<PlanAlertPreferences> = state.asStateFlow()

    private fun read(): PlanAlertPreferences =
        PlanAlertPreferences(
            enabled = plans.alertsOn(),
            threshold = plans.defaultThreshold.roundToInt(),
            notifyReset = plans.resetNotice,
            quietStart = stores.prefs.long(QUIET_START)?.takeIf { it in 0..1439 }?.toInt(),
            quietEnd = stores.prefs.long(QUIET_END)?.takeIf { it in 0..1439 }?.toInt(),
            wifiOnly = stores.prefs.bool(WIFI) == true,
            widget = plans.widgetSettings,
        )

    private suspend fun save(block: suspend () -> Boolean): Outcome<Unit> {
        val ok = block()
        state.value = read()
        return if (ok) Outcome.Ok(Unit) else Outcome.Failed(Failure.StorageRefused())
    }

    override suspend fun setAlerts(on: Boolean): Outcome<Unit> {
        val result = plans.setAlertsOn(on)
        state.value = read()
        return result
    }

    override suspend fun setDefaultThreshold(level: Int): Outcome<Unit> = save {
        plans.setThreshold(null, level.coerceIn(1, 100).toDouble())
    }

    override suspend fun setDefaultReset(on: Boolean): Outcome<Unit> = save {
        plans.setResetNotice(null, on)
    }

    override suspend fun setThreshold(key: String, level: Int?): Outcome<Unit> = save {
        plans.setThreshold(key, level?.coerceIn(1, 100)?.toDouble())
    }

    override suspend fun setReset(key: String, on: Boolean): Outcome<Unit> = save {
        plans.setResetNotice(key, on)
    }

    override suspend fun setQuiet(start: Int?, end: Int?): Outcome<Unit> = save {
        stores.prefs.commit(stores.lanes.app) {
            if (start == null || end == null || start !in 0..1439 || end !in 0..1439) {
                remove(QUIET_START)
                remove(QUIET_END)
            } else {
                putLong(QUIET_START, start.toLong())
                putLong(QUIET_END, end.toLong())
            }
        } is Outcome.Ok
    }

    override suspend fun setWifiOnly(on: Boolean): Outcome<Unit> = save {
        stores.prefs.commit(stores.lanes.app) { putBool(WIFI, on) } is Outcome.Ok
    }

    override suspend fun setWidget(settings: UsageWidgetSettings): Outcome<Unit> = save {
        plans.setWidgetSettings(settings)
    }

    private companion object {
        const val WIFI = "oc.notify.wifiOnly"
        const val QUIET_START = "oc.notify.quietStart"
        const val QUIET_END = "oc.notify.quietEnd"
    }
}
