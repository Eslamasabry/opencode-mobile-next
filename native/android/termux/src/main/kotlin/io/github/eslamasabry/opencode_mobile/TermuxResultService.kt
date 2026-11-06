package io.github.eslamasabry.opencode_mobile

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.eslamasabry.oc.android.termux.TermuxLateResults
import io.github.eslamasabry.oc.android.termux.TermuxResultBody

/**
 * E09-S04: the Flutter app's RUN_COMMAND result service under its frozen class name (component freeze,
 * `flutter-2.1.4.tsv`). Termux answers the exact component the Flutter app named in its PendingIntent, so a setup
 * step or command that was still running in Termux when the app updated reaches this shell instead of a missing
 * class. The body keeps only the kind and outcome (never output) for the Termux notice.
 */
public class TermuxResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = intent?.getBundleExtra(EXTRA_RESULT_BUNDLE)
        try {
            TermuxResultBody(TermuxLateResults.inApp(filesDir), System::currentTimeMillis).accept(
                setupToken = intent?.getStringExtra(EXTRA_SETUP_TOKEN),
                executionId = intent?.getIntExtra(EXTRA_EXECUTION_ID, -1) ?: -1,
                err = result?.takeIf { it.containsKey(KEY_ERR) }?.getInt(KEY_ERR),
                exitCode = result?.takeIf { it.containsKey(KEY_EXIT_CODE) }?.getInt(KEY_EXIT_CODE),
            )
        } catch (_: Exception) {
            // A full disk or a bad extra must not crash the app on Termux's answer; the notice still shows from
            // termux-setup-v2.json, and Termux keeps its own setup state.
        } finally {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val EXTRA_EXECUTION_ID = "oc.executionId"
        const val EXTRA_SETUP_TOKEN = "oc.setupToken"
        const val EXTRA_RESULT_BUNDLE = "result"
        const val KEY_ERR = "err"
        const val KEY_EXIT_CODE = "exitCode"
    }
}
