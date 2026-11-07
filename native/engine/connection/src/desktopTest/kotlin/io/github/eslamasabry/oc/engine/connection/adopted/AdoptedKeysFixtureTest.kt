package io.github.eslamasabry.oc.engine.connection.adopted

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E00-S03: each Flutter chat key, read in place from a 2.1.4-shaped dump (`prefs.json`, the format
 * `tools/flutter-fixtures/xml_to_prefs_json.py` writes for E00-S02). Server A holds values exactly as the Dart writers
 * left them; server B holds every key in a form Dart never wrote. When the seeded E00-S02 dump exists
 * (`e2e/fixtures/flutter-2.1.4/prefs.json`) it is checked too.
 */
class AdoptedKeysFixtureTest {
    private val a = ProfileId("1735000000000001")
    private val b = ProfileId("1735000000000002")
    private val acme = "/home/dev/code/acme"

    /** Records every commit: reading the adopted keys must never write. */
    private class Disk(initial: Map<String, RawPref>) : InMemoryPrefsBackend(initial) {
        val commits = mutableListOf<Map<String, RawPref?>>()

        override fun commit(changes: Map<String, RawPref?>): Boolean {
            commits += LinkedHashMap(changes)
            return super.commit(changes)
        }
    }

    private object NoSecrets : SecretStore {
        override suspend fun read(name: String): Outcome<String?> = Outcome.Ok(null)
        override suspend fun write(name: String, value: String): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun delete(name: String): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun names(prefix: String): Outcome<Set<String>> = Outcome.Ok(emptySet())
    }

    private fun storeOver(disk: Disk, io: CoroutineDispatcher) =
        PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), io)

    private fun fixture(): Map<String, RawPref> =
        prefsJson(checkNotNull(javaClass.getResource("/adopted/flutter-2.1.4-chat-keys.prefs.json")).readText())

    @Test
    fun `B-MIGR-005 oc_lastProject is read in place for the first paint`() = runTest {
        val disk = Disk(fixture())
        val keys = AdoptedChatKeys(storeOver(disk, StandardTestDispatcher(testScheduler)))
        assertEquals(acme, keys.lastProject(a))
        assertEquals(acme, keys.startState(a).lastProject)
        assertTrue(disk.commits.isEmpty())
    }

    @Test
    fun `B-MIGR-005 oc_chatTail is read in place and painted at once`() = runTest {
        val disk = Disk(fixture())
        val keys = AdoptedChatKeys(storeOver(disk, StandardTestDispatcher(testScheduler)))
        val tail = assertIs<ChatTail>(keys.startState(a).chatTail)
        assertEquals(acme, tail.scope)
        assertEquals("ses_tail1", tail.session)
        assertEquals(1762500000000, tail.fetchedAtMs)
        assertEquals(listOf("user", "assistant"), tail.rows.map { it.role })
        assertEquals("Fix the login bug", tail.rows[0].text)
        assertEquals("تم إصلاح الخطأ.\nLine two — done 🚀", tail.rows[1].text)
        assertEquals(tail, keys.chatTail(a, acme, "ses_tail1"))
        assertNull(keys.chatTail(a, acme, "ses_other"), "a tail of another conversation is not shown")
        assertNull(keys.chatTail(a, "/elsewhere", "ses_tail1"))
        assertTrue(disk.commits.isEmpty())
    }

    @Test
    fun `B-MIGR-005 oc_chatNoticesSeen keeps dismissed notes dismissed`() = runTest {
        val disk = Disk(fixture())
        val keys = AdoptedChatKeys(storeOver(disk, StandardTestDispatcher(testScheduler)))
        assertTrue(keys.noticeSeen(a, AdoptedChatKeys.AUTO_APPROVE_NOTICE, "ses_tail1"))
        assertTrue(keys.noticeSeen(a, AdoptedChatKeys.AUTO_APPROVE_NOTICE, "ses_old"))
        assertFalse(keys.noticeSeen(a, AdoptedChatKeys.AUTO_APPROVE_NOTICE, "ses_new"))
        assertTrue(disk.commits.isEmpty())
    }

    @Test
    fun `B-MIGR-005 oc_withdrawnPrompts keeps withdrawn copies out per conversation`() = runTest {
        val disk = Disk(fixture())
        val keys = AdoptedChatKeys(storeOver(disk, StandardTestDispatcher(testScheduler)))
        assertEquals(setOf("msg_w1", "msg_w3"), keys.withdrawnPrompts(a, "ses_tail1"))
        assertEquals(setOf("msg_w2"), keys.withdrawnPrompts(a, "ses_other"))
        assertEquals(emptySet(), keys.withdrawnPrompts(a, "ses_none"))
        assertTrue(disk.commits.isEmpty())
    }

    @Test
    fun `B-MIGR-005 oc_providerRuntime keys are read per location`() = runTest {
        val disk = Disk(fixture())
        val keys = AdoptedChatKeys(storeOver(disk, StandardTestDispatcher(testScheduler)))
        assertTrue(keys.providerRuntimeRefreshed(a))
        assertTrue(keys.providerRuntimeRefreshed(a, directory = acme))
        assertFalse(keys.providerRuntimeRefreshed(a, directory = "/home/dev/code/other"))
        assertFalse(keys.providerRuntimeRefreshed(a, directory = acme, workspace = "wrk_1"))
        assertEquals("anthropic,openai", keys.providerRuntimeUnloadable(a, directory = acme))
        assertNull(keys.providerRuntimeUnloadable(a))
        assertTrue(disk.commits.isEmpty())
    }

    @Test
    fun `B-MIGR-005 garbled values are ignored and never rewritten`() = runTest {
        val raw = fixture()
        val disk = Disk(raw)
        val keys = AdoptedChatKeys(storeOver(disk, StandardTestDispatcher(testScheduler)))
        assertNull(keys.lastProject(b), "a bool where a path belongs")
        assertNull(keys.chatTail(b), "a tail of an unknown version")
        assertEquals(AdoptedStartState(null, null), keys.startState(b))
        assertFalse(keys.noticeSeen(b, AdoptedChatKeys.AUTO_APPROVE_NOTICE, "ses_b"), "a broken JSON list")
        assertEquals(emptySet(), keys.withdrawnPrompts(b, "ses_b"), "a plain string where a list belongs")
        assertFalse(keys.providerRuntimeRefreshed(b), "a string where a bool belongs")
        assertNull(keys.providerRuntimeUnloadable(b), "a number where the id list belongs")
        assertTrue(disk.commits.isEmpty(), "nothing was written")
        assertEquals(raw, disk.persisted(), "every value is still on disk exactly as it was")
    }

    @Test
    fun `B-MIGR-007 ProfileScope still sweeps every adopted chat key with its server`() = runTest {
        val disk = Disk(fixture())
        val store = storeOver(disk, StandardTestDispatcher(testScheduler))
        val lanes = DefaultWriterLanes()
        val scope = DefaultProfileScope(store, NoSecrets, lanes.app)
        AdoptedChatKeys.SHAPES.forEach(scope::register)
        val adopted = store.keys().filter { k -> AdoptedChatKeys.SHAPES.any { k.startsWith(it.prefix) } }
        val adoptedA = adopted.filter { a.raw in it }.toSet()
        val adoptedB = adopted.filter { b.raw in it }.toSet()
        assertEquals(7, adoptedA.size, adoptedA.toString())
        assertEquals(6, adoptedB.size, adoptedB.toString())
        assertTrue(scope.scopedKeys(a).containsAll(adoptedA))

        lanes.closeProfile(a)
        lanes.drainProfile(a)
        assertIs<Outcome.Ok<*>>(scope.sweep(a))

        val after = store.keys()
        assertTrue(after.none { it in adoptedA }, "every chat key of the deleted server is gone")
        assertTrue(after.containsAll(adoptedB), "the other server keeps its keys")
        assertTrue("oc.profiles" in after && "oc.offlineQueue" in after, "app-wide keys stay")
        val keys = AdoptedChatKeys(store)
        assertEquals(AdoptedStartState(null, null), keys.startState(a))
        assertEquals(emptySet(), keys.withdrawnPrompts(a, "ses_tail1"))
    }

    @Test
    fun `B-MIGR-005 the seeded E00-S02 dump reads in place when it exists`() = runTest {
        val dump = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "native/e2e/fixtures/flutter-2.1.4/prefs.json") }
            .firstOrNull { it.isFile } ?: return@runTest // Not seeded yet (tools/flutter-fixtures/README.md).
        val raw = prefsJson(dump.readText())
        val disk = Disk(raw)
        val store = storeOver(disk, StandardTestDispatcher(testScheduler))
        val keys = AdoptedChatKeys(store)
        for (key in store.keys(AdoptedChatKeys.CHAT_TAIL_PREFIX)) {
            val profile = ProfileId(key.removePrefix(AdoptedChatKeys.CHAT_TAIL_PREFIX))
            assertIs<ChatTail>(keys.chatTail(profile), "$key: a tail Dart wrote reads back")
        }
        for (key in store.keys(AdoptedChatKeys.LAST_PROJECT_PREFIX)) {
            val profile = ProfileId(key.removePrefix(AdoptedChatKeys.LAST_PROJECT_PREFIX))
            assertTrue(keys.lastProject(profile) != null, "$key: the last project Dart saved reads back")
        }
        assertTrue(disk.commits.isEmpty())
        assertEquals(raw, disk.persisted())
    }

    /** `prefs.json` (physical key → {type, value}) as Android's loader would hand it over. */
    private fun prefsJson(text: String): Map<String, RawPref> {
        val root = DartJson.decode(text) as Map<*, *>
        return root.entries.associate { (k, v) ->
            val entry = v as Map<*, *>
            val value = entry["value"]
            k as String to when (entry["type"]) {
                "string" -> RawPref.Str(value as String)
                "boolean" -> RawPref.Bool(value as Boolean)
                "int" -> RawPref.Int32((value as Long).toInt())
                "long" -> RawPref.Int64(value as Long)
                "float" -> RawPref.F32((value as Number).toFloat())
                "set" -> RawPref.StrSet((value as List<*>).map { it as String }.toSet())
                else -> error("unknown entry type")
            }
        }
    }
}
