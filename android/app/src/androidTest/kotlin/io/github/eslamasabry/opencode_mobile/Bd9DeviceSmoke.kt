package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import dev.flutter.plugins.integration_test.IntegrationTestPlugin
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Test APK only. RELEASE AOT results arrive through the integration-test plugin. */
class Bd9DeviceSmoke : Instrumentation() {
    private var explicitlyEnabled = false

    override fun onCreate(arguments: Bundle?) {
        explicitlyEnabled = arguments?.getString("bd9Qa") == "true"
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val report = Bundle()
        var passed = false
        var activity: Activity? = null
        try {
            check(explicitlyEnabled && targetContext.packageName == STABLE_PACKAGE)
            val checks = PhoneEngineNativeRegressions.runOfflineSmoke(targetContext)
            checks.forEach { name ->
                sendStatus(0, Bundle().apply { putString("bd9NativeCheck", "$name:PASS") })
            }
            report.putString("bd9PhoneEngineChecks", checks.size.toString())
            report.putString("bd9PhoneEngine", "PASS")
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // Release registrants omit dev plugins; this test runner registers
            // its QA-only dependency without adding it to production startup.
            val flutterActivity = activity as FlutterActivity
            runOnMainSync {
                val view = checkNotNull(flutterActivity.findViewById<FlutterView>(FlutterActivity.FLUTTER_VIEW_ID))
                val engine = checkNotNull(view.attachedFlutterEngine)
                if (!engine.plugins.has(IntegrationTestPlugin::class.java)) {
                    engine.plugins.add(IntegrationTestPlugin())
                }
            }
            val results = IntegrationTestPlugin.testResults.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            check(results.size == 1 && results.values.all { it == "success" })
            report.putString("bd9FlutterTests", results.size.toString())
            report.putString("bd9Flutter", "PASS")
            exportScreenshot()
            passed = true
        } catch (_: Throwable) {
            // Never expose Throwable text, Dart failure details or credentials.
            report.putString("bd9Failure", "device_smoke_failed")
        } finally {
            activity?.let { current -> runOnMainSync { current.finish() } }
            report.putString("bd9Result", if (passed) "PASS" else "FAIL")
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, report)
        }
    }

    private fun exportScreenshot() {
        val directory = checkNotNull(targetContext.getExternalFilesDir(null))
        val png = File(directory, "bd9-conversations.png")
        val original = checkNotNull(BitmapFactory.decodeFile(png.absolutePath))
        val width = minOf(original.width, SCREENSHOT_WIDTH)
        val height = original.height * width / original.width
        val image = Bitmap.createScaledBitmap(original, width, height, true)
        FileOutputStream(File(directory, "bd9-conversations.jpg")).use { output ->
            check(image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
        }
        if (image !== original) image.recycle()
        original.recycle()
        check(png.delete())
    }

    private companion object {
        const val STABLE_PACKAGE = "io.github.eslamasabry.opencode_mobile"
        const val RESULT_TIMEOUT_SECONDS = 120L
        const val SCREENSHOT_WIDTH = 480
        const val JPEG_QUALITY = 75
    }
}
