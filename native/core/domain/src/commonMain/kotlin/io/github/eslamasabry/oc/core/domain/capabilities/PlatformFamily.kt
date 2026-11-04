package io.github.eslamasabry.oc.core.domain.capabilities

/**
 * The platform a graph runs on (port of Dart `PlatformCapabilities(platform:, isWeb:)`, B-XPLAT-012/013). Only Android
 * and Linux desktop ship today; the others exist so a future target starts with every unsupported feature off.
 */
public enum class PlatformFamily { Android, LinuxDesktop, Windows, MacOs, Ios, Web }

/** True only for the Android build (web never counts). */
public val PlatformFamily.isAndroid: Boolean get() = this == PlatformFamily.Android

/** Linux, Windows and macOS are desktop; Android, iOS and web are not. */
public val PlatformFamily.isDesktop: Boolean
    get() = this == PlatformFamily.LinuxDesktop || this == PlatformFamily.Windows || this == PlatformFamily.MacOs

/**
 * The capabilities for [family]. Android has every platform feature; Linux desktop has none of the Android-only ones
 * and checks GitHub releases instead; Windows is desktop but ships no artifact yet; macOS is desktop with no release
 * artifact; iOS and web leave every platform feature off.
 */
public fun PlatformCapabilities.Companion.forPlatform(family: PlatformFamily): PlatformCapabilities =
    when (family) {
        PlatformFamily.Android -> android()
        PlatformFamily.LinuxDesktop -> desktop()
        PlatformFamily.Windows, PlatformFamily.MacOs -> desktop().copy(desktopReleaseCheck = false)
        PlatformFamily.Ios, PlatformFamily.Web -> none()
    }

/** Every platform feature off (iOS, web, and any target not built yet). */
public fun PlatformCapabilities.Companion.none(): PlatformCapabilities =
    PlatformCapabilities(
        builtinLinux = false, termuxMigration = false, voice = false, readAloud = false, localPdf = false,
        promptPhotos = false, backgroundService = false, notifications = false, homeWidgets = false,
        launchShortcuts = false, quickSettingsTiles = false, qrPairing = false, tailscaleHandoff = false,
        usbHostBridge = false, inAppUpdater = false, desktopReleaseCheck = false, wifiFileShare = false,
        appFunctions = false,
    )

/** The update channels a capability set enables; at most one may claim it (B-XPLAT-013). */
public val PlatformCapabilities.updateChannels: List<String>
    get() = listOfNotNull("in-app updater".takeIf { inAppUpdater }, "desktop release check".takeIf { desktopReleaseCheck })
