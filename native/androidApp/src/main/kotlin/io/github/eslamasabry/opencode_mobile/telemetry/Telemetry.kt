package io.github.eslamasabry.opencode_mobile.telemetry

import android.content.Context
import io.github.eslamasabry.oc.engine.analytics.health.AppTelemetry
import io.github.eslamasabry.oc.engine.analytics.health.NoOpTelemetryVendor
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsent
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryService
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryVendor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The process-wide [TelemetryService], built first thing in `OcApplication.onCreate` so even the earliest crash of a
 * person who agreed can be reported. The vendor is chosen here and nowhere else: [FirebaseTelemetryVendor] when the
 * build carries the private Firebase config, otherwise nothing (a preview, CI or F-Droid build sends nothing and offers
 * no switches).
 *
 * Consent is off until the person turns it on, in every flavour. The answers live under Flutter 2.1.5's keys in the
 * adopted preferences (`HealthConsentStore`, read once the migration gate opens); this file keeps only an early copy
 * in app-private preferences so a crash before that point still follows the last answer. The early copy is never
 * applied to the vendor: Firebase keeps its own last setting until the stored answers are adopted.
 */
internal object Telemetry {
    private const val PREFS = "oc.telemetry"
    private const val EARLY_USAGE = "early.usage"
    private const val EARLY_CRASHES = "early.crashes"

    @Volatile private var current: TelemetryService? = null

    val service: TelemetryService
        get() = checkNotNull(current) { "Telemetry.init was not called" }

    /** True when reports can actually leave this build (the vendor config is present). */
    val vendorAvailable: Boolean
        get() = current?.available ?: false

    @Synchronized
    fun init(context: Context): TelemetryService {
        current?.let { return it }
        val app = context.applicationContext ?: context
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Gap 3: the Firebase singletons are built on a background thread on first use, not here on the main thread.
        val vendor: TelemetryVendor? =
            if (DeferredFirebaseTelemetryVendor.Config.present(app)) DeferredFirebaseTelemetryVendor(app) else null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val early = TelemetryConsent(prefs.getBoolean(EARLY_USAGE, false), prefs.getBoolean(EARLY_CRASHES, false))
        val service =
            TelemetryService(
                vendor = vendor ?: NoOpTelemetryVendor,
                available = vendor != null,
                early = early,
                scope = scope,
            )
        // The early copy follows every later answer.
        scope.launch {
            combine(service.usage, service.crashes) { u, c -> u to c }
                .drop(1)
                .collect { (u, c) -> prefs.edit().putBoolean(EARLY_USAGE, u).putBoolean(EARLY_CRASHES, c).apply() }
        }
        AppTelemetry.install(service)
        current = service
        return service
    }
}

/**
 * The app's log lines (already through the app's redacting writer) into the telemetry ring, as Flutter routes
 * `debugPrint` into `AppLogRing`. The ring never leaves the phone by itself: only a problem report the person
 * previewed and sent carries it, redacted again.
 */
internal object TelemetryRingSink : io.github.eslamasabry.oc.core.common.LogSink {
    override fun write(entry: io.github.eslamasabry.oc.core.common.LogEntry) {
        val ring = runCatching { Telemetry.service.ring }.getOrNull() ?: return
        val level = entry.severity.name.first()
        ring.add("$level ${entry.tag}: ${entry.message}" + (entry.error?.let { " (${it.type})" } ?: ""))
    }
}
