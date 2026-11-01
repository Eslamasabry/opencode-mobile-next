package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.drafts.DraftAutosave
import io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation
import io.github.eslamasabry.oc.engine.composer.drafts.DraftOpening
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.drafts.SessionDraft
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DraftsTest {
    private val prefs = MemoryComposerPrefs()
    private val payloads = MemoryPayloadStore()
    private val vault = AttachmentVault(payloads)
    private val profiles = TestProfiles("alpha", "beta")
    private val clock = TestClock()
    private val here = DraftLocation("/srv/app", null)

    private fun drafts() = Drafts(prefs, vault, profiles, clock)

    @Test
    fun `B-STATE-100 drafts persist and reload per server, and an empty draft clears the entry`() = runTest {
        val drafts = drafts()
        assertIs<Outcome.Ok<Unit>>(drafts.save("alpha", "s1", "half a thought"))
        assertIs<Outcome.Ok<Unit>>(drafts.save("beta", "s1", "same id, other server"))
        val restarted = drafts()
        assertEquals("half a thought", restarted.draft("alpha", "s1")?.text)
        assertEquals("same id, other server", restarted.draft("beta", "s1")?.text)
        restarted.save("alpha", "s1", "   ")
        assertNull(drafts().draft("alpha", "s1"))
        assertEquals("same id, other server", drafts().draft("beta", "s1")?.text)
    }

    @Test
    fun `B-STATE-100 the index is written in the Flutter format and a Flutter index reads unchanged`() = runTest {
        clock.millis = 5
        drafts().save("alpha", "s1", "hi")
        assertEquals("""[{"sessionID":"s1","profileID":"alpha","text":"hi","updatedAt":5}]""", prefs.string(Drafts.KEY))
        // As Flutter 2.1 wrote it: an older unattributed draft, a draft with a vault file and a file URL.
        val blob = "a".repeat(64)
        prefs.rawPut(
            Drafts.KEY,
            """[{"sessionID":"s9","profileID":"beta","text":"","updatedAt":9,""" +
                """"attachments":[{"filename":"p.png","mime":"image/png","blob":"$blob","bytes":12},""" +
                """{"filename":"a.dart","mime":"text/plain","url":"file:///srv/app/a.dart","bytes":0}],""" +
                """"directory":"/srv/app","workspace":null},{"sessionID":"old","text":"older","updatedAt":1}]""",
        )
        val read = drafts()
        val draft = read.draft("beta", "s9")!!
        assertEquals(listOf(blob, null), draft.attachments.map { it.blob })
        assertEquals("/srv/app", draft.directory)
        assertNull(read.draft("alpha", "old"), "two servers: an unattributed draft is ambiguous")
        profiles.ids.remove("beta")
        assertEquals("older", read.draft("alpha", "old")?.text, "one server: the older draft is its")
    }

    @Test
    fun `B-STATE-100 attachment-only drafts persist references`() = runTest {
        val drafts = drafts()
        val photo = dataAttachment("p.png", "png bytes", "image/png")
        drafts.save("alpha", "s1", "", listOf(photo), here)
        assertEquals(true, prefs.bool(Drafts.VAULT_FLAG), "the vault marker precedes any payload")
        val saved = drafts().draft("alpha", "s1")!!
        assertEquals("", saved.text)
        assertEquals(1, saved.attachments.size)
        val back = drafts().restoreAttachments("alpha", "s1", here)
        assertEquals(listOf(photo), back.attachments)
    }

    @Test
    fun `B-STATE-100 deleting a server collects only its files`() = runTest {
        val drafts = drafts()
        drafts.save("alpha", "s1", "a", listOf(dataAttachment("a.txt", "a")), here)
        drafts.save("beta", "s1", "b", listOf(dataAttachment("b.txt", "b")), here)
        prefs.rawPut(Drafts.KEY, prefs.string(Drafts.KEY)!!.dropLast(1) + """,{"sessionID":"legacy","text":"x","updatedAt":0}]""")
        assertTrue(drafts.closeProfile("alpha"))
        val left = drafts()
        assertNull(left.draft("alpha", "s1"))
        assertEquals(setOf(SessionDraft.keyFor("beta", "s1")), left.all().keys, "the unattributed draft goes too")
        assertEquals(setOf(AttachmentVault.ownerKey("beta")), payloads.keys().map { it.substringBefore('/') }.toSet())
    }

    @Test
    fun `B-STATE-101 a failed save keeps the last saved draft and a retry recovers`() = runTest {
        val drafts = drafts()
        drafts.save("alpha", "s1", "first")
        prefs.refuse = true
        assertEquals(Failure.Storage(StorageProblem.WriteRefused), (drafts.save("alpha", "s1", "second") as Outcome.Failed).failure)
        assertEquals("first", drafts().draft("alpha", "s1")?.text)
        prefs.refuse = false
        drafts.save("alpha", "s1", "second")
        assertEquals("second", drafts().draft("alpha", "s1")?.text)
    }

    @Test
    fun `B-STATE-101 a metadata failure keeps the previous bytes and drops the new ones`() = runTest {
        val drafts = drafts()
        val first = dataAttachment("one.txt", "one")
        drafts.save("alpha", "s1", "t", listOf(first), here)
        prefs.refuse = true
        assertIs<Outcome.Failed>(drafts.save("alpha", "s1", "t", listOf(dataAttachment("two.txt", "two")), here))
        prefs.refuse = false
        assertEquals(1, payloads.size)
        assertEquals(listOf(first), drafts.restoreAttachments("alpha", "s1", here).attachments)
    }

    @Test
    fun `B-STATE-101 caller metadata is snapshotted and saves keep their order`() = runTest {
        val drafts = drafts()
        val list = mutableListOf<PromptAttachment>(dataAttachment("a.txt", "a"))
        val first = async { drafts.save("alpha", "s1", "one", list, here) }
        list.add(dataAttachment("b.txt", "b"))
        val second = async { drafts.save("alpha", "s1", "two") }
        val removal = async { drafts.save("alpha", "s1", "") }
        first.await(); second.await(); removal.await()
        assertNull(drafts().draft("alpha", "s1"), "the remove was not overtaken by the stale saves")
    }

    @Test
    fun `B-STATE-101 late saves cannot recreate data after profile removal`() = runTest {
        val drafts = drafts()
        drafts.save("alpha", "s1", "a")
        val late = async { drafts.save("alpha", "s2", "late words", listOf(dataAttachment("x.txt", "x")), here) }
        assertTrue(drafts.closeProfile("alpha"))
        profiles.ids.remove("alpha")
        assertEquals(Failure.Refused(RefusalReason.ProfileDeleted), (late.await() as Outcome.Failed).failure)
        assertEquals(Failure.Refused(RefusalReason.ProfileDeleted), (drafts.save("alpha", "s3", "later") as Outcome.Failed).failure)
        assertIs<Outcome.Ok<Unit>>(drafts.save("alpha", "s3", ""), "clearing nothing is not an error")
        assertTrue(drafts().all().isEmpty())
        assertEquals(0, payloads.size)
    }

    @Test
    fun `B-STATE-102 a full store refuses a new draft without evicting unsent work`() = runTest {
        val drafts = drafts()
        repeat(Drafts.MAX_DRAFTS) {
            clock.millis += 1
            assertIs<Outcome.Ok<Unit>>(drafts.save("alpha", "s$it", "draft $it"))
        }
        assertEquals(Failure.Storage(StorageProblem.Full), (drafts.save("alpha", "new", "one more") as Outcome.Failed).failure)
        assertEquals(Drafts.MAX_DRAFTS, drafts().all().size)
        assertEquals("draft 0", drafts().draft("alpha", "s0")?.text)
        // Changing an existing draft still works.
        assertIs<Outcome.Ok<Unit>>(drafts.save("alpha", "s0", "edited"))
    }

    @Test
    fun `B-STATE-102 leaving with a draft that could not be saved is told, and a retry saves it`() = runTest {
        val drafts = drafts()
        val autosave = DraftAutosave(this, drafts, "alpha", "s1")
        prefs.refuse = true
        autosave.changed("unsaved words")
        assertFalse(autosave.settled.value)
        assertIs<Outcome.Failed>(autosave.flush())
        assertEquals(Failure.Storage(StorageProblem.WriteRefused), autosave.failure.value)
        assertFalse(autosave.settled.value)
        prefs.refuse = false
        assertIs<Outcome.Ok<Unit>>(autosave.flush())
        assertTrue(autosave.settled.value)
        assertEquals("unsaved words", drafts().draft("alpha", "s1")?.text)
    }

    @Test
    fun `B-STATE-100 autosave waits for a 600 ms pause in typing`() = runTest {
        val drafts = drafts()
        val autosave = DraftAutosave(this, drafts, "alpha", "s1")
        autosave.changed("h")
        advanceTimeBy(400)
        autosave.changed("hello")
        advanceTimeBy(599)
        runCurrent()
        assertNull(drafts.draft("alpha", "s1"))
        advanceTimeBy(2)
        runCurrent()
        assertEquals("hello", drafts.draft("alpha", "s1")?.text)
        assertTrue(autosave.settled.value)
    }

    @Test
    fun `B-STATE-101 an unreadable index is never overwritten`() = runTest {
        prefs.rawPut(Drafts.KEY, "{broken")
        val drafts = drafts()
        assertFalse(drafts.readable)
        assertIs<Outcome.Failed>(drafts.save("alpha", "s1", "words"))
        assertEquals("{broken", prefs.string(Drafts.KEY))
    }

    @Test
    fun `DraftRestoreTest rule 1 a draft that waits as an unconfirmed send is not restored`() = runTest {
        val drafts = drafts()
        drafts.save("alpha", "s1", "deploy it")
        assertEquals(null, drafts.restoreFor("alpha", "s1", listOf(" deploy it ")).text)
        assertEquals("deploy it", drafts.restoreFor("alpha", "s1", listOf("something else")).text)
    }

    @Test
    fun `DraftRestoreTest rule 2 share text replaces the saved draft for that opening`() = runTest {
        val drafts = drafts()
        drafts.save("alpha", "s1", "saved", listOf(dataAttachment("a.txt", "a")), here)
        val shared = drafts.restoreFor("alpha", "s1", emptyList(), DraftOpening(initialText = "shared words"))
        assertEquals("shared words", shared.text)
        assertFalse(shared.recoverSavedAttachments)
        val plain = drafts.restoreFor("alpha", "s1", emptyList())
        assertEquals("saved", plain.text)
        assertTrue(plain.recoverSavedAttachments)
        val sharedFile = drafts.restoreFor("alpha", "s1", emptyList(), DraftOpening(initialAttachments = listOf(fileAttachment("x"))))
        assertFalse(sharedFile.recoverSavedAttachments, "shared files replace the saved files")
    }

    @Test
    fun `DraftRestoreTest rule 3 missing attachments are named`() = runTest {
        val drafts = drafts()
        drafts.save("alpha", "s1", "t", listOf(dataAttachment("gone.txt", "g"), fileAttachment("a.dart")), here)
        payloads.keys().forEach { payloads.delete(it.substringBefore('/'), it.substringAfter('/')) }
        val back = drafts.restoreAttachments("alpha", "s1", here)
        assertEquals(listOf("gone.txt"), back.unavailable)
        assertEquals(listOf("a.dart"), back.attachments.map { it.filename })
    }

    @Test
    fun `DraftRestoreTest rule 5 watched and demo chats have no drafts`() = runTest {
        val drafts = drafts()
        drafts.save("alpha", "s1", "mine")
        val watched = drafts.restoreFor("alpha", "s1", emptyList(), DraftOpening(isolated = true))
        assertNull(watched.text)
        assertFalse(watched.tracksDraft)
    }
}
