package io.github.eslamasabry.opencode_mobile.telemetry

import android.content.Context
import io.github.eslamasabry.oc.engine.analytics.ErrorReporter
import io.github.eslamasabry.oc.engine.analytics.NoOpReportSink
import io.github.eslamasabry.oc.engine.analytics.Reporting
import io.github.eslamasabry.oc.engine.analytics.ReportSink
import io.github.eslamasabry.oc.engine.analytics.product.NoOpProductEventSink
import io.github.eslamasabry.oc.engine.analytics.product.ProductAnalytics

/**
 * The process-wide [Reporting] and [ProductAnalytics], built first thing in `OcApplication.onCreate` so even the
 * earliest crash can be reported. The vendors are chosen here and nowhere else: [CrashlyticsSink] and
 * [FirebaseAnalyticsSink] when the build carries the Firebase config, otherwise the no-op sinks.
 *
 * One switch, as Flutter 2.1.5 ("Send reports to developer"): crash reports and usage analytics follow the same answer.
 * Before the adopted prefs store opens, the answer is the app's early copy (this prefs file), else the flavor default
 * (preview builds, the owner's test builds, start ON; switch builds start OFF). `TelemetryContinuity` then adopts the
 * stored Flutter-era answer `oc.telemetry.crashReports` through [adopt], so a "yes" in Flutter stays yes.
 */
internal object Telemetry {
    private const val PREFS = "oc.telemetry"
    private const val KEY_REPORTS = "oc.reports.consent"

    @Volatile private var current: Reporting? = null
    @Volatile private var products: ProductAnalytics? = null
    @Volatile private var crash: CrashlyticsSink? = null

    /** True while [adopt] applies a stored answer: that is not the person turning the switch off now. */
    @Volatile private var adopting = false

    val reporting: Reporting
        get() = checkNotNull(current) { "Telemetry.init was not called" }

    val product: ProductAnalytics
        get() = checkNotNull(products) { "Telemetry.init was not called" }

    /** True when a crash report or an event can actually leave this build (the vendor config is present). */
    @Volatile var vendorAvailable: Boolean = false
        private set

    @Synchronized
    fun init(context: Context, previewFlavor: Boolean): Reporting {
        current?.let { return it }
        val app = context.applicationContext ?: context
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val crashVendor = CrashlyticsSink.create(app).also { crash = it }
        val eventVendor = FirebaseAnalyticsSink.create(app)
        vendorAvailable = crashVendor != null || eventVendor != null
        val sink: ReportSink = crashVendor ?: NoOpReportSink
        val initial = prefs.getBoolean(KEY_REPORTS, previewFlavor)
        val reporter =
            ErrorReporter(sink, initial) { granted ->
                prefs.edit().putBoolean(KEY_REPORTS, granted).apply()
                val chosenNow = !adopting
                products?.applyConsent(granted, chosenNow)
                // Off by the person: unsent crash reports are discarded too (PRIVACY.md).
                if (chosenNow && !granted) crash?.forgetUnsent()
            }
        products =
            ProductAnalytics(
                eventVendor ?: NoOpProductEventSink,
                reporter.reportingConsent,
                available = eventVendor != null,
            )
        current = reporter
        return reporter
    }

    /** Applies the answer stored in the adopted store (Flutter's, or this app's from an earlier start). */
    @Synchronized
    fun adopt(granted: Boolean) {
        val reporter = current ?: return
        if (reporter.reportingConsent.value == granted) return
        adopting = true
        try {
            reporter.setReportingConsent(granted)
        } finally {
            adopting = false
        }
    }
}
