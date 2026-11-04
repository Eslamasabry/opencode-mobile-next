package io.github.eslamasabry.oc.engine.linuxsetup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/setup_preflight_test.dart. */
class SetupPreflightTest {
    private val download = 165_000_000L

    private fun device(abis: List<String> = listOf("arm64-v8a"), memoryMb: Int? = 4096, availableBytes: Long? = 2_000_000_000) =
        SetupDevice(supportedAbis = abis, totalMemoryMb = memoryMb, availableStorageBytes = availableBytes)

    private fun check(device: SetupDevice) = SetupPreflight.check(device, download)

    @Test
    fun `B-LINUX-209 passes a supported, roomy, well-remembered phone`() {
        val result = check(device())
        assertTrue(result.supported)
        assertNull(result.issue)
    }

    @Test
    fun `B-LINUX-209 blocks a 32-bit-only ABI, reporting the CPU it found`() {
        val result = check(device(abis = listOf("armeabi-v7a")))
        assertEquals(SetupPreflightIssue.UnsupportedAbi, result.issue)
        assertEquals("armeabi-v7a", result.reportedAbi)
        assertEquals(
            "This app's Ubuntu only runs on a 64-bit Arm or Intel phone; this one reports armeabi-v7a.",
            SetupPreflightWordsEn.body(result),
        )
    }

    @Test
    fun `B-LINUX-209 an x86_64 device passes and an unknown ABI list never blocks`() {
        assertTrue(check(device(abis = listOf("x86_64"))).supported)
        assertTrue(check(device(abis = emptyList())).supported)
    }

    @Test
    fun `B-LINUX-209 ABI is checked before memory or space`() {
        val result = check(device(abis = listOf("armeabi-v7a"), memoryMb = 512, availableBytes = 0))
        assertEquals(SetupPreflightIssue.UnsupportedAbi, result.issue)
    }

    @Test
    fun `B-LINUX-210 under the memory floor is refused naming both numbers`() {
        for (memory in listOf(1024, 1700)) {
            val result = check(device(memoryMb = memory))
            assertFalse(result.supported)
            assertEquals(SetupPreflightIssue.LowMemory, result.issue)
            assertEquals(memory, result.totalMemoryMb)
            assertFalse(result.mayBeSlow)
            val body = SetupPreflightWordsEn.body(result)!!
            assertTrue(body.contains("1800 MB") && body.contains("$memory MB"), body)
        }
    }

    @Test
    fun `B-LINUX-210 a nominal 2 GB phone may set up with the slow note`() {
        val result = check(device(memoryMb = 1972))
        assertTrue(result.supported)
        assertTrue(result.mayBeSlow)
        assertEquals(1972, result.totalMemoryMb)
        assertEquals("It may be slow on this phone, which has 1972 MB of memory.", SetupPreflightWordsEn.body(result))
    }

    @Test
    fun `B-LINUX-210 exactly at the floor passes with the note`() {
        assertEquals(1800, SetupPreflight.MINIMUM_MEMORY_MB)
        val result = check(device(memoryMb = SetupPreflight.MINIMUM_MEMORY_MB))
        assertTrue(result.supported)
        assertTrue(result.mayBeSlow)
    }

    @Test
    fun `B-LINUX-210 a nominal 3 GB phone keeps the note`() {
        val result = check(device(memoryMb = 2900))
        assertTrue(result.supported)
        assertTrue(result.mayBeSlow)
        assertEquals(2900, result.totalMemoryMb)
    }

    @Test
    fun `B-LINUX-210 4 GB and 3072 MB and above have no note`() {
        val result = check(device(memoryMb = 4096))
        assertTrue(result.supported)
        assertFalse(result.mayBeSlow)
        assertNull(result.totalMemoryMb)
        assertFalse(check(device(memoryMb = SetupPreflight.COMFORTABLE_MEMORY_MB)).mayBeSlow)
    }

    @Test
    fun `B-LINUX-210 unknown memory never blocks`() {
        val result = check(device(memoryMb = null))
        assertTrue(result.supported)
        assertFalse(result.mayBeSlow)
    }

    @Test
    fun `B-LINUX-211 low space blocks naming exactly how many bytes to free`() {
        val result = check(device(availableBytes = 100_000_000))
        assertEquals(SetupPreflightIssue.LowSpace, result.issue)
        assertEquals(230_000_000L, result.bytesToFree)
        assertEquals("Not enough free space", SetupPreflightWordsEn.headline(result))
    }

    @Test
    fun `B-LINUX-211 low space on a slow phone still blocks, checked before the slow note`() {
        val result = check(device(memoryMb = 1972, availableBytes = 100_000_000))
        assertEquals(SetupPreflightIssue.LowSpace, result.issue)
        assertFalse(result.mayBeSlow)
    }

    @Test
    fun `B-LINUX-211 exactly the required space passes and unknown storage never blocks`() {
        assertTrue(check(device(availableBytes = SetupPreflight.requiredFreeBytes(download))).supported)
        assertTrue(check(device(availableBytes = null)).supported)
    }

    @Test
    fun `B-LINUX-211 required free bytes doubles the download over a floor, counts known payloads and saturates`() {
        assertEquals(330_000_000L, SetupPreflight.requiredFreeBytes(download))
        assertEquals(1_000_000_000L, SetupPreflight.requiredFreeBytes(500_000_000))
        assertEquals(300_000_000L, SetupPreflight.requiredFreeBytes(0))
        assertEquals(300_000_000L, SetupPreflight.requiredFreeBytes(1000))
        val scratch = 64L * 1024 * 1024
        assertEquals(120_000_000L + 350_000_000L + scratch, SetupPreflight.requiredFreeBytes(120_000_000, 350_000_000))
        assertEquals(300_000_000L, SetupPreflight.requiredFreeBytes(1000, 1000))
        assertEquals(1_000_000_000L, SetupPreflight.requiredFreeBytes(500_000_000, 1))
        for (installed in listOf<Long?>(null, 0, -1)) {
            assertEquals(300_000_000L, SetupPreflight.requiredFreeBytes(120_000_000, installed))
        }
        assertEquals(350_000_000L + scratch, SetupPreflight.requiredFreeBytes(-1, 350_000_000))
        assertEquals(Long.MAX_VALUE, SetupPreflight.requiredFreeBytes(1, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, SetupPreflight.requiredFreeBytes(1, Long.MAX_VALUE - scratch - 1))
        assertEquals(Long.MAX_VALUE, SetupPreflight.requiredFreeBytes(Long.MAX_VALUE))
        assertFalse(SetupPreflight.checkStorage(1, 0).supported)
        assertTrue(SetupPreflight.checkStorage(null, 0).supported)
    }
}
