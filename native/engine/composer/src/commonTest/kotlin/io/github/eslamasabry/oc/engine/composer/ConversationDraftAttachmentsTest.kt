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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationDraftAttachmentsTest {
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
    fun `B-STATE-016 attach persists and a restart restores exact text and bytes without payload in metadata`() =
        runTest {
            val note = dataAttachment("ملاحظة.txt", "Hello مرحبا 🌍")
            val handle = facade().open(ref())
            runCurrent()
            handle.changed("  spaced words\n")
            assertIs<Outcome.Ok<Unit>>(handle.replaceAttachments(listOf(note)))
            assertEquals(listOf(note), handle.state.value.attachments)
            assertEquals(handle.state.value.attachments, handle.state.value.savedAttachments)
            assertEquals(1, assertNotNull(store().draft("alpha", "same")).attachments.size)
            assertFalse(assertNotNull(prefs.string(Drafts.KEY)).contains("data:"))
            val restarted = facade().open(ref())
            runCurrent()
            assertTrue(restarted.state.value.ready)
            assertEquals("  spaced words\n", restarted.state.value.text)
            assertEquals("  spaced words\n", restarted.state.value.saved)
            assertEquals(listOf(note), restarted.state.value.attachments)
            assertEquals(
                restarted.state.value.attachments,
                restarted.state.value.savedAttachments,
            )
        }

    @Test
    fun `B-STATE-100 add and remove persist across restarts and collected payloads leave nothing behind`() =
        runTest {
            val first = dataAttachment("first.txt", "first bytes")
            val second = dataAttachment("second.txt", "second bytes")
            val handle = facade().open(ref())
            runCurrent()
            assertIs<Outcome.Ok<Unit>>(handle.replaceAttachments(listOf(first)))
            val reopened = facade().open(ref())
            runCurrent()
            assertEquals(listOf(first), reopened.state.value.attachments)
            assertIs<Outcome.Ok<Unit>>(reopened.replaceAttachments(listOf(first, second)))
            val both = facade().open(ref())
            runCurrent()
            assertEquals(listOf(first, second), both.state.value.attachments)
            both.changed("kept words")
            assertIs<Outcome.Ok<Unit>>(both.replaceAttachments(emptyList()))
            assertEquals(0, assertNotNull(store().draft("alpha", "same")).attachments.size)
            assertTrue(payloads.keys().isEmpty())
            val cleared = facade().open(ref())
            runCurrent()
            assertEquals("kept words", cleared.state.value.text)
            assertTrue(cleared.state.value.attachments.isEmpty())
            assertTrue(cleared.state.value.savedAttachments.isEmpty())
        }

    @Test
    fun `B-STATE-101 a missing payload blocks the session without overwriting metadata or bytes`() =
        runTest {
            val handle = facade().open(ref())
            runCurrent()
            handle.changed("with a file")
            assertIs<Outcome.Ok<Unit>>(
                handle.replaceAttachments(listOf(dataAttachment("keep.txt", "bytes"))),
            )
            val original = prefs.string(Drafts.KEY)
            val blob = assertNotNull(store().draft("alpha", "same")).attachments.single().blob
            payloads.delete(AttachmentVault.ownerKey("alpha"), assertNotNull(blob))
            val commits = prefs.commits
            val restarted = facade().open(ref())
            runCurrent()
            assertFalse(restarted.state.value.ready)
            assertIs<Failure.StorageRefused>(restarted.state.value.failure)
            restarted.changed("")
            assertIs<Outcome.Failed>(restarted.flush())
            assertEquals(original, prefs.string(Drafts.KEY))
            assertEquals(commits, prefs.commits)
            assertEquals(
                "with a file",
                store().draft("alpha", "same")?.text,
                "the saved words stay untouched behind the block",
            )
        }

    @Test
    fun `B-STATE-101 an older token never clears a same-text replacement carrying new files`() =
        runTest {
            val handle = facade().open(ref())
            runCurrent()
            handle.changed("hello")
            val first = dataAttachment("first.txt", "one")
            val second = dataAttachment("second.txt", "two")
            assertIs<Outcome.Ok<Unit>>(handle.replaceAttachments(listOf(first)))
            val oldToken = assertNotNull(handle.captureSubmission())
            assertIs<Outcome.Ok<Unit>>(handle.replaceAttachments(listOf(second)))
            val current = assertNotNull(handle.captureSubmission())
            assertIs<Outcome.Ok<Unit>>(handle.clearAfterSend(oldToken))
            assertEquals("hello", handle.state.value.text)
            assertEquals(listOf(second), handle.state.value.attachments)
            assertEquals(listOf(second), handle.state.value.savedAttachments)
            assertIs<Outcome.Ok<Unit>>(handle.clearAfterSend(current))
            assertEquals("", handle.state.value.text)
            assertTrue(handle.state.value.attachments.isEmpty())
            assertNull(store().draft("alpha", "same"))
            assertTrue(payloads.keys().isEmpty())
        }

    @Test
    fun `B-STATE-100 profile deletion fences a late file result and collects only owned payloads`() =
        runTest {
            assertIs<Outcome.Ok<Unit>>(
                store().save("beta", "same", "other profile", listOf(dataAttachment("other.txt", "x"))),
            )
            val gated = GatedPrefs(prefs)
            val owner = facade(store(gated))
            val handle = owner.open(ref())
            runCurrent()
            handle.changed("in flight")
            val gate = CompletableDeferred<Unit>()
            gated.gate = gate
            val replacing =
                async { handle.replaceAttachments(listOf(dataAttachment("late.txt", "late"))) }
            runCurrent()
            val closing = async { owner.closeProfile(ProfileId("alpha")) }
            runCurrent()
            assertFalse(handle.state.value.ready)
            assertTrue(handle.state.value.attachments.isEmpty())
            gate.complete(Unit)
            assertIs<Outcome.Failed>(replacing.await())
            assertIs<Outcome.Ok<Unit>>(closing.await())
            assertIs<Outcome.Failed>(handle.flush())
            assertNull(store().draft("alpha", "same"))
            assertEquals("other profile", store().draft("beta", "same")?.text)
            assertEquals(
                setOf(AttachmentVault.ownerKey("beta")),
                payloads.keys().map { it.substringBefore('/') }.toSet(),
            )
        }

    @Test
    fun `B-STATE-101 a refused commit keeps chips unpublished and a retry commits them`() = runTest {
        val handle = facade().open(ref())
        runCurrent()
        handle.changed("with words")
        prefs.refuse = true
        assertIs<Outcome.Failed>(
            handle.replaceAttachments(listOf(dataAttachment("new.txt", "bytes"))),
        )
        assertIs<Failure.StorageRefused>(handle.state.value.failure)
        assertTrue(handle.state.value.attachments.isEmpty())
        assertTrue(handle.state.value.savedAttachments.isEmpty())
        assertNull(store().draft("alpha", "same"))
        prefs.refuse = false
        assertIs<Outcome.Ok<Unit>>(
            handle.replaceAttachments(listOf(dataAttachment("new.txt", "bytes"))),
        )
        assertEquals(1, handle.state.value.attachments.size)
        assertNull(handle.state.value.failure)
        assertEquals(1, assertNotNull(store().draft("alpha", "same")).attachments.size)
        val restarted = facade().open(ref())
        runCurrent()
        assertEquals(handle.state.value.attachments, restarted.state.value.attachments)
    }

    @Test
    fun `B-STATE-101 a file commit preserves newer text edits typed during its write`() = runTest {
        val gated = GatedPrefs(prefs)
        val handle = facade(store(gated)).open(ref())
        runCurrent()
        handle.changed("old")
        assertIs<Outcome.Ok<Unit>>(handle.flush())
        val gate = CompletableDeferred<Unit>()
        gated.gate = gate
        val replacing =
            async { handle.replaceAttachments(listOf(dataAttachment("f.txt", "bytes"))) }
        runCurrent()
        handle.changed("newer")
        assertTrue(handle.state.value.attachments.isEmpty(), "chips appear only after the commit")
        gate.complete(Unit)
        assertIs<Outcome.Ok<Unit>>(replacing.await())
        assertEquals("newer", handle.state.value.text)
        assertEquals(1, handle.state.value.attachments.size)
        assertEquals(1, handle.state.value.savedAttachments.size)
        assertIs<Outcome.Ok<Unit>>(handle.flush())
        val saved = assertNotNull(store().draft("alpha", "same"))
        assertEquals("newer", saved.text)
        assertEquals(1, saved.attachments.size)
    }

    @Test
    fun `B-STATE-100 files-only submissions are captured without text and clear for the exact revision`() =
        runTest {
            val handle = facade().open(ref())
            runCurrent()
            assertNull(handle.captureSubmission())
            val file = dataAttachment("only.txt", "only bytes")
            assertIs<Outcome.Ok<Unit>>(handle.replaceAttachments(listOf(file)))
            val token = assertNotNull(handle.captureSubmission())
            assertEquals("", token.text)
            assertEquals(listOf(file), token.attachments)
            assertSame(token, handle.captureSubmission())
            assertIs<Outcome.Ok<Unit>>(handle.clearAfterSend(token))
            assertEquals("", handle.state.value.text)
            assertTrue(handle.state.value.attachments.isEmpty())
            assertNull(store().draft("alpha", "same"))
            assertTrue(payloads.keys().isEmpty())
        }

    @Test
    fun `B-STATE-101 a vault refusal keeps the current chips and a flush recovers the session`() =
        runTest {
            val handle = facade().open(ref())
            runCurrent()
            handle.changed("kept words")
            assertIs<Outcome.Ok<Unit>>(
                handle.replaceAttachments(listOf(dataAttachment("ok.txt", "ok"))),
            )
            val tooMany =
                (1..AttachmentVault.MAX_ATTACHMENTS).map { dataAttachment("n$it.txt", "n$it") } +
                    dataAttachment("extra.txt", "extra")
            assertIs<Outcome.Failed>(handle.replaceAttachments(tooMany))
            assertIs<Failure.StorageRefused>(handle.state.value.failure)
            assertEquals(1, handle.state.value.attachments.size)
            val saved = assertNotNull(store().draft("alpha", "same"))
            assertEquals("kept words", saved.text)
            assertEquals(1, saved.attachments.size)
            assertIs<Outcome.Ok<Unit>>(handle.flush())
            assertNull(handle.state.value.failure)
            assertEquals(1, handle.state.value.attachments.size)
        }

    // ---- RUNBOOK §7 finding 7: the draft keeps its directory/workspace binding ----

    private val here = io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation("/work/a", "ws1")
    private val elsewhere = io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation("/work/b", "ws1")
    private val projectFile =
        io.github.eslamasabry.oc.core.model.PromptAttachment(
            "text/plain",
            "main.kt",
            "file:///work/a/main.kt",
        )

    @Test
    fun `B-STATE-016 finding 7 a whole-state write records the conversation's location`() = runTest {
        val handle = facade().open(ref())
        handle.bindLocation(here)
        runCurrent()
        handle.changed("see @main.kt")
        assertIs<Outcome.Ok<Unit>>(handle.replaceAttachments(listOf(projectFile)))
        val saved = assertNotNull(store().draft("alpha", "same"))
        assertEquals("/work/a", saved.directory)
        assertEquals("ws1", saved.workspace)
    }

    @Test
    fun `B-STATE-016 finding 7 a file reference restores only in its own location and fails closed elsewhere`() =
        runTest {
            val first = facade().open(ref())
            first.bindLocation(here)
            runCurrent()
            first.changed("see @main.kt")
            assertIs<Outcome.Ok<Unit>>(first.replaceAttachments(listOf(projectFile)))
            val original = prefs.string(Drafts.KEY)

            val moved = facade().open(ref())
            runCurrent()
            assertFalse(moved.state.value.ready, "a file reference waits for the conversation's location")
            moved.bindLocation(elsewhere)
            runCurrent()
            assertFalse(moved.state.value.ready)
            assertNotNull(moved.state.value.failure, "another place's file is never restored")
            assertEquals(original, prefs.string(Drafts.KEY), "the saved draft is preserved")

            val back = facade().open(ref())
            back.bindLocation(here)
            runCurrent()
            assertTrue(back.state.value.ready)
            assertEquals(listOf(projectFile), back.state.value.attachments)
        }

    @Test
    fun `B-STATE-016 finding 7 data files restore without waiting for a location`() = runTest {
        val note = dataAttachment("n.txt", "bytes")
        val first = facade().open(ref())
        runCurrent()
        assertIs<Outcome.Ok<Unit>>(first.replaceAttachments(listOf(note)))
        val again = facade().open(ref())
        runCurrent()
        assertTrue(again.state.value.ready)
        assertEquals(listOf(note), again.state.value.attachments)
    }
}
