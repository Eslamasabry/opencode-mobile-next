package io.github.eslamasabry.oc.core.domain.capabilities

/** What this build can do on this platform (B-XPLAT-012/013). Bound once per app graph. Plan 03 §10.4. */
public data class PlatformCapabilities(
    val builtinLinux: Boolean,
    val termuxMigration: Boolean,
    val voice: Boolean,
    val readAloud: Boolean,
    val localPdf: Boolean,
    val promptPhotos: Boolean,
    val backgroundService: Boolean,
    val notifications: Boolean,
    val homeWidgets: Boolean,
    val launchShortcuts: Boolean,
    val quickSettingsTiles: Boolean,
    val qrPairing: Boolean,
    val tailscaleHandoff: Boolean,
    val usbHostBridge: Boolean,
    val inAppUpdater: Boolean,
    val desktopReleaseCheck: Boolean,
    val wifiFileShare: Boolean,
    val appFunctions: Boolean,
) {
    public companion object {
        /** Everything the Android build ships at the switch. A feature not built yet is turned off by its slice. */
        public fun android(): PlatformCapabilities =
            PlatformCapabilities(
                builtinLinux = true, termuxMigration = true, voice = true, readAloud = true, localPdf = true,
                promptPhotos = true, backgroundService = true, notifications = true, homeWidgets = true,
                launchShortcuts = true, quickSettingsTiles = true, qrPairing = true, tailscaleHandoff = true,
                usbHostBridge = true, inAppUpdater = true, desktopReleaseCheck = false, wifiFileShare = true,
                appFunctions = true,
            )

        public fun desktop(): PlatformCapabilities =
            PlatformCapabilities(
                builtinLinux = false, termuxMigration = false, voice = false, readAloud = false, localPdf = true,
                promptPhotos = true, backgroundService = false, notifications = false, homeWidgets = false,
                launchShortcuts = false, quickSettingsTiles = false, qrPairing = false, tailscaleHandoff = false,
                usbHostBridge = false, inAppUpdater = false, desktopReleaseCheck = true, wifiFileShare = false,
                appFunctions = false,
            )
    }
}
