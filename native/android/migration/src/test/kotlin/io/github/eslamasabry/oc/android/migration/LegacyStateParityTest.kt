package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.steps.LogicalPrefs
import io.github.eslamasabry.oc.android.migration.steps.M1Census
import io.github.eslamasabry.oc.android.migration.steps.M3LegacyTransforms
import io.github.eslamasabry.oc.android.migration.steps.NotifyMerge
import io.github.eslamasabry.oc.android.migration.steps.NotifyMerge.ProfileRules
import io.github.eslamasabry.oc.android.migration.steps.NotifyMerge.QuotaRules
import io.github.eslamasabry.oc.android.migration.steps.SharedNotifyRules
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Flutter cases `LegacyTransformsTest` did not port yet: `notification_preferences_test.dart` (B-MIGR-001),
 * `oc1_profile_upgrade_test.dart` as far as the migration reads it (B-MIGR-002), `settings_scope_controller_test.dart`
 * "load migrates existing profiles once" for each legacy value (B-MIGR-003) and `external_agent_state_test.dart`'s
 * "no bearer is ever persisted or resurrected" (B-SEC-063; the A2A feature itself is dropped in the native app, D6).
 */
class LegacyStateParityTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.io() = StandardTestDispatcher(testScheduler)

    private fun profiles(vararg ids: String) =
        ids.joinToString(",", "[", "]") { """{"id":"$it","name":"$it","baseUrl":"https://$it.example.test","backend":"openCode","flavor":"v2"}""" }

    private fun quota(notifications: Boolean = false, wifiOnly: Boolean = false, quiet: Boolean = false) =
        """{"version":1,"rules":{"codex":{"source":"${"a".repeat(64)}","notifications":$notifications,"wifiOnly":$wifiOnly,""" +
            """"quietStart":${if (quiet) 1320 else "null"},"quietEnd":${if (quiet) 480 else "null"},"alerted":{},"threshold":90}}}"""

    private fun dir(logical: Map<String, RawPref>): MigrationPaths {
        val paths = MigrationPaths(tmp.newFolder())
        paths.sharedPrefs.mkdirs()
        File(paths.sharedPrefs, "FlutterSharedPreferences.xml")
            .writeBytes(AndroidPrefsXmlWriter.write(logical.mapKeys { FlutterPrefsCodec.physicalKey(it.key) }))
        return paths
    }

    private fun m3(paths: MigrationPaths): Map<String, RawPref> {
        assertIs<M3LegacyTransforms.Result.Done>(M3LegacyTransforms(paths, FakeFlutterPrefs(paths.sharedPrefs)).run())
        return LogicalPrefs(FakeFlutterPrefs(paths.sharedPrefs).live()).map
    }

    private fun logical(paths: MigrationPaths) = LogicalPrefs(FakeFlutterPrefs(paths.sharedPrefs).live())

    // ---- B-MIGR-001 ---------------------------------------------------------------------------------------------

    @Test
    fun `B-MIGR-001 neither source - quiet hours are off even when the servers notified`() {
        val merged = NotifyMerge.merge(listOf(ProfileRules(false, null, null, null)), listOf(QuotaRules(true, false, false)))
        assertFalse(merged.quietEnabled)
        assertNull(merged.quietStart)
        assertNull(merged.quietEnd)
        assertTrue(merged.quotaAlerts)
    }

    @Test
    fun `B-MIGR-001 both sources - the first saved-server pair in order wins over the quota default and later servers`() {
        val merged = NotifyMerge.merge(
            listOf(ProfileRules(false, null, null, null), ProfileRules(false, 60, 120, null), ProfileRules(false, 200, 300, null)),
            listOf(QuotaRules(false, false, true)),
        )
        assertEquals(60L, merged.quietStart)
        assertEquals(120L, merged.quietEnd)
        assertFalse(NotifyMerge.merge(listOf(ProfileRules(false, null, null, null)), listOf(QuotaRules(false, false, false))).wifiOnly)
    }

    @Test
    fun `B-MIGR-001 nothing is shared until the migration has run, and the legacy answer stays readable`() {
        val legacy = """{"quietStart":60,"quietEnd":120}"""
        val paths = dir(mapOf("oc.profiles" to s(profiles("a")), "oc.notifyRules.a" to s(legacy)))
        val before = logical(paths)
        assertTrue(before.map.keys.none { it.startsWith("oc.notify.") }, "no shared rule exists before the merge")
        assertEquals(60L, NotifyMerge.readLegacy(before, listOf("a")).quietStart)

        val after = m3(paths)
        assertEquals(l(60), after["oc.notify.quietStart"])
        assertEquals(l(1), after["oc.notify.version"])
        assertEquals(s(legacy), after["oc.notifyRules.a"])
    }

    @Test
    fun `B-MIGR-001 only the quota toggle - 22-00 to 08-00 and Wi-Fi only, quota alerts stay off`() {
        val after = m3(dir(mapOf("oc.profiles" to s(profiles("a")), "oc.quotaMonitor.a" to s(quota(quiet = true, wifiOnly = true)))))
        assertEquals(l(22 * 60), after["oc.notify.quietStart"])
        assertEquals(l(8 * 60), after["oc.notify.quietEnd"])
        assertEquals(b(true), after["oc.notify.wifiOnly"])
        assertEquals(b(false), after["oc.notify.quotaAlerts"])
    }

    @Test
    fun `B-MIGR-001 a second pass never overwrites an edit made after the first`() {
        val paths = dir(mapOf("oc.profiles" to s(profiles("a")), "oc.notifyRules.a" to s("""{"quietStart":60,"quietEnd":120}""")))
        m3(paths)
        // The person clears quiet hours and turns Wi-Fi only on in Settings.
        assertTrue(FakeFlutterPrefs(paths.sharedPrefs).commit(mapOf("oc.notify.quietStart" to null, "oc.notify.quietEnd" to null, "oc.notify.wifiOnly" to b(true)).mapKeys { FlutterPrefsCodec.physicalKey(it.key) }))
        val again = m3(paths)
        assertNull(again["oc.notify.quietStart"])
        assertEquals(b(true), again["oc.notify.wifiOnly"])
        assertTrue(again.containsKey("oc.notifyRules.a"), "the legacy record is left for an older build")
    }

    @Test
    fun `B-MIGR-001 the two what-notifies-me choices are never written by the merge, so an explicit off survives`() {
        val after = m3(
            dir(
                mapOf(
                    "oc.profiles" to s(profiles("a")),
                    "oc.notify.finishedRuns" to b(false),
                    "oc.notify.requests" to b(false),
                ),
            ),
        )
        assertEquals(b(false), after["oc.notify.finishedRuns"])
        assertEquals(b(false), after["oc.notify.requests"])
        assertEquals(SharedNotifyRules(), NotifyMerge.merge(emptyList(), emptyList()))
    }

    // ---- B-MIGR-002 ---------------------------------------------------------------------------------------------

    @Test
    fun `B-MIGR-002 old managed OpenCode 1 rows keep their migration requirement, agent and v2 rows never get one`() {
        for (flavor in listOf(null, "v1", "unknown")) {
            val row = mapOf("id" to "phone", "baseUrl" to "http://127.0.0.1:4096", "flavor" to flavor)
            assertTrue(M1Census.requiresOpenCode2Migration(row), "flavor $flavor")
        }
        assertFalse(M1Census.requiresOpenCode2Migration(mapOf("id" to "phone", "flavor" to "v2")))
        assertTrue(M1Census.requiresOpenCode2Migration(mapOf("id" to "phone", "flavor" to "v2", "requiresOpenCode2Migration" to true)))
        for (backend in listOf("codex", "paseo")) {
            assertFalse(M1Census.requiresOpenCode2Migration(mapOf("id" to "a", "backend" to backend)), backend)
        }
    }

    @Test
    fun `B-MIGR-002 the census counts pending upgrades and copied history without changing the profile rows`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        val rows = (w.logical()["oc.profiles"] as RawPref.Str).value
        assertIs<RunnerState.Open>(w.runner(io()).run())
        val journal = w.journalText()
        assertTrue(journal.contains("\"oc1MigrationRequired\":1"), "the v1 phone row still needs its upgrade")
        assertTrue(journal.contains("\"oc2HistoryPending\":1"), "copied history stays pending until confirmed")
        assertEquals(rows, (w.logical()["oc.profiles"] as RawPref.Str).value, "the rows are adopted as they are")
    }

    // ---- B-MIGR-003 ---------------------------------------------------------------------------------------------

    @Test
    fun `B-MIGR-003 load migrates existing profiles once from each legacy consent value, and later profiles are not`() {
        for (legacy in listOf<Boolean?>(true, false, null)) {
            val seed = linkedMapOf<String, RawPref>("oc.profiles" to s(profiles("old")))
            if (legacy != null) seed["oc.shareSessionViews"] = b(legacy)
            val paths = dir(seed)
            val first = m3(paths)
            assertEquals(b(legacy ?: true), first["oc.shareSessionViews.old"], "legacy $legacy")

            // A profile added after the migration is not swept in on the next load: it keeps the default (off).
            assertTrue(FakeFlutterPrefs(paths.sharedPrefs).commit(mapOf(FlutterPrefsCodec.physicalKey("oc.profiles") to s(profiles("old", "new")))))
            val second = m3(paths)
            assertNull(second["oc.shareSessionViews.new"], "legacy $legacy")
            assertEquals(b(legacy ?: true), second["oc.shareSessionViews.old"], "legacy $legacy")

            // An explicit later choice for the old profile survives every further load.
            assertTrue(FakeFlutterPrefs(paths.sharedPrefs).commit(mapOf(FlutterPrefsCodec.physicalKey("oc.shareSessionViews.old") to b(false))))
            assertEquals(b(false), m3(paths)["oc.shareSessionViews.old"], "legacy $legacy")
            assertNull(m3(paths)["oc.shareSessionViews.new"], "legacy $legacy")
        }
    }

    // ---- B-SEC-063 ----------------------------------------------------------------------------------------------

    @Test
    fun `B-SEC-063 no bearer reaches the native registry, journal, audit or files, and a re-run never recreates one`() = runTest {
        val w = World(tmp.newFolder())
        Seed.phone(
            w,
            prefs = Seed.prefs + mapOf(
                "oc.externalAgents" to s("""[{"id":"agent-7","name":"Agent","url":"https://agent.example.test"}]"""),
                "oc.a2aTasks.agent-7" to s("""[{"localId":"t1","task":{"id":"task-1","contextId":"context-1"}}]"""),
            ),
        )
        assertIs<RunnerState.Open>(w.runner(io()).run())
        assertIs<RunnerState.Open>(w.runner(io()).run())

        assertTrue(w.secrets.values.keys.none { it.startsWith("oc.a2aBearer.") })
        assertTrue(w.secrets.values.values.none { it.contains("fixture-a2a-bearer") })
        val native = snapshotFiles(w.paths.nativeDir).keys.map { File(w.paths.nativeDir, it).readText(Charsets.ISO_8859_1) }
        assertTrue(native.none { it.contains("fixture-a2a-bearer") }, "no bearer in the journal, audit or ledger")
        assertTrue(w.prefsBytes().toString(Charsets.UTF_8).let { !it.contains("fixture-a2a-bearer") })
        assertTrue(w.journalText().contains("\"a2aPrefsKeys\":2"), "the dropped records are counted, not copied")
    }
}
