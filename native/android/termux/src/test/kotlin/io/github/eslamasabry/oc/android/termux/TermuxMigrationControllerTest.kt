package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// Flutter test/termux_migration_controller_test.dart, case for case (the controller is the real one over fakes).
@OptIn(ExperimentalCoroutinesApi::class)
class TermuxMigrationControllerTest {
    private val selected = setOf(TermuxMigrationItem.projects)

    private fun <T> MigrationAnswer<T>.ok(): T = (this as MigrationAnswer.Ok).value

    private fun MigrationAnswer<*>.code(): TermuxMigrationFailure = (this as MigrationAnswer.Refused).code

    private fun assertInOrder(expected: List<TermuxMigrationPhase>, actual: List<TermuxMigrationPhase>) {
        var at = 0
        for (phase in actual) if (at < expected.size && phase == expected[at]) at++
        assertEquals(expected.size, at, "phases $actual do not contain $expected in order")
    }

    @Test
    fun `B-LINUX-234 a profile deletion during a journal write cannot recreate scoped state`() = runTest {
        val prefs = MemoryPreferences()
        var checks = 0
        val journal = PreferencesTermuxMigrationJournal(prefs) { ++checks == 1 }
        expectFailure(TermuxMigrationFailure.cancelled) {
            journal.write("termux", JsonObject(mapOf("job" to JsonPrimitive(JOB))))
        }
        assertFalse(prefs.contains("oc.termuxMigration.termux"))
    }

    @Test
    fun `B-LINUX-234 a failed pack can be discarded repeatably and a fresh copy can start`() = runTest {
        val fixture = Fixture()
        fixture.transport.packError = TermuxMigrationException(TermuxMigrationFailure.timedOut)
        val controller = fixture.controller(backgroundScope)
        controller.start("termux", selected)
        assertEquals(TermuxMigrationFailure.timedOut, controller.snapshot.failure)
        assertEquals(selected, controller.savedSelection("termux").ok())
        assertEquals(TermuxMigrationDiscardResult.Discarded, controller.discardSavedCopy("termux").ok())
        assertNull(controller.savedSelection("termux").ok())
        assertEquals(TermuxMigrationDiscardResult.NothingSaved, controller.discardSavedCopy("termux").ok())
        assertEquals(1, fixture.archives.cleanupCalls)
        assertTrue(fixture.transport.cancelledJobs.isEmpty())
        fixture.transport.packError = null
        controller.start("termux", selected)
        assertEquals(TermuxMigrationPhase.done, controller.snapshot.phase)
        assertEquals(1, fixture.switches)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 discard keeps committed files and keeps the journal when cleanup fails`() = runTest {
        val fixture = Fixture()
        fixture.archives.afterCommit = { throw IllegalStateException("simulated exit") }
        val controller = fixture.controller(backgroundScope)
        controller.start("termux", selected)
        fixture.archives.cleanupError = IllegalStateException("synthetic-private-error")
        assertEquals(TermuxMigrationFailure.storage, controller.discardSavedCopy("termux").code())
        assertEquals(selected, controller.savedSelection("termux").ok())
        fixture.archives.cleanupError = null
        controller.discardSavedCopy("termux")
        assertTrue(TermuxMigrationItem.projects in fixture.archives.receipts.keys)
        assertEquals(0, fixture.switches)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 completion survives a restart and user edits without contacting the source`() = runTest {
        val fixture = Fixture()
        val first = fixture.controller(backgroundScope)
        assertNull(first.completedJob("termux").ok())
        first.start("termux", selected)
        first.dispose()
        fixture.archives.receipts.clear() // The person changed files; the historical copy still finished.
        fixture.transport.inspectError = IllegalStateException("offline")
        val restarted = fixture.controller(backgroundScope)
        val before = restarted.snapshot
        val completed = restarted.completedJob("termux").ok()
        assertEquals(JOB, completed?.jobId)
        assertEquals("builtin", completed?.destinationProfileId)
        assertSame(before, restarted.snapshot)
        assertEquals(TermuxMigrationDiscardResult.AlreadyCompleted, restarted.discardSavedCopy("termux").ok())
        assertNotNull(restarted.completedJob("termux").ok())
        assertEquals(1, fixture.transport.inspectCalls)
        assertEquals(1, fixture.archives.cleanupCalls)
        assertEquals(1, fixture.switches)
        fixture.journal.edit("termux") { put("destination", JsonNull) }
        assertEquals(TermuxMigrationFailure.storage, restarted.completedJob("termux").code())
        restarted.dispose()
    }

    @Test
    fun `B-LINUX-234 the review reports capacity before packing and start uses the same budget`() = runTest {
        val fixture = Fixture().apply { freeBytes = 50 }
        fixture.transport.sourceFreeBytes = 80
        val controller = fixture.controller(backgroundScope)
        controller.check(selected)
        assertEquals(TermuxMigrationPhase.ready, controller.snapshot.phase)
        assertEquals(50L, controller.snapshot.availableBytes)
        val review = controller.reviewSpace(selected).ok()
        assertEquals(50L, review.appAvailableBytes)
        assertEquals(80L, review.sourceAvailableBytes)
        assertEquals(false, review.sufficient)
        assertEquals(0L, controller.reviewSpace(emptySet()).ok().requiredBytes)
        controller.start("termux", selected)
        assertEquals(TermuxMigrationPhase.needsSpace, controller.snapshot.phase)
        assertEquals(review.requiredBytes, controller.snapshot.requiredBytes)
        assertEquals(0, fixture.transport.packCalls)
        fixture.freeBytes = null
        controller.check(selected)
        assertNull(controller.snapshot.availableBytes)
        assertNull(controller.reviewSpace(selected).ok().sufficient)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 stop settles only after the copy and the stop command, then notifies idle once`() = runTest {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val releaseCopy = CompletableDeferred<Unit>()
        val releaseStop = CompletableDeferred<Unit>()
        fixture.transport.onCopy = {
            entered.complete(Unit)
            releaseCopy.await()
        }
        fixture.transport.onCancel = { releaseStop.await() }
        val controller = fixture.controller(backgroundScope)
        var idleNotifications = 0
        controller.addListener { if (!controller.busy) idleNotifications++ }
        val running = launch { controller.start("termux", selected) }
        entered.await()
        val stop = async { controller.cancel() }
        runCurrent()
        var settled = false
        val settlement = launch {
            controller.awaitSettled()
            settled = true
        }
        assertEquals(TermuxMigrationFailure.sourceBusy, controller.discardSavedCopy("termux").code())
        releaseCopy.complete(Unit)
        runCurrent()
        assertTrue(controller.busy)
        assertFalse(settled)
        releaseStop.complete(Unit)
        stop.await()
        settlement.join()
        running.join()
        assertFalse(controller.busy)
        assertEquals(1, idleNotifications)
        assertEquals(0, fixture.archives.importCalls)
        controller.discardSavedCopy("termux")
        assertTrue(fixture.journal.records.isEmpty())
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 resume owns settlement before its first journal read`() = runTest {
        val fixture = Fixture()
        fixture.transport.packError = TermuxMigrationException(TermuxMigrationFailure.timedOut)
        val controller = fixture.controller(backgroundScope)
        controller.start("termux", selected)
        val read = CompletableDeferred<Unit>()
        fixture.journal.readGate = read
        val resume = launch { controller.resume("termux") }
        runCurrent()
        assertTrue(controller.busy)
        controller.cancel()
        var settled = false
        val settlement = launch {
            controller.awaitSettled()
            settled = true
        }
        runCurrent()
        assertFalse(settled)
        read.complete(Unit)
        resume.join()
        settlement.join()
        assertEquals(TermuxMigrationPhase.cancelled, controller.snapshot.phase)
        assertEquals(1, fixture.transport.packCalls)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 the provider query is offline and ignores unexported or failed config`() = runTest {
        val fixture = Fixture()
        val controller = fixture.controller(backgroundScope)
        assertEquals(emptyList(), controller.providerNames("termux"))
        fixture.journal.records["termux"] = JsonObject(
            mapOf(
                "schema" to JsonPrimitive(1),
                "job" to JsonPrimitive(JOB),
                "selected" to JsonArray(listOf(JsonPrimitive("config"))),
                "archives" to JsonObject(
                    mapOf("config" to JsonObject(mapOf("bytes" to JsonPrimitive(ARCHIVE.bytes), "sha256" to JsonPrimitive(ARCHIVE.sha256)))),
                ),
            ),
        )
        fixture.archives.names = listOf("Anthropic")
        assertEquals(listOf("Anthropic"), controller.providerNames("termux"))
        fixture.archives.namesError = IllegalStateException("synthetic-private-config")
        assertEquals(emptyList(), controller.providerNames("termux"))
        assertEquals(0, fixture.transport.inspectCalls)
        assertEquals(0, fixture.transport.packCalls)
        assertEquals(0, fixture.switches)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 journal removal is scoped and idempotent even after profile deletion`() = runTest {
        val prefs = MemoryPreferences(mapOf("oc.termuxMigration.termux" to "{}", "oc.termuxMigration.other" to "{}"))
        val journal = PreferencesTermuxMigrationJournal(prefs) { false }
        journal.remove("termux")
        journal.remove("termux")
        assertFalse(prefs.contains("oc.termuxMigration.termux"))
        assertTrue(prefs.contains("oc.termuxMigration.other"))
    }

    @Test
    fun `B-LINUX-234 a verified import switches the profile once and a completed retry is local`() = runTest {
        val fixture = Fixture()
        val controller = fixture.controller(backgroundScope)
        val phases = mutableListOf<TermuxMigrationPhase>()
        controller.addListener { phases.add(controller.snapshot.phase) }
        controller.start("termux", selected)
        assertEquals(TermuxMigrationPhase.done, controller.snapshot.phase)
        assertEquals("builtin", controller.snapshot.destinationProfileId)
        assertEquals(1, fixture.switches)
        assertEquals(mapOf("/root/projects" to "/root/projects/termux-$JOB"), fixture.paths)
        assertInOrder(
            listOf(
                TermuxMigrationPhase.checking, TermuxMigrationPhase.ready, TermuxMigrationPhase.packing,
                TermuxMigrationPhase.copying, TermuxMigrationPhase.verifying, TermuxMigrationPhase.unpacking,
                TermuxMigrationPhase.verifying, TermuxMigrationPhase.switching, TermuxMigrationPhase.done,
            ),
            phases,
        )
        assertEquals(1, fixture.archives.importCalls)
        assertEquals(1, fixture.archives.cleanupCalls)
        fixture.transport.inspectError = TermuxMigrationException(TermuxMigrationFailure.unavailable)
        controller.start("termux", selected)
        assertEquals(TermuxMigrationPhase.done, controller.snapshot.phase)
        assertEquals(1, fixture.transport.inspectCalls)
        assertEquals(1, fixture.transport.packCalls)
        assertEquals(1, fixture.archives.importCalls)
        assertEquals(1, fixture.switches)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 a missing in-app server and low or unknown space stop before packing`() = runTest {
        val missing = Fixture().apply { archives.installed = false }
        val missingController = missing.controller(backgroundScope)
        missingController.start("termux", selected)
        assertEquals(TermuxMigrationPhase.needsBuiltin, missingController.snapshot.phase)
        assertEquals(0, missing.transport.inspectCalls)
        assertEquals(0, missing.transport.packCalls)
        assertTrue(missing.journal.records.isEmpty())
        missingController.dispose()
        for (space in listOf(0L, null, AMPLE_SPACE)) {
            val fixture = Fixture().apply { freeBytes = space }
            if (space == AMPLE_SPACE) fixture.transport.sourceFreeBytes = 0
            val controller = fixture.controller(backgroundScope)
            controller.start("termux", selected)
            assertEquals(TermuxMigrationPhase.needsSpace, controller.snapshot.phase, "space=$space")
            assertTrue(controller.snapshot.requiredBytes!! > TermuxMigrationController.RESERVE_BYTES)
            assertEquals(0, fixture.transport.packCalls)
            assertEquals(0, fixture.archives.importCalls)
            assertTrue(fixture.journal.records.isEmpty())
            assertEquals(0, fixture.switches)
            controller.dispose()
        }
    }

    @Test
    fun `B-LINUX-234 losing Termux during the copy keeps resumable metadata and never switches`() = runTest {
        val fixture = Fixture()
        fixture.transport.onCopy = { throw TermuxMigrationException(TermuxMigrationFailure.unavailable) }
        val controller = fixture.controller(backgroundScope)
        controller.start("termux", selected)
        assertEquals(TermuxMigrationPhase.termuxUnreachable, controller.snapshot.phase)
        assertTrue("projects" in (fixture.journal.records.getValue("termux")["archives"] as JsonObject))
        assertEquals(0, fixture.archives.importCalls)
        assertEquals(0, fixture.switches)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 a new controller resumes a committed import after a lost completion`() = runTest {
        val fixture = Fixture()
        fixture.archives.afterCommit = { throw IllegalStateException("simulated process exit") }
        val first = fixture.controller(backgroundScope)
        first.start("termux", selected)
        assertEquals(TermuxMigrationPhase.failed, first.snapshot.phase)
        assertTrue(TermuxMigrationItem.projects in fixture.archives.receipts)
        assertNull(fixture.journal.records.getValue("termux")["done"])
        first.dispose()
        fixture.archives.afterCommit = null
        val resumed = fixture.controller(backgroundScope)
        resumed.start("termux", selected)
        assertEquals(TermuxMigrationPhase.done, resumed.snapshot.phase)
        assertEquals(JOB, resumed.snapshot.jobId)
        assertEquals(1, fixture.archives.importCalls)
        assertEquals(1, fixture.transport.packCalls)
        assertEquals(1, fixture.transport.copyCalls)
        assertEquals(1, fixture.switches)
        resumed.dispose()
    }

    @Test
    fun `B-LINUX-234 a checksum mismatch never imports or switches`() = runTest {
        val fixture = Fixture()
        fixture.archives.verifyError = TermuxMigrationException(TermuxMigrationFailure.checksumMismatch)
        val controller = fixture.controller(backgroundScope)
        controller.start("termux", selected)
        assertEquals(TermuxMigrationPhase.failed, controller.snapshot.phase)
        assertEquals(TermuxMigrationFailure.checksumMismatch, controller.snapshot.failure)
        assertEquals(0, fixture.archives.importCalls)
        assertEquals(0, fixture.switches)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 cancel stops admission after the current copy and preserves the source`() = runTest {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cancelledSeen: Boolean? = null
        fixture.transport.onCopy = { cancelled ->
            entered.complete(Unit)
            release.await()
            cancelledSeen = cancelled()
        }
        val controller = fixture.controller(backgroundScope)
        val running = launch { controller.start("termux", selected) }
        entered.await()
        controller.cancel()
        assertEquals(TermuxMigrationPhase.cancelled, controller.snapshot.phase)
        assertEquals(listOf(JOB), fixture.transport.cancelledJobs)
        release.complete(Unit)
        running.join()
        assertEquals(true, cancelledSeen)
        assertEquals(TermuxMigrationPhase.cancelled, controller.snapshot.phase)
        assertEquals(0, fixture.archives.importCalls)
        assertEquals(0, fixture.switches)
        assertNull(fixture.journal.records.getValue("termux")["done"])
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 raw credential-bearing exceptions never enter output, state or journal`() = runTest {
        val secret = "test-only-provider-secret-DO-NOT-RENDER"
        val fixture = Fixture()
        fixture.transport.onCopy = { throw IllegalStateException(secret) }
        val controller = fixture.controller(backgroundScope)
        val snapshots = mutableListOf<String>()
        controller.addListener { snapshots.add(controller.snapshot.toString()) }
        val out = ByteArrayOutputStream()
        val previousOut = System.out
        val previousErr = System.err
        System.setOut(PrintStream(out))
        System.setErr(PrintStream(out))
        try {
            controller.start("termux", selected)
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
        }
        assertEquals(TermuxMigrationFailure.storage, controller.snapshot.failure)
        assertFalse(TermuxMigrationException(controller.snapshot.failure!!).toString().contains(secret))
        assertFalse(out.toString().contains(secret))
        assertTrue(snapshots.isNotEmpty())
        assertFalse(snapshots.joinToString().contains(secret))
        assertFalse(fixture.journal.records.toString().contains(secret))
        assertEquals(0, fixture.switches)
        controller.dispose()
    }

    @Test
    fun `B-LINUX-234 resume refuses a changed source archive instead of replacing earlier bytes`() = runTest {
        val fixture = Fixture()
        fixture.transport.onCopy = { throw TermuxMigrationException(TermuxMigrationFailure.unavailable) }
        val first = fixture.controller(backgroundScope)
        first.start("termux", selected)
        first.dispose()
        fixture.transport.onCopy = null
        fixture.transport.archive = TermuxMigrationArchive(20480, "f".repeat(64))
        val resumed = fixture.controller(backgroundScope)
        resumed.start("termux", selected)
        assertEquals(TermuxMigrationFailure.sourceChanged, resumed.snapshot.failure)
        assertEquals(1, fixture.transport.copyCalls)
        assertEquals(0, fixture.archives.importCalls)
        assertEquals(0, fixture.switches)
        resumed.dispose()
    }

    @Test
    fun `B-LINUX-234 an attempt past its 15 minute foreground budget stops as timed out`() = runTest {
        val fixture = Fixture()
        fixture.transport.onCopy = { fixture.clock.now += 16.minutes }
        val controller = fixture.controller(backgroundScope)
        controller.start("termux", selected)
        assertEquals(TermuxMigrationPhase.failed, controller.snapshot.phase)
        assertEquals(TermuxMigrationFailure.timedOut, controller.snapshot.failure)
        assertEquals(0, fixture.archives.importCalls)
        assertEquals(0, fixture.switches)
        controller.dispose()
    }
}
