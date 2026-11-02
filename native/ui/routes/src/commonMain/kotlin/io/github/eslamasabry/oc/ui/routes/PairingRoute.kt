package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/**
 * Pairing a server by QR (plan 07 E03 `ServersRoute.PairingScanner`, unit PREVIEW-QR). The scanned code never travels in
 * a key: it goes to the memory-only pairing inbox, and the server form takes it from there.
 */
@Serializable
public sealed interface PairingRoute : AppRoute {
    @Serializable
    public data object Scanner : PairingRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp
    }
}
