package io.github.eslamasabry.oc.core.domain.capabilities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ports test/platform_capabilities_test.dart and the capability half of desktop_platform_gating_test.dart. */
class PlatformCapabilitiesParityTest {
    private val android = PlatformCapabilities.forPlatform(PlatformFamily.Android)
    private val linux = PlatformCapabilities.forPlatform(PlatformFamily.LinuxDesktop)

    @Test
    fun `B-XPLAT-012 Android has every platform feature this app ships`() {
        assertTrue(PlatformFamily.Android.isAndroid)
        assertFalse(PlatformFamily.Android.isDesktop)
        with(android) {
            assertTrue(termuxMigration && builtinLinux)
            assertTrue(voice)
            assertTrue(promptPhotos)
            assertTrue(backgroundService)
            assertTrue(notifications)
            assertTrue(homeWidgets)
            assertTrue(inAppUpdater) // the code-push / update channel
            assertTrue(usbHostBridge)
            assertTrue(qrPairing)
            assertFalse(desktopReleaseCheck, "the in-app updater covers Android, so the GitHub fallback stays off")
        }
        assertEquals(PlatformCapabilities.android(), android)
    }

    @Test
    fun `B-XPLAT-012 Linux desktop has none of the Android-only features`() {
        assertFalse(PlatformFamily.LinuxDesktop.isAndroid)
        assertTrue(PlatformFamily.LinuxDesktop.isDesktop)
        with(linux) {
            assertFalse(termuxMigration || builtinLinux)
            assertFalse(voice)
            assertFalse(backgroundService)
            assertFalse(notifications)
            assertFalse(homeWidgets)
            assertFalse(inAppUpdater)
            assertFalse(usbHostBridge)
            assertFalse(qrPairing, "no scanner on desktop: paste is better")
            assertTrue(desktopReleaseCheck, "something has to tell a desktop user a new build exists")
        }
        assertEquals(PlatformCapabilities.desktop(), linux)
    }

    @Test
    fun `B-XPLAT-012 iOS leaves every unsupported platform feature off`() {
        val ios = PlatformCapabilities.forPlatform(PlatformFamily.Ios)
        assertFalse(PlatformFamily.Ios.isAndroid)
        assertFalse(PlatformFamily.Ios.isDesktop)
        with(ios) {
            listOf(termuxMigration, voice, promptPhotos, backgroundService, notifications, homeWidgets, inAppUpdater,
                usbHostBridge, qrPairing, desktopReleaseCheck).forEach { assertFalse(it) }
        }
    }

    @Test
    fun `B-XPLAT-012 macOS is desktop but has no release artifact to check, web is neither Android nor desktop`() {
        val mac = PlatformCapabilities.forPlatform(PlatformFamily.MacOs)
        assertTrue(PlatformFamily.MacOs.isDesktop)
        assertFalse(mac.desktopReleaseCheck)
        assertFalse(mac.inAppUpdater)

        val web = PlatformCapabilities.forPlatform(PlatformFamily.Web)
        assertFalse(PlatformFamily.Web.isAndroid)
        assertFalse(PlatformFamily.Web.isDesktop)
        assertFalse(web.termuxMigration)
        assertFalse(web.desktopReleaseCheck)
    }

    @Test
    fun `B-XPLAT-013 every capability set is claimed by at most one update channel`() {
        for (family in PlatformFamily.entries) {
            val caps = PlatformCapabilities.forPlatform(family)
            assertTrue(caps.updateChannels.size <= 1, "two update notices would fight on $family")
            assertFalse(caps.inAppUpdater && caps.desktopReleaseCheck)
        }
        assertEquals(listOf("in-app updater"), android.updateChannels)
        assertEquals(listOf("desktop release check"), linux.updateChannels)
    }

    @Test
    fun `B-ANDR-021 the background live-update service is an Android capability and off on desktop`() {
        assertTrue(android.backgroundService)
        assertTrue(android.notifications)
        assertFalse(linux.backgroundService)
        assertFalse(linux.notifications)
    }

    @Test
    fun `B-ANDR-022 widget snapshot, launcher shortcuts and tiles are Android capabilities and silent on desktop`() {
        assertTrue(android.homeWidgets && android.launchShortcuts && android.quickSettingsTiles)
        assertFalse(linux.homeWidgets || linux.launchShortcuts || linux.quickSettingsTiles)
    }
}
