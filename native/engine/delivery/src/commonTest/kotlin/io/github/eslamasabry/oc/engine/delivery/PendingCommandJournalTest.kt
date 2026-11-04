package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceipt
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptState
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournal
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournals
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of `test/pending_command_journal_test.dart` (rows B-STATE-047 to B-STATE-053). */
class PendingCommandJournalTest {
    private val phone = ProfileId("phone")
    private val tablet = ProfileId("tablet")
    private val key = "${PendingCommandJournal.KEY_PREFIX}phone"
    private val scopeHash = "a".repeat(64)

    private fun receipt(id: String = "command_1", tab: String = "tab_1", state: CommandReceiptState = CommandReceiptState.Sent) =
        CommandReceipt(id, "msg_$id", "ses_1", tab, scopeHash, 100, state)

    private fun encoded(commands: List<CommandReceipt>) = PendingCommandJournal.encode(commands)

    /** A journal "process" over [disk]; [known] is the profile registry the directory reads. */
    private class Rig(val disk: Disk, test: TestScope, val known: MutableSet<ProfileId>, wrap: (KeyValueStore) -> KeyValueStore = { it }) {
        val store = wrap(kv(disk, test))
        var registryReadable = true
        val journals = PendingCommandJournals(store, DefaultWriterLanes(), ProfileDirectory { registryReadable && it in known })

        fun journal(profile: ProfileId): PendingCommandJournal = (journals.forProfile(profile) as Outcome.Ok).value
    }

    private fun TestScope.rig(raw: String? = null, wrap: (KeyValueStore) -> KeyValueStore = { it }): Rig {
        val initial = raw?.let { mapOf(FlutterPrefsCodec.physicalKey(key) to RawPref.Str(it)) } ?: emptyMap()
        return Rig(Disk(initial), this, mutableSetOf(ProfileId("phone"), ProfileId("tablet")), wrap)
    }

    private fun Rig.persisted(k: String = key): String? = (disk.persisted()[FlutterPrefsCodec.physicalKey(k)] as? RawPref.Str)?.value

    private fun PendingCommandJournal.receipts(): List<CommandReceipt> = (read() as Outcome.Ok).value

    @Test
    fun `B-STATE-047 a pending command is stored as metadata only and survives restart`() = runTest {
        val r = rig()
        val journal = r.journal(phone)
        assertEquals(emptyList(), journal.receipts())
        assertEquals(Outcome.Ok(true), journal.claim(receipt()))
        val envelope = DartJson.decodeOrNull(r.persisted()!!) as Map<*, *>
        assertEquals(setOf("schemaVersion", "commands"), envelope.keys)
        val entry = (envelope["commands"] as List<*>).single() as Map<*, *>
        assertEquals(setOf("commandID", "receiptID", "sessionID", "tabID", "scope", "createdAt", "state"), entry.keys, "metadata only: no prompt body")

        val reopened = Rig(r.disk, this, r.known).journal(phone)
        assertEquals("command_1", reopened.receipts().single().commandId)
        assertEquals(Outcome.Ok(false), reopened.claim(receipt()))
        assertEquals(Outcome.Ok(Unit), reopened.update(receipt(state = CommandReceiptState.Confirmed)))
        assertEquals(CommandReceiptState.Confirmed, reopened.receipts().single().state)
        assertEquals(Outcome.Ok(false), reopened.claim(receipt()))
    }

    @Test
    fun `B-STATE-048 simultaneous duplicate claims of one command grant exactly one`() = runTest {
        val r = rig()
        val journal = r.journal(phone)
        assertTrue(journal === r.journal(phone), "one journal per profile per process")
        val claims = (0 until 30).map { async { journal.claim(receipt()) } }.awaitAll()
        assertEquals(1, claims.count { it == Outcome.Ok(true) })
        assertEquals(1, journal.receipts().size)
    }

    @Test
    fun `B-STATE-048 concurrent distinct claims all survive a durable reload`() = runTest {
        val r = rig()
        val journal = r.journal(phone)
        val claims = (0 until 15).map { i -> async { journal.claim(receipt(id = "command_$i")) } }.awaitAll()
        assertTrue(claims.all { it == Outcome.Ok(true) })
        assertEquals(15, journal.receipts().size)
        assertEquals(15, Rig(r.disk, this, r.known).journal(phone).receipts().size)
    }

    @Test
    fun `B-STATE-048 a duplicate claim cannot change the tab or overwrite the state`() = runTest {
        val journal = rig().journal(phone)
        journal.claim(receipt())
        journal.update(receipt(state = CommandReceiptState.Uncertain))
        assertEquals(Outcome.Ok(false), journal.claim(receipt(tab = "other_tab")))
        assertEquals("tab_1", journal.receipts().single().tabId)
        assertEquals(CommandReceiptState.Uncertain, journal.receipts().single().state)
        assertIs<Outcome.Failed>(journal.update(receipt(tab = "other_tab", state = CommandReceiptState.Uncertain)))
        assertIs<Outcome.Failed>(journal.update(receipt(id = "absent")))
        assertEquals("tab_1", journal.receipts().single().tabId)
    }

    @Test
    fun `B-STATE-049 uncertain and terminal receipts can never become sent again`() = runTest {
        val journal = rig().journal(phone)
        journal.claim(receipt())
        journal.update(receipt(state = CommandReceiptState.Uncertain))
        assertIs<Outcome.Failed>(journal.update(receipt()))
        assertEquals(Outcome.Ok(Unit), journal.update(receipt(state = CommandReceiptState.Confirmed)))
        assertIs<Outcome.Failed>(journal.update(receipt(state = CommandReceiptState.Uncertain)))
        assertIs<Outcome.Failed>(journal.update(receipt(state = CommandReceiptState.Failed)))
        assertEquals(CommandReceiptState.Confirmed, journal.receipts().single().state)
        journal.claim(receipt(id = "failed_command"))
        journal.update(receipt(id = "failed_command", state = CommandReceiptState.Failed))
        assertIs<Outcome.Failed>(journal.update(receipt(id = "failed_command")))
    }

    @Test
    fun `B-STATE-050 an unknown profile or a malformed profile registry blocks admission`() = runTest {
        val r = rig()
        assertIs<Outcome.Failed>(r.journals.forProfile(ProfileId("absent")))
        assertIs<Outcome.Failed>(r.journals.forProfile(ProfileId("phone.bad")))
        assertIs<Outcome.Failed>(r.journals.forProfile(ProfileId("phone\n")))
        val journal = r.journal(phone)
        r.registryReadable = false // the registry no longer parses
        assertIs<Outcome.Failed>(journal.read())
        assertIs<Outcome.Failed>(journal.claim(receipt()))
        assertNull(r.persisted())
    }

    @Test
    fun `B-STATE-050 a malformed journal fails closed even after the bad value is removed`() = runTest {
        val bad = listOf(
            "not json",
            """{"schemaVersion":2,"commands":[]}""",
            """{"schemaVersion":1,"commands":[],"payload":"unexpected"}""",
            """{"schemaVersion":1,"commands":["invalid"]}""",
            encoded(listOf(receipt(), receipt())),
            encoded(listOf(receipt())).replace("\"state\":\"sent\"", "\"state\":\"sent\",\"prompt\":\"unexpected\""),
        )
        for (raw in bad) {
            val r = rig(raw)
            val journal = r.journal(phone)
            assertIs<Outcome.Failed>(journal.read(), raw)
            r.store.commit(DefaultWriterLanes().app) { remove(key) }
            assertIs<Outcome.Failed>(journal.claim(receipt()), raw)
        }
        // A non-string value (Dart: 42) is not a journal either.
        val disk = Disk(mapOf(FlutterPrefsCodec.physicalKey(key) to RawPref.Int64(42)))
        assertIs<Outcome.Failed>(Rig(disk, this, mutableSetOf(phone)).journal(phone).read())
    }

    @Test
    fun `B-STATE-050 unverified writes never grant admission or reopen the journal`() = runTest {
        for (mode in listOf("refuse", "throw")) {
            val r = rig()
            val journal = r.journal(phone)
            r.disk.refuse = { changes ->
                if (changes.keys.any { it.endsWith(key) }) {
                    if (mode == "throw") throw IllegalStateException("Synthetic failure")
                    true
                } else false
            }
            assertIs<Outcome.Failed>(journal.claim(receipt()), mode)
            r.disk.allowAll()
            assertIs<Outcome.Failed>(journal.read(), mode)
            assertIs<Outcome.Failed>(journal.claim(receipt()), mode)
            assertIs<Outcome.Failed>(r.journal(phone).read(), "the same owner stays closed ($mode)")
            assertNull(r.persisted())
        }
    }

    @Test
    fun `B-STATE-050 a reload failure blocks before any platform write`() = runTest {
        val disk = object : Disk() {
            var failLoads = true
            override fun load(): Map<String, RawPref> = if (failLoads) throw IllegalStateException("Synthetic failure") else super.load()
        }
        val r = Rig(disk, this, mutableSetOf(phone))
        val journal = r.journal(phone)
        assertIs<Outcome.Failed>(journal.claim(receipt()))
        disk.failLoads = false
        assertEquals(0, disk.commits, "nothing reached the platform")
        assertIs<Outcome.Failed>(journal.claim(receipt()))
        assertNull(r.persisted())
    }

    @Test
    fun `B-STATE-051 reload checks durable membership, not an optimistic cache`() = runTest {
        val r = rig()
        val journal = r.journal(phone)
        r.known.clear() // the stored registry now lists no profiles
        assertIs<Outcome.Failed>(journal.claim(receipt()))
        assertNull(r.persisted())
    }

    @Test
    fun `B-STATE-051 profiles keep independent journals even with matching command ids`() = runTest {
        val r = rig()
        val phoneJournal = r.journal(phone)
        val tabletJournal = r.journal(tablet)
        assertEquals(Outcome.Ok(true), phoneJournal.claim(receipt()))
        assertEquals(Outcome.Ok(true), tabletJournal.claim(receipt(tab = "tablet_tab")))
        phoneJournal.update(receipt(state = CommandReceiptState.Confirmed))
        assertEquals(CommandReceiptState.Sent, tabletJournal.receipts().single().state)
        assertTrue(r.persisted("${PendingCommandJournal.KEY_PREFIX}tablet") != null)
        r.journals.closeProfile(phone)
        r.store.commit(DefaultWriterLanes().app) { remove(key) }
        assertEquals("tablet_tab", tabletJournal.receipts().single().tabId)
    }

    @Test
    fun `B-STATE-052 capacity keeps tombstones and refuses another new command`() = runTest {
        val full = (0 until PendingCommandJournal.MAX_ENTRIES).map { receipt(id = "command_$it", state = CommandReceiptState.Confirmed) }
        val journal = rig(encoded(full)).journal(phone)
        assertEquals(Outcome.Ok(false), journal.claim(receipt(id = "command_0")))
        assertIs<Outcome.Failed>(journal.claim(receipt(id = "another_command")))
        assertEquals(PendingCommandJournal.MAX_ENTRIES, journal.receipts().size)
        assertEquals(CommandReceiptState.Confirmed, journal.receipts().first().state)
    }

    @Test
    fun `B-STATE-052 the byte limit refuses a new claim without removing old tombstones`() = runTest {
        fun large(i: Int, state: CommandReceiptState = CommandReceiptState.Confirmed) =
            CommandReceipt("${"a".repeat(240)}_$i", "b".repeat(256), "c".repeat(256), "d".repeat(256), scopeHash, 100, state)
        val receipts = ArrayList<CommandReceipt>()
        while (DartJson.utf8(encoded(receipts + large(receipts.size))).size <= PendingCommandJournal.MAX_BYTES) receipts += large(receipts.size)
        assertTrue(receipts.size < PendingCommandJournal.MAX_ENTRIES)
        val original = encoded(receipts)
        val r = rig(original)
        val journal = r.journal(phone)
        assertIs<Outcome.Failed>(journal.claim(large(receipts.size, CommandReceiptState.Sent)))
        assertEquals(original, r.persisted())
        assertEquals(receipts.size, journal.receipts().size)
        assertEquals(Outcome.Ok(false), journal.claim(receipt(id = receipts.first().commandId)))
    }

    @Test
    fun `B-STATE-052 oversized and over-capacity journals cannot be read or replaced`() = runTest {
        val r = rig("x".repeat(PendingCommandJournal.MAX_BYTES + 1))
        val journal = r.journal(phone)
        assertIs<Outcome.Failed>(journal.read())
        assertIs<Outcome.Failed>(journal.claim(receipt()))
        assertEquals("x".repeat(PendingCommandJournal.MAX_BYTES + 1), r.persisted(), "never replaced")
        val over = (0..PendingCommandJournal.MAX_ENTRIES).map { receipt(id = "command_$it") }
        assertIs<Outcome.Failed>(rig(encoded(over)).journal(phone).read())
    }

    @Test
    fun `B-STATE-053 profile deletion closes the journal at once and drains in-flight and queued claims`() = runTest {
        val gate = HoldingStore.Gate()
        val r = rig { HoldingStore(it, gate) }
        val journal = r.journal(phone)
        val writing = async { journal.claim(receipt()) }
        runCurrent()
        assertTrue(gate.entered.isCompleted, "the first claim is inside its platform write")
        val queued = async { journal.claim(receipt(id = "queued_command")) }
        var drained = false
        val draining = async { r.journals.closeProfile(phone); drained = true }
        runCurrent()
        assertFalse(drained, "deletion waits for admitted work")
        assertIs<Outcome.Failed>(journal.read())
        assertIs<Outcome.Failed>(r.journals.forProfile(phone))
        assertIs<Outcome.Failed>(journal.claim(receipt(id = "late_command")))
        gate.release.complete(Unit)
        assertIs<Outcome.Failed>(writing.await(), "an in-flight claim is not granted once deletion started")
        assertIs<Outcome.Failed>(queued.await())
        draining.await()
        assertTrue(drained)
        r.store.commit(DefaultWriterLanes().app) { remove(key) }
        r.known.remove(phone)
        assertIs<Outcome.Failed>(journal.claim(receipt()))
        assertNull(r.persisted())
    }

    @Test
    fun `B-STATE-053 closing before the owner exists prevents all later admission`() = runTest {
        val r = rig()
        r.journals.closeProfile(phone)
        assertIs<Outcome.Failed>(r.journals.forProfile(phone))
        assertNull(r.persisted())
    }
}

/** A store whose journal writes wait at [Gate] (Dart `_Disk.hold`). */
private class HoldingStore(private val inner: KeyValueStore, private val gate: Gate) : KeyValueStore by inner {
    class Gate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    override val changes: SharedFlow<Set<String>> get() = inner.changes

    override fun get(key: String): PrefValue? = inner.get(key)

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        if (!gate.entered.isCompleted) {
            gate.entered.complete(Unit)
            gate.release.await()
        }
        return inner.commit(lane, edit)
    }
}
