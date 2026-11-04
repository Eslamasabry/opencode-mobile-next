package io.github.eslamasabry.oc.engine.runtime.storage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.projects.ProjectStorage
import io.github.eslamasabry.oc.engine.runtime.setup.FakeLinuxChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The free-space warning (test/phone_storage_test.dart). */
class PhoneStorageTest {
    private class Sources(
        var device: () -> Outcome<Long?> = { null.ok() },
        var host: () -> Outcome<ProjectStorage> = { Failure.Device().failed() },
    ) : PhoneStorageSources {
        var deviceReads = 0
        var hostScans = 0

        override suspend fun availableBytes(): Outcome<Long?> = device().also { deviceReads++ }

        override suspend fun builtinStorage(): Outcome<ProjectStorage> = host().also { hostScans++ }
    }

    @Test
    fun `B-ANDR-052 the free-space warning includes zero and the boundary, and unknown stays unknown`() = runTest {
        var bytes: Long? = null
        val sources = Sources(device = { bytes.ok() })
        val storage = PhoneStorage(sources, isSupported = true, nearlyFullAtBytes = 100)
        for ((value, expected) in listOf(
            null to StoragePressure.Unknown,
            -1L to StoragePressure.Unknown,
            0L to StoragePressure.NearlyFull,
            100L to StoragePressure.NearlyFull,
            101L to StoragePressure.AboveThreshold,
        )) {
            bytes = value
            val result = storage.read()
            assertEquals(if (value == null || value < 0) null else value, result.availableBytes)
            assertEquals(expected, result.pressure, "$value")
            assertEquals(HostStorageAvailability.NotRequested, result.builtinAvailability)
        }
        assertEquals(0, sources.hostScans, "no implicit disk walk")
        assertFailsWith<IllegalArgumentException> { PhoneStorage(sources, isSupported = true, nearlyFullAtBytes = -1) }
        assertEquals(1024L * 1024 * 1024, PhoneStorage(sources, isSupported = true).nearlyFullAtBytes)
    }

    @Test
    fun `B-ANDR-052 independent failures preserve the other source and never stale data`() = runTest {
        var failDevice = false
        var failHost = false
        var invalidHost = false
        val storage = PhoneStorage(
            Sources(
                device = { if (failDevice) Failure.Device().failed() else 20L.ok() },
                host = {
                    if (failHost) Failure.Device().failed() else ProjectStorage(if (invalidHost) -1 else 0, 30, 10).ok()
                },
            ),
            isSupported = true,
        )
        assertEquals(30L, storage.read(includeBuiltinHost = true).builtinHost?.projectsBytes)
        failHost = true
        val missingHost = storage.read(includeBuiltinHost = true)
        assertEquals(20L, missingHost.availableBytes)
        assertNull(missingHost.builtinHost)
        assertEquals(HostStorageAvailability.Unavailable, missingHost.builtinAvailability)
        failHost = false
        failDevice = true
        val missingDevice = storage.read(includeBuiltinHost = true)
        assertEquals(StoragePressure.Unknown, missingDevice.pressure)
        assertEquals(0L, missingDevice.builtinHost?.runtimeBytes)
        assertEquals(HostStorageAvailability.Available, missingDevice.builtinAvailability)
        invalidHost = true
        val invalid = storage.read(includeBuiltinHost = true)
        assertNull(invalid.builtinHost)
        assertEquals(HostStorageAvailability.Unavailable, invalid.builtinAvailability)
    }

    @Test
    fun `B-ANDR-052 an unsupported platform performs no probes`() = runTest {
        val sources = Sources(device = { 5L.ok() })
        val storage = PhoneStorage(sources, isSupported = false)
        assertFalse(storage.isSupported)
        val result = storage.read(includeBuiltinHost = true)
        assertEquals(0, sources.deviceReads + sources.hostScans)
        assertNull(result.availableBytes)
        assertEquals(HostStorageAvailability.Unavailable, result.builtinAvailability)
    }

    @Test
    fun `B-ANDR-052 the default adapters use the existing read-only native methods`() = runTest {
        val voice = FakeLinuxChannel { _, _ -> mapOf("availableStorageBytes" to 42).ok() }
        val linux = FakeLinuxChannel { _, _ -> mapOf("runtimeBytes" to 80, "projectsBytes" to 20, "measuredAtMilliseconds" to 123).ok() }
        val result = PhoneStorage(ChannelPhoneStorageSources(voice, linux), isSupported = true).read(includeBuiltinHost = true)
        assertEquals(42L, result.availableBytes)
        assertEquals(80L, result.builtinHost?.runtimeBytes)
        assertEquals(20L, result.builtinHost?.projectsBytes)
        assertEquals(listOf("getDeviceInfo", "projectStorage"), (voice.calls + linux.calls).map { it.method })
    }
}
