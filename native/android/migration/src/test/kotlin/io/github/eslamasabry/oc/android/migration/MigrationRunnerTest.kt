package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.migration.SimulatedCrash
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.steps.DeclaredWrites
import io.github.eslamasabry.oc.android.migration.steps.S0Backup
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Plan 05 §10.2 `MigrationRunnerTest` rows on plain JVM files (the Robolectric twin is `AndroidPrefsMigrationTest`). */
class MigrationRunnerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun world() = World(tmp.newFolder())

    private fun TestScope.io() = StandardTestDispatcher(testScheduler)

    @Test
    fun `B-MIGR-006 B-MIGR-005 the full run on the P5 fixture opens the gate with every secret re-stored`() = runTest {
        val w = world().apply { seedReadersFixture() }
        val state = w.runner(io()).run()

        val open = assertIs<RunnerState.Open>(state)
        assertTrue(open.firstNativeLaunch)
        assertEquals(World.fixtureSecrets().toSortedMap(), w.secrets.values.toSortedMap())
        assertTrue(w.paths.audit.isFile)
        assertTrue(w.journalText().contains("\"native.migration.v1\":{\"complete\":true"))
        assertTrue(w.paths.backup.resolve(S0Backup.MANIFEST).isFile)
        assertEquals(listOf<Long?>(null), w.signals.updated)
        assertEquals(MigrationOutcome.Done, w.signals.done.single().outcome)
        // The census found AI Team, Memory data in the fixture (S2-GATEUI's one-time notice).
        assertTrue(DeferredFeature.AiTeam in open.notices.deferredFeatures)
        assertTrue(DeferredFeature.Memory in open.notices.deferredFeatures)
    }

    @Test
    fun `B-MIGR-010 a re-run is a no-op that changes no byte and emits nothing again`() = runTest {
        val w = world().apply { Seed.phone(this) }
        assertIs<RunnerState.Open>(w.runner(io()).run())
        val before = snapshotFiles(w.paths.dataDir)
        val secrets = w.secrets.values.toMap()
        val events = w.signals.done.size

        assertIs<RunnerState.Open>(w.runner(io()).run())

        assertEquals(before.keys, snapshotFiles(w.paths.dataDir).keys)
        assertEquals(before, snapshotFiles(w.paths.dataDir))
        assertEquals(secrets, w.secrets.values.toMap())
        assertEquals(events, w.signals.done.size)
    }

    @Test
    fun `B-MIGR-010 a kill at every step and every S0 filesystem point resumes to the same end state`() = runTest {
        val corrupt = setOf("oc.codexToken.${Seed.CODEX}")
        val reference = world().apply { Seed.phone(this, corrupt) }
        val recorder = CrashAt()
        val refState = reference.runner(io(), recorder).run()
        val points = recorder.seen.distinct()
        assertTrue(points.containsAll(listOf("S0:partialCreated", "S0:renamed", "S0", "M1", "M2:1", "M2:all", "M3", "M4", "M5", "M6")), "$points")

        for (point in points) {
            val w = world().apply { Seed.phone(this, corrupt) }
            assertFailsWith<SimulatedCrash>(point) { w.runner(io(), CrashAt(point)).run() }
            val resumed = w.runner(io()).run()
            assertEquals(refState, resumed, point)
            assertEquals(reference.logical(), w.logical(), point)
            assertEquals(reference.secrets.values, w.secrets.values, point)
            assertTrue(S0Backup(w.paths, StepInterrupter.None, { 0 }, 0, { 0 }).verifyPublished() != null, point)
            // A flagged secret is retried on the next three launches (journal `secretRetries` counts down); after
            // that, a further relaunch changes no byte of prefs, journal, audit or anything else.
            repeat(3) { w.runner(io()).run() }
            assertTrue(w.journalText().contains("\"secretRetries\":{\"remaining\":0}"), point)
            val settled = snapshotFiles(w.paths.dataDir)
            w.runner(io()).run()
            assertEquals(settled, snapshotFiles(w.paths.dataDir), point)
        }
    }

    @Test
    fun `B-MIGR-010 three crashes in one step block with StepCrashed and Try again resumes`() = runTest {
        val w = world().apply { Seed.phone(this) }
        repeat(3) { assertFailsWith<SimulatedCrash> { w.runner(io(), CrashAt("M2:all")).run() } }

        val blocked = assertIs<RunnerState.Blocked>(w.runner(io()).run())
        assertEquals(BlockReason.StepCrashed, blocked.reason)
        assertEquals("M2", blocked.step)
        assertTrue(blocked.restoreAvailable) // the gate never opened and the backup verifies
        assertEquals(MigrationOutcome.Failed, w.signals.done.single().outcome)
        assertEquals(MigrationStepId.M2, w.signals.done.single().failedStep)

        // Still blocked on the next launch, until the person asks again.
        assertIs<RunnerState.Blocked>(w.runner(io()).run())
        assertIs<RunnerState.Open>(w.runner(io()).retry())
        assertEquals(1, w.signals.done.size) // migration_done is emitted once
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `B-MIGR-010 Try again while Running waits on the same run and never starts a second runner`() = runTest {
        val w = world().apply { Seed.phone(this) }
        val probe = CrashAt(delay = 5_000)
        val runner = w.runner(io(), probe)
        val first = async { runner.run() }
        testScheduler.advanceTimeBy(1_000)
        assertEquals(RunnerState.Running("S0"), runner.state.value)
        val second = async { runner.retry() }
        val a = first.await()
        val b = second.await()
        assertIs<RunnerState.Open>(a)
        assertEquals(a, b)
        assertEquals(1, probe.seen.count { it == "S0:partialCreated" })
        assertEquals(1, probe.seen.count { it == "M2:all" })
    }

    @Test
    fun `B-MIGR-004 a fresh install is a clean first run, never a failure`() = runTest {
        val w = world()
        val open = assertIs<RunnerState.Open>(w.runner(io()).run())
        assertFalse(open.firstNativeLaunch)
        assertTrue(w.journalText().contains("\"kind\":\"none\""))
        assertFalse(w.paths.backup.exists())
        assertTrue(w.signals.updated.isEmpty())
        assertTrue(w.signals.done.isEmpty())
        assertTrue(w.secrets.values.isEmpty())
    }

    @Test
    fun `B-MIGR-010 a planted bak twin is backed up as the authoritative copy and is what the census reads`() = runTest {
        val w = world().apply { Seed.phone(this) }
        val xml = File(w.paths.sharedPrefs, "FlutterSharedPreferences.xml")
        xml.copyTo(File(w.paths.sharedPrefs, "FlutterSharedPreferences.xml.bak"))
        w.seedPrefs(mapOf("oc.stale" to s("older half-written file")))

        assertIs<RunnerState.Open>(w.runner(io()).run())

        val manifest = File(w.paths.backup, S0Backup.MANIFEST).readText()
        val files = assertNotNull(S0Backup.parseManifest(manifest))
        assertTrue(files.single { it.name == "FlutterSharedPreferences.xml.bak" }.authoritative)
        assertFalse(files.single { it.name == "FlutterSharedPreferences.xml" }.authoritative)
        assertEquals(s(Seed.PASSWORD), s(w.secrets.values.getValue("pw.${Seed.REMOTE}")))
        assertTrue("oc.stale" !in w.logical())
        assertTrue(w.journalText().contains("\"backupParseMismatch\":false"))
    }

    @Test
    fun `B-MIGR-010 a tampered published backup blocks with BackupInvalid and is never overwritten`() = runTest {
        val w = world().apply { Seed.phone(this) }
        assertFailsWith<SimulatedCrash> { w.runner(io(), CrashAt("S0:renamed")).run() }
        val copy = File(w.paths.backup, "FlutterSharedPreferences.xml")
        copy.appendText("<!-- changed -->")
        val tampered = snapshotFiles(w.paths.backup)

        val blocked = assertIs<RunnerState.Blocked>(w.runner(io()).run())
        assertEquals(BlockReason.BackupInvalid, blocked.reason)
        assertFalse(blocked.restoreAvailable)
        assertEquals(tampered, snapshotFiles(w.paths.backup))
        assertIs<RunnerState.Blocked>(w.runner(io()).retry())
        assertEquals(tampered, snapshotFiles(w.paths.backup))
    }

    @Test
    fun `B-MIGR-010 J8 low space blocks before anything is touched and Try again completes after space is freed`() = runTest {
        val w = world().apply { Seed.phone(this) }
        val prefs = w.prefsBytes().toList()
        w.freeBytes = 1_000

        val blocked = assertIs<RunnerState.Blocked>(w.runner(io()).run())
        assertEquals(BlockReason.LowSpace, blocked.reason)
        assertTrue((blocked.neededBytes ?: 0) > 16L * 1024 * 1024)
        assertFalse(w.paths.backup.exists())
        assertEquals(prefs, w.prefsBytes().toList())
        assertTrue(w.secrets.values.isEmpty())

        w.freeBytes = 1L shl 40
        assertIs<RunnerState.Open>(w.runner(io()).retry())
    }

    @Test
    fun `B-MIGR-010 an unreadable journal blocks and is never overwritten`() = runTest {
        val w = world().apply { Seed.phone(this) }
        w.paths.nativeDir.mkdirs()
        w.paths.journal.writeText("{not json")
        val blocked = assertIs<RunnerState.Blocked>(w.runner(io()).run())
        assertEquals(BlockReason.StepCrashed, blocked.reason)
        assertEquals("{not json", w.journalText())
    }

    @Test
    fun `app_updated fires on each new native versionCode and migration_done exactly once`() = runTest {
        val w = world().apply { Seed.phone(this) }
        w.runner(io()).run()
        w.runner(io()).run()
        w.versionCode = 10_010
        val again = assertIs<RunnerState.Open>(w.runner(io()).run())
        assertFalse(again.firstNativeLaunch)
        assertEquals(listOf<Long?>(null, 10_000L), w.signals.updated)
        assertEquals(1, w.signals.done.size)
        assertEquals(1, w.signals.lines.size)
    }

    @Test
    fun `B-MIGR-005 every key outside the declared write sets stays byte-identical, unknown keys included`() = runTest {
        val w = world().apply { Seed.phone(this) }
        val before = w.logical()
        assertIs<RunnerState.Open>(w.runner(io()).run())
        val after = w.logical()

        val untouched = before.filterKeys { !DeclaredWrites.isDeclared(it) }
        assertEquals(untouched, after.filterKeys { !DeclaredWrites.isDeclared(it) })
        assertEquals(s("kept as is"), after["oc.unknownFutureKey"])
        assertEquals(before["oc.offlineQueue"], after["oc.offlineQueue"]) // queue and dispatch markers untouched
        assertEquals(RawPref.Bool(true), after["oc.native.flutterReadable"])
    }

    @Test
    fun `the compatibility barrier is durable as true before the first secret is written`() = runTest {
        val w = world().apply { Seed.phone(this) }
        assertFailsWith<SimulatedCrash> { w.runner(io(), CrashAt("M2:1")).run() }
        assertEquals(RawPref.Bool(true), w.logical()["oc.native.flutterReadable"])
        assertTrue(w.journalText().contains("\"flutterReadable\":true"))
    }

    @Test
    fun `a journal barrier of false wins over the pref and is never raised again`() = runTest {
        val w = world().apply { Seed.phone(this) }
        assertFailsWith<SimulatedCrash> { w.runner(io(), CrashAt("M2:1")).run() }
        w.paths.journal.writeText(w.journalText().replace("\"flutterReadable\":true", "\"flutterReadable\":false"))

        w.runner(io()).run()

        assertEquals(RawPref.Bool(false), w.logical()["oc.native.flutterReadable"])
        assertTrue(w.journalText().contains("\"flutterReadable\":false"))
    }

    @Test
    fun `the census flags AI Team, Memory and Gemma data for the one-time notice, which never shows again`() = runTest {
        val w = world().apply { Seed.phone(this) }
        File(w.paths.files, "oc.teamEngine.${Seed.REMOTE}").mkdirs()
        File(w.paths.files, "local_llm_models/gemma").apply { mkdirs() }.resolve("model.bin").writeBytes(byteArrayOf(1))
        File(w.paths.externalFilesDir, "factsbook").mkdirs()

        val runner = w.runner(io())
        val open = assertIs<RunnerState.Open>(runner.run())
        assertEquals(setOf(DeferredFeature.AiTeam, DeferredFeature.Memory, DeferredFeature.Gemma), open.notices.deferredFeatures)
        runner.acknowledgeNotices()
        assertEquals(MigrationNoticeSet(), assertIs<RunnerState.Open>(runner.state.value).notices)

        assertEquals(MigrationNoticeSet(), assertIs<RunnerState.Open>(w.runner(io()).run()).notices)
        assertTrue(File(w.paths.files, "oc.teamEngine.${Seed.REMOTE}").isDirectory) // never touched
    }

    @Test
    fun `a phone without AI Team, Memory or Gemma data gets no deferred-feature notice`() = runTest {
        val w = world().apply { Seed.phone(this) }
        assertEquals(emptySet(), assertIs<RunnerState.Open>(w.runner(io()).run()).notices.deferredFeatures)
    }
}
