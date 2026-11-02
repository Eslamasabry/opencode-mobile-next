package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationDraftsTest {
    private val prefs = MemoryComposerPrefs()
    private val profiles = TestProfiles("alpha", "beta")
    private val payloads = MemoryPayloadStore()

    private fun store(port: ComposerPrefs = prefs) =
        Drafts(port, AttachmentVault(payloads), profiles, TestClock())

    private fun TestScope.facade(drafts: Drafts = store()) =
        ConversationDrafts(
            drafts,
            AppCoroutineScope(backgroundScope.coroutineContext),
            TestDispatchers(StandardTestDispatcher(testScheduler)),
        )

    private fun ref(profile: String = "alpha") =
        ConversationRef(ProfileId(profile), SessionId("same"))

    @Test
    fun `B-STATE-100 autosave restores exact text after process restart without an initial empty write`() =
        runTest {
            val owner = facade()
            val handle = owner.open(ref())
            handle.changed("")
            runCurrent()
            assertTrue(handle.state.value.ready)
            handle.changed("  my unfinished words\n ")
            advanceTimeBy(599)
            runCurrent()
            assertNull(store().draft("alpha", "same"))
            advanceTimeBy(1)
            runCurrent()
            assertEquals("  my unfinished words\n ", handle.state.value.saved)
            assertEquals(handle.state.value.text, store().draft("alpha", "same")?.text)
            val restarted = facade().open(ref())
            restarted.changed("")
            runCurrent()
            assertEquals("  my unfinished words\n ", restarted.state.value.text)
            assertEquals(restarted.state.value.text, restarted.state.value.saved)
        }

    @Test
    fun `B-STATE-100 whitespace only is acknowledged and restored exactly for the text session`() =
        runTest {
            val session = facade().open(ref())
            runCurrent()
            session.changed(" \n  ")
            assertIs<Outcome.Ok<Unit>>(session.flush())
            val restored = facade().open(ref())
            runCurrent()
            assertEquals(" \n  ", restored.state.value.text)
        }

    @Test
    fun `B-STATE-101 malformed initial storage refuses editing and preserves the original bytes`() =
        runTest {
            prefs.rawPut(Drafts.KEY, "unreadable but important")
            val session = facade().open(ref())
            runCurrent()
            assertFalse(session.state.value.ready)
            assertIs<Failure.StorageRefused>(session.state.value.failure)
            session.changed("replacement")
            assertIs<Outcome.Failed>(session.flush())
            assertEquals("unreadable but important", prefs.string(Drafts.KEY))
            assertEquals(0, prefs.commits)
        }

    @Test
    fun `B-STATE-101 a thrown read refusal is visible without fabricating an empty saved draft`() =
        runTest {
            store().save("alpha", "same", "kept")
            val raw = prefs.string(Drafts.KEY)
            val refused =
                object : ComposerPrefs by prefs {
                    override fun string(key: String): String? =
                        throw IllegalStateException("storage unavailable")
                }
            val session = facade(store(refused)).open(ref())
            runCurrent()
            assertFalse(session.state.value.ready)
            assertIs<Failure.StorageRefused>(session.state.value.failure)
            assertIs<Outcome.Failed>(session.flush())
            assertEquals(raw, prefs.string(Drafts.KEY))
        }

    @Test
    fun `B-STATE-102 a failed save keeps unsent text and the last durable acknowledgement until retry`() =
        runTest {
            store().save("alpha", "same", "before")
            val session = facade().open(ref())
            runCurrent()
            session.changed("after")
            prefs.refuse = true
            assertIs<Outcome.Failed>(session.flush())
            assertEquals("after", session.state.value.text)
            assertEquals("before", session.state.value.saved)
            assertIs<Failure.StorageRefused>(session.state.value.failure)
            assertEquals("before", store().draft("alpha", "same")?.text)
            prefs.refuse = false
            assertIs<Outcome.Ok<Unit>>(session.flush())
            assertEquals("after", session.state.value.saved)
            assertNull(session.state.value.failure)
        }

    @Test
    fun `B-STATE-102 a slow acknowledgement never claims that a newer edit was saved`() = runTest {
        val gated = GatedPrefs(prefs)
        val session = facade(store(gated)).open(ref())
        runCurrent()
        session.changed("first")
        val gate = CompletableDeferred<Unit>()
        gated.gate = gate
        val flushing = async { session.flush() }
        runCurrent()
        session.changed("newer")
        gate.complete(Unit)
        assertIs<Outcome.Ok<Unit>>(flushing.await())
        assertEquals("first", session.state.value.saved)
        assertEquals("newer", session.state.value.text)
        assertIs<Outcome.Ok<Unit>>(session.flush())
        assertEquals("newer", store().draft("alpha", "same")?.text)
    }

    @Test
    fun `B-STATE-100 acknowledged send clears only the captured revision even after equal text is retyped`() =
        runTest {
            val session = facade().open(ref())
            runCurrent()
            session.changed("submitted")
            assertIs<Outcome.Ok<Unit>>(session.flush())
            val token = assertNotNull(session.captureSubmission())
            val restore = session.state.value.restoreRevision
            session.changed("different")
            session.changed("submitted")
            assertIs<Outcome.Ok<Unit>>(session.clearAfterSend(token))
            assertEquals("submitted", session.state.value.text)
            assertEquals(restore, session.state.value.restoreRevision)
            assertIs<Outcome.Ok<Unit>>(session.flush())
            assertEquals("submitted", store().draft("alpha", "same")?.text)
            val current = assertNotNull(session.captureSubmission())
            assertIs<Outcome.Ok<Unit>>(session.clearAfterSend(current))
            assertEquals("", session.state.value.text)
            assertEquals("", session.state.value.saved)
            assertTrue(session.state.value.restoreRevision > restore)
            assertNull(store().draft("alpha", "same"))
        }

    @Test
    fun `B-STATE-100 clear acknowledgement cannot erase typing that happened during its disk write`() =
        runTest {
            val gated = GatedPrefs(prefs)
            val session = facade(store(gated)).open(ref())
            runCurrent()
            session.changed("submitted")
            session.flush()
            val token = assertNotNull(session.captureSubmission())
            val gate = CompletableDeferred<Unit>()
            gated.gate = gate
            val clearing = async { session.clearAfterSend(token) }
            runCurrent()
            session.changed("new unsent text")
            gate.complete(Unit)
            assertIs<Outcome.Ok<Unit>>(clearing.await())
            assertEquals("new unsent text", session.state.value.text)
            assertIs<Outcome.Ok<Unit>>(session.flush())
            assertEquals("new unsent text", store().draft("alpha", "same")?.text)
        }

    @Test
    fun `B-STATE-102 failed acknowledged clear is blocked and retry never resaves the sent text`() =
        runTest {
            val owner = facade()
            val session = owner.open(ref())
            runCurrent()
            session.changed("sent already")
            session.flush()
            val token = assertNotNull(session.captureSubmission())
            prefs.refuse = true
            assertIs<Outcome.Failed>(session.clearAfterSend(token))
            assertEquals("", session.state.value.text)
            assertFalse(session.state.value.ready)
            assertIs<Failure.StorageRefused>(session.state.value.failure)
            session.close()
            val reopened = owner.open(ref())
            runCurrent()
            assertEquals("", reopened.state.value.text)
            assertNull(reopened.captureSubmission())
            prefs.refuse = false
            assertIs<Outcome.Ok<Unit>>(reopened.flush())
            assertTrue(reopened.state.value.ready)
            assertNull(store().draft("alpha", "same"))
        }

    @Test
    fun `B-STATE-100 identical session ids stay profile scoped and submissions cannot cross handles`() =
        runTest {
            val owner = facade()
            val alpha = owner.open(ref())
            val beta = owner.open(ref("beta"))
            runCurrent()
            alpha.changed("same text")
            beta.changed("same text")
            alpha.flush()
            beta.flush()
            val a = assertNotNull(alpha.captureSubmission())
            beta.clearAfterSend(a)
            assertEquals("same text", beta.state.value.text)
            alpha.clearAfterSend(a)
            assertNull(store().draft("alpha", "same"))
            assertEquals("same text", store().draft("beta", "same")?.text)
        }

    @Test
    fun `B-STATE-101 profile close drains an admitted save then refuses late resurrection`() =
        runTest {
            store().save("beta", "same", "other profile")
            val gated = GatedPrefs(prefs)
            val owner = facade(store(gated))
            val handle = owner.open(ref())
            runCurrent()
            handle.changed("in flight")
            val gate = CompletableDeferred<Unit>()
            gated.gate = gate
            val saving = async { handle.flush() }
            runCurrent()
            val closing = async { owner.closeProfile(ProfileId("alpha")) }
            runCurrent()
            handle.changed("late resurrection")
            gate.complete(Unit)
            saving.await()
            assertIs<Outcome.Ok<Unit>>(closing.await())
            assertNull(store().draft("alpha", "same"))
            assertEquals("other profile", store().draft("beta", "same")?.text)
            assertIs<Outcome.Failed>(handle.flush())
            assertFalse(owner.open(ref()).state.value.ready)
        }

    @Test
    fun `B-STATE-101 corrupt attachment restoration refuses without deleting metadata or payloads`() =
        runTest {
            val drafts = store()
            drafts.save("alpha", "same", "with a file", listOf(dataAttachment("keep.txt", "bytes")))
            val original = prefs.string(Drafts.KEY)
            val entry = payloads.list().single()
            payloads.tamper(entry.ownerKey, entry.id, ByteArray(entry.bytes.toInt()) { 120 })
            val blobs = payloads.keys()
            val handle = facade(drafts).open(ref())
            runCurrent()
            assertFalse(handle.state.value.ready)
            assertNotNull(handle.state.value.failure)
            handle.changed("")
            assertIs<Outcome.Failed>(handle.flush())
            assertEquals(original, prefs.string(Drafts.KEY))
            assertEquals(blobs, payloads.keys())
        }

    @Test
    fun `B-STATE-101 ambiguous legacy drafts prevent profile cleanup without deleting anyone's bytes`() =
        runTest {
            store().save("alpha", "same", "mine")
            prefs.rawPut(
                Drafts.KEY,
                prefs.string(Drafts.KEY)!!.dropLast(1) +
                    """,{"sessionID":"old","text":"unknown owner","updatedAt":0}]""",
            )
            val original = prefs.string(Drafts.KEY)
            assertIs<Outcome.Failed>(facade().closeProfile(ProfileId("alpha")))
            assertEquals(original, prefs.string(Drafts.KEY))
        }

    @Test
    fun `B-STATE-100 draft diagnostics never include words from the editor or submission`() =
        runTest {
            val handle = facade().open(ref())
            runCurrent()
            handle.changed("private draft words")
            assertFalse(handle.state.value.toString().contains("private draft words"))
            assertFalse(
                assertNotNull(handle.captureSubmission()).toString().contains("private draft words")
            )
        }

    @Test
    fun `B-STATE-100 edit revisions are observable without allocating tokens and obsolete captures cannot clear`() =
        runTest {
            val session = facade().open(ref())
            runCurrent()
            session.changed("first")
            val before = session.state.value.editRevision
            val token = assertNotNull(session.captureSubmission())
            assertEquals(before, token.revision)
            assertSame(token, session.captureSubmission())
            session.changed("second")
            session.changed("first")
            assertEquals(before + 2, session.state.value.editRevision)
            val current = assertNotNull(session.captureSubmission())
            assertNotSame(token, current)
            assertEquals(session.state.value.editRevision, current.revision)
            session.clearAfterSend(token)
            assertEquals("first", session.state.value.text)
            assertSame(current, session.captureSubmission())
        }

    @Test
    fun `B-STATE-102 a bounded display close never cancels an already admitted save`() = runTest {
        val gated = GatedPrefs(prefs)
        val owner = facade(store(gated))
        val session = owner.open(ref())
        runCurrent()
        session.changed("survives close")
        val gate = CompletableDeferred<Unit>()
        gated.gate = gate
        session.close()
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertIs<Failure.TimedOut>(session.state.value.failure)
        gate.complete(Unit)
        runCurrent()
        assertEquals("survives close", session.state.value.saved)
        assertNull(session.state.value.failure)
        val reopened = owner.open(ref())
        assertSame(session, reopened)
        assertEquals("survives close", reopened.state.value.text)
        assertEquals("survives close", store().draft("alpha", "same")?.text)
    }

    @Test
    fun `B-STATE-101 explicit recovery after refused removal creates a fresh writer and never revives old handles`() =
        runTest {
            val owner = facade()
            val old = owner.open(ref())
            runCurrent()
            old.changed("before removal")
            old.flush()
            val token = assertNotNull(old.captureSubmission())
            prefs.refuse = true
            assertIs<Outcome.Failed>(owner.closeProfile(ProfileId("alpha")))
            prefs.refuse = false
            assertIs<Outcome.Ok<Unit>>(owner.reopenProfile(ProfileId("alpha")))
            val fresh = owner.open(ref())
            assertNotSame(old, fresh)
            runCurrent()
            assertTrue(fresh.state.value.ready)
            assertEquals("before removal", fresh.state.value.text)
            old.changed("late old handle")
            assertIs<Outcome.Failed>(old.flush())
            fresh.changed("fresh words")
            assertIs<Outcome.Ok<Unit>>(fresh.flush())
            assertIs<Outcome.Failed>(old.clearAfterSend(token))
            assertEquals("fresh words", store().draft("alpha", "same")?.text)
        }

    @Test
    fun `B-STATE-101 explicit recovery waits for cleanup and fences handles opened during closure`() =
        runTest {
            val gated = GatedPrefs(prefs)
            store(gated).save("alpha", "same", "before cleanup")
            val owner = facade(store(gated))
            val old = owner.open(ref())
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            gated.gate = gate
            val closing = async { owner.closeProfile(ProfileId("alpha")) }
            runCurrent()
            val during = owner.open(ref())
            runCurrent()
            val reopening = async { owner.reopenProfile(ProfileId("alpha")) }
            runCurrent()
            assertFalse(reopening.isCompleted)
            gate.complete(Unit)
            assertIs<Outcome.Ok<Unit>>(closing.await())
            assertIs<Outcome.Ok<Unit>>(reopening.await())
            assertIs<Outcome.Failed>(old.flush())
            assertIs<Outcome.Failed>(during.flush())
            val fresh = owner.open(ref())
            assertNotSame(during, fresh)
            runCurrent()
            fresh.changed("after cleanup")
            assertIs<Outcome.Ok<Unit>>(fresh.flush())
            assertEquals("after cleanup", store().draft("alpha", "same")?.text)
        }

    @Test
    fun `B-STATE-101 a queued text save rechecks write authority after entering the real store lane`() =
        runTest {
            val gated = GatedPrefs(prefs)
            val drafts = store(gated)
            drafts.save("alpha", "same", "current words")
            val gate = CompletableDeferred<Unit>()
            gated.gate = gate
            val holding = async { drafts.save("beta", "other", "other profile") }
            runCurrent()
            var allowed = true
            val stale = async { drafts.saveText("alpha", "same", "obsolete words") { allowed } }
            runCurrent()
            allowed = false
            gate.complete(Unit)
            assertIs<Outcome.Ok<Unit>>(holding.await())
            assertIs<Outcome.Failed>(stale.await())
            assertEquals("current words", drafts.draft("alpha", "same")?.text)
        }
}
