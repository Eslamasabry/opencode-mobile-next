package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.eslamasabry.oc.feature.servers.presentation.TailscaleSetup

/**
 * The Servers pages ask for `TailscaleSetup?` (ServersEntries). Metro keys a nullable type apart from its non-null
 * binding (AndroidTailscaleSetup), so without this it was always null on the phone and the Tailscale setup sheet always
 * said Tailscale is unavailable (since 3.0.0). Binding the nullable key to the phone's check fills it, without touching
 * the entries.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object NullableTailscaleBinding {
    @Provides fun tailscale(setup: TailscaleSetup): TailscaleSetup? = setup
}
