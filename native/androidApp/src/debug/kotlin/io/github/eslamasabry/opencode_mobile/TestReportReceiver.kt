package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.eslamasabry.opencode_mobile.telemetry.Telemetry

/**
 * Debug builds only (src/debug): `adb shell am broadcast -a <applicationId>.TEST_REPORT -p <applicationId>` sends one
 * synthetic non-fatal through the same Reporting layer the app uses (consent and redaction apply). Add
 * `--es kind crash` for a safe-stack crash report. The keys below are fixed strings, nothing from the intent.
 */
class TestReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reporting = Telemetry.reporting
        reporting.breadcrumb("debug test report requested")
        if (intent.getStringExtra("kind") == "crash") {
            reporting.reportCrash("java.lang.IllegalStateException", listOf("io.github.eslamasabry.opencode_mobile.TestReportReceiver.onReceive:1"))
        } else {
            reporting.reportFailure("debug_test_report", mapOf("step" to "manual", "elapsed_ms" to "1"))
        }
        Log.i(
            "OcTelemetry",
            "test report handed to ${reporting.javaClass.simpleName}; consent=${reporting.reportingConsent.value}; vendor=${Telemetry.vendorAvailable}",
        )
    }
}
