package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceipt
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptController
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptState
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchProof
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournal
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptsTest {
    private fun receiptLink() = FakeLink(capabilities = ServerCapabilities(commandReceipts = true), correlated = true)

    private fun World.journal(): PendingCommandJournal = (journals.forProfile(P1) as Outcome.Ok).value

    private fun World.receipts(): List<CommandReceipt> = (journal().read() as Outcome.Ok).value

    private val scope = DispatchScope(P1, "http://127.0.0.1:4097", null, null)

    private fun receipt(command: String = "queue:a", tab: String = "ses_1", scopeHash: String = scope.receiptScope) =
        CommandReceipt(command, "msg_1", "ses_1", tab, scopeHash, NOW_MS, CommandReceiptState.Sent)

    @Test
    fun `B-STATE-008 a lost response is confirmed by lookup on Retry and never sent again`() = runTest {
        val w = World(Disk(), this, link = receiptLink())
        w.queue.queuePrompt(prompt("a"))
        w.link.answer = { FakeLink.Answer.LostResponse }
        w.queue.flush()
        assertNotNull(w.queue.entries().single().dispatchedAt)
        assertEquals(CommandReceiptState.Uncertain, w.receipts().single().state)
        assertEquals("queue:a", w.receipts().single().commandId)
        assertTrue(w.link.delivered.single().prompt.clientMessageId!!.raw.startsWith("msg_"), "the wire id, never queue:a")

        assertEquals(Outcome.Ok(true), w.queue.resend("a"), "Retry looks the receipt up")
        assertEquals(1, w.link.attempts.size, "and never sends again")
        assertTrue(w.queue.entries().isEmpty())
        assertEquals(CommandReceiptState.Confirmed, w.receipts().single().state, "kept as a tombstone")
    }

    @Test
    fun `B-STATE-008 after a restart a pending command reconciles on flush and keeps its tab`() = runTest {
        val w = World(Disk(), this, link = receiptLink())
        w.queue.queuePrompt(prompt("a"))
        w.link.answer = { FakeLink.Answer.LostResponse }
        w.queue.flush()
        val reborn = w.restart()
        reborn.link.answer = { FakeLink.Answer.Confirmed }
        reborn.queue.flush()
        assertTrue(reborn.queue.entries().isEmpty(), "reconciled on the first flush")
        assertEquals(1, reborn.link.attempts.size)
        assertEquals("ses_1", reborn.receipts().single().tabId)
    }

    @Test
    fun `B-STATE-008 an absent receipt or a lost capability never authorises a resend`() = runTest {
        val w = World(Disk(), this, link = receiptLink())
        w.queue.queuePrompt(prompt("a"))
        w.link.answer = { FakeLink.Answer.Refused(Failure.Network(NetworkWhy.Reset)) } // never reached the server
        w.queue.flush()
        w.link.answer = { FakeLink.Answer.Confirmed }
        assertEquals(Outcome.Ok(false), w.queue.resend("a"), "absent on the server: stays unknown")
        w.link.capabilities = ServerCapabilities(commandReceipts = false)
        assertEquals(Outcome.Ok(false), w.queue.resend("a"), "capability lost: still bound to its first dispatch")
        w.queue.flush()
        assertEquals(1, w.link.attempts.size)
        assertNotNull(w.queue.entries().single().dispatchedAt)
    }

    @Test
    fun `B-STATE-009 simultaneous sends with one command dispatch once`() = runTest {
        val w = World(Disk(), this)
        var dispatches = 0
        val results = (1..3).map {
            async { CommandReceiptController(w.journal()).send(receipt()) { dispatches++; DispatchProof.Confirmed } }
        }.awaitAll()
        assertEquals(1, dispatches)
        assertTrue(results.all { it is Outcome.Ok })
    }

    @Test
    fun `B-STATE-009 the journal is durably claimed before dispatch`() = runTest {
        val w = World(Disk(), this)
        CommandReceiptController(w.journal()).send(receipt()) {
            val fresh = w.restart()
            assertEquals(CommandReceiptState.Sent, fresh.receipts().single().state, "on disk before any byte leaves")
            DispatchProof.Uncertain
        }
        assertEquals(CommandReceiptState.Uncertain, w.restart().receipts().single().state)
    }

    @Test
    fun `B-STATE-009 a command cannot be borrowed by another tab or scope`() = runTest {
        val w = World(Disk(), this)
        val controller = CommandReceiptController(w.journal())
        controller.send(receipt()) { DispatchProof.Uncertain }
        var dispatched = false
        assertIs<Outcome.Failed>(controller.send(receipt(tab = "ses_other")) { dispatched = true; DispatchProof.Confirmed })
        assertIs<Outcome.Failed>(controller.send(receipt(scopeHash = "0".repeat(64))) { dispatched = true; DispatchProof.Confirmed })
        assertEquals(false, dispatched)
    }

    @Test
    fun `B-STATE-009 failed and confirmed receipts are tombstones, uncertain never becomes sent again`() = runTest {
        val w = World(Disk(), this)
        val j = w.journal()
        j.claim(receipt())
        assertEquals(Outcome.Ok(Unit), j.update(receipt().withState(CommandReceiptState.Failed)))
        assertEquals(Outcome.Ok(false), j.claim(receipt()), "an existing id never claims again")
        assertIs<Outcome.Failed>(j.update(receipt().withState(CommandReceiptState.Confirmed)), "terminal never changes")
        j.claim(receipt(command = "queue:b"))
        j.update(receipt(command = "queue:b").withState(CommandReceiptState.Uncertain))
        assertIs<Outcome.Failed>(j.update(receipt(command = "queue:b")), "uncertain never becomes sent")
        assertIs<Outcome.Failed>(j.update(receipt(command = "queue:never").withState(CommandReceiptState.Confirmed)), "absent is never created")
        var looked = false
        val retried = CommandReceiptController(j).retry("queue:a") { looked = true; true }
        assertEquals(CommandReceiptState.Failed, (retried as Outcome.Ok).value.state)
        assertEquals(false, looked, "a terminal receipt is not even looked up")
    }

    @Test
    fun `B-STATE-009 a late lookup keeps a replacement draft`() = runTest {
        val w = World(Disk(), this, link = receiptLink())
        w.queue.queuePrompt(prompt("a"))
        w.link.answer = { FakeLink.Answer.LostResponse }
        w.queue.flush()
        // The person discards the review entry and writes a new one that happens to reuse the id.
        w.queue.remove("a")
        w.policy.allows = false
        w.queue.queuePrompt(prompt("a", text = "replacement"))
        assertEquals(false, w.queue.checkReceipt("a"))
        assertEquals("replacement", w.queue.entries().single().text, "the replacement is not discarded")
    }

    @Test
    fun `B-STATE-009 gateways without receipts keep normal queue dispatch`() = runTest {
        val w = World(Disk(), this, link = FakeLink(capabilities = ServerCapabilities(commandReceipts = true), correlated = false))
        w.queue.queuePrompt(prompt("a"))
        w.queue.flush()
        assertEquals(1, w.link.delivered.size)
        assertNull(w.link.delivered.single().prompt.clientMessageId)
        assertTrue(w.receipts().isEmpty())
    }

    @Test
    fun `B-STATE-009 profile deletion closes the journal, and a malformed journal fails closed`() = runTest {
        val w = World(Disk(), this, link = receiptLink())
        val j = w.journal()
        w.queue.closeProfile(P1)
        assertIs<Outcome.Failed>(j.claim(receipt()))
        assertIs<Outcome.Failed>(w.journals.forProfile(P1))

        val disk = Disk(mapOf(FlutterPrefsCodec.physicalKey("${PendingCommandJournal.KEY_PREFIX}${P1.raw}") to RawPref.Str("{\"schemaVersion\":2,\"commands\":[]}")))
        val m = World(disk, this, link = receiptLink())
        m.queue.queuePrompt(prompt("a"))
        m.queue.flush()
        assertTrue(m.link.attempts.isEmpty(), "a malformed journal grants no claim: nothing sent")
        assertNotNull(m.queue.entries().single().dispatchedAt, "kept for review, conservatively")
    }

    @Test
    fun `B-STATE-009 the journal format and scope hash are Flutter's bytes`() {
        val r = receipt(scopeHash = scope.receiptScope)
        assertEquals(
            """{"schemaVersion":1,"commands":[{"commandID":"queue:a","receiptID":"msg_1","sessionID":"ses_1","tabID":"ses_1","scope":"${scope.receiptScope}","createdAt":$NOW_MS,"state":"sent"}]}""",
            PendingCommandJournal.encode(listOf(r)),
        )
        assertEquals(listOf(r), PendingCommandJournal.parse(PendingCommandJournal.encode(listOf(r))))
        // sha256(jsonEncode([id, baseUrl, directory, workspace])) with Dart's compact encoding and nulls.
        assertEquals(DartJson.sha256Hex("""["1735000000000001","http://127.0.0.1:4097",null,null]"""), scope.receiptScope)
        val unicode = DispatchScope(P1, "http://h", "/مشروع/\"q\"", "w")
        assertEquals(DartJson.sha256Hex("""["1735000000000001","http://h","/مشروع/\"q\"","w"]"""), unicode.receiptScope)
        for (bad in listOf("{\"schemaVersion\":1}", "{\"schemaVersion\":1,\"commands\":[],\"x\":1}", "[]",
            """{"schemaVersion":1,"commands":[{"commandID":"a b","receiptID":"m","sessionID":"s","tabID":"t","scope":"${scope.receiptScope}","createdAt":1,"state":"sent"}]}""")) {
            assertNull(PendingCommandJournal.parse(bad), bad)
        }
    }
}
