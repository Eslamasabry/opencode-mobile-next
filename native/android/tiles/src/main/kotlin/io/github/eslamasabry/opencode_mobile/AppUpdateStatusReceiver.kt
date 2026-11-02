package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.github.eslamasabry.oc.android.tiles.updates.UpdateInstallEvents
import io.github.eslamasabry.oc.android.tiles.updates.UpdateSigners
import io.github.eslamasabry.oc.engine.updates.InstallEvent
import io.github.eslamasabry.oc.engine.updates.InstallState

/**
 * PackageInstaller's answer (frozen FQCN from the Flutter app): Android's own confirm dialog, then the outcome, handed
 * to the running install flow. Android's message is technical and goes only to Details.
 */
public class AppUpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
        }
        val state = UpdateSigners.stateFor(status)
        val message = if (state == InstallState.Failure) intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) else null
        UpdateInstallEvents.events.tryEmit(InstallEvent(state, message))
    }
}
