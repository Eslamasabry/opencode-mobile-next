package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.Seed.CODEX
import io.github.eslamasabry.oc.android.migration.Seed.PHONE
import io.github.eslamasabry.oc.android.migration.Seed.REMOTE
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import io.github.eslamasabry.oc.android.migration.readers.secure.FlutterSecret
import io.github.eslamasabry.oc.android.migration.readers.secure.FlutterSecureStorageReader
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.android.migration.readers.secure.FssStores
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.android.migration.steps.S0Backup
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraCopies
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraCopy
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraCopyKind
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraRead
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraValue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E00-S08 (B-SEC-201, plan 16 §1.4 CR16-14): the Android `RetainedCopies`. A migrated phone is seeded with the Flutter
 * prefs and flutter_secure_storage files of three servers, the runner takes the S0 backup and opens, then the "Work"
 * server is forgotten through [FlutterEraCopies] with the same kind of edit `:engine:profiles`' `FlutterEraSweep`
 * applies. Nothing of it may remain in any copy, every other entry keeps its exact bytes, and the backup still verifies.
 */
class FlutterEraCopiesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.io() = StandardTestDispatcher(testScheduler)

    private suspend fun TestScope.migrated(): World {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        assertIs<RunnerState.Open>(w.runner(io()).run())
        return w
    }

    /** What the engine's structural edit does to a copy, reduced to this fixture: the server's keys, row and queue. */
    private fun forget(copy: FlutterEraCopy, id: String): Boolean {
        val entries = assertIs<FlutterEraRead.Entries>(copy.read()).entries
        val next = LinkedHashMap(entries)
        next.keys.removeAll { it.endsWith(".$id") || ".$id." in it }
        if (copy.kind == FlutterEraCopyKind.Prefs) {
            if ((next["oc.activeProfile"] as? FlutterEraValue.Str)?.value == id) next.remove("oc.activeProfile")
            for (key in listOf("oc.profiles", "oc.offlineQueue")) {
                val rows = MiniJson.parseOrNull((next[key] as? FlutterEraValue.Str)?.value ?: continue) as List<*>
                val kept = rows.filterNot { (it as Map<*, *>)["id"] == id || it["profileID"] == id }
                next[key] = FlutterEraValue.Str(MiniJson.encode(kept))
            }
        }
        return copy.rewrite(next)
    }

    private fun secretsIn(dir: File, w: World): Map<String, String> =
        FlutterSecureStorageReader(w.unwrapper).read(FssStores.fromDirectory(dir)).secrets
            .filterIsInstance<FlutterSecret.Plain>().associate { it.name to it.value.reveal() }

    private val remoteSecrets = Seed.secrets.keys.filter { it.endsWith(".$REMOTE") }

    @Test
    fun `B-SEC-201 forgetting a migrated server removes its password and tokens from the live secure-storage file and the S0 backup, the others still decrypt`() = runTest {
        val w = migrated()
        assertTrue("pw.$REMOTE" in remoteSecrets && "oc.githubToken.$REMOTE" in remoteSecrets)
        val copies = FlutterEraCopies(w.paths).list()
        assertEquals(
            listOf(
                "shared_prefs/FlutterSecureStorage.xml",
                "files/migration/flutter-v57/FlutterSecureStorage.xml",
                "files/migration/flutter-v57/FlutterSharedPreferences.xml",
            ),
            copies.map { it.label },
        )
        val liveFss = File(w.paths.sharedPrefs, "FlutterSecureStorage.xml")
        val before = liveFss.readText()

        assertTrue(copies.all { forget(it, REMOTE) })

        for (dir in listOf(w.paths.sharedPrefs, w.paths.backup)) {
            val file = File(dir, "FlutterSecureStorage.xml")
            assertFalse(REMOTE in file.readText(), "$file still names the server")
            val left = secretsIn(dir, w)
            assertTrue(remoteSecrets.none { it in left }, "$dir: $left")
            // Every other server's secret still decrypts: the wrapped key and their ciphertext were not touched.
            for ((name, value) in Seed.secrets) if (name !in remoteSecrets) assertEquals(value, left[name], "$dir $name")
        }
        // Only the forgotten elements' lines left the live file; every other byte is where it was.
        val doomedLines = remoteSecrets.map { "name=\"${FssFormat.entryName(it)}\"" }
        assertEquals(before.lines().filterNot { line -> doomedLines.any { it in line } }, liveFss.readText().lines())
        val prefsCopy = File(w.paths.backup, "FlutterSharedPreferences.xml").readText()
        assertFalse(REMOTE in prefsCopy || Seed.SERVER_URL in prefsCopy || Seed.PROMPT_TEXT in prefsCopy)
        assertTrue(CODEX in prefsCopy && PHONE in prefsCopy)
    }

    @Test
    fun `B-SEC-201 the S0 backup still verifies after a sweep, and after a kill between the file and its manifest`() = runTest {
        val w = migrated()
        val s0 = S0Backup(w.paths, StepInterrupter.None, { 1L shl 40 }, 10_000L, { w.nowMs })
        assertNotNull(s0.verifyPublished())

        val copy = FlutterEraCopies(w.paths).list().first { it.label == "files/migration/flutter-v57/FlutterSecureStorage.xml" }
        assertTrue(forget(copy, REMOTE))
        val after = assertNotNull(s0.verifyPublished())
        assertTrue(after.all { it.pendingSha256 == null }, "the pending pair is committed")

        // A kill after the pending pair was recorded and the file renamed, before the manifest's second write.
        val file = File(w.paths.backup, "FlutterSecureStorage.xml")
        val edited = file.readText().lines().filterNot { "pw.$CODEX" in it || "codexToken.$CODEX" in it }
            .joinToString("\n").toByteArray()
        assertTrue(S0Backup.markPending(w.paths.backup, file.name, edited.size.toLong(), Fs.sha256Hex(edited)))
        assertNotNull(s0.verifyPublished(), "before the rename")
        file.writeBytes(edited)
        assertNotNull(s0.verifyPublished(), "after the rename, manifest not yet committed")
        file.appendText("<!-- damaged -->")
        assertEquals(null, s0.verifyPublished(), "any other content still fails")
    }

    @Test
    fun `B-SEC-201 a Start fresh copy and a plugin backup entry are swept too, and a second sweep changes nothing`() = runTest {
        val w = migrated()
        val replaced = File(w.paths.backupRoot, "replaced-1730457600000").apply { mkdirs() }
        File(w.paths.backup, "FlutterSharedPreferences.xml").copyTo(File(replaced, "FlutterSharedPreferences.xml"))
        // flutter_secure_storage's own "migrate with backup" path leaves `<entry>_BACKUP` holding the same secret.
        val live = File(w.paths.sharedPrefs, "FlutterSecureStorage.xml")
        val backupEntry = FssFormat.entryName("pw.$REMOTE") + FssFormat.BACKUP_SUFFIX
        live.writeText(live.readText().replace("</map>", "    <string name=\"$backupEntry\">b2xkLWNpcGhlcg==</string>\n</map>"))

        val copies = FlutterEraCopies(w.paths).list()
        assertTrue("files/migration/replaced-1730457600000/FlutterSharedPreferences.xml" in copies.map { it.label })
        assertTrue(copies.all { forget(it, REMOTE) })
        assertTrue(AndroidPrefsXml.parse(live).entries.keys.none { REMOTE in it })
        assertFalse(REMOTE in File(replaced, "FlutterSharedPreferences.xml").readText())

        val bytes = copies.associate { it.label to File(w.paths.dataDir, it.label).readBytes() }
        assertTrue(FlutterEraCopies(w.paths).list().all { forget(it, REMOTE) })
        for ((label, b) in bytes) assertTrue(b.contentEquals(File(w.paths.dataDir, label).readBytes()), label)
    }

    @Test
    fun `B-SEC-201 a copy Android would load empty is refused and left exactly as it was`() = runTest {
        val w = migrated()
        val live = File(w.paths.sharedPrefs, "FlutterSecureStorage.xml")
        val broken = live.readText().substringBefore("</map>") // truncated: Android loads an empty map
        live.writeText(broken)
        val copy = FlutterEraCopies(w.paths).list().first { it.label == "shared_prefs/FlutterSecureStorage.xml" }

        assertEquals(FlutterEraRead.Unreadable, copy.read())
        assertFalse(copy.rewrite(emptyMap()))
        assertEquals(broken, live.readText())
    }

    @Test
    fun `B-SEC-201 a secure-storage copy never takes a new or changed value`() = runTest {
        val w = migrated()
        val copy = FlutterEraCopies(w.paths).list().first { it.kind == FlutterEraCopyKind.SecureStorage }
        val entries = assertIs<FlutterEraRead.Entries>(copy.read()).entries
        val before = File(w.paths.sharedPrefs, "FlutterSecureStorage.xml").readBytes()

        assertFalse(copy.rewrite(entries + ("pw.new" to FlutterEraValue.Opaque)))
        assertFalse(copy.rewrite(entries + ("pw.$CODEX" to FlutterEraValue.Str("plain"))))
        assertTrue(before.contentEquals(File(w.paths.sharedPrefs, "FlutterSecureStorage.xml").readBytes()))
    }
}
