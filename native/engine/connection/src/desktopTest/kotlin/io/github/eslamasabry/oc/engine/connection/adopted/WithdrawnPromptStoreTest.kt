package io.github.eslamasabry.oc.engine.connection.adopted

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The stopped-and-resent copy stays hidden after a restart, is bounded, and goes with its conversation and server. */
class WithdrawnPromptStoreTest {
    private val a = ProfileId("1735000000000001")
    private val b = ProfileId("1735000000000002")

    private fun open(disk: InMemoryPrefsBackend, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), StandardTestDispatcher(scheduler))

    @Test
    fun `it survives a restart`() = runTest {
        val disk = InMemoryPrefsBackend(emptyMap())
        val first = WithdrawnPromptStore(open(disk, testScheduler), DefaultWriterLanes())
        assertIs<Outcome.Ok<*>>(first.remember(a, "ses_1", "msg_1"))
        first.remember(a, "ses_2", "msg_9")
        val second = WithdrawnPromptStore(open(disk, testScheduler), DefaultWriterLanes())
        assertEquals(setOf("msg_1"), second.read(a, "ses_1"))
        assertEquals(setOf("msg_9"), second.read(a, "ses_2"))
        assertTrue(second.read(b, "ses_1").isEmpty())
    }

    @Test
    fun `it is bounded to the newest entries`() = runTest {
        val store = WithdrawnPromptStore(open(InMemoryPrefsBackend(emptyMap()), testScheduler), DefaultWriterLanes())
        repeat(WithdrawnPromptStore.MAX_ENTRIES + 20) { store.remember(a, "ses", "m$it") }
        val kept = store.read(a, "ses")
        assertEquals(WithdrawnPromptStore.MAX_ENTRIES, kept.size)
        assertTrue("m0" !in kept && "m${WithdrawnPromptStore.MAX_ENTRIES + 19}" in kept)
    }

    @Test
    fun `deleting a conversation clears only its entries`() = runTest {
        val store = WithdrawnPromptStore(open(InMemoryPrefsBackend(emptyMap()), testScheduler), DefaultWriterLanes())
        store.remember(a, "ses_1", "m1")
        store.remember(a, "ses_2", "m2")
        store.forget(a, "ses_1")
        assertTrue(store.read(a, "ses_1").isEmpty())
        assertEquals(setOf("m2"), store.read(a, "ses_2"))
    }

    @Test
    fun `the profile sweep owns the key`() = runTest {
        val key = AdoptedChatKeys.withdrawnPromptsKey(a)
        assertEquals(setOf(key), ProfileKeyRule.scopedKeys(a.raw, setOf(key, AdoptedChatKeys.withdrawnPromptsKey(b)), setOf(b.raw)))
        val shape = AdoptedChatKeys.SHAPES.single { it.prefix == AdoptedChatKeys.WITHDRAWN_PROMPTS_PREFIX }
        assertEquals(KeyScope.Profile, shape.scope)
        assertEquals(PrefType.StrList, shape.type)
    }
}
