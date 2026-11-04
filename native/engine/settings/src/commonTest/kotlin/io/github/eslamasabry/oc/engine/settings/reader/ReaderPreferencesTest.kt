package io.github.eslamasabry.oc.engine.settings.reader

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.engine.settings.prefs.GatedStore
import io.github.eslamasabry.oc.engine.settings.prefs.TestDisk
import io.github.eslamasabry.oc.engine.settings.prefs.storeOver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports test/reader_preferences_test.dart (B-STATE-069). */
class ReaderPreferencesTest {
    private object NoSecrets : SecretStore {
        override suspend fun read(name: String): Outcome<String?> = null.ok()
        override suspend fun write(name: String, value: String): Outcome<Unit> = Unit.ok()
        override suspend fun delete(name: String): Outcome<Unit> = Unit.ok()
        override suspend fun names(prefix: String): Outcome<Set<String>> = emptySet<String>().ok()
    }

    private val a = ProfileId("a")
    private val b = ProfileId("b")

    @Test
    fun `B-STATE-069 saved choices restore per profile and deletion removes their key`() = runTest {
        val kv = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val lanes = DefaultWriterLanes()
        val first = ReaderPreferencesStore(kv, lanes, a)
        assertNull(first.value.value.wrapCode)
        assertTrue(first.update(sourceFirst = true, wrapCode = false))
        val restarted = ReaderPreferencesStore(kv, lanes, a)
        assertTrue(restarted.value.value.sourceFirst)
        assertEquals(false, restarted.value.value.wrapCode)
        assertFalse(ReaderPreferencesStore(kv, lanes, b).value.value.sourceFirst)

        val scope = DefaultProfileScope(kv, NoSecrets, WriterLane("sweep"))
        ReaderPreferencesStore.register(scope)
        assertTrue(ReaderPreferencesStore.keyFor(a) in scope.scopedKeys(a))
        assertIs<Outcome.Ok<*>>(scope.sweep(a))
        val removed = ReaderPreferencesStore(kv, lanes, a).value.value
        assertFalse(removed.sourceFirst)
        assertNull(removed.wrapCode)
        assertEquals("oc.readerPreferences.a", ReaderPreferencesStore.keyFor(a))
    }

    @Test
    fun `B-STATE-069 a storage refusal keeps the prior display choices`() = runTest {
        val disk = TestDisk().apply { refuse = { _, _ -> true } }
        val store = ReaderPreferencesStore(storeOver(disk, StandardTestDispatcher(testScheduler)), DefaultWriterLanes(), a)
        assertFalse(store.update(sourceFirst = true, wrapCode = false))
        assertFalse(store.value.value.sourceFirst)
        assertNull(store.value.value.wrapCode)
    }

    @Test
    fun `B-STATE-069 profile deletion drains an accepted save before sweeping`() = runTest {
        val kv = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val gated = GatedStore(kv, inLane = true).apply { gate = CompletableDeferred() }
        val lanes = DefaultWriterLanes()
        val store = ReaderPreferencesStore(gated, lanes, a)
        val save = async { store.update(sourceFirst = true) }
        gated.entered.await()

        var deleted = false
        val deletion = async {
            lanes.closeProfile(a)
            lanes.drainProfile(a)
            DefaultProfileScope(kv, NoSecrets, WriterLane("sweep")).apply { ReaderPreferencesStore.register(this) }.sweep(a)
            deleted = true
        }
        runCurrent()
        assertFalse(deleted, "deletion waits for the accepted save")
        gated.gate!!.complete(Unit)
        assertTrue(save.await())
        deletion.await()
        assertTrue(deleted)
        assertFalse(ReaderPreferencesStore.read(kv, a).sourceFirst, "the swept key is gone, not resurrected")
        // A late save after deletion is refused.
        assertFalse(ReaderPreferencesStore(kv, lanes, a).update(wrapCode = true))
    }
}
