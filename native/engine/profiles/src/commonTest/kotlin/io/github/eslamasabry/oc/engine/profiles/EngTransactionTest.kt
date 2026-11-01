package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EngTransactionTest {
    private val profile = StoredProfile(id("workstation"), "Workstation", "https://server.example")
    private class ProcessStopped : Error()

    @Test
    fun `B-STATE-063 eng-2 deletion drains a passwordless profile save before enumerating secrets`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        assertIs<Outcome.Ok<*>>(h.store.upsert(profile))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        h.secrets.beforeWrite = { name -> if (name == profile.secretName) { entered.complete(Unit); release.await() } }
        val saving = async { h.store.upsert(profile, SecretEdit.Set("synthetic-canary")) }
        entered.await()
        val deletion = async { h.deletion().delete(profile.id) }
        runCurrent()
        assertFalse(deletion.isCompleted)
        release.complete(Unit)
        saving.await()
        val result = assertIs<Outcome.Ok<DeleteProfileResult>>(deletion.await()).value
        assertTrue(result.complete)
        assertFalse(profile.secretName in h.secrets.values)
    }

    @Test
    fun `B-STATE-098 eng-3 cancellation finishes an admitted profile save`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        h.secrets.beforeWrite = { name -> if (name == profile.secretName) { entered.complete(Unit); release.await() } }
        val saving = async { h.store.upsert(profile, SecretEdit.Set("synthetic-canary")) }
        entered.await()
        saving.cancel()
        release.complete(Unit)
        saving.join()
        assertTrue(profile.secretName in h.secrets.values)
        assertNotNull(h.store.profile(profile.id))
    }

    @Test
    fun `B-STATE-098 eng-3 cancellation completes the admitted row and secret removal`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        h.store.upsert(profile, SecretEdit.Set("synthetic-canary"))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        h.secrets.beforeDelete = { name -> if (name == profile.secretName) { entered.complete(Unit); release.await() } }
        val deletion = async { h.deletion().delete(profile.id) }
        entered.await()
        deletion.cancel()
        release.complete(Unit)
        deletion.join()
        assertFalse(profile.secretName in h.secrets.values)
        assertNull(h.store.profile(profile.id))
    }

    @Test
    fun `B-STATE-098 eng-3 restart repairs a row removed before secret deletion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.store.upsert(profile, SecretEdit.Set("synthetic-canary"))
        h.secrets.beforeDelete = { name -> if (name == profile.secretName) throw ProcessStopped() }
        try { h.deletion().delete(profile.id); fail("expected simulated process stop") } catch (_: ProcessStopped) { }
        h.secrets.beforeDelete = null
        val kv = PrefsKeyValueStore(InMemoryPrefsBackend(h.backend.persisted()), FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), dispatcher)
        val restarted = ProfileStore(kv, h.secrets, DefaultWriterLanes(), Redactor())
        assertIs<Outcome.Ok<*>>(restarted.load())
        assertFalse(profile.secretName in h.secrets.values)
        assertNull(restarted.profile(profile.id))
    }

    @Test
    fun `B-STATE-098 eng-3 refused deletion completion keeps the row across restart`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.store.upsert(profile, SecretEdit.Set("original-canary"))
        h.secrets.beforeDelete = { name ->
            if (name == profile.secretName) h.backend.refuse += ProfileKeys.TRANSACTION + profile.id.raw
        }
        val result = assertIs<Outcome.Ok<DeleteProfileResult>>(h.deletion().delete(profile.id)).value
        assertFalse(result.removedProfile)
        assertTrue(h.string(ProfileKeys.PROFILES)?.contains(profile.id.raw) == true)
        h.secrets.beforeDelete = null
        h.backend.refuse.clear()
        val kv = PrefsKeyValueStore(InMemoryPrefsBackend(h.backend.persisted()), FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), dispatcher)
        val restarted = ProfileStore(kv, h.secrets, DefaultWriterLanes(), Redactor())
        assertIs<Outcome.Ok<*>>(restarted.load())
        assertNotNull(restarted.profile(profile.id))
    }

    @Test
    fun `B-STATE-098 eng-3 sign-in reset cannot restore an interrupted save backup`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.store.upsert(profile, SecretEdit.Set("original-canary"))
        h.secrets.afterWrite = { name -> if (name == profile.secretName) throw ProcessStopped() }
        try { h.store.upsert(profile.copy(name = "Changed"), SecretEdit.Set("replacement-canary")); fail("expected process stop") } catch (_: ProcessStopped) { }
        h.secrets.afterWrite = null
        assertIs<Outcome.Ok<Unit>>(SignInReset(h.store, h.kv, h.secrets, h.lanes, h.redactor).reset())
        val kv = PrefsKeyValueStore(InMemoryPrefsBackend(h.backend.persisted()), FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), dispatcher)
        val restarted = ProfileStore(kv, h.secrets, DefaultWriterLanes(), Redactor())
        assertIs<Outcome.Ok<*>>(restarted.load())
        assertFalse(profile.secretName in h.secrets.values)
        assertTrue(h.secrets.values.isEmpty())
    }

    @Test
    fun `B-STATE-098 eng-3 restart rolls back an interrupted save without losing the old sign-in`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.store.upsert(profile, SecretEdit.Set("original-canary"))
        h.secrets.afterWrite = { name -> if (name == profile.secretName) throw ProcessStopped() }
        try { h.store.upsert(profile.copy(name = "Changed"), SecretEdit.Set("replacement-canary")); fail("expected process stop") } catch (_: ProcessStopped) { }
        h.secrets.afterWrite = null
        val kv = PrefsKeyValueStore(InMemoryPrefsBackend(h.backend.persisted()), FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), dispatcher)
        val restarted = ProfileStore(kv, h.secrets, DefaultWriterLanes(), Redactor())
        assertIs<Outcome.Ok<*>>(restarted.load())
        assertEquals(profile.name, restarted.profile(profile.id)?.profile?.name)
        assertEquals("original-canary", h.secrets.values[profile.secretName])
    }
}
