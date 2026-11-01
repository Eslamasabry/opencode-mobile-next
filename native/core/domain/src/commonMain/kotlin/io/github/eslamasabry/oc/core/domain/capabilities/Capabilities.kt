package io.github.eslamasabry.oc.core.domain.capabilities

/** Slice-0 subset; the 85 flags are generated once from lib/domain/server_capabilities.dart (slice-1 freeze). */
public data class ServerCapabilities(
    val promptAttachments: Boolean = true,
    val fileBrowsing: Boolean = true,
    val terminal: Boolean = true,
    val forms: Boolean = false,
    val inbox: Boolean = false,
    val promptSteering: Boolean = false,
) {
    public companion object {
        public val Unknown: ServerCapabilities = ServerCapabilities()
    }
}

/** What this build can do on this platform (B-XPLAT-012/013). Bound once per app graph. */
public data class PlatformCapabilities(
    val builtinLinux: Boolean,
    val notifications: Boolean,
    val homeWidgets: Boolean,
    val inAppUpdater: Boolean,
) {
    public companion object {
        public fun android(): PlatformCapabilities = PlatformCapabilities(true, true, true, true)

        public fun desktop(): PlatformCapabilities = PlatformCapabilities(false, false, false, false)
    }
}
