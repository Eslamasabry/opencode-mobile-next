package io.github.eslamasabry.oc.core.domain.capabilities

import kotlin.concurrent.Volatile

/**
 * Form factor decided once at app start (B-CAP-300..302): tablets (smallest screen width >= 600 dp without a fold)
 * use tablet wording and glyph; phones, foldables and desktop stay [Phone]. Never changes on window resize or split.
 */
public enum class DeviceClass { Phone, Tablet }

/** What this build can do on this platform (B-XPLAT-012/013). Bound once per app graph. Plan 03 §10.4. */
public data class PlatformCapabilities(
    val builtinLinux: Boolean,
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
    val deviceClass: DeviceClass = DeviceClass.Phone,
) {
    public companion object {
        /** Android's smallest-width threshold in dp for a tablet (non-foldable). */
        public const val TABLET_SMALLEST_WIDTH_DP: Int = 600

        /**
         * Process-wide device class recorded once at startup by the platform graph. Screens accept an explicit
         * `deviceClass` parameter that defaults here so standalone tests and renders stay pure.
         */
        @Volatile
        public var currentDeviceClass: DeviceClass = DeviceClass.Phone
            private set

        public fun recordDeviceClass(deviceClass: DeviceClass) {
            currentDeviceClass = deviceClass
        }

        /**
         * Pure form-factor rule: a foldable is always [DeviceClass.Phone] regardless of its unfolded width;
         * otherwise smallest screen width >= [TABLET_SMALLEST_WIDTH_DP] is [DeviceClass.Tablet].
         */
        public fun resolveDeviceClass(smallestScreenWidthDp: Int, hasFold: Boolean): DeviceClass =
            if (!hasFold && smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP) DeviceClass.Tablet
            else DeviceClass.Phone

        /** Everything the Android build ships at the switch. A feature not built yet is turned off by its slice. */
        public fun android(deviceClass: DeviceClass = DeviceClass.Phone): PlatformCapabilities =
            PlatformCapabilities(
                builtinLinux = true, voice = true, readAloud = true, localPdf = true,
                promptPhotos = true, backgroundService = true, notifications = true, homeWidgets = true,
                launchShortcuts = true, quickSettingsTiles = true, qrPairing = true, tailscaleHandoff = true,
                usbHostBridge = true, inAppUpdater = true, desktopReleaseCheck = false, wifiFileShare = true,
                appFunctions = true, deviceClass = deviceClass,
            )

        public fun desktop(): PlatformCapabilities =
            PlatformCapabilities(
                builtinLinux = false, voice = false, readAloud = false, localPdf = true,
                promptPhotos = true, backgroundService = false, notifications = false, homeWidgets = false,
                launchShortcuts = false, quickSettingsTiles = false, qrPairing = false, tailscaleHandoff = false,
                usbHostBridge = false, inAppUpdater = false, desktopReleaseCheck = true, wifiFileShare = false,
                appFunctions = false, deviceClass = DeviceClass.Phone,
            )
    }
}
