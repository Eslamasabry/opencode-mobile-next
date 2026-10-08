package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import dev.flutter.plugins.integration_test.IntegrationTestPlugin
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterView
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Explicit test APK only; output contains states and account-label presence. */
class Bb8DeviceSmoke : Instrumentation() {
    private var enabled = false
    private var readyChannel: MethodChannel? = null
    private var registrationStage = "register_view"

    override fun onCreate(arguments: Bundle?) {
        enabled = arguments?.getString("bb8Qa") == "true"
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val report = Bundle()
        var activity: Activity? = null
        var monitor: Instrumentation.ActivityMonitor? = null
        var passed = false
        var phase = "authorization"
        try {
            check(enabled && targetContext.packageName == PACKAGE)
            val receipt = File(checkNotNull(targetContext.getExternalFilesDir(null)), RECEIPT)
            check(!receipt.exists() || receipt.delete())
            phase = "launch_activity"
            val activityMonitor = addMonitor(MainActivity::class.java.name, null, false)
            monitor = activityMonitor
            targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = checkNotNull(activityMonitor.waitForActivityWithTimeout(ACTIVITY_TIMEOUT_MILLIS)) {
                "bb8_qa_activity_unavailable"
            }
            check(activity is MainActivity) { "bb8_qa_activity_invalid" }
            phase = "register_plugin"
            registerPlugin(activity as FlutterActivity)
            phase = "flutter_results"
            val results = IntegrationTestPlugin.testResults.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            phase = failurePhase(results)
            check(results.keys == setOf("BB8 real private authentication channel") &&
                results.values.all { it == "success" })
            phase = "receipt"
            projectReceipt(receipt, report)
            report.putString("bb8FlutterTests", "1")
            report.putString("bb8Flutter", "PASS")
            passed = true
        } catch (_: TimeoutException) {
            report.putString("bb8Failure", "flutter_timeout")
        } catch (_: Throwable) {
            report.putString("bb8Failure", if (phase == "register_plugin") registrationStage else phase)
        } finally {
            val cleaned = Bb8MainCallback.run(::runOnMainSync) {
                monitor?.let { removeMonitor(it) }
                readyChannel?.setMethodCallHandler(null)
                readyChannel = null
                activity?.finish()
            }
            if (!cleaned) {
                passed = false
                report.putString("bb8Failure", "cleanup")
            }
            report.putString("bb8Result", if (passed) "PASS" else "FAIL")
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, report)
        }
    }

    private fun registerPlugin(flutterActivity: FlutterActivity) {
        check(Bb8MainCallback.run(::runOnMainSync) {
            registrationStage = "register_view"
            val view = checkNotNull(findFlutterView(flutterActivity.window.decorView))
            registrationStage = "register_engine"
            val engine = checkNotNull(view.attachedFlutterEngine)
            registrationStage = "register_plugin"
            if (!engine.plugins.has(IntegrationTestPlugin::class.java)) {
                engine.plugins.add(IntegrationTestPlugin())
            }
            // Dart cannot begin authentication until the result bridge is
            // registered. This rendezvous exists only in the test runner.
            registrationStage = "register_ready"
            readyChannel = MethodChannel(engine.dartExecutor.binaryMessenger, "oc/bb8_qa")
                .also { channel ->
                    channel.setMethodCallHandler { call, result ->
                        if (call.method == "ready") result.success(true) else result.notImplemented()
                    }
                }
        }) { "bb8_qa_registration_failed" }
    }

    private fun findFlutterView(root: View): FlutterView? = when (root) {
        is FlutterView -> root
        is ViewGroup -> (0 until root.childCount).firstNotNullOfOrNull { index ->
            findFlutterView(root.getChildAt(index))
        }
        else -> null
    }

    private fun projectReceipt(file: File, report: Bundle) {
        check(file.isFile && file.length() in 1..MAX_RECEIPT_BYTES)
        val data = JSONObject(file.readText())
        val fields = mapOf(
            "claude" to "signedIn", "fx" to "signedOut",
            "fxLogout" to "signedOut", "fxAfterLogout" to "signedOut",
        )
        check(data.keys().asSequence().toSet() == fields.keys + "claudeAccountLabel")
        fields.forEach { (key, value) ->
            check(data.get(key) == value)
            report.putString("bb8" + key.replaceFirstChar { it.uppercaseChar() }, value)
        }
        val presence = data.get("claudeAccountLabel")
        check(presence == "present" || presence == "absent")
        report.putString("bb8ClaudeAccountLabel", presence as String)
        check(file.delete())
    }

    private fun failurePhase(results: Map<String, String>): String {
        val phases = listOf("initializing", "profile", "ready", "claude_probe", "fx_probe",
            "fx_logout", "fx_after_logout", "receipt")
        return phases.firstOrNull { name ->
            results.values.any { it.contains("bb8_phase_$name") }
        }?.let { "flutter_$it" } ?: "flutter_results"
    }

    private companion object {
        const val PACKAGE = "io.github.eslamasabry.opencode_mobile"
        const val RECEIPT = "bb8-agent-auth.json"
        const val ACTIVITY_TIMEOUT_MILLIS = 20_000L
        const val TIMEOUT_SECONDS = 100L
        const val MAX_RECEIPT_BYTES = 1024L
    }
}
