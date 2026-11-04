package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.platform.AndroidTailscaleApp
import io.github.eslamasabry.oc.android.platform.CurrentActivity
import io.github.eslamasabry.oc.android.platform.TailscaleAppState
import io.github.eslamasabry.oc.feature.servers.presentation.TailscalePresence
import io.github.eslamasabry.oc.feature.servers.presentation.TailscaleSetup

@ContributesBinding(AppScope::class, binding = binding<TailscaleSetup>())
@Inject
class AndroidTailscaleSetup(app: Application, activities: ResumedActivities) : TailscaleSetup {
    private val bridge = AndroidTailscaleApp(app, CurrentActivity { activities.current })

    override fun check(): TailscalePresence =
        when (bridge.state()) {
            TailscaleAppState.Installed -> TailscalePresence.Installed
            TailscaleAppState.Missing -> TailscalePresence.Missing
            TailscaleAppState.Unavailable -> TailscalePresence.Unavailable
        }

    override fun open(): Boolean = bridge.open()
}
