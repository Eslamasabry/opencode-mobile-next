package io.github.eslamasabry.oc.core.data

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.double
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.GateState
import io.github.eslamasabry.oc.core.data.migration.MigrationNotices
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
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

@OptIn(MigrationOnly::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrefsKeyValueStoreTest {
    private val lane = WriterLane("app")

    @Test
    fun `B-STATE-004 a refused write keeps the old value visible, publishes nothing, and the next write works`() = runTest {
        val backend = FakeBackend(physical("oc.appearance" to RawPref.Str("dark")))
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        store.changes.test {
            backend.refuseNext = 1
            val refused = store.commit(lane) { putString("oc.appearance", "light") }
            assertIs<Failure.StorageRefused>((refused as Outcome.Failed).failure)
            assertEquals("dark", store.string("oc.appearance"))
            assertEquals(RawPref.Str("dark"), backend.memory["flutter.oc.appearance"], "platform memory restored")
            expectNoEvents()

            assertEquals(Outcome.Ok(Unit), store.commit(lane) { putString("oc.appearance", "light") })
            assertEquals("light", store.string("oc.appearance"))
            assertEquals(setOf("oc.appearance"), awaitItem())
        }
    }

    @Test
    fun `B-STATE-004 a refused multi-key write rolls every touched key back and reports failure`() = runTest {
        val backend = FakeBackend(physical("oc.a" to RawPref.Str("1"), "oc.b" to RawPref.Int64(2)))
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        backend.refuseNext = 1
        val result = store.commit(lane) {
            putString("oc.a", "x")
            putLong("oc.b", 9)
            putBool("oc.c", true)
        }
        assertIs<Outcome.Failed>(result)
        assertEquals("1", store.string("oc.a"))
        assertEquals(2L, store.long("oc.b"))
        assertNull(store.get("oc.c"))
        // The compensation commit wrote the previous values back, including "absent" for the new key.
        assertEquals(
            mapOf("flutter.oc.a" to RawPref.Str("1"), "flutter.oc.b" to RawPref.Int64(2), "flutter.oc.c" to null),
            backend.commits.last(),
        )
        assertEquals(physical("oc.a" to RawPref.Str("1"), "oc.b" to RawPref.Int64(2)), LinkedHashMap(backend.memory))
    }

    @Test
    fun `B-STATE-005 a write that throws restores the cached value and leaks nothing`() = runTest {
        val backend = FakeBackend(physical("oc.textScale" to RawPref.Str(FlutterPrefsCodec.DOUBLE_PREFIX + "1.0")))
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        backend.throwNext = 1
        val result = store.commit(lane) { putDouble("oc.textScale", 1.3) }
        val failure = (result as Outcome.Failed).failure
        assertIs<Failure.StorageRefused>(failure)
        assertFalse(failure.details!!.text.contains("secret-value-must-not-leak"), "only the error's class name")
        assertEquals(1.0, store.double("oc.textScale"))
        assertEquals(RawPref.Str(FlutterPrefsCodec.DOUBLE_PREFIX + "1.0"), backend.memory["flutter.oc.textScale"])
    }

    @Test
    fun `B-MIGR-005 native writes use the encodings the Flutter reader expects`() = runTest {
        val backend = FakeBackend()
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        store.commit(lane) {
            putLong("oc.notify.quietStart", 1320)
            putBool("oc.notify.requests", false)
            putStringList("oc.chatNoticesSeen.1", listOf("a\"b", "مرحبا"))
            putDouble("oc.textScale", 1.25)
            putString("oc.appearance", "light")
        }
        val disk = backend.persisted()
        assertEquals(RawPref.Int64(1320), disk["flutter.oc.notify.quietStart"])
        assertEquals(RawPref.Bool(false), disk["flutter.oc.notify.requests"])
        assertEquals(RawPref.Str(FlutterPrefsCodec.JSON_LIST_PREFIX + "[\"a\\\"b\",\"مرحبا\"]"), disk["flutter.oc.chatNoticesSeen.1"])
        assertEquals(RawPref.Str(FlutterPrefsCodec.DOUBLE_PREFIX + "1.25"), disk["flutter.oc.textScale"])
        assertEquals(RawPref.Str("light"), disk["flutter.oc.appearance"])
        // and they read back as the same logical values
        assertEquals(listOf("a\"b", "مرحبا"), store.stringList("oc.chatNoticesSeen.1"))
        assertEquals(1320L, store.long("oc.notify.quietStart"))
        assertEquals(false, store.bool("oc.notify.requests"))
    }

    @Test
    fun `B-MIGR-005 a string starting with a Flutter prefix is refused before anything is written`() = runTest {
        val backend = FakeBackend()
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        val result = store.commit(lane) {
            putString("oc.ok", "fine")
            putString("oc.draft.x.1", FlutterPrefsCodec.LIST_PREFIX + "typed by a person")
        }
        assertIs<Failure.StorageRefused>((result as Outcome.Failed).failure)
        assertTrue(backend.commits.isEmpty())
        assertNull(store.get("oc.ok"))
        assertIs<Outcome.Failed>(store.compareAndSet(lane, "oc.x", null, FlutterPrefsCodec.DOUBLE_PREFIX + "1"))
    }

    @Test
    fun `B-MIGR-005 writes wait for the migration gate, reads do not, and the migrator writes past it`() = runTest {
        val backend = FakeBackend(physical("oc.appearance" to RawPref.Str("dark")))
        val gate = StateMigrationGate(GateState.Running("M2"))
        val store = storeOver(backend, StandardTestDispatcher(testScheduler), gate)
        assertEquals("dark", store.string("oc.appearance"), "reads are never gated")

        val pending = async { store.commit(lane) { putString("oc.appearance", "light") } }
        runCurrent()
        assertFalse(pending.isCompleted)
        assertTrue(backend.commits.isEmpty())

        assertEquals(Outcome.Ok(Unit), store.migrationWriter().commit { putLong("oc.notify.version", 3) })
        assertEquals(3L, store.long("oc.notify.version"))

        gate.publish(GateState.Blocked(io.github.eslamasabry.oc.core.data.migration.BlockReason.WriteRefused))
        runCurrent()
        assertFalse(pending.isCompleted, "never writes while Blocked")

        gate.publish(GateState.Open(firstNativeLaunch = true, notices = MigrationNotices()))
        assertEquals(Outcome.Ok(Unit), pending.await())
        assertEquals("light", store.string("oc.appearance"))
    }

    @Test
    fun `B-STATE-004 compareAndSet commits only when the expected value matches`() = runTest {
        val backend = FakeBackend(physical("oc.scheduledPrompts" to RawPref.Str("v1"), "oc.n" to RawPref.Int64(1)))
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(false), store.compareAndSet(lane, "oc.scheduledPrompts", "v0", "v2"))
        assertEquals("v1", store.string("oc.scheduledPrompts"))
        assertEquals(Outcome.Ok(true), store.compareAndSet(lane, "oc.scheduledPrompts", "v1", "v2"))
        assertEquals("v2", store.string("oc.scheduledPrompts"))
        assertEquals(Outcome.Ok(true), store.compareAndSet(lane, "oc.new", null, "x"))
        assertEquals(Outcome.Ok(false), store.compareAndSet(lane, "oc.n", "1", "2"), "a Long never equals a string")
        assertEquals(Outcome.Ok(true), store.compareAndSet(lane, "oc.new", "x", null))
        assertNull(store.get("oc.new"))

        backend.refuseNext = 1
        assertIs<Outcome.Failed>(store.compareAndSet(lane, "oc.scheduledPrompts", "v2", "v3"))
        assertEquals("v2", store.string("oc.scheduledPrompts"))
    }

    @Test
    fun `B-MIGR-005 keys without the flutter prefix and null values are never exposed`() = runTest {
        val backend = FakeBackend(
            mapOf(
                "flutter.oc.a" to RawPref.Str("x"),
                "flutter.voice.language" to RawPref.Str("ar"),
                "flutter.oc.gone" to RawPref.Null,
                "unprefixed.kotlin.key" to RawPref.Str("kept"),
            ),
        )
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        assertEquals(setOf("oc.a", "voice.language"), store.keys())
        assertEquals(setOf("oc.a"), store.keys("oc."))
        assertNull(store.get("oc.gone"))
        store.commit(lane) { putString("oc.b", "y") }
        assertEquals(RawPref.Str("kept"), backend.persisted()["unprefixed.kotlin.key"], "untouched keys stay as they are")
        assertEquals(RawPref.Null, backend.persisted()["flutter.oc.gone"])
    }

    @Test
    fun `B-MIGR-005 removing a key and an undecodable value round trip`() = runTest {
        val backend = FakeBackend(physical("oc.bad" to RawPref.Str(FlutterPrefsCodec.JSON_LIST_PREFIX + "[1]")))
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        assertIs<PrefValue.Undecodable>(store.get("oc.bad"))
        assertNull(store.stringList("oc.bad"))
        store.commit(lane) { remove("oc.bad") }
        assertNull(store.get("oc.bad"))
        assertFalse(backend.persisted().containsKey("flutter.oc.bad"))
    }
}
