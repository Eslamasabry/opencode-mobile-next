package io.github.eslamasabry.oc.engine.integrations.host

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok as okOutcome
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SENSITIVE = "oc.byoHostSsh.key-and-device-token"

/** The Dart `_Vault`: failure switches; a thrown error carries the sensitive sentinel. */
private class Vault : SecretStore {
    val values = linkedMapOf<String, String>()
    var failOperation: String? = null
    var retainDeletedValue = false
    var calls = 0

    private fun check(operation: String) {
        calls++
        if (failOperation == operation) throw IllegalStateException(SENSITIVE)
    }

    override suspend fun read(name: String): Outcome<String?> {
        check("read")
        return values[name].okOutcome()
    }

    override suspend fun write(name: String, value: String): Outcome<Unit> {
        check("write")
        values[name] = value
        return Unit.okOutcome()
    }

    override suspend fun delete(name: String): Outcome<Unit> {
        check("delete")
        if (!retainDeletedValue) values.remove(name)
        return Unit.okOutcome()
    }

    override suspend fun names(prefix: String): Outcome<Set<String>> = values.keys.filter { it.startsWith(prefix) }.toSet().okOutcome()
}

/** The Dart `_Disk`: refuses writes or removals on demand. */
private class Disk : InMemoryPrefsBackend() {
    var refuseWrite = false
    var refuseRemove = false

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        if (refuseWrite && changes.values.any { it != null }) return false
        if (refuseRemove && changes.values.any { it == null }) return false
        return super.commit(changes)
    }
}

/** Reads that throw (Dart `failRead`), delegating everything else. */
private class FlakyKv(private val inner: KeyValueStore) : KeyValueStore by inner {
    var failRead = false

    override fun get(key: String) = if (failRead) throw IllegalStateException(SENSITIVE) else inner.get(key)
}

/** Flutter test/byo_host/store_test.dart. */
class ByoHostStoreTest {
    private val disk = Disk()
    private val vault = Vault()
    private val lane = WriterLane("app")

    private fun TestScope.kv(): FlakyKv =
        FlakyKv(PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), StandardTestDispatcher(testScheduler)))

    private fun record(id: String) =
        ByoHostRecord(
            profileId = id,
            target = ByoHostTarget.of("ubuntu", tsNet("machine.tailnet"))!!,
            hostKey = hostKey(1),
            phase = ByoHostPhase.Ready,
            hostId = "host-$id",
            bundleVersion = "1.1.0",
            openCodeVersion = "1.18.32",
            remotePort = 4096,
        )

    private fun secrets(id: String) = ByoHostSecrets(ByoHostIdentity("oc.byoHostSsh.$id", "fixture-public-$id"), "fixture-token-$id")

    private val storage = HostResult.Failed(ByoHostFailureCode.Storage)

    private fun assertSafe(result: HostResult<*>) {
        assertEquals(storage, result)
        assertFalse(SENSITIVE in result.toString())
    }

    @Test
    fun `B-SEC-036 legacy exportable identities are refused without returning private data`() =
        runTest {
            val store = PersistentByoHostStore(kv(), vault, lane)
            vault.values["oc.byoHostSecrets.one"] =
                """{"schema":1,"privateKey":"LEGACY_PRIVATE_SENTINEL","publicKey":"old","deviceToken":"old"}"""
            val result = store.readSecrets("one")
            assertSafe(result)
            assertFalse("LEGACY_PRIVATE_SENTINEL" in result.toString())
            assertTrue("LEGACY_PRIVATE_SENTINEL" in vault.values["oc.byoHostSecrets.one"]!!)
        }

    @Test
    fun `B-SEC-036 round trips after reopening with secrets only in the vault`() =
        runTest {
            val prefs = kv()
            val store = PersistentByoHostStore(prefs, vault, lane)
            repeat(2) {
                store.saveRecord(record("one")).ok()
                store.saveSecrets("one", secrets("one")).ok()
            }
            val metadata = prefs.string("oc.byoHost.one")!!
            assertFalse("oc.byoHostSsh.one" in metadata)
            assertFalse("fixture-token-one" in metadata)
            assertFalse("privateKey" in metadata)
            assertEquals(setOf("oc.byoHost.one"), prefs.keys())
            assertEquals(listOf("oc.byoHostSecrets.one"), vault.values.keys.toList())

            val reopened = PersistentByoHostStore(kv(), vault, lane)
            assertEquals(record("one"), reopened.readRecord("one").ok())
            assertEquals("oc.byoHostSsh.one", reopened.readSecrets("one").ok()!!.identity.keyAlias)
            assertEquals("fixture-token-one", reopened.readSecrets("one").ok()!!.deviceToken)
        }

    @Test
    fun `B-SEC-036 removal is idempotent and leaves another profile untouched`() =
        runTest {
            val prefs = kv()
            val signer = FakeSigner()
            val store = PersistentByoHostStore(prefs, vault, lane, signer)
            for (id in listOf("one", "two")) {
                store.saveRecord(record(id)).ok()
                store.saveSecrets(id, secrets(id)).ok()
            }
            prefs.commit(lane) { putString("unrelated", "keep") }
            vault.values["unrelated"] = "keep"
            store.remove("one").ok()
            store.remove("one").ok()
            assertNull(store.readRecord("one").ok())
            assertNull(store.readSecrets("one").ok())
            assertEquals("two", store.readRecord("two").ok()!!.profileId)
            assertEquals("fixture-token-two", store.readSecrets("two").ok()!!.deviceToken)
            assertEquals("keep", prefs.string("unrelated"))
            assertEquals("keep", vault.values["unrelated"])
            assertEquals(listOf("one", "one"), signer.deleted) // the Keystore identity goes with it
        }

    @Test
    fun `B-SEC-036 lists immutable records sorted by profile without opening the vault`() =
        runTest {
            val prefs = kv()
            val store = PersistentByoHostStore(prefs, vault, lane)
            assertTrue(store.listRecords().ok().isEmpty())
            store.saveRecord(record("two")).ok()
            store.saveRecord(record("one")).ok()
            prefs.commit(lane) {
                putString("oc.byoHostSecrets.other", "unrelated")
                putString("unrelated", "keep")
            }
            vault.failOperation = "read"
            val records = store.listRecords().ok()
            assertEquals(listOf("one", "two"), records.map { it.profileId })
            assertTrue((records as Any) is kotlinx.collections.immutable.ImmutableList<*>)
            assertEquals(0, vault.calls)
            store.saveRecord(record("three")).ok()
            assertEquals(2, records.size)
        }

    @Test
    fun `B-SEC-037 enumeration fails closed instead of hiding corrupted profiles`() =
        runTest {
            val prefs = kv()
            val store = PersistentByoHostStore(prefs, vault, lane)
            store.saveRecord(record("one")).ok()
            for (value in listOf(SENSITIVE, PersistentByoHostStore.encodeRecord(record("one")))) {
                prefs.commit(lane) { putString("oc.byoHost.two", value) }
                assertSafe(store.listRecords())
            }
            prefs.commit(lane) { remove("oc.byoHost.two") }
            prefs.commit(lane) { putString("oc.byoHost.invalid.id", PersistentByoHostStore.encodeRecord(record("one"))) }
            assertSafe(store.listRecords())
        }

    @Test
    fun `B-SEC-037 rejects invalid profile ids before either store is touched`() =
        runTest {
            val prefs = kv()
            val store = PersistentByoHostStore(prefs, vault, lane)
            for (id in listOf("", "../one", "one.two", "a".repeat(65))) {
                assertSafe(store.readRecord(id))
                assertSafe(store.readSecrets(id))
                assertSafe(store.saveRecord(record(id)))
                assertSafe(store.saveSecrets(id, secrets(id)))
                assertSafe(store.remove(id))
            }
            assertEquals(0, vault.calls)
            assertTrue(prefs.keys().isEmpty())
        }

    @Test
    fun `B-SEC-037 rejects corrupt and cross-profile metadata with safe failures`() =
        runTest {
            val prefs = kv()
            val store = PersistentByoHostStore(prefs, vault, lane)
            val one = PersistentByoHostStore.encodeRecord(record("one"))
            for (value in listOf(
                SENSITIVE,
                """{"schema":99,"profileId":"one"}""",
                PersistentByoHostStore.encodeRecord(record("two")),
                one.replace("\"phase\":\"ready\"", "\"phase\":\"$SENSITIVE\""),
            )) {
                prefs.commit(lane) { putString("oc.byoHost.one", value) }
                assertSafe(store.readRecord("one"))
            }
            vault.values["oc.byoHostSecrets.one"] = SENSITIVE
            assertSafe(store.readSecrets("one"))
            vault.values["oc.byoHostSecrets.one"] = "{}"
            assertSafe(store.readSecrets("one"))
        }

    @Test
    fun `B-SEC-037 vault read and write errors reveal no plugin details`() =
        runTest {
            val store = PersistentByoHostStore(kv(), vault, lane)
            vault.failOperation = "write"
            assertSafe(store.saveSecrets("one", secrets("one")))
            vault.failOperation = "read"
            assertSafe(store.readSecrets("one"))
            vault.failOperation = null
            store.saveSecrets("one", secrets("one")).ok()
            assertEquals("fixture-token-one", store.readSecrets("one").ok()!!.deviceToken)
        }

    @Test
    fun `B-SEC-037 preferences errors are safe and failed writes restore the cache`() =
        runTest {
            val prefs = kv()
            val store = PersistentByoHostStore(prefs, vault, lane)
            disk.refuseWrite = true
            assertSafe(store.saveRecord(record("one")))
            assertNull(prefs.get("oc.byoHost.one"))
            disk.refuseWrite = false
            prefs.failRead = true
            assertSafe(store.readRecord("one"))
            prefs.failRead = false
            store.saveRecord(record("one")).ok()
            assertEquals("one", store.readRecord("one").ok()!!.profileId)
        }

    @Test
    fun `B-SEC-037 vault deletion error preserves metadata for a later retry`() =
        runTest {
            val store = PersistentByoHostStore(kv(), vault, lane)
            store.saveRecord(record("one")).ok()
            store.saveSecrets("one", secrets("one")).ok()
            vault.failOperation = "delete"
            assertSafe(store.remove("one"))
            assertEquals("one", store.readRecord("one").ok()!!.profileId)
            assertTrue(vault.values.containsKey("oc.byoHostSecrets.one"))
            vault.failOperation = null
            store.remove("one").ok()
            assertNull(store.readRecord("one").ok())
        }

    @Test
    fun `B-SEC-037 unconfirmed vault deletion keeps the cleanup record`() =
        runTest {
            val store = PersistentByoHostStore(kv(), vault, lane)
            store.saveRecord(record("one")).ok()
            store.saveSecrets("one", secrets("one")).ok()
            vault.retainDeletedValue = true
            assertSafe(store.remove("one"))
            assertEquals("one", store.readRecord("one").ok()!!.profileId)
        }

    @Test
    fun `B-SEC-037 metadata deletion refusal retries after secrets were already removed`() =
        runTest {
            val store = PersistentByoHostStore(kv(), vault, lane)
            store.saveRecord(record("one")).ok()
            store.saveSecrets("one", secrets("one")).ok()
            disk.refuseRemove = true
            assertSafe(store.remove("one"))
            assertNull(store.readSecrets("one").ok())
            assertEquals("one", store.readRecord("one").ok()!!.profileId)
            disk.refuseRemove = false
            store.remove("one").ok()
            assertNull(store.readRecord("one").ok())
        }
}
