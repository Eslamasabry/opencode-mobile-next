package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Owner decision 2026-11-07 (native has no Termux): a Flutter phone whose saved setup used Termux migrates like any
 * other. Nothing is imported from Termux; the Termux server stays a saved server, its password is re-stored, the
 * Termux setup file stays where it was (the app's notice reads it), and every other record migrates as before.
 */
class TermuxSetupMigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    private val termuxProfile =
        linkedMapOf<String, Any?>(
            "id" to TERMUX,
            "name" to "Termux",
            "baseUrl" to "http://127.0.0.1:4096",
            "backend" to "openCode",
            "username" to "opencode",
            "flavor" to "v2",
            "requiresOpenCode2Migration" to false,
            "openCode2HistoryPending" to false,
        )

    private fun profilesWithTermux(): String {
        @Suppress("UNCHECKED_CAST")
        val rows = MiniJson.parse(Seed.profiles) as List<Any?>
        return MiniJson.encode(rows + listOf(termuxProfile))
    }

    @Test
    fun `B-MIGR-016 a Flutter Termux setup migrates without a crash and keeps every other record`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val reference = World(tmp.newFolder()).apply { Seed.phone(this) }
        assertIs<RunnerState.Open>(reference.runner(io).run())

        val termuxPrefs =
            LinkedHashMap(Seed.prefs).apply {
                put("oc.profiles", s(profilesWithTermux()))
                // What a Flutter Termux user leaves behind: the move-out journal and offer keys.
                put("oc.termuxMigration.$TERMUX", s("""{"v":1,"state":"offered"}"""))
                put("oc.termuxMigrationOffer.$TERMUX", b(true))
            }
        val w = World(tmp.newFolder())
        w.seedPrefs(termuxPrefs)
        w.seedSecrets(LinkedHashMap(secretsWithTermux()))
        val setupFile = File(w.paths.files, "termux-setup-v2.json").apply { writeText("""{"state":"done"}""") }

        assertIs<RunnerState.Open>(w.runner(io).run())

        // The Termux server is still a saved server, exactly as Flutter stored it.
        val profiles = (w.logical()["oc.profiles"] as RawPref.Str).value
        assertTrue(profiles.contains("\"baseUrl\":\"http://127.0.0.1:4096\""), profiles)
        assertTrue(profiles.contains("\"id\":\"$TERMUX\""), profiles)
        assertEquals("fixture-termux-pass", w.secrets.values["pw.$TERMUX"])
        // Nothing reads or rewrites the Termux setup file: the notice finds it after migration.
        assertEquals("""{"state":"done"}""", setupFile.readText())
        // Every other record migrated as it does on a phone without Termux.
        val others = reference.logical().filterKeys { it != "oc.profiles" }
        assertEquals(others, w.logical().filterKeys { it in others.keys })
        assertEquals(reference.secrets.values, w.secrets.values.filterKeys { it != "pw.$TERMUX" })
    }

    private fun secretsWithTermux(): Map<String, String> = Seed.secrets + ("pw.$TERMUX" to "fixture-termux-pass")

    private companion object {
        const val TERMUX = "1735000000000009"
    }
}
