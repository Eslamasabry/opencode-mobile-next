package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.steps.LogicalPrefs
import io.github.eslamasabry.oc.android.migration.steps.M3LegacyTransforms
import io.github.eslamasabry.oc.android.migration.steps.NotifyMerge
import io.github.eslamasabry.oc.android.migration.steps.NotifyMerge.ProfileRules
import io.github.eslamasabry.oc.android.migration.steps.NotifyMerge.QuotaRules
import io.github.eslamasabry.oc.android.migration.steps.SharedNotifyRules
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** Plan 05 §10.2 `LegacyTransformsTest`: L1–L3 ported case by case from the Dart tests, and L4b (Astra B2). */
class LegacyTransformsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val profiles = """[{"id":"a","name":"A","baseUrl":"https://a.example.test","backend":"openCode","flavor":"v2"},""" +
        """{"id":"b","name":"B","baseUrl":"https://b.example.test","backend":"openCode","flavor":"v2"}]"""

    /** Runs M3 over [logical] and returns the logical map afterwards. */
    private fun m3(logical: Map<String, RawPref>, picker: Map<String, RawPref>? = null, refuse: Boolean = false): Pair<M3LegacyTransforms.Result, Map<String, RawPref>> {
        val dir = tmp.newFolder()
        val paths = MigrationPaths(dir)
        paths.sharedPrefs.mkdirs()
        File(paths.sharedPrefs, "FlutterSharedPreferences.xml").writeBytes(AndroidPrefsXmlWriter.write(logical.mapKeys { FlutterPrefsCodec.physicalKey(it.key) }))
        if (picker != null) File(paths.sharedPrefs, "flutter_image_picker_shared_preference.xml").writeBytes(AndroidPrefsXmlWriter.write(picker))
        val prefs = FakeFlutterPrefs(paths.sharedPrefs).also { if (refuse) it.refuse = { true } }
        val result = M3LegacyTransforms(paths, prefs).run()
        return result to LogicalPrefs(FakeFlutterPrefs(paths.sharedPrefs).live()).map
    }

    // ---- L1 -------------------------------------------------------------------------------------------------------

    @Test
    fun `B-STATE-081 B-STATE-068 L1 removes the retired budgets and glass keys and nothing else`() {
        val (_, after) = m3(
            mapOf(
                "oc.budgets.a" to s("{}"), "oc.budgets.b" to s("{}"), "oc.glassLiquidActive" to b(true),
                "oc.glassLiquidStrikes" to l(1), "oc.glassLiquidOffUntil" to l(5), "oc.effectsLiveBackground" to b(true),
                "oc.consumptionBudgets.a" to s("{}"), "oc.glass" to s("soft"), "oc.notify.version" to l(1),
            ),
        )
        assertEquals(setOf("oc.consumptionBudgets.a", "oc.glass", "oc.notify.version", "oc.readStateConsentMigration.v1"), after.keys)
    }

    // ---- L2 (notification_preferences_test.dart) -----------------------------------------------------------------

    @Test
    fun `B-MIGR-001 the merge keeps the saved-server pair, else 22-08 from a quota toggle, else none`() {
        assertEquals(SharedNotifyRules(quietStart = 23 * 60 + 30, quietEnd = 6 * 60 + 15),
            NotifyMerge.merge(listOf(ProfileRules(false, 23 * 60 + 30, 6 * 60 + 15, null)), emptyList()))
        assertEquals(SharedNotifyRules(quietStart = 22 * 60, quietEnd = 8 * 60),
            NotifyMerge.merge(emptyList(), listOf(QuotaRules(false, false, true))))
        assertEquals(60L, NotifyMerge.merge(listOf(ProfileRules(false, 60, 120, null)), listOf(QuotaRules(false, false, true))).quietStart)
        assertFalse(NotifyMerge.merge(emptyList(), emptyList()).quietEnabled)
        assertFalse(NotifyMerge.merge(listOf(ProfileRules(false, 60, null, null)), emptyList()).quietEnabled, "half a pair is not a pair")
    }

    @Test
    fun `B-MIGR-001 Wi-Fi only if either was on, check-ins take the first duration, quota alerts any source`() {
        assertFalse(NotifyMerge.merge(emptyList(), emptyList()).wifiOnly)
        assertTrue(NotifyMerge.merge(listOf(ProfileRules(true, null, null, null)), emptyList()).wifiOnly)
        assertTrue(NotifyMerge.merge(emptyList(), listOf(QuotaRules(false, true, false))).wifiOnly)
        val merged = NotifyMerge.merge(
            listOf(ProfileRules(false, null, null, null), ProfileRules(false, null, null, 60), ProfileRules(false, null, null, 30)),
            listOf(QuotaRules(false, false, false), QuotaRules(true, false, false)),
        )
        assertEquals(60L, merged.checkInAfterMinutes)
        assertTrue(merged.quotaAlerts)
    }

    @Test
    fun `B-MIGR-001 the migration reads both legacy stores in order, active server first, and keeps the sources`() {
        val legacyA = """{"quietStart":100,"quietEnd":150,"checkInAfterMinutes":45}"""
        val legacyB = """{"quietStart":200,"quietEnd":300,"wifiOnly":true}"""
        val quotaA = """{"rules":{"x":{"notifications":true,"wifiOnly":false}}}"""
        val (_, after) = m3(
            mapOf(
                "oc.profiles" to s(profiles), "oc.activeProfile" to s("b"),
                "oc.notifyRules.a" to s(legacyA), "oc.notifyRules.b" to s(legacyB), "oc.quotaMonitor.a" to s(quotaA),
            ),
        )
        assertEquals(l(200), after["oc.notify.quietStart"]) // the active server's pair wins
        assertEquals(l(300), after["oc.notify.quietEnd"])
        assertEquals(b(true), after["oc.notify.wifiOnly"])
        assertEquals(l(45), after["oc.notify.checkInAfterMinutes"])
        assertEquals(b(true), after["oc.notify.quotaAlerts"])
        assertEquals(l(1), after["oc.notify.version"])
        assertEquals(s(legacyA), after["oc.notifyRules.a"]) // never deleted: an older build must find its own values
        assertEquals(s(quotaA), after["oc.quotaMonitor.a"])
    }

    @Test
    fun `B-MIGR-001 runs once - a migrated store is never overwritten by a second pass`() {
        val (_, after) = m3(
            mapOf(
                "oc.profiles" to s(profiles), "oc.notifyRules.a" to s("""{"quietStart":100,"quietEnd":150}"""),
                "oc.notify.version" to l(1), "oc.notify.wifiOnly" to b(true),
            ),
        )
        assertNull(after["oc.notify.quietStart"])
        assertEquals(b(true), after["oc.notify.wifiOnly"])
    }

    @Test
    fun `B-MIGR-001 a corrupt legacy record migrates as off and the two choices keep their defaults`() {
        val (_, after) = m3(mapOf("oc.profiles" to s(profiles), "oc.notifyRules.a" to s("{not json"), "oc.quotaMonitor.a" to s("[1]")))
        assertNull(after["oc.notify.quietStart"])
        assertEquals(b(false), after["oc.notify.wifiOnly"])
        assertNull(after["oc.notify.checkInAfterMinutes"])
        assertEquals(b(false), after["oc.notify.quotaAlerts"])
        assertEquals(l(1), after["oc.notify.version"])
        assertNull(after["oc.notify.finishedRuns"]) // absent = on (Dart default); never written by the migration
        assertNull(after["oc.notify.requests"])
    }

    // ---- L3 (read-state consent) ---------------------------------------------------------------------------------

    @Test
    fun `B-MIGR-003 B-SEC-142 existing servers take the legacy value once, missing legacy meaning on`() {
        val (_, after) = m3(mapOf("oc.profiles" to s(profiles), "oc.notify.version" to l(1)))
        assertEquals(b(true), after["oc.shareSessionViews.a"])
        assertEquals(s("migrated"), after["oc.shareSessionViewsOrigin.a"])
        assertEquals(b(true), after["oc.shareSessionViews.b"])
        assertEquals(s("""{"version":1,"complete":true,"ids":[],"legacy":true}"""), after["oc.readStateConsentMigration.v1"])
    }

    @Test
    fun `B-SEC-142 a legacy off stays off, an explicit choice wins, invalid legacy data never grants sharing`() {
        val (_, off) = m3(mapOf("oc.profiles" to s(profiles), "oc.shareSessionViews" to b(false), "oc.shareSessionViews.b" to b(true)))
        assertEquals(b(false), off["oc.shareSessionViews.a"])
        assertEquals(b(true), off["oc.shareSessionViews.b"])
        assertNull(off["oc.shareSessionViewsOrigin.b"])
        val (_, invalid) = m3(mapOf("oc.profiles" to s(profiles), "oc.shareSessionViews" to s("yes")))
        assertEquals(b(false), invalid["oc.shareSessionViews.a"])
    }

    @Test
    fun `B-MIGR-003 an incomplete journal resumes from its own population and a completed one is never rerun`() {
        val incomplete = """{"version":1,"complete":false,"ids":["a"],"legacy":false}"""
        val (_, resumed) = m3(mapOf("oc.profiles" to s(profiles), "oc.readStateConsentMigration.v1" to s(incomplete)))
        assertEquals(b(false), resumed["oc.shareSessionViews.a"])
        assertNull(resumed["oc.shareSessionViews.b"], "b joined after the population was captured")

        val done = """{"version":1,"complete":true,"ids":[],"legacy":true}"""
        val (_, again) = m3(mapOf("oc.profiles" to s(profiles), "oc.readStateConsentMigration.v1" to s(done)))
        assertNull(again["oc.shareSessionViews.a"])
    }

    @Test
    fun `B-SEC-142 a journal Dart would reject is left alone and grants nothing`() {
        val (result, after) = m3(mapOf("oc.profiles" to s(profiles), "oc.readStateConsentMigration.v1" to s("""{"version":2}""")))
        assertEquals("unreadable", assertIs<M3LegacyTransforms.Result.Done>(result).counts["L3"])
        assertNull(after["oc.shareSessionViews.a"])
        assertEquals(s("""{"version":2}"""), after["oc.readStateConsentMigration.v1"])
    }

    // ---- L4b (Astra B2) ------------------------------------------------------------------------------------------

    private fun photoRecord(path: String? = null, failed: Boolean = false, ref: String? = null) =
        """{"id":"ph1","profileID":"a","sessionID":"ses_9","directory":"/work","workspace":null,"ref":${ref ?: "null"},""" +
            """"path":${path?.let { "\"$it\"" } ?: "null"},"name":null,"failed":$failed}"""

    @Test
    fun `B-STATE-088 L4b a photo only the picker kept is put back into its own record, every other field byte-identical`() {
        val photo = File(tmp.newFolder(), "image_picker_123.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val picker = mapOf<String, RawPref>("flutter_image_picker_image_path" to RawPref.StrSet(linkedSetOf(photo.path)))
        val (_, after) = m3(mapOf("oc.notify.version" to l(1), "oc.pendingPromptPhoto" to s(photoRecord())), picker)
        assertEquals(
            s(
                """{"id":"ph1","profileID":"a","sessionID":"ses_9","directory":"/work","workspace":null,"ref":null,""" +
                    """"path":"${photo.path}","name":"image_picker_123.jpg","failed":false}""",
            ),
            after["oc.pendingPromptPhoto"],
        )
    }

    @Test
    fun `B-STATE-088 L4b an error marks the record failed, no result leaves it, and a finished record is never touched`() {
        val error = mapOf<String, RawPref>("flutter_image_picker_error_code" to s("camera_access_denied"))
        val (_, failed) = m3(mapOf("oc.notify.version" to l(1), "oc.pendingPromptPhoto" to s(photoRecord())), error)
        assertTrue((failed["oc.pendingPromptPhoto"] as RawPref.Str).value.endsWith("\"failed\":true}"))

        val missing = mapOf<String, RawPref>("flutter_image_picker_image_path" to RawPref.StrSet(linkedSetOf("/nowhere/x.jpg")))
        val (_, unchanged) = m3(mapOf("oc.notify.version" to l(1), "oc.pendingPromptPhoto" to s(photoRecord())), missing)
        assertEquals(s(photoRecord()), unchanged["oc.pendingPromptPhoto"])

        val photo = File(tmp.newFolder(), "y.jpg").apply { writeBytes(byteArrayOf(9)) }
        val found = mapOf<String, RawPref>("flutter_image_picker_image_path" to RawPref.StrSet(linkedSetOf(photo.path)))
        val withRef = photoRecord(ref = """{"sha256":"ab","bytes":1}""")
        val (_, done) = m3(mapOf("oc.notify.version" to l(1), "oc.pendingPromptPhoto" to s(withRef)), found)
        assertEquals(s(withRef), done["oc.pendingPromptPhoto"])
    }

    @Test
    fun `a refused commit blocks with WriteRefused and the file keeps its old state`() {
        val (result, after) = m3(mapOf("oc.budgets.a" to s("{}")), refuse = true)
        assertEquals("L1", assertIs<M3LegacyTransforms.Result.WriteRefused>(result).transform)
        assertEquals(s("{}"), after["oc.budgets.a"])
    }

    @Test
    fun `B-MIGR-010 the runner blocks with WriteRefused on a refused M3 commit and Try again completes`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this) }
        val runner = w.runner(StandardTestDispatcher(testScheduler))
        w.prefs.refuse = { changes -> changes.keys.any { it.startsWith("flutter.oc.budgets.") } }
        val blocked = assertIs<RunnerState.Blocked>(runner.run())
        assertEquals(BlockReason.WriteRefused, blocked.reason)
        assertEquals("M3", blocked.step)
        assertIs<RunnerState.Open>(w.runner(StandardTestDispatcher(testScheduler)).retry())
        assertNull(w.logical()["oc.budgets.${Seed.REMOTE}"])
    }
}
