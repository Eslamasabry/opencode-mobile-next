package io.github.eslamasabry.opencode_mobile.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.eslamasabry.oc.engine.analytics.ReportKind
import io.github.eslamasabry.oc.engine.analytics.ReportSink
import io.github.eslamasabry.oc.engine.analytics.SafeReport
import io.github.eslamasabry.oc.engine.analytics.SinkId

/**
 * The Firebase Crashlytics adapter: the only class in the app that knows the vendor. It receives [SafeReport]s that have
 * already passed the redactor and consent, and never anything else. When the build has no Firebase config
 * ([FirebaseApp] was not initialised) it does nothing, exactly like the no-op sink.
 */
internal class CrashlyticsSink private constructor(private val crashlytics: FirebaseCrashlytics) :
    ReportSink {
    override val id: SinkId get() = SinkId.Crashlytics

    override fun setCollectionEnabled(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    /** The person turned reports off: anything not yet sent is discarded (Flutter 2.1.5 `deleteUnsentReports`). */
    fun forgetUnsent() {
        try {
            crashlytics.deleteUnsentReports()
        } catch (_: Throwable) {
            // Reporting must never be the reason the app fails.
        }
    }

    override fun breadcrumb(text: String) {
        crashlytics.log(text)
    }

    override fun record(report: SafeReport) {
        for ((key, value) in report.keys) crashlytics.setCustomKey(key, value)
        crashlytics.setCustomKey("report_kind", report.kind.name.lowercase())
        crashlytics.recordException(SafeReportException(report))
    }

    /** A throwable built only from the redacted report: no message from the app, only its category and safe frames. */
    private class SafeReportException(report: SafeReport) :
        RuntimeException(
            when (report.kind) {
                ReportKind.Crash -> "crash:${report.name}"
                ReportKind.Failure -> "failure:${report.name}"
            }
        ) {
        init {
            stackTrace =
                report.stack
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
    }

    companion object {
        /** Null when this build has no Firebase config (or the vendor could not start). */
        fun create(context: Context): CrashlyticsSink? =
            try {
                if (FirebaseApp.getApps(context).isEmpty()) null
                else CrashlyticsSink(FirebaseCrashlytics.getInstance())
            } catch (_: Throwable) {
                null
            }
    }
}
