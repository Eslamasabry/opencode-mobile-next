package io.github.eslamasabry.oc.feature.plans.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetSettings
import kotlinx.coroutines.flow.StateFlow

public data class PlanAlertPreferences(
    val enabled: Boolean = false,
    val threshold: Int = 80,
    val notifyReset: Boolean = false,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
    val wifiOnly: Boolean = false,
    val widget: UsageWidgetSettings = UsageWidgetSettings(),
)

/** Acknowledged choices, backed by the subscriptions engine and shared notification preferences. */
public interface PlansPreferences {
    public val preferences: StateFlow<PlanAlertPreferences>

    public suspend fun setAlerts(on: Boolean): Outcome<Unit>

    public suspend fun setDefaultThreshold(level: Int): Outcome<Unit>

    public suspend fun setDefaultReset(on: Boolean): Outcome<Unit>

    public suspend fun setThreshold(key: String, level: Int?): Outcome<Unit>

    public suspend fun setReset(key: String, on: Boolean): Outcome<Unit>

    public suspend fun setQuiet(start: Int?, end: Int?): Outcome<Unit>

    public suspend fun setWifiOnly(on: Boolean): Outcome<Unit>

    public suspend fun setWidget(settings: UsageWidgetSettings): Outcome<Unit>
}
