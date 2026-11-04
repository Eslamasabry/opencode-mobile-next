package io.github.eslamasabry.opencode_mobile

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import io.github.eslamasabry.oc.android.tiles.LaunchAction
import io.github.eslamasabry.oc.android.tiles.LaunchActions
import io.github.eslamasabry.opencode_mobile.graph.AndroidLinuxIdleSource
import io.github.eslamasabry.opencode_mobile.graph.PreviewStores
import io.github.eslamasabry.opencode_mobile.graph.appGraph
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** Only this app's immutable notification PendingIntent can enter this unexported start action. */
class IdleStartActivity : ComponentActivity() {
    private var started = false

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        lifecycleScope.launch {
            // Let the application's resumed callbacks establish foreground admission first.
            yield()
            val graph = appGraph as IdleStartAccessors
            graph.idleStartStores.warm()
            // The native receipt checks the exact profile, generation and recovery policy.
            graph.idleStartSource.resume()
            startActivity(
                LaunchActions.mainActivityIntent(packageName, LaunchAction.Linux).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            finish()
        }
    }
}

@ContributesTo(AppScope::class)
interface IdleStartAccessors {
    val idleStartSource: AndroidLinuxIdleSource
    val idleStartStores: PreviewStores
}
