package io.github.eslamasabry.opencode_mobile.telemetry

import android.content.Context
import io.github.eslamasabry.oc.engine.analytics.ErrorReporter
import io.github.eslamasabry.oc.engine.analytics.NoOpReportSink
import io.github.eslamasabry.oc.engine.analytics.Reporting
import io.github.eslamasabry.oc.engine.analytics.ReportSink

/**
 * The process-wide [Reporting], built first thing in `OcApplication.onCreate` so even the earliest crash can be
 * reported. The vendor is chosen here and nowhere else: [CrashlyticsSink] when the build carries the Firebase config,
 * otherwise [NoOpReportSink].
 *
 * Consent: preview builds (the owner's test builds) start ON, switch builds (public) start OFF; the Settings > Privacy
 * switch overrides either and is stored in app-private preferences.
 */
internal object Telemetry {
    private const val PREFS = "oc.telemetry"
    private const val KEY_REPORTS = "oc.reports.consent"

    @Volatile private var current: Reporting? = null

    val reporting: Reporting
        get() = checkNotNull(current) { "Telemetry.init was not called" }

    /** True when a crash report can actually leave this build (the vendor config is present). */
    @Volatile var vendorAvailable: Boolean = false
        private set

    @Synchronized
    fun init(context: Context, previewFlavor: Boolean): Reporting {
        current?.let { return it }
        val app = context.applicationContext ?: context
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val vendor = CrashlyticsSink.create(app)
        vendorAvailable = vendor != null
        val sink: ReportSink = vendor ?: NoOpReportSink
        val initial = prefs.getBoolean(KEY_REPORTS, previewFlavor)
        return ErrorReporter(sink, initial) { prefs.edit().putBoolean(KEY_REPORTS, it).apply() }
            .also { current = it }
    }
}
