package ai.opencode.opencode_mobile

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val channelName = "oc/termux"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isTermuxInstalled" -> result.success(isPackageInstalled("com.termux"))

                    "openTermux" -> {
                        val intent = packageManager.getLaunchIntentForPackage("com.termux")
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }

                    "runInTermux" -> {
                        val script = call.argument<String>("script") ?: ""
                        val background = call.argument<Boolean>("background") ?: true
                        val workdir = call.argument<String>("workdir")
                        result.success(runInTermux(script, background, workdir))
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Sends a command to Termux via the documented RUN_COMMAND service.
     * Requires:
     *  - this app holds com.termux.permission.RUN_COMMAND (manifest)
     *  - Termux has allow-external-apps=true in ~/.termux/termux.properties
     * Returns false when Termux rejects the call (bridge not unlocked yet).
     */
    private fun runInTermux(script: String, background: Boolean, workdir: String?): Boolean =
        try {
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra(
                    "com.termux.RUN_COMMAND_PATH",
                    "/data/data/com.termux/files/usr/bin/bash"
                )
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
                putExtra(
                    "com.termux.RUN_COMMAND_WORKDIR",
                    workdir ?: "/data/data/com.termux/files/home"
                )
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            }
            if (background) startService(intent) else startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
}
