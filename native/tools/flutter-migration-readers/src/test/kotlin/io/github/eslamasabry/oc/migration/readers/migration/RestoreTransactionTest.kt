package io.github.eslamasabry.oc.migration.readers.migration

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.migration.readers.xml.SharedPrefsFile
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** R2 restore under interruption, with `.bak` twins, generation gating and outbound tombstones (findings 2, 5, 6). */
class RestoreTransactionTest {

    private val root: File = Files.createTempDirectory("r2").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private class App(val dir: File) {
        val shared = File(dir, "shared_prefs")
        val journal = File(dir, "files/native/migration.json")
        val migration = File(dir, "files/migration")
        val tombstones = File(dir, "files/native/outbound-tombstones.json")
        fun publisher(faults: FaultPoints = FaultPoints.None) = BackupPublisher(shared, migration, JournalStore(journal, faults), faults, freeBytes = { Long.MAX_VALUE })
        fun restore(faults: FaultPoints = FaultPoints.None, clock: Long = 1730460000000L) =
            RestoreTransaction(shared, migration, publisher(faults), JournalStore(journal, faults), { clock }, faults)
    }

    /** Pre-update Flutter data → S0 → native writes (including a .bak twin left by an interrupted commit). */
    private fun seed(name: String): Pair<App, Map<String, String>> {
        val app = App(File(root, name).apply { mkdirs() })
        FixtureGenerator.writeAll(app.dir, FixtureGenerator.newKeyPair(SecureRandom()), SecureRandom())
        val preUpdate = app.shared.listFiles()!!.filter { it.isFile }.associate { it.name to Fs.sha256Hex(it) }
        app.publisher().runIfNeeded()
        JournalStore(app.journal).update {
            it.flutterReadable = true
            it.steps["M2"] = StepRecord(StepState.Verified, 1)
            it.blocked = Blocked("AuthorityChanged", "M4")
        }
        // Native changed the prefs, wrote its own secret store, and a .bak twin exists for both.
        File(app.shared, "FlutterSharedPreferences.xml").writeBytes(AndroidPrefsXmlWriter.write(mapOf("flutter.native" to RawPref.Str("after"))))
        File(app.shared, "FlutterSharedPreferences.xml.bak").writeBytes(AndroidPrefsXmlWriter.write(mapOf("flutter.native" to RawPref.Str("bak"))))
        File(app.shared, "oc_secrets_v1.xml").writeText("<map />")
        File(app.shared, "oc_secrets_v1.xml.bak").writeText("<map />")
        File(app.shared, "builtin_server_recovery.xml").writeText("<map><boolean name=\"wanted\" value=\"true\" /></map>")
        OutboundTombstones(app.tombstones).record("queue:q2")
        return app to preUpdate
    }

    private fun assertRestored(app: App, preUpdate: Map<String, String>, label: String) {
        val live = app.shared.listFiles()!!.filter { it.isFile }.associate { it.name to Fs.sha256Hex(it) }
        for ((name, sha) in preUpdate) assertEquals(sha, live[name], "$label: $name not restored")
        assertFalse(File(app.shared, "FlutterSharedPreferences.xml.bak").exists(), "$label: a stale .bak would shadow the restored file")
        assertFalse(File(app.shared, "oc_secrets_v1.xml").exists(), "$label: native secret store must move aside")
        assertTrue(File(app.shared, "builtin_server_recovery.xml").exists(), "$label: Kotlin-owned files are not touched")
        val restoredMap = SharedPrefsFile(app.shared, "FlutterSharedPreferences").read()!!.androidMap
        assertFalse("flutter.native" in restoredMap, label)
        val j = JournalStore(app.journal).load()
        assertEquals(RestoreState.Idle, j.restoreState, label)
        assertTrue(j.steps.isEmpty(), label)
        assertEquals(null, j.flutterReadable, label)
        assertTrue(j.outboundHold, "$label: restored outbound work is held for review")
        assertEquals(null, j.blocked, label)
        assertEquals(BackupState.Published, j.backupState, "$label: the backup stays")
        assertEquals(setOf("queue:q2"), OutboundTombstones(app.tombstones).load(), "$label: tombstones live outside the snapshot")
        val aside = app.migration.listFiles()!!.single { it.name.startsWith("replaced-") }
        assertTrue(File(aside, "FlutterSharedPreferences.xml.bak").exists() && File(aside, "oc_secrets_v1.xml").exists(), label)
        assertFalse(File(app.migration, "restore-staging").exists(), label)
    }

    @Test
    fun `restore puts back the pre-update stores and holds outbound work`() {
        val (app, pre) = seed("plain")
        assertEquals(RestoreTransaction.Outcome.Restored, app.restore().start())
        assertRestored(app, pre, "plain")
        assertEquals(RestoreTransaction.Outcome.NothingToResume, app.restore().recoverBeforeOpen())
        // The restored queue still lists q1 and q2; q2 was sent after the update and must never be resent.
        assertEquals(listOf("queue:q2") to listOf("queue:q1"), OutboundTombstones(app.tombstones).partition(listOf("queue:q1", "queue:q2")))
    }

    @Test
    fun `a crash at any point is finished by recoverBeforeOpen`() {
        val points = seed("ref").first.let { ref -> RecordingFaults().also { ref.restore(it).start() }.seen }
        assertTrue(points.count { it.startsWith("R2:") } >= 8, "$points")
        for ((i, point) in points.withIndex()) {
            val occurrence = points.subList(0, i + 1).count { it == point }
            val (app, pre) = seed("case$i")
            assertFailsWith<SimulatedCrash> { app.restore(RecordingFaults(point, occurrence)).start() }
            val j = JournalStore(app.journal).load()
            val out = if (j.restoreState == RestoreState.Idle && j.steps.isNotEmpty()) {
                // Died before the transaction was recorded: nothing moved yet; the person taps Restore again.
                assertTrue(File(app.shared, "oc_secrets_v1.xml").exists(), "nothing moved before $point")
                app.restore().start()
            } else {
                app.restore().recoverBeforeOpen()
            }
            assertTrue(out == RestoreTransaction.Outcome.Restored || out == RestoreTransaction.Outcome.NothingToResume, "$point: $out")
            assertRestored(app, pre, "crash at $point")
        }
    }

    @Test
    fun `R2 is refused once Room is authoritative, without a backup, or with a damaged backup`() {
        val (room, _) = seed("room")
        JournalStore(room.journal).update { it.generation = StorageGeneration.RoomV2 }
        assertEquals(RestoreTransaction.Outcome.Refused("GenerationNotRestorable"), room.restore().start())
        assertTrue(File(room.shared, "oc_secrets_v1.xml").exists())

        val (damaged, _) = seed("damaged")
        File(damaged.migration, "flutter-v57/FlutterSecureStorage.xml").appendText(" ")
        assertEquals(RestoreTransaction.Outcome.Refused("BackupInvalid"), damaged.restore().start())

        val none = App(File(root, "none").apply { mkdirs() })
        assertEquals(RestoreTransaction.Outcome.Refused("NoBackup"), none.restore().start())
    }

    @Test
    fun `tombstones survive a restore cycle and a second restore`() {
        val (app, pre) = seed("twice")
        app.restore(clock = 1).start()
        OutboundTombstones(app.tombstones).record("occ:${FixtureGenerator.PROFILE_A}:s1:2026-11-02T09:00")
        File(app.shared, "oc_secrets_v1.xml").writeText("<map />")
        app.restore(clock = 2).start()
        assertEquals(setOf("queue:q2", "occ:${FixtureGenerator.PROFILE_A}:s1:2026-11-02T09:00"), OutboundTombstones(app.tombstones).load())
        val live = app.shared.listFiles()!!.filter { it.isFile }.associate { it.name to Fs.sha256Hex(it) }
        for ((name, sha) in pre) assertEquals(sha, live[name], name)
    }
}
