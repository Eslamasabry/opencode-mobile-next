package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.lifecycle.AppStorageFacts
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReport
import io.github.eslamasabry.oc.engine.runtime.storage.HostStorageAvailability
import io.github.eslamasabry.oc.engine.runtime.storage.PhoneStorageSnapshot
import io.github.eslamasabry.oc.engine.runtime.storage.StoragePressure
import io.github.eslamasabry.oc.feature.phone.presentation.InstalledInventoryRetry
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneStateDetails
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneStorageReadings
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneModel
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageFacts
import io.github.eslamasabry.oc.feature.phone.storage.StoredProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** w4-phonepages: This phone's reads open on real content, and Try again really reads again. */
class PhonePagesReadsTest {
    private class Owner : PhoneStateDetails {
        override val recovery: StateFlow<LifecycleReport> = MutableStateFlow(LifecycleReport())
        val answers = ArrayDeque<CompletableDeferred<Outcome<AppStorageFacts>>>()
        var measured = 0

        override fun invalidate() {}

        override fun dismissRecovery() {}

        override suspend fun refresh() {}

        override suspend fun measureStorage(): Outcome<AppStorageFacts> {
            measured++
            return answers.removeFirst().await()
        }
    }

    private class Readings : PhoneStorageReadings {
        override val supported: Boolean = true
        val asked = mutableListOf<Boolean>()

        override suspend fun read(includeBuiltinHost: Boolean): PhoneStorageSnapshot {
            asked += includeBuiltinHost
            return PhoneStorageSnapshot(
                2L shl 30,
                StoragePressure.AboveThreshold,
                null,
                if (includeBuiltinHost) HostStorageAvailability.Unavailable
                else HostStorageAvailability.NotRequested,
            )
        }
    }

    private val facts = AppStorageFacts(serverInstalled = true, serverBytes = 1L shl 30)

    @Test
    fun `S17 App storage says Measuring on open, and a failed reading's Try again measures again`() =
        runTest {
            val owner = Owner()
            val first = CompletableDeferred<Outcome<AppStorageFacts>>()
            owner.answers += first
            val model =
                ThisPhoneModel(
                    FakeServer(PhoneServerFacts()),
                    FakeSettings(),
                    RecordingNav(),
                    backgroundScope,
                    testClock(),
                    foregroundPacer(),
                    details = setOf(owner),
                    phoneStorage = Readings(),
                )
            model.shown(true)
            runCurrent()
            // On open: measuring, not an error.
            assertTrue(model.state.value.measuringApp)
            assertFalse(model.state.value.storageFailed)
            // Pressing again while it runs starts no second walk.
            model.refreshStorage()
            runCurrent()
            assertEquals(1, owner.measured)
            first.complete(Outcome.Failed(Failure.StorageRefused()))
            runCurrent()
            assertTrue(model.state.value.storageFailed)
            assertFalse(model.state.value.measuringApp)
            // Try again really measures again, and the failure gives way to "Measuring…" meanwhile.
            val second = CompletableDeferred<Outcome<AppStorageFacts>>()
            owner.answers += second
            model.refreshStorage()
            runCurrent()
            assertEquals(2, owner.measured)
            assertTrue(model.state.value.measuringApp)
            assertFalse(model.state.value.storageFailed)
            second.complete(Outcome.Ok(facts))
            runCurrent()
            assertEquals(facts, model.state.value.storage)
            assertFalse(model.state.value.storageFailed)
        }

    @Test
    fun `S17 the one Measure action measures the app's storage again, without the strict Linux walk`() =
        runTest {
            val owner = Owner()
            repeat(2) { owner.answers += CompletableDeferred(Outcome.Ok(facts)) }
            val readings = Readings()
            val model =
                ThisPhoneModel(
                    FakeServer(PhoneServerFacts()),
                    FakeSettings(),
                    RecordingNav(),
                    backgroundScope,
                    testClock(),
                    foregroundPacer(),
                    details = setOf(owner),
                    phoneStorage = readings,
                )
            model.shown(true)
            runCurrent()
            assertEquals(1, owner.measured)
            model.measureStorage()
            runCurrent()
            assertEquals(2, owner.measured)
            // The app's measurement already gives Ubuntu's and each project's size: no second, strict walk
            // whose failure would sit under real numbers ("Linux file sizes could not be measured").
            assertEquals(listOf(false, false), readings.asked)
            assertNotNull(model.state.value.storage)
        }

    @Test
    fun `S15 a check refused because another phone task held the slot is read again, not reported`() {
        val busy = Failure.Runtime(RuntimeProblem.SlotBusy)
        val broken = Failure.Runtime(RuntimeProblem.Generic)
        // Busy: read again up to the bound.
        assertTrue((1 until InstalledInventoryRetry.ATTEMPTS).all { InstalledInventoryRetry.again(it, busy) })
        assertFalse(InstalledInventoryRetry.again(InstalledInventoryRetry.ATTEMPTS, busy))
        // Any other failure keeps its one second read, then says so.
        assertTrue(InstalledInventoryRetry.again(1, broken))
        assertFalse(InstalledInventoryRetry.again(2, broken))
    }

    @Test
    fun `S10 Export counts project folders as projects and lists loose files apart`() {
        val stored =
            ProjectStorageFacts(
                projects =
                    persistentListOf(
                        StoredProject("bg5.txt", 12, 1, 0, folder = false),
                        StoredProject("second", 4_096, 3, 0),
                        StoredProject("site", 2_048, 2, 0),
                    )
            )
        assertEquals(listOf("second", "site"), stored.folders.map { it.name })
        assertEquals(listOf("bg5.txt"), stored.looseFiles.map { it.name })
        // The zip still holds everything: the size counts the loose file too.
        assertEquals(6_156, stored.projectBytes)
    }
}
