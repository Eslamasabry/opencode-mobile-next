package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.analytics.health.AppTelemetry
import io.github.eslamasabry.oc.engine.analytics.health.MemoryTelemetryReportStore
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsent
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsentStore
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryService
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryVendor
import kotlinx.coroutines.CoroutineScope

/**
 * The app's telemetry with usage on and a vendor that only remembers what it was sent, so a test can see which
 * `feature_used` and `starter_used` events a user action produced. Call [stop] (in a finally) to leave the inert default.
 */
internal class FeatureRecorder(scope: CoroutineScope) {
    val events = mutableListOf<Pair<String, Map<String, String>>>()

    private val vendor =
        object : TelemetryVendor {
            override fun setCollection(usage: Boolean, crashes: Boolean) = Unit

            override fun crash(category: String, frames: List<String>, fatal: Boolean) = Unit

            override fun failure(name: String, keys: Map<String, String>) = Unit

            override fun breadcrumb(name: String) = Unit

            override fun event(name: String, props: Map<String, String>) {
                events += name to props
            }

            override fun setKeys(keys: Map<String, String>) = Unit

            override fun setUserProperties(properties: Map<String, String>) = Unit

            override fun perfTrace(name: String, attributes: Map<String, String>, durationMs: Long) = Unit

            override suspend fun problemReport(text: String, collecting: Boolean) = Unit

            override suspend fun appInstanceId(): String? = null
        }

    init {
        val service = TelemetryService(vendor, available = true, scope = scope)
        service.start(
            TelemetryConsent(usage = true),
            object : TelemetryConsentStore {
                override suspend fun write(consent: TelemetryConsent) = true
            },
            MemoryTelemetryReportStore(),
        )
        AppTelemetry.install(service)
    }

    /** The `feature` word of each `feature_used` event, in order. */
    fun features(): List<String> = events.filter { it.first == "feature_used" }.map { it.second.getValue("feature") }

    /** The `starter` word of each `starter_used` event, in order. */
    fun starters(): List<String> = events.filter { it.first == "starter_used" }.map { it.second.getValue("starter") }

    fun stop() = AppTelemetry.reset()
}
