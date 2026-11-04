package io.github.eslamasabry.oc.engine.runtime.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The free-space rule and the pre-flight it feeds (port of test/setup_preflight_test.dart). */
class SetupPreflightTest {
    private val download = 165_000_000L
    private val scratch = 64L * 1024 * 1024
    private val maximum = Long.MAX_VALUE

    private fun device(
        abis: List<String> = listOf("arm64-v8a"),
        memoryMb: Int? = 4096,
        availableBytes: Long? = 2_000_000_000,
    ) = SetupDeviceInfo(abis, memoryMb, availableBytes)

    private fun required(download: Long, installed: Long? = null) = SetupPreflight.requiredFreeBytes(download, installed)

    @Test
    fun `B-LINUX-192 required free space is twice the download once that clears the floor`() {
        assertEquals(330_000_000, required(download))
        assertEquals(1_000_000_000, required(500_000_000))
    }

    @Test
    fun `B-LINUX-192 required free space never drops below the floor for a tiny or zero download`() {
        assertEquals(300_000_000, required(0))
        assertEquals(300_000_000, required(1000))
    }

    @Test
    fun `B-LINUX-192 a known extracted payload counts with the retained download and scratch`() {
        assertEquals(120_000_000 + 350_000_000 + scratch, required(120_000_000, 350_000_000))
        assertEquals(300_000_000, required(1000, 1000))
        assertEquals(1_000_000_000, required(500_000_000, 1))
    }

    @Test
    fun `B-LINUX-192 unknown and nonpositive extracted sizes keep the legacy admission`() {
        for (installed in listOf(null, 0L, -1L)) {
            assertEquals(300_000_000, required(120_000_000, installed))
            assertEquals(1_000_000_000, required(500_000_000, installed))
        }
        assertEquals(350_000_000 + scratch, required(-1, 350_000_000))
    }

    @Test
    fun `B-LINUX-192 the known peak and the doubled download saturate without overflow`() {
        assertEquals(maximum, required(1, maximum))
        assertEquals(maximum, required(1, maximum - scratch - 1))
        assertEquals(maximum - 1, required(1, maximum - scratch - 2))
        assertEquals(maximum, required(maximum, 1))
        assertEquals(maximum, required(maximum / 2 + 1, 1))
    }

    @Test
    fun `B-LINUX-192 the known extracted peak forwards through storage and full preflight`() {
        val download = 120_000_000L
        val installed = 350_000_000L
        val need = download + installed + scratch
        val low = SetupPreflight.checkStorage(need - 1, download, installed)
        assertEquals(SetupPreflightIssue.LowSpace, low.issue)
        assertEquals(1, (low as SetupPreflightResult.LowSpace).bytesToFree)
        assertTrue(SetupPreflight.checkStorage(need, download, installed).supported)
        assertTrue(SetupPreflight.checkStorage(null, download, installed).supported)
        val full = SetupPreflight.check(device(availableBytes = need - 1), download, installed)
        assertEquals(SetupPreflightIssue.LowSpace, full.issue)
        assertEquals(1, (full as SetupPreflightResult.LowSpace).bytesToFree)
        assertTrue(SetupPreflight.check(device(availableBytes = need), download, installed).supported)
    }

    @Test
    fun `B-LINUX-192 component clones preserve the known extracted payload and other metadata`() {
        val component = SetupComponent(
            id = "fixture", title = "Fixture", shortTitle = "Fixture", checkScript = "check", installScript = "install",
            dependsOn = listOf("dependency"), required = true, defaultOn = true, estimatedSeconds = 13,
            downloadBytes = 120_000_000, installedBytes = 350_000_000, removeScript = "remove",
            presenceScript = "present", sizeScript = "size", why = "Required fixture", summary = "Fixture summary",
            agentUser = true,
        )
        val copied = component.withDownloadBytes(42)
        assertEquals(42, copied.downloadBytes)
        assertEquals(350_000_000, copied.installedBytes)
        val offer = component.withAppOffer(SetupAppOffer(downloadBytes = 77, downloadSize = DownloadSize.Unknown))
        assertEquals(77, offer.downloadBytes)
        assertEquals(350_000_000, offer.installedBytes)
        val changed = copied.withInstalledBytes(123)
        assertEquals(123, changed.installedBytes)
        assertEquals(42, changed.downloadBytes)
        assertEquals(listOf("dependency"), changed.dependsOn)
        assertTrue(changed.required)
        assertTrue(changed.defaultOn)
        assertEquals(13, changed.estimatedSeconds)
        assertEquals("check", changed.checkScript)
        assertEquals("install", changed.installScript)
        assertEquals("remove", changed.removeScript)
        assertEquals("present", changed.presenceScript)
        assertEquals("size", changed.sizeScript)
        assertEquals("Required fixture", changed.why)
        assertEquals("Fixture summary", changed.summary)
        assertTrue(changed.agentUser)
        assertNull(changed.withInstalledBytes(null).installedBytes)
    }

    @Test
    fun `B-LINUX-192 the repeatable storage check respects fresh readings and the exact threshold`() {
        assertEquals(SetupPreflightIssue.LowSpace, SetupPreflight.checkStorage(299_999_999, 0).issue)
        assertTrue(SetupPreflight.checkStorage(300_000_000, 0).supported)
        assertFalse(SetupPreflight.checkStorage(1, 0).supported)
        assertTrue(SetupPreflight.checkStorage(null, 0).supported)
        assertEquals(300_000_000, required(-1))
        assertEquals(maximum, required(maximum))
    }

    @Test
    fun `B-LINUX-192 the full preflight checks ABI first, then RAM, then space`() {
        assertEquals(SetupPreflightResult.Ok, SetupPreflight.check(device(), download))
        assertEquals(SetupPreflightResult.UnsupportedAbi("armeabi-v7a"),
            SetupPreflight.check(device(abis = listOf("armeabi-v7a"), memoryMb = 512), download))
        assertTrue(SetupPreflight.check(device(abis = listOf("x86_64")), download).supported)
        assertEquals(SetupPreflightResult.LowMemory(1500), SetupPreflight.check(device(memoryMb = 1500), download))
        assertEquals(SetupPreflightResult.MayBeSlow(2048), SetupPreflight.check(device(memoryMb = 2048), download))
        assertTrue(SetupPreflight.check(SetupDeviceInfo(emptyList(), null, null), download).supported)
    }
}
