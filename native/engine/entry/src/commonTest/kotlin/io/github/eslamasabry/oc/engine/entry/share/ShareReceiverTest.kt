package io.github.eslamasabry.oc.engine.entry.share

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The share receiver and ShareOut over a fake `oc/share` (test/share_intent_test.dart). */
class ShareReceiverTest {
    /** One listener at a time, like a MethodChannel handler. */
    private class FakeInbox(var coldStart: suspend () -> Outcome<String?> = { Outcome.Ok(null) }) : ShareInbox {
        var handler: ((String) -> Unit)? = null
        var consumeCalls = 0

        override suspend fun consumeSharedText(): Outcome<String?> {
            consumeCalls++
            return coldStart()
        }

        override fun listen(onShared: (String) -> Unit): ShareRegistration {
            handler = onShared
            return ShareRegistration { if (handler === onShared) handler = null }
        }

        fun share(text: String) = handler?.invoke(text)
    }

    private class FakeSheet(val answer: suspend () -> Outcome<Boolean>) : ShareSheet {
        val calls = mutableListOf<Pair<String, String?>>()

        override suspend fun shareText(text: String, subject: String?): Outcome<Boolean> {
            calls += text to subject
            return answer()
        }
    }

    @Test
    fun `B-ANDR-076 drains the cold-start share trimmed and take hands it over once`() = runTest {
        val share = ShareReceiver(FakeInbox { "  https://x.example.invalid/z  ".ok() })
        share.start()
        assertEquals("https://x.example.invalid/z", share.pending.value)
        assertEquals("https://x.example.invalid/z", share.take())
        assertNull(share.pending.value)
        assertNull(share.take())
    }

    @Test
    fun `B-ANDR-076 a live share lands while running and blank or missing shares are ignored`() = runTest {
        val inbox = FakeInbox()
        val share = ShareReceiver(inbox)
        share.start()
        inbox.share("stack trace here")
        assertEquals("stack trace here", share.pending.value)
        share.take()
        inbox.share("   ")
        assertNull(share.pending.value)

        val missing = ShareReceiver(FakeInbox { Outcome.Failed(Failure.Unavailable(FeatureId("share"))) })
        missing.start()
        assertNull(missing.pending.value)
        val throwing = ShareReceiver(FakeInbox { error("no plugin") })
        throwing.start()
        assertNull(throwing.pending.value)
        val inert = ShareReceiver(null)
        inert.start()
        assertNull(inert.pending.value)
    }

    @Test
    fun `B-ANDR-076 a newer live share wins over a cold-start read still in flight`() = runTest {
        val consumed = CompletableDeferred<String?>()
        val inbox = FakeInbox { consumed.await().ok() }
        val share = ShareReceiver(inbox)
        val start = async { share.start() }
        runCurrent()
        inbox.share("newer warm share")
        consumed.complete("stale cold-start share")
        start.await()
        assertEquals("newer warm share", share.pending.value)
        assertEquals("newer warm share", share.take())
    }

    @Test
    fun `B-ANDR-076 a warm share taken before the cold-start read finishes stays consumed`() = runTest {
        val consumed = CompletableDeferred<String?>()
        val inbox = FakeInbox { consumed.await().ok() }
        val share = ShareReceiver(inbox)
        val start = async { share.start() }
        runCurrent()
        assertEquals(1, inbox.consumeCalls)
        inbox.share("warm share")
        assertEquals("warm share", share.take())
        consumed.complete("stale cold-start share")
        start.await()
        assertNull(share.pending.value)
    }

    @Test
    fun `B-ANDR-076 dispose during the cold-start read ignores its answer and sends no update`() = runTest {
        val consumed = CompletableDeferred<String?>()
        val share = ShareReceiver(FakeInbox { consumed.await().ok() })
        val start = async { share.start() }
        runCurrent()
        val updates = mutableListOf<String?>()
        val watch = launch { share.pending.drop(1).collect { updates += it } }
        runCurrent()
        share.dispose()
        consumed.complete("after dispose")
        start.await()
        runCurrent()
        watch.cancel()
        assertTrue(updates.isEmpty())
        assertNull(share.take())
        assertNull(share.pending.value)
    }

    @Test
    fun `B-ANDR-076 disposing a never-started receiver leaves the active receiver listening`() = runTest {
        val inbox = FakeInbox()
        val active = ShareReceiver(inbox)
        val neverStarted = ShareReceiver(inbox)
        active.start()
        neverStarted.dispose()
        inbox.share("still active")
        assertEquals("still active", active.pending.value)
    }

    @Test
    fun `B-ANDR-076 dispose unregisters the listener and blocks every later update`() = runTest {
        val inbox = FakeInbox()
        val share = ShareReceiver(inbox)
        share.start()
        val updates = mutableListOf<String?>()
        val watch = launch { share.pending.drop(1).collect { updates += it } }
        runCurrent()
        share.dispose()
        assertNull(inbox.handler)
        inbox.share("after dispose")
        runCurrent()
        watch.cancel()
        assertTrue(updates.isEmpty())
        assertNull(share.take())
        // A disposed receiver never starts again.
        share.start()
        assertNull(inbox.handler)
    }

    @Test
    fun `B-ANDR-077 ShareOut sends the text and subject and says whether the sheet opened`() = runTest {
        val sheet = FakeSheet { true.ok() }
        val out = ShareOut(sheet)
        assertTrue(out.text("report body", subject = "Title"))
        assertEquals(listOf<Pair<String, String?>>("report body" to "Title"), sheet.calls)
        assertFalse(out.text(""))
        assertEquals(1, sheet.calls.size)
        assertFalse(ShareOut(FakeSheet { false.ok() }).text("x"))
    }

    @Test
    fun `B-ANDR-077 ShareOut answers false when the platform side is missing or fails`() = runTest {
        assertFalse(ShareOut(null).text("x"))
        assertFalse(ShareOut(FakeSheet { Outcome.Failed(Failure.Unexpected()) }).text("x"))
        assertFalse(ShareOut(FakeSheet { error("nope") }).text("x"))
    }
}
