package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of Flutter `settings_preferences_coverage_test.dart` "the sweep takes every per-server key shape of the ledger"
 * over the Flutter ledger (`test/fixtures/coverage/settings_preferences_ledger.json`, 188 entries) until the native
 * ledger (`quality/src/test/resources/prefs-ledger.json`, plan 05 §3.6) exists.
 */
class ProfileSweepShapesTest {
    @Suppress("UNCHECKED_CAST")
    private val ledger: Map<String, Map<String, Any?>> =
        DartJson.decode(Fixtures.flutterLedger.readText()) as Map<String, Map<String, Any?>>

    /**
     * The key a profile `id` really gets for a ledger family. The Dart test seeds `<prefix><id>` for every family, but
     * five families put the owner elsewhere (see [ProfileKeyRule]); seeding them that way would not exercise the rule.
     */
    private fun instance(prefix: String, id: String): String =
        when (prefix) {
            "oc.draft." -> "${prefix}note.ses_1.$id"
            "oc.promptStash.", "oc.orchestration.", "oc.providerRuntimeUnloadable." -> "$prefix$id.item"
            "oc.providerRuntimeRefresh." -> "${prefix}v1.$id.%3Cdefault%3E"
            else -> if (prefix.endsWith('.')) "$prefix$id" else "$prefix.$id"
        }

    @Test
    fun `B-STATE-060 the sweep takes every per-server key shape of the ledger and nothing of another server`() {
        val profileShapes = ledger.filterValues { it["scope"] == "profile" }.keys
        assertEquals(107, profileShapes.size, "the Flutter ledger's profile rows")
        val doomed = profileShapes.map { instance(it, "doomed") }
        val keeper = profileShapes.map { instance(it, "keeper") }
        val appWide = ledger.filterValues { it["scope"] == "global" }.keys.filterNot { it.endsWith('.') }
        val all = doomed + keeper + appWide + "oc.appearance"

        val swept = ProfileKeyRule.scopedKeys("doomed", all, setOf("keeper"))
        assertEquals(doomed.toSet(), swept, "missed: ${doomed - swept}, extra: ${swept - doomed.toSet()}")
        assertTrue(swept.none { "keeper" in it })
    }

    @Test
    fun `B-SEC-039 every ledger row is classified with a reason and no app-wide key is ever swept`() {
        for ((key, row) in ledger) {
            assertTrue(row["scope"] in setOf("global", "profile", "shared", "other"), key)
            assertTrue((row["why"] as String).isNotEmpty(), key)
        }
        val appWide = ledger.filterValues { it["scope"] == "global" }.keys
        assertTrue(appWide.none { it.endsWith('.') }, "an app-wide key has no id")
        // Never taken, whichever id is swept, including ids spelled like the keys' own segments.
        val segments = appWide.flatMap { it.split('.') }.filter { it.isNotEmpty() && it != "oc" }.toSet()
        for (id in segments + "1735000000000001") {
            val taken = ProfileKeyRule.scopedKeys(id, appWide, emptySet())
            assertTrue(taken.isEmpty(), "sweeping '$id' took app-wide $taken")
        }
    }
}
