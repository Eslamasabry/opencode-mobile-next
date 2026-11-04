package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.github.eslamasabry.oc.core.model.NetworkStatus

/** The same process observer drives registry offline state and connection network changes. */
@BindingContainer
@ContributesTo(AppScope::class)
object ProtoNetworkWiring {
    @Provides @IntoSet fun network(source: SetupNetwork): NetworkStatus = source.monitor
}
