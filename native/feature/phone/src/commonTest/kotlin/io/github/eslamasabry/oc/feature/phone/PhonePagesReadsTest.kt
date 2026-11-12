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
import io.github.eslamasabry.oc.feature.phone.presentation.InstalledLine
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneDetailsUi
import io.github.eslamasabry.oc.feature.phone.presentation.installedFailureShown
import io.github.eslamasabry.oc.feature.phone.presentation.installedLine
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
import kotlin.test.assertNull
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
        // Busy: read again up to the bound, then say so.
        assertNull(settle(List(InstalledInventoryRetry.ATTEMPTS - 1) { busy } + null))
        assertEquals(busy, settle(List(InstalledInventoryRetry.ATTEMPTS) { busy }))
        assertFalse(InstalledInventoryRetry.again(InstalledInventoryRetry.ATTEMPTS))
    }

    // Device QA basics-3 B18: the first open after a restart failed twice, 1.7 s apart (the old rule's one read again),
    // and the same read worked a few seconds later; one press on Try again cleared it.
    @Test
    fun `B18 a check that fails while the phone settles is read again quietly and the list shows`() {
        val broken = Failure.Runtime(RuntimeProblem.Generic)
        assertNull(settle(listOf(broken, broken, null)))
        // Bounded: a check that really cannot run is said after the last read, about 15 s of pauses in all.
        assertEquals(broken, settle(List(InstalledInventoryRetry.ATTEMPTS) { broken }))
        val pauses = (1 until InstalledInventoryRetry.ATTEMPTS).sumOf { InstalledInventoryRetry.pauseMs(it) }
        assertEquals(15_000L, pauses)
        // The read after the device's two failures comes 4.5 s after the first one, not 1.5 s.
        assertTrue(InstalledInventoryRetry.pauseMs(2) > InstalledInventoryRetry.pauseMs(1))
    }

    // Gate: reads take time of their own (8.9 s on the device), so five failing reads plus 15 s of pauses could keep a
    // really broken Linux behind "Checking…" for a minute or more. No read starts later than WINDOW_MS after the first.
    @Test
    fun `B18 failing reads stop at the time window, so a broken Linux is said within about 40 s`() {
        // The device's own sequence (8.9 s fail, 3.4 s fail, then a good read) still reaches its third read.
        assertTrue(InstalledInventoryRetry.again(2, elapsedMs = 8_900 + 1_500 + 3_400))
        // Slow failures (9 s each): the third read is the last, and the error shows 31.5 s after the first read began.
        assertEquals(3 to 31_500L, failingReads(9_000))
        // Quick failures (1 s each) keep all five reads and end at 20 s.
        assertEquals(InstalledInventoryRetry.ATTEMPTS to 20_000L, failingReads(1_000))
        // A read that runs past the window is never followed by another.
        assertFalse(InstalledInventoryRetry.again(1, elapsedMs = InstalledInventoryRetry.WINDOW_MS))
    }

    /** Reads that each take [readMs] and fail: how many ran, and when the last one ended (ms after the first began). */
    private fun failingReads(readMs: Long): Pair<Int, Long> {
        var clock = readMs
        var attempt = 1
        while (InstalledInventoryRetry.again(attempt, elapsedMs = clock)) {
            clock += InstalledInventoryRetry.pauseMs(attempt) + readMs
            attempt++
        }
        return attempt to clock
    }

    /**
     * PhoneDetailsWiring's read loop over [reads] (a failure, or null for a read that worked): the failure the row is
     * left with, or null when the list was read.
     */
    private fun settle(reads: List<Failure?>): Failure? {
        val queue = ArrayDeque(reads)
        var result = queue.removeFirst()
        var attempt = 1
        while (result != null && InstalledInventoryRetry.again(attempt)) {
            result = queue.removeFirst()
            attempt++
        }
        return result
    }

    @Test
    fun `S15 while Add tools' install runs no check is retried and the row says Installing, not a failure`() {
        val busy = Failure.Runtime(RuntimeProblem.SlotBusy)
        // The install holds the one task slot for minutes: waiting reads would only end as "busy".
        assertFalse(InstalledInventoryRetry.again(1, addingTools = true))
        assertFalse(InstalledInventoryRetry.again(2, addingTools = true))
        val names = persistentListOf("Linux base", "Git and SSH")
        val installing =
            PhoneDetailsUi(installed = true, installedNames = names, addingTools = true, inventoryFailure = busy)
        assertEquals(InstalledLine.Installing, installedLine(installing))
        assertNull(installedFailureShown(installing))
        // The previous list is kept under the Installing line.
        assertEquals(names, installing.installedNames)
        // A read in flight says Checking and hides the old failure; only a settled failure is shown.
        val checking = installing.copy(addingTools = false, loading = true)
        assertEquals(InstalledLine.Checking, installedLine(checking))
        assertNull(installedFailureShown(checking))
        val settled = checking.copy(loading = false)
        assertEquals(InstalledLine.Names, installedLine(settled))
        assertEquals(busy, installedFailureShown(settled))
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
