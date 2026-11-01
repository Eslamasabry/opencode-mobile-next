package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.engine.voice.state.PackId
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal fun device(
    ramMb: Long? = 8000,
    storage: Long? = 50L * 1024 * 1024 * 1024,
    abis: List<String> = listOf("arm64-v8a"),
    mic: Boolean = true,
    memoryClassMb: Int? = 256,
    cpus: Int = 8,
) = DeviceProbeResult.Known(ramMb, storage, persistentListOf(*abis.toTypedArray()), mic, false, memoryClassMb, cpus)

class PackSupportTest {
    private fun auto(device: DeviceProbeResult, selected: String = "base", installed: Set<String> = emptySet()) =
        AutomaticPack.choose(PackId(selected), device, { it.raw in installed })?.id?.raw

    @Test
    fun `B-MEDIA-011 the pack is chosen from total RAM, never the Java heap class`() {
        assertEquals("small", auto(device(ramMb = 8000)))
        assertEquals("small", auto(device(ramMb = 3400)))
        assertEquals("base", auto(device(ramMb = 3399)))
        assertEquals("base", auto(device(ramMb = 1536)))
        assertEquals("tiny", auto(device(ramMb = 1535)))
        assertEquals("tiny", auto(device(ramMb = 1100, memoryClassMb = 4096))) // a large heap class cannot qualify it
        assertNull(auto(device(ramMb = 1023)))
    }

    @Test
    fun `B-MEDIA-011 unknown or zero RAM, unsupported hardware or no capture block automatic setup`() {
        assertNull(auto(device(ramMb = null)))
        assertNull(auto(device(ramMb = 0)))
        assertNull(auto(device(abis = listOf("mips"))))
        assertNull(auto(device(mic = false)))
        assertNull(auto(DeviceProbeResult.Unsupported))
        assertNull(auto(DeviceProbeResult.Unknown))
    }

    @Test
    fun `B-MEDIA-011 low free space keeps the offer and never switches to a smaller pack`() {
        val tight = device(ramMb = 8000, storage = 10L * 1024 * 1024)
        assertEquals("small", auto(tight))
        val support = PackSupport.supportFor(ModelCatalog.small, tight)
        assertEquals(PackBlock.Storage, (support as Support.Blocked).block)
        assertEquals(PackSupport.requiredStorageBytes(ModelCatalog.small), support.requiredStorageBytes)
    }

    @Test
    fun `B-MEDIA-011 an installed selected eligible pack is kept and a stored preference never overrides RAM`() {
        assertEquals("tiny", auto(device(ramMb = 8000), selected = "tiny", installed = setOf("tiny")))
        assertEquals("small", auto(device(ramMb = 8000), selected = "tiny")) // not installed
        assertEquals("small", auto(device(ramMb = 8000), selected = "base", installed = setOf("tiny"))) // not selected
        assertEquals("base", auto(device(ramMb = 2000), selected = "small", installed = setOf("small"))) // above RAM
    }

    @Test
    fun `B-MEDIA-024 device preflight disables High accuracy but keeps Balanced`() {
        val weak = device(ramMb = 3000)
        assertEquals(Support.Blocked(PackBlock.Memory), PackSupport.supportFor(ModelCatalog.small, weak))
        assertEquals(Support.Supported, PackSupport.supportFor(ModelCatalog.base, weak))
        assertEquals(Support.Supported, PackSupport.supportFor(ModelCatalog.small, device(ramMb = null))) // unknown: try
        assertEquals(Support.Blocked(PackBlock.Abi), PackSupport.supportFor(ModelCatalog.base, device(abis = listOf("mips"))))
        assertEquals(Support.Supported, PackSupport.supportFor(ModelCatalog.base, device(abis = emptyList())))
        assertEquals(Support.Blocked(PackBlock.Platform), PackSupport.supportFor(ModelCatalog.base, DeviceProbeResult.Unsupported))
        assertEquals(Support.Supported, PackSupport.supportFor(ModelCatalog.base, DeviceProbeResult.Unknown))
    }

    @Test
    fun `B-MEDIA-024 the storage check counts only the remaining bytes and skips installed packs`() {
        val mib = 1024L * 1024
        val pack = ModelCatalog.base
        assertEquals(pack.totalBytes + 128 * mib, PackSupport.requiredStorageBytes(pack))
        assertEquals(pack.totalBytes - 100 * mib + 128 * mib, PackSupport.requiredStorageBytes(pack, 100 * mib))
        assertEquals(375_485_327L + 128 * mib, PackSupport.requiredStorageBytes(ModelCatalog.small)) // size / 4 < 128 MiB
        val big = ModelCatalog.small.copy(files = persistentListOf(ModelFile("x", 1_000 * mib, "0".repeat(64))))
        assertEquals(1_250 * mib, PackSupport.requiredStorageBytes(big))
        val tight = device(storage = 200 * mib)
        assertEquals(PackBlock.Storage, (PackSupport.supportFor(pack, tight) as Support.Blocked).block)
        assertEquals(Support.Supported, PackSupport.supportFor(pack, tight, partialBytes = 100 * mib))
        assertEquals(Support.Supported, PackSupport.supportFor(pack, device(storage = 1), installed = true))
        assertEquals(PackBlock.Storage, (PackSupport.supportFor(pack, device(storage = 1), installed = true, replacing = true) as Support.Blocked).block)
    }

    @Test
    fun `B-MEDIA-002 the default pack follows physical RAM thresholds, supported packs and an explicit choice`() {
        fun resolve(ram: Long?, explicit: String? = null) = AutomaticPack.defaultFor(ram, ModelCatalog.speech, explicit)?.id?.raw
        assertNull(resolve(1023))
        assertEquals("tiny", resolve(1024))
        assertEquals("tiny", resolve(1535))
        assertEquals("base", resolve(1536))
        assertEquals("base", resolve(3399))
        assertEquals("small", resolve(3400))
        assertEquals("tiny", resolve(null))
        assertEquals("base", resolve(8000, explicit = "base"))
        assertEquals("tiny", resolve(1100, explicit = "small"))
        assertNull(AutomaticPack.defaultFor(8000, emptyList()))
    }
}
