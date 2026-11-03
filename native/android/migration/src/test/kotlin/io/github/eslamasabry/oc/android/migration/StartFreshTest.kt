package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.migration.FaultPoints
import io.github.eslamasabry.oc.android.migration.readers.migration.SimulatedCrash
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** P12-MIGRATION: Start fresh (plan 05 §9.4) — two phases, a kept copy, nothing deleted, crash-safe. */
class StartFreshTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun world() = World(tmp.newFolder())

    private fun TestScope.io() = StandardTestDispatcher(testScheduler)

    /** A phone whose migration blocked: three crashes in M2 (as in MigrationRunnerTest). */
    private suspend fun TestScope.blockedPhone(): World {
        val w = world().apply { Seed.phone(this) }
        repeat(3) { assertFailsWith<SimulatedCrash> { w.runner(io(), CrashAt("M2:all")).run() } }
        assertIs<RunnerState.Blocked>(w.runner(io()).run())
        File(w.paths.sharedPrefs, "oc_secrets_v1.xml").writeText("<map />")
        return w
    }

    private fun bytesOf(dir: File, names: List<String>) = names.associateWith { File(dir, it).takeIf(File::exists)?.readBytes()?.toList() }

    @Test
    fun `B-MIGR-010 a blocked migration offers Start fresh while the gate never opened`() = runTest {
        val w = blockedPhone()
        val blocked = assertIs<RunnerState.Blocked>(w.runner(io()).run())
        assertTrue(blocked.startFreshAvailable)
    }

    @Test
    fun `B-MIGR-010 Start fresh moves nothing in the requesting process`() = runTest {
        val w = blockedPhone()
        val before = snapshotFiles(w.paths.sharedPrefs)
        assertTrue(w.runner(io()).requestStartFresh())
        assertEquals(before, snapshotFiles(w.paths.sharedPrefs))
        assertTrue(w.journalText().contains("\"startFresh\""))
    }

    @Test
    fun `B-MIGR-010 Start fresh keeps a byte-exact copy of the old data and opens an empty app on the next start`() = runTest {
        val w = blockedPhone()
        val names = StartFreshTransaction.MOVED
        val old = bytesOf(w.paths.sharedPrefs, names)
        val fss = snapshotFiles(w.paths.sharedPrefs).filterKeys { it.startsWith("FlutterSecureStorage") }
        assertTrue(w.runner(io()).requestStartFresh())

        assertTrue(StartFreshTransaction(w.paths, { w.nowMs }).recoverBeforeOpen())

        val aside = w.paths.backupRoot.listFiles()!!.single { it.name.startsWith("replaced-") }
        assertEquals(old.filterValues { it != null }, bytesOf(aside, names).filterValues { it != null })
        names.forEach { assertFalse(File(w.paths.sharedPrefs, it).exists(), "$it left behind would load") }
        assertEquals(fss, snapshotFiles(w.paths.sharedPrefs).filterKeys { it.startsWith("FlutterSecureStorage") })
        assertTrue(w.paths.backup.exists(), "the S0 backup is kept")

        val open = assertIs<RunnerState.Open>(w.runner(io()).run())
        assertFalse(open.firstNativeLaunch)
        assertEquals(MigrationNoticeSet(), open.notices)
        assertTrue(w.logical().isEmpty())
        // A later start stays open and does nothing more.
        assertFalse(StartFreshTransaction(w.paths, { w.nowMs }).recoverBeforeOpen())
        assertIs<RunnerState.Open>(w.runner(io()).run())
    }

    @Test
    fun `B-MIGR-010 a kill at every Start fresh point resumes to the same end state`() = runTest {
        val points = listOf(
            "fresh:move:FlutterSharedPreferences.xml", "fresh:move:oc_secrets_v1.xml", "fresh:moved", "fresh:resetting",
        )
        for (point in points) {
            val w = blockedPhone()
            val old = bytesOf(w.paths.sharedPrefs, StartFreshTransaction.MOVED).filterValues { it != null }
            assertTrue(w.runner(io()).requestStartFresh())
            val crash = FaultPoints { if (it == point) throw SimulatedCrash(it) }
            assertFailsWith<SimulatedCrash>(point) { StartFreshTransaction(w.paths, { w.nowMs }, crash).recoverBeforeOpen() }
            assertTrue(StartFreshTransaction(w.paths, { w.nowMs }).recoverBeforeOpen(), point)
            val aside = w.paths.backupRoot.listFiles()!!.single { it.name.startsWith("replaced-") }
            assertEquals(old, bytesOf(aside, StartFreshTransaction.MOVED).filterValues { it != null }, point)
            assertIs<RunnerState.Open>(w.runner(io()).run(), point)
        }
    }

    @Test
    fun `B-MIGR-010 Start fresh is refused when nothing is blocked or the gate already opened`() = runTest {
        val w = world().apply { Seed.phone(this) }
        assertFalse(w.runner(io()).requestStartFresh(), "not blocked")
        assertIs<RunnerState.Open>(w.runner(io()).run())
        assertFalse(w.runner(io()).requestStartFresh(), "the gate opened: engines may have written")
        assertFalse(StartFreshTransaction(w.paths, { w.nowMs }).recoverBeforeOpen())
    }

    /**
     * Codex review p12/migration #4: phase 2 runs in `Application.onCreate`. A failed rename or journal write (storage
     * filled up after phase 1) escaped it, and every later start repeated the crash with no way out on screen.
     */
    @Test
    fun `B-MIGR-010 a Start fresh that cannot finish never crashes the start and offers Try again`() = runTest {
        val w = blockedPhone()
        assertTrue(w.runner(io()).requestStartFresh())
        val full = FaultPoints { if (it == "fresh:move:oc_secrets_v1.xml") throw java.io.IOException("No space left on device") }

        assertEquals(StartFreshTransaction.Recovery.Failed, StartFreshTransaction(w.paths, { w.nowMs }, full).recoverSafely())

        val runner = w.runner(io())
        val blocked = assertIs<RunnerState.Blocked>(runner.run())
        assertEquals(BlockReason.WriteRefused, blocked.reason)
        assertEquals("fresh", blocked.step)
        assertTrue(runner.startFreshPending(), "Try again restarts the app so phase 2 runs again")
        assertIs<RunnerState.Blocked>(runner.retry()) // never resets the steps under a pending Start fresh
        w.freeBytes = 1_000
        assertEquals(BlockReason.LowSpace, assertIs<RunnerState.Blocked>(w.runner(io()).run()).reason)
        w.freeBytes = 1L shl 40

        assertEquals(StartFreshTransaction.Recovery.Finished, StartFreshTransaction(w.paths, { w.nowMs }).recoverSafely())
        assertIs<RunnerState.Open>(w.runner(io()).run())
        assertTrue(w.logical().isEmpty())
        assertFalse(w.runner(io()).startFreshPending())
    }
}
