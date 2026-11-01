package io.github.eslamasabry.oc.engine.connection

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.ServerProfile

/**
 * One graph per LIVE connection (ADR §9.1, plan 03 §13.4). Engines above `connection` add their own accessors with
 * `@ContributesTo(ConnectionScope::class)` (plan 03 S4); Metro merges them into the generated extension.
 */
@GraphExtension(ConnectionScope::class)
public interface ConnectionGraph {
    public val profile: ServerProfile
    public val session: ConnectionSession
    public val scope: ConnectionCoroutineScope

    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    public fun interface Factory {
        public fun create(
            @Provides profile: ServerProfile,
            @Provides gateways: GatewayPair,
            @Provides scope: ConnectionCoroutineScope,
        ): ConnectionGraph
    }
}

@BindingContainer
@ContributesTo(ConnectionScope::class)
public object GatewayBindings {
    @Provides public fun live(pair: GatewayPair): ServerGateway = pair.live

    @Provides public fun operations(pair: GatewayPair): ServerOperationsGateway = pair.operations
}
