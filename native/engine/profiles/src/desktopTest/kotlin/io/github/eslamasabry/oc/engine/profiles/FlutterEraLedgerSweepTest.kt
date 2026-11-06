package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.UndecodableKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * E00-S08: the Flutter-era sweep over EVERY per-server key shape of the frozen Flutter ledger (the one copy in
 * `:core:data`'s desktop test resources, read in place), not only the shapes registered today. A Flutter-era copy holds
 * whatever the Flutter app wrote, so a shape no native engine registers yet must still leave with the server.
 */
class FlutterEraLedgerSweepTest {
    private val opaqueCipher = PrefValue.Undecodable(UndecodableKind.UnknownType)
    private val doomed = id("1735000000000001")
    private val keeper = id("1735000000000002")

    @Suppress("UNCHECKED_CAST")
    private val ledger: Map<String, Map<String, Any?>> by lazy {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "native/settings.gradle.kts").isFile }
        val file = File(root, "native/core/data/src/desktopTest/resources/settings_preferences_ledger.json")
        DartJson.decode(file.readText()) as Map<String, Map<String, Any?>>
    }

    /** Same owner positions as `ProfileSweepShapesTest` (`:core:data`) and `ProfileKeyRule`. */
    private fun instance(prefix: String, owner: String): String = when (prefix) {
        "oc.draft." -> "${prefix}note.ses_1.$owner"
        "oc.promptStash.", "oc.orchestration.", "oc.providerRuntimeUnloadable." -> "$prefix$owner.item"
        "oc.providerRuntimeRefresh." -> "${prefix}v1.$owner.%3Cdefault%3E"
        else -> if (prefix.endsWith('.')) "$prefix$owner" else "$prefix.$owner"
    }

    /** The secret names flutter_secure_storage holds per server (Flutter `ProfileStore.remove`'s list). */
    private val secretPrefixes = ProfileKeys.SECRET_PREFIXES.keys

    private fun secretName(prefix: String, owner: String): String =
        if (prefix == ProfileKeys.ORCHESTRATION_SECRET) "$prefix$owner.grant" else prefix + owner

    @Test
    fun `B-SEC-201 every per-server ledger shape and secret of the deleted server leaves each Flutter-era copy, the other server stays whole`() = runTest {
        val profileRows = ledger.filterValues { it["scope"] == "profile" }.keys
        assertEquals(107, profileRows.size, "the Flutter ledger's profile rows")
        // App-wide keys that hold embedded per-server records have their own edits (and JSON values): not seeded here.
        val embedded = FlutterEraEdits.forCopies.flatMap { it.keys }.toSet()
        val appWide = ledger.filterValues { it["scope"] == "global" }.keys.filterNot { it.endsWith('.') || it in embedded }

        val prefs = LinkedHashMap<String, PrefValue>()
        for (row in profileRows) {
            prefs[instance(row, doomed.raw)] = PrefValue.Str("value-of-doomed")
            prefs[instance(row, keeper.raw)] = PrefValue.Str("value-of-keeper")
        }
        appWide.forEach { prefs[it] = PrefValue.Str("app-wide") }
        prefs[ProfileKeys.PROFILES] = PrefValue.Str(
            json(listOf(mapOf("id" to doomed.raw, "name" to "Doomed"), mapOf("id" to keeper.raw, "name" to "Keeper"))),
        )
        val secrets = LinkedHashMap<String, PrefValue>()
        for (prefix in secretPrefixes) {
            secrets[secretName(prefix, doomed.raw)] = opaqueCipher
            secrets[secretName(prefix, keeper.raw)] = opaqueCipher
        }
        // The acceptance's three by name, so a change to SECRET_PREFIXES cannot drop one silently.
        for (name in listOf("pw.", "oc.codexToken.", "oc.githubToken.")) assertTrue(name in secretPrefixes, name)

        val copies = listOf(
            MapCopy("shared_prefs/FlutterSecureStorage.xml", RetainedCopyKind.SecureStorage, secrets),
            MapCopy("files/migration/flutter-v57/FlutterSecureStorage.xml", RetainedCopyKind.SecureStorage, secrets),
            MapCopy("files/migration/flutter-v57/FlutterSharedPreferences.xml", RetainedCopyKind.Prefs, prefs),
            MapCopy("files/migration/replaced-1/FlutterSharedPreferences.xml", RetainedCopyKind.Prefs, prefs),
        )

        val removed = FlutterEraSweep(RetainedCopies { copies.ok() }).clear(doomed, DeletionOptions())

        assertEquals(copies.size, assertIs<Outcome.Ok<Int>>(removed).value)
        for (copy in copies) {
            val left = copy.entries.keys.filter { doomed.raw in it }
            assertTrue(left.isEmpty(), "${copy.label} still holds $left")
            val source = if (copy.kind == RetainedCopyKind.Prefs) prefs else secrets
            val keeperBefore = source.filterKeys { keeper.raw in it && it != ProfileKeys.PROFILES }
            assertEquals(keeperBefore, copy.entries.filterKeys { keeper.raw in it && it != ProfileKeys.PROFILES }, copy.label)
        }
        val prefsCopy = copies.first { it.kind == RetainedCopyKind.Prefs }
        for (key in appWide) assertEquals(PrefValue.Str("app-wide"), prefsCopy.entries[key], key)
        val rows = (prefsCopy.entries[ProfileKeys.PROFILES] as PrefValue.Str).value
        assertTrue(keeper.raw in rows && doomed.raw !in rows)
    }
}
