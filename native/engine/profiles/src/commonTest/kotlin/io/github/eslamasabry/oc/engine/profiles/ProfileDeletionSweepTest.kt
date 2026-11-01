package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Plan 16 §1.4 `ProfileDeletionSweepTest` (SI-5, Astra M1): seeds every registered shape for profiles A and B in the
 * live prefs, the live secret store, the live Flutter-era secure-storage file and every retained copy (S0,
 * `replaced-*`, `start-fresh-*`, each with its `.bak` twin, and `v2-sources/`), with A's embedded records; deletes A;
 * then greps the bytes of every store for A's canary prompt text, address and id. B is unchanged.
 */
class ProfileDeletionSweepTest {
    private val a = id("1735000000000001")
    private val b = id("1735000000000002")
    private val canary = "canary-prompt-7f3a"
    private val addressA = "https://alpha-canary.example:4096"

    /** Shapes other engines register (their real registrations join through the Metro multibinding later). */
    private val otherEngines = listOf(
        KeyShape("oc.model.", KeyScope.Profile, PrefType.Str, ":engine:connection", "model"),
        KeyShape("oc.location.", KeyScope.Profile, PrefType.Str, ":engine:connection", "location"),
        KeyShape("oc.widgetTarget.", KeyScope.Profile, PrefType.Str, ":android:widgets", "widget target"),
        KeyShape("oc.promptStash.", KeyScope.Profile, PrefType.Str, ":engine:composer", "stash item"),
        KeyShape("oc.draft.", KeyScope.Profile, PrefType.Str, ":engine:composer", "draft, owner last"),
        KeyShape("oc.genui.covered.", KeyScope.Profile, PrefType.Str, ":engine:genui", "genui"),
        KeyShape("oc.providerRuntimeRefresh.v1.", KeyScope.Profile, PrefType.Bool, ":engine:connection", "refresh"),
        KeyShape("oc.automation.", KeyScope.Profile, PrefType.Str, ":engine:settings", "restore authority"),
        KeyShape("oc.scheduleDeleted.", KeyScope.Profile, PrefType.Bool, ":engine:schedule", "tombstone"),
    )

    /** One key of [shape] owned by [owner], following the sweep rule's owner position for that namespace. */
    private fun keyFor(shape: KeyShape, owner: String): String = when (shape.prefix) {
        "oc.promptStash.", "oc.orchestration." -> "${shape.prefix}$owner.item"
        "oc.providerRuntimeRefresh.v1." -> "${shape.prefix}$owner.%3Cdefault%3E"
        "oc.draft." -> "${shape.prefix}note.ses_1.$owner"
        else -> shape.prefix + owner
    }

    private fun valueFor(shape: KeyShape, owner: String): Any = when (shape.type) {
        PrefType.Bool -> true
        PrefType.Int64 -> 7L
        PrefType.Dbl -> 1.5
        PrefType.StrList, PrefType.Str -> "value-of-$owner"
    }

    private fun prefsFor(h: Harness): Map<String, PrefValue> = h.kv.keys().associateWith { h.kv.get(it)!! }

    private fun embedded(): List<Pair<String, Any>> = listOf(
        "oc.profiles" to json(
            listOf(
                mapOf("id" to a.raw, "name" to "Alpha", "baseUrl" to addressA, "flavor" to "v2"),
                mapOf("id" to b.raw, "name" to "Beta", "baseUrl" to "https://beta.example:4096", "flavor" to "v2"),
            ),
        ),
        "oc.activeProfile" to a.raw,
        "oc.offlineQueue" to json(
            listOf(
                mapOf("id" to "q1", "profileID" to a.raw, "sessionID" to "s", "text" to canary, "createdAt" to 1),
                mapOf("id" to "q2", "profileID" to b.raw, "sessionID" to "s", "text" to "beta prompt", "createdAt" to 2),
            ),
        ),
        "oc.sessionDrafts" to json(
            listOf(
                mapOf("sessionID" to "s1", "profileID" to a.raw, "text" to canary),
                mapOf("sessionID" to "s2", "profileID" to "", "text" to "$canary unattributed"),
                mapOf("sessionID" to "s3", "profileID" to b.raw, "text" to "beta draft"),
            ),
        ),
        "oc.pendingPromptPhoto" to json(mapOf("id" to "p", "profileID" to a.raw, "caption" to canary)),
        "oc.widgetSessions" to json(mapOf("profileID" to a.raw, "sessions" to listOf(mapOf("title" to canary)))),
        "oc.builtinServerOwner" to a.raw,
        "oc.appearance" to "dark",
    )

    private suspend fun TestScope.seeded(): Pair<Harness, List<MapCopy>> {
        val h = Harness(StandardTestDispatcher(testScheduler))
        otherEngines.forEach(h.scope::register)
        // Recovery journals are consumed on load; their interrupted states have dedicated B-STATE-098 tests.
        val shapes = h.scope.shapes().filter { (it.scope == KeyScope.Profile || it.scope == KeyScope.Owner) && it.prefix != ProfileKeys.TRANSACTION }
        // A new registration without a seed fails here: every registered per-profile shape is seeded for A and B.
        val perProfile = shapes.flatMap { s -> listOf(keyFor(s, a.raw) to valueFor(s, a.raw), keyFor(s, b.raw) to valueFor(s, b.raw)) }
        h.seed(*(perProfile + embedded()).toTypedArray())
        for (prefix in ProfileKeys.SECRET_PREFIXES.keys) {
            val suffix = if (prefix == ProfileKeys.ORCHESTRATION_SECRET) ".grant" else ""
            h.secrets.values[prefix + a.raw + suffix] = "secret-a-$prefix"
            h.secrets.values[prefix + b.raw + suffix] = "secret-b-$prefix"
        }
        check(h.store.load() is Outcome.Ok)
        // Every registered shape really is in A's sweep set (the shape table and the sweep rule agree).
        val scoped = h.scope.scopedKeys(a)
        for (s in shapes) assertTrue(keyFor(s, a.raw) in scoped, "shape ${s.prefix} is not swept")

        val livePrefs = prefsFor(h)
        val liveFss = h.secrets.values.mapValues { PrefValue.Str("fss-cipher-of-" + it.value) }
        val copies = buildList {
            for (dir in listOf("flutter-v57", "replaced-20261101", "start-fresh-20261101")) {
                for (twin in listOf("", ".bak")) {
                    add(MapCopy("$dir/FlutterSharedPreferences.xml$twin", RetainedCopyKind.Prefs, livePrefs))
                    add(MapCopy("$dir/FlutterSecureStorage.xml$twin", RetainedCopyKind.SecureStorage, liveFss))
                }
            }
            add(MapCopy("v2-sources/prefs.json", RetainedCopyKind.Prefs, livePrefs))
            add(MapCopy("live/FlutterSecureStorage.xml", RetainedCopyKind.SecureStorage, liveFss))
        }
        return h to copies
    }

    @Test
    fun `B-SEC-201 nothing of a deleted server remains in live data or any retained copy, and the other server is whole`() = runTest {
        val (h, copies) = seeded()
        val bBefore = h.scope.scopedKeys(b).associateWith { h.kv.get(it) }
        val bCopiesBefore = copies.associate { it.label to it.entries.filterKeys { k -> b.raw in k } }
        val sweep = FlutterEraSweep(RetainedCopies { copies.ok() })

        val result = h.deletion(cleaners = h.embeddedCleaners() + sweep).delete(a, DeletionOptions(confirmedQueue = h.confirmedQueue(a)))

        val done = assertIs<Outcome.Ok<DeleteProfileResult>>(result).value
        assertTrue(done.complete, done.failures.toString())
        assertEquals(copies.size, done.removedByOwner[FlutterEraSweep.OWNER])

        val stores = listOf("live prefs" to h.persistedText(), "live secrets" to h.secrets.values.entries.joinToString("\n")) +
            copies.map { it.label to it.bytes() }
        for ((label, bytes) in stores) {
            for (needle in listOf(canary, addressA, a.raw)) assertFalse(needle in bytes, "$label still holds $needle")
        }
        // B is unchanged everywhere.
        assertEquals(bBefore, h.scope.scopedKeys(b).associateWith { h.kv.get(it) })
        for (copy in copies) assertEquals(bCopiesBefore.getValue(copy.label), copy.entries.filterKeys { b.raw in it }, copy.label)
        for (prefix in ProfileKeys.SECRET_PREFIXES.keys) {
            assertTrue(h.secrets.values.keys.any { it.startsWith(prefix + b.raw) }, prefix)
        }
        val liveCopy = copies.first { it.kind == RetainedCopyKind.Prefs }.bytes()
        assertTrue("beta prompt" in liveCopy && "beta draft" in liveCopy && "https://beta.example:4096" in liveCopy)
        assertTrue("oc.appearance=dark" in h.persistedText())
    }

    @Test
    fun `B-SEC-201 a retained copy that refuses keeps the server and a retry finishes`() = runTest {
        val (h, copies) = seeded()
        copies[3].refuse = true
        val sweep = FlutterEraSweep(RetainedCopies { copies.ok() })

        val kept = assertIs<Outcome.Ok<DeleteProfileResult>>(h.deletion(cleaners = h.embeddedCleaners() + sweep).delete(a, DeletionOptions(confirmedQueue = h.confirmedQueue(a)))).value
        assertFalse(kept.removedProfile)
        assertEquals(listOf(FlutterEraSweep.OWNER), kept.failedOwners)
        assertTrue(h.store.profiles.value.any { it.id == a })
        assertTrue(a.raw in copies[3].bytes())
        // Every other copy was still tried.
        assertTrue(copies.filterIndexed { i, _ -> i != 3 }.all { a.raw !in it.bytes() })

        copies[3].refuse = false
        val done = assertIs<Outcome.Ok<DeleteProfileResult>>(h.deletion(cleaners = h.embeddedCleaners() + sweep).delete(a, DeletionOptions(confirmedQueue = h.confirmedQueue(a)))).value
        assertTrue(done.complete)
        assertTrue(copies.all { a.raw !in it.bytes() })
    }

    @Test
    fun `B-SEC-201 kept queued prompts survive in a copy only when the person chose Keep`() {
        val edit = ProfileStructuralEdit()
        val base = mapOf(
            "oc.offlineQueue" to PrefValue.Str(json(listOf(mapOf("id" to "q1", "profileID" to a.raw, "text" to canary)))),
        )
        val dropped = LinkedHashMap<String, PrefValue>(base).also { edit.applyPrefs(it, a, StructuralPlan(false)) }
        assertFalse(canary in dropped.values.joinToString { (it as PrefValue.Str).value })
        val kept = LinkedHashMap<String, PrefValue>(base).also { edit.applyPrefs(it, a, StructuralPlan(true)) }
        assertTrue(canary in (kept["oc.keptQueuedPrompts"] as PrefValue.Str).value)
        assertFalse(canary in (kept["oc.offlineQueue"] as PrefValue.Str).value)
        // An unreadable embedded value in a copy is removed whole.
        val broken = linkedMapOf<String, PrefValue>("oc.sessionDrafts" to PrefValue.Str("{not json $canary"))
        val receipt = edit.applyPrefs(broken, a, StructuralPlan())
        assertEquals(setOf("oc.sessionDrafts"), receipt.unreadableKeys)
        assertTrue(broken.isEmpty())
    }
}
