package io.github.eslamasabry.opencode_mobile.telemetry

import android.content.Context
import android.os.Bundle
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import io.github.eslamasabry.oc.engine.analytics.health.PerfSchema
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryVendor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * B-180/B-190: Firebase as the one [TelemetryVendor], the twin of Flutter 2.1.5's three adapters
 * (`firebase_crashlytics_adapter.dart`, `firebase_analytics_adapter.dart`, `firebase_performance_adapter.dart`). The
 * only class in the app that knows the vendor. It receives what `TelemetryService` already checked against the fixed
 * lists, and only while the matching consent is on (except [setCollection] and [problemReport]).
 *
 * The manifest keeps every collection off ("enabled false", never "deactivated") and denies every ad signal; this class
 * turns collection on only as the person's answers say. When the build has no Firebase config ([create] answers null)
 * nothing here ever runs.
 */
internal class FirebaseTelemetryVendor
private constructor(
    private val crashlytics: FirebaseCrashlytics,
    private val analytics: FirebaseAnalytics,
    private val performance: FirebasePerformance,
) : TelemetryVendor {
    @Volatile private var perfOn = false

    override fun setCollection(usage: Boolean, crashes: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(crashes)
        if (!crashes) crashlytics.deleteUnsentReports()
        // Consent mode: analytics storage only with `usage`; ad storage, ad user data and ad personalisation never.
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status(usage),
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            )
        )
        analytics.setAnalyticsCollectionEnabled(usage)
        // Off clears the analytics data kept on the phone, with its random install id (PRIVACY.md).
        if (!usage) analytics.resetAnalyticsData()
        performance.isPerformanceCollectionEnabled = usage
        perfOn = usage
    }

    private fun status(granted: Boolean) =
        if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED

    override fun crash(category: String, frames: List<String>, fatal: Boolean) {
        crashlytics.setCustomKey("report_kind", if (fatal) "crash" else "error")
        crashlytics.recordException(NamedThrowable(category, frames))
    }

    override fun failure(name: String, keys: Map<String, String>) {
        for ((key, value) in keys) crashlytics.setCustomKey(key, value)
        crashlytics.setCustomKey("report_kind", "failure")
        crashlytics.recordException(NamedThrowable(name, emptyList()))
    }

    override fun breadcrumb(name: String) {
        crashlytics.log(name)
    }

    /** `screen_view` goes through Analytics' own screen event, so its reports group pages by the fixed word. */
    override fun event(name: String, props: Map<String, String>) {
        if (name == "screen_view") {
            val screen = props["screen"] ?: return
            analytics.logEvent(
                FirebaseAnalytics.Event.SCREEN_VIEW,
                Bundle().apply { screenViewParams(screen).forEach { (k, v) -> putString(k, v) } },
            )
        } else {
            analytics.logEvent(name, Bundle().apply { props.forEach { (k, v) -> putString(k, v) } })
        }
    }

    override fun setKeys(keys: Map<String, String>) {
        for ((key, value) in keys) crashlytics.setCustomKey(key, value)
    }

    override fun setUserProperties(properties: Map<String, String>) {
        for ((key, value) in properties) analytics.setUserProperty(key, value)
    }

    /** A trace whose time was measured at the call site: started and stopped at once, the time kept as a metric. */
    override fun perfTrace(name: String, attributes: Map<String, String>, durationMs: Long) {
        if (!perfOn) return
        val trace = performance.newTrace(name)
        trace.start()
        for ((key, value) in attributes) trace.putAttribute(key, value)
        trace.putMetric(PerfSchema.DURATION_METRIC, durationMs)
        trace.stop()
    }

    /**
     * The person's reviewed problem report, as one non-fatal with the text attached as Crashlytics logs (newest 64 KB
     * kept, so the text is cut from its start to fit). With crash reports off, anything unsent is deleted first and
     * only this report is sent.
     */
    override suspend fun problemReport(text: String, collecting: Boolean) {
        if (!collecting) crashlytics.deleteUnsentReports()
        for (chunk in logChunks(text)) crashlytics.log(chunk)
        crashlytics.setCustomKey("report_kind", "problem_report")
        crashlytics.recordException(NamedThrowable("problem_report", emptyList()))
        if (!collecting) crashlytics.sendUnsentReports()
    }

    /** Null while analytics collection is off, as Firebase answers. */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun appInstanceId(): String? =
        withContext(Dispatchers.IO) {
            try {
                Tasks.await(analytics.appInstanceId, ID_WAIT_SECONDS, TimeUnit.SECONDS)
            } catch (_: Throwable) {
                null
            }
        }

    /**
     * A throwable built only from a fixed name and `Class.method:line` frames: no message from the app, no cause. Its
     * class name is the report's title in the console, so the name is carried in [toString] and no message is set.
     */
    private class NamedThrowable(private val name: String, frames: List<String>) : RuntimeException() {
        init {
            stackTrace =
                frames
                    .mapNotNull { frame ->
                        val at = frame.lastIndexOf(':')
                        val line = frame.substring(at + 1).toIntOrNull() ?: return@mapNotNull null
                        val method = frame.substring(0, at)
                        val dot = method.lastIndexOf('.')
                        if (dot <= 0) null
                        else StackTraceElement(method.substring(0, dot), method.substring(dot + 1), null, line)
                    }
                    .toTypedArray()
        }

        override val message: String
            get() = name

        override fun toString(): String = name

        override fun fillInStackTrace(): Throwable = this
    }

    companion object {
        private const val ID_WAIT_SECONDS = 5L
        private const val LINE_MAX = 1000
        private const val LOG_BUDGET = 60 * 1024

        /** [text] as log lines of at most 1 KB each, newest kept, within 60 KB (Flutter `logChunks`). */
        fun logChunks(text: String, budget: Int = LOG_BUDGET): List<String> {
            val kept = ArrayList<String>()
            var used = 0
            for (line in text.split('\n').asReversed()) {
                val piece = if (line.length > LINE_MAX) line.substring(0, LINE_MAX) else line
                used += piece.length + 1
                if (used > budget) break
                kept += piece
            }
            return kept.asReversed()
        }

        /** Null when this build has no Firebase config (or the vendor could not start). */
        @Suppress("TooGenericExceptionCaught")
        fun create(context: Context): FirebaseTelemetryVendor? =
            try {
                if (FirebaseApp.getApps(context).isEmpty()) null
                else
                    FirebaseTelemetryVendor(
                        FirebaseCrashlytics.getInstance(),
                        FirebaseAnalytics.getInstance(context),
                        FirebasePerformance.getInstance(),
                    )
            } catch (_: Throwable) {
                null
            }
    }
}

/**
 * The params of a `screen_view`: Analytics' own screen name and class, and the registered custom dimension `screen`.
 * Flutter 2.1.5 sent only the first two, so GA's `screen` column was blank on ~1,477 events; the word is the page
 * kind from the closed list, never a route argument.
 */
internal fun screenViewParams(screen: String): Map<String, String> =
    mapOf("screen_name" to screen, "screen_class" to screen, "screen" to screen)
