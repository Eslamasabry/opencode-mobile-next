package io.github.eslamasabry.oc.migration.readers.migration

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** S0 under interruption at every filesystem operation (audit finding 5). */
class BackupPublisherTest {

    private val root: File = Files.createTempDirectory("s0").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun seedApp(dir: File) {
        FixtureGenerator.writeAll(dir, FixtureGenerator.newKeyPair(SecureRandom()), SecureRandom())
    }

    private fun snapshot(dir: File): Map<String, String> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to Fs.sha256Hex(it) }

    private fun publisher(app: File, faults: FaultPoints = FaultPoints.None, free: Long = Long.MAX_VALUE) = BackupPublisher(
        sharedPrefsDir = File(app, "shared_prefs"),
        backupRoot = File(app, "files/migration"),
        journal = JournalStore(File(app, "files/native/migration.json"), faults),
        faults = faults,
        freeBytes = { free },
    )

    @Test
    fun `publishes a verified byte copy with a manifest, then is a no-op`() {
        val app = File(root, "app").apply { mkdirs() }
        seedApp(app)
        // A .bak twin: Android would load it instead of the .xml, so it is the authoritative copy.
        File(app, "shared_prefs/FlutterSharedPreferences.xml.bak").writeText("<map />")
        val before = snapshot(File(app, "shared_prefs"))

        val out = publisher(app).runIfNeeded()
        assertIs<BackupPublisher.Outcome.Published>(out)
        val byName = out.files.associateBy { it.name }
        assertEquals(true, byName.getValue("FlutterSharedPreferences.xml.bak").authoritative)
        assertEquals(false, byName.getValue("FlutterSharedPreferences.xml").authoritative)
        assertEquals(true, byName.getValue("FlutterSecureStorage.xml").authoritative)
        assertTrue("flutter_image_picker_shared_preference.xml" in byName)
        assertEquals(before, snapshot(File(app, "shared_prefs")), "sources are only read")

        val published = snapshot(File(app, "files/migration/flutter-v57"))
        assertEquals(BackupPublisher.Outcome.AlreadyPublished, publisher(app).runIfNeeded())
        assertEquals(published, snapshot(File(app, "files/migration/flutter-v57")), "a rerun never rewrites the backup")
        assertEquals(BackupState.Published, JournalStore(File(app, "files/native/migration.json")).load().backupState)
    }

    @Test
    fun `a crash at any point ends, after restart, in the same published backup`() {
        val reference = File(root, "ref").apply { mkdirs() }
        seedApp(reference)
        val points = RecordingFaults().also { publisher(reference, it).runIfNeeded() }.seen
        assertTrue(points.size >= 10, "expected many fault points, saw $points")
        val expected = snapshot(File(reference, "files/migration/flutter-v57"))

        for ((i, point) in points.withIndex()) {
            val occurrence = points.subList(0, i + 1).count { it == point }
            val app = File(root, "case$i").apply { mkdirs() }
            reference.resolve("shared_prefs").copyRecursively(File(app, "shared_prefs"))
            assertFailsWith<SimulatedCrash> { publisher(app, RecordingFaults(point, occurrence)).runIfNeeded() }
            val second = publisher(app).runIfNeeded()
            assertTrue(second is BackupPublisher.Outcome.Published || second == BackupPublisher.Outcome.AlreadyPublished, "after $point: $second")
            assertEquals(expected, snapshot(File(app, "files/migration/flutter-v57")), "after crash at $point")
            assertTrue(!File(app, "files/migration/flutter-v57.partial").exists(), "partial dir left after $point")
            val j = JournalStore(File(app, "files/native/migration.json")).load()
            assertEquals(BackupState.Published, j.backupState, "after $point")
            // Third run: nothing changes.
            assertEquals(BackupPublisher.Outcome.AlreadyPublished, publisher(app).runIfNeeded())
        }
    }

    @Test
    fun `a published backup that no longer verifies blocks and is never overwritten`() {
        val app = File(root, "app").apply { mkdirs() }
        seedApp(app)
        publisher(app).runIfNeeded()
        val copy = File(app, "files/migration/flutter-v57/FlutterSharedPreferences.xml")
        copy.appendText("tampered")
        val tampered = Fs.sha256Hex(copy)
        assertEquals(BackupPublisher.Outcome.BackupInvalid, publisher(app).runIfNeeded())
        assertEquals(tampered, Fs.sha256Hex(copy))
        assertEquals("BackupInvalid", JournalStore(File(app, "files/native/migration.json")).load().blocked?.reason)
    }

    @Test
    fun `fresh install and low space`() {
        val fresh = File(root, "fresh").apply { mkdirs() }
        assertEquals(BackupPublisher.Outcome.NoSource, publisher(fresh).runIfNeeded())
        assertEquals("none", JournalStore(File(fresh, "files/native/migration.json")).load().sourceKind)

        val full = File(root, "full").apply { mkdirs() }
        seedApp(full)
        val out = publisher(full, free = 1024).runIfNeeded()
        assertIs<BackupPublisher.Outcome.LowSpace>(out)
        assertTrue(!File(full, "files/migration").exists() || File(full, "files/migration").list()!!.isEmpty(), "nothing written when space is low")
        assertIs<BackupPublisher.Outcome.Published>(publisher(full).runIfNeeded(), "Try again works after freeing space")
    }
}
