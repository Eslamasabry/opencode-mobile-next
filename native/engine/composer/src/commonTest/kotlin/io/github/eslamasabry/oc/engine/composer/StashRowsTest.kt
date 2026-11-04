package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.references.ReviewReference
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferenceKind
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.stash.StashedPrompt
import io.github.eslamasabry.oc.engine.composer.stash.toJson
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flutter `test/stash_attachments_test.dart` and `test/stash_lifecycle_test.dart` rows on the native shelf
 * (BUILD-REST): file-backed payloads, legacy migration, damage reporting, the owner marker and profile deletion.
 */
class StashRowsTest {
    private val prefs = SelectivePrefs()
    private val payloads = CountingPayloadStore()
    private val profiles = TestProfiles("a", "b")
    private val data = dataAttachment("note.txt", "hello")
    private val serverFile = PromptAttachment("text/plain", "server.txt", "file:///workspace/server.txt")

    private fun shelf() = PromptShelf(prefs, AttachmentVault(payloads), profiles, Redactor())

    private fun prompt(id: String, attachments: List<PromptAttachment> = listOf(data)) =
        StashedPrompt(
            id = id,
            text = "Synthetic unsent work",
            createdAt = 123,
            directory = "/workspace",
            workspace = "workspace-a",
            attachments = attachments,
        )

    private fun key(profile: String, id: String) = PromptShelf.stashKey(profile, id)

    private fun marker(profile: String) = PromptShelf.ownerKey(profile)

    private fun list(shelf: PromptShelf, profile: String) = (shelf.stashes(profile) as Outcome.Ok).value

    private suspend fun restore(shelf: PromptShelf, profile: String, id: String, same: Boolean = true) =
        shelf.restoreAttachments(profile, id, sameLocation = same).getOrNull()!!

    /** A Flutter 1.x record with inline attachments, as an older build left it. */
    private fun putLegacy(profile: String, prompt: StashedPrompt): String {
        val raw = prompt.toJson().toString() + "\n"
        prefs.inner.rawPut(key(profile, prompt.id), raw)
        return raw
    }

    @Test
    fun `B-STATE-134 payloads live in files outside preferences and restart keeps every metadata field`() = runTest {
        val shelf = shelf()
        val withReference =
            prompt("first", listOf(data, serverFile)).copy(
                references = listOf(ReviewReference(id = "r1", kind = ReviewReferenceKind.Comment, path = "a.dart", comment = "Review this")),
            )
        assertIs<Outcome.Ok<Unit>>(shelf.stash("a", withReference))
        val raw = assertNotNull(prefs.string(key("a", "first")))
        assertFalse(data.url in raw, "the payload never sits in preferences")
        assertTrue("\"attachmentFormat\":1" in raw)
        assertTrue("\"attachments\":[]" in raw)
        val saved = list(shelf(), "a").single()
        assertEquals(2, saved.attachmentCount)
        assertEquals(listOf("note.txt", "server.txt"), saved.attachmentNames)
        assertEquals("/workspace", saved.directory)
        assertEquals("workspace-a", saved.workspace)
        assertEquals(123, saved.createdAt)
        assertEquals("Review this", saved.references.single().comment)
        assertTrue(saved.locationBound)
        val recovery = restore(shelf(), "a", "first")
        assertEquals(listOf(data.url, serverFile.url), recovery.attachments.map { it.url })
        assertEquals(emptyList(), recovery.unavailable)
    }

    @Test
    fun `B-STATE-134 migration from the legacy inline format replaces only acknowledged metadata and is idempotent`() = runTest {
        val shelf = shelf()
        putLegacy("a", prompt("legacy", listOf(serverFile, data)))
        assertEquals(emptyList(), shelf.migrateAttachments("a"))
        val first = assertNotNull(prefs.string(key("a", "legacy")))
        assertFalse(data.url in first)
        assertEquals(emptyList(), shelf.migrateAttachments("a"))
        assertEquals(first, prefs.string(key("a", "legacy")), "a second run changes nothing")
        assertEquals(listOf(serverFile.url, data.url), restore(shelf, "a", "legacy").attachments.map { it.url })
        assertEquals("legacy", list(shelf, "a").single().id)
    }

    @Test
    fun `B-STATE-134 a refused metadata write keeps the literal legacy record and a retry works`() = runTest {
        val shelf = shelf()
        val original = putLegacy("a", prompt("legacy"))
        prefs.inner.rawPut(marker("a"), true)
        prefs.refused += key("a", "legacy")
        assertEquals(listOf("legacy"), shelf.migrateAttachments("a"))
        assertEquals(original, prefs.string(key("a", "legacy")), "the legacy bytes are untouched")
        prefs.refused.clear()
        assertEquals(emptyList(), shelf.migrateAttachments("a"))
        assertEquals(data.url, restore(shelf, "a", "legacy").attachments.single().url)
    }

    @Test
    fun `B-STATE-134 oversized legacy attachment counts are deferred, never truncated or evicted`() = runTest {
        assertEquals(32L * 1024 * 1024, AttachmentVault.MAX_DRAFT_BYTES)
        assertEquals(256L * 1024 * 1024, AttachmentVault.MAX_DISK_BYTES)
        assertTrue(AttachmentVault.STASH_ROOT != AttachmentVault.DRAFT_ROOT)
        val shelf = shelf()
        val original = putLegacy("a", prompt("legacy", List(6) { data }))
        assertEquals(listOf("legacy"), shelf.migrateAttachments("a"))
        assertEquals(original, prefs.string(key("a", "legacy")))
        assertIs<Outcome.Failed>(shelf.stash("a", prompt("new", List(6) { data })))
        assertNull(prefs.string(key("a", "new")))
    }

    @Test
    fun `B-STATE-137 missing files and wrong-location references are named without consuming the source`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("mixed", listOf(data, serverFile)))
        val stored = list(shelf, "a").single()
        payloads.inner.delete(AttachmentVault.ownerKey("a"), stored.attachmentRefs.first().blob!!)
        val restored = restore(shelf, "a", "mixed", same = false)
        assertEquals(emptyList(), restored.attachments)
        assertEquals(listOf("note.txt", "server.txt"), restored.unavailable)
        val after = list(shelf, "a").single()
        assertEquals(2, after.attachmentCount)
        assertEquals("Synthetic unsent work", after.text)
        assertNotNull(prefs.string(key("a", "mixed")))
    }

    @Test
    fun `B-STATE-137 corrupt bytes stay unavailable until an explicit save repairs the shared blob`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("one"))
        val ref = list(shelf, "a").single().attachmentRefs.single()
        payloads.inner.tamper(AttachmentVault.ownerKey("a"), ref.blob!!, data.url.replace("aGVsbG8=", "aGVsbG9=").encodeToByteArray())
        val recovery = restore(shelf, "a", "one")
        assertEquals(emptyList(), recovery.attachments)
        assertEquals(listOf("note.txt"), recovery.unavailable)
        assertEquals("Synthetic unsent work", list(shelf, "a").single().text)
        shelf.stash("a", prompt("two"))
        assertEquals(data.url, restore(shelf, "a", "one").attachments.single().url)
    }

    @Test
    fun `B-STATE-137 unused shelves do no file I-O, including profile deletion`() = runTest {
        val shelf = shelf()
        assertEquals(emptyList(), shelf.migrateAttachments("a"))
        assertTrue(shelf.clearForProfile("a"))
        shelf.reopenProfile("a")
        shelf.stash("a", prompt("text", emptyList()))
        shelf.stash("a", prompt("url", listOf(serverFile)))
        assertEquals(emptyList(), shelf.migrateAttachments("a"))
        shelf.remove("a", "url")
        shelf.recordSent("a", "synthetic history")
        assertTrue(shelf.clearForProfile("a"))
        assertEquals(0, payloads.ioCount)
        assertNull(prefs.bool(marker("a")))
    }

    @Test
    fun `B-STATE-139 the owner marker is durable before the first payload is published`() = runTest {
        val shelf = shelf()
        var checked = 0
        payloads.beforeWrite = { _ ->
            assertEquals(true, prefs.bool(marker("a")), "marker first")
            assertNull(prefs.string(key("a", "first")), "metadata last")
            checked++
        }
        shelf.stash("a", prompt("first"))
        assertEquals(1, checked)
        assertTrue(shelf.clearForProfile("a"))
        assertNull(prefs.bool(marker("a")))
    }

    @Test
    fun `B-STATE-139 a refused owner marker prevents file publication and permits retry`() = runTest {
        val shelf = shelf()
        val original = putLegacy("a", prompt("legacy"))
        prefs.refused += marker("a")
        assertEquals(listOf("legacy"), shelf.migrateAttachments("a"))
        assertIs<Outcome.Failed>(shelf.stash("a", prompt("new")))
        assertEquals(original, prefs.string(key("a", "legacy")))
        assertNull(prefs.string(key("a", "new")))
        assertNull(prefs.bool(marker("a")))
        assertEquals(0, payloads.writes)
        prefs.refused.clear()
        assertEquals(emptyList(), shelf.migrateAttachments("a"))
        assertEquals(data.url, restore(shelf, "a", "legacy").attachments.single().url)
    }

    @Test
    fun `B-STATE-139 pre-marker files are never deleted when admission fails, and cleanup is retried after restart`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("old-files"))
        prefs.inner.rawPut(marker("a"), null)
        val original = prefs.string(key("a", "old-files"))
        prefs.refused += marker("a")
        assertIs<Outcome.Failed>(shelf.remove("a", "old-files"))
        assertFalse(shelf.clearForProfile("a"))
        assertEquals(original, prefs.string(key("a", "old-files")))
        shelf.reopenProfile("a")
        assertEquals(data.url, restore(shelf, "a", "old-files").attachments.single().url)
        prefs.refused.clear()
        payloads.failLists = true
        assertFalse(shelf.clearForProfile("a"))
        assertNull(prefs.string(key("a", "old-files")))
        assertEquals(true, prefs.bool(marker("a")), "the durable marker keeps the orphans discoverable")
        payloads.failLists = false
        assertTrue(shelf().clearForProfile("a"), "a restarted shelf finishes the cleanup")
        assertNull(prefs.bool(marker("a")))
        assertEquals(0, payloads.inner.size)
    }

    @Test
    fun `B-STATE-139 failed marker retirement stays discoverable and is retried after restart`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("one"))
        prefs.refused += marker("a")
        assertFalse(shelf.clearForProfile("a"))
        assertNull(prefs.string(key("a", "one")))
        assertEquals(true, prefs.bool(marker("a")))
        prefs.refused.clear()
        assertTrue(shelf().clearForProfile("a"))
        assertNull(prefs.bool(marker("a")))
    }

    @Test
    fun `B-STATE-139 an unacknowledged owner marker never permits new publication`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("old-files"))
        val writes = payloads.writes
        prefs.inner.rawPut(marker("a"), null)
        prefs.refused += marker("a")
        assertIs<Outcome.Failed>(shelf.stash("a", prompt("new")))
        assertEquals(writes, payloads.writes, "only the original acknowledged file")
        assertNull(prefs.string(key("a", "new")))
        assertNull(prefs.bool(marker("a")))
        prefs.refused.clear()
        val restarted = shelf()
        assertEquals(data.url, restore(restarted, "a", "old-files").attachments.single().url)
        assertTrue(restarted.clearForProfile("a"))
    }

    @Test
    fun `B-SEC-172 profile deletion closes admission and drains a pending save before cleanup finishes`() = runTest {
        val shelf = shelf()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        payloads.writeEntered = entered
        payloads.writeGate = release
        val pending = async { shelf.stash("a", prompt("pending")) }
        entered.await()
        val deletion = async { shelf.clearForProfile("a") }
        testScheduler.runCurrent()
        // Admission is closed at once: a late save and a late history write are refused.
        val late = async { shelf.stash("a", prompt("late")) }
        val lateHistory = async { shelf.recordSent("a", "Late callback") }
        assertFalse(deletion.isCompleted, "deletion waits for the pending save")
        release.complete(Unit)
        assertEquals(Failure.Refused(RefusalReason.ProfileDeleted), (pending.await() as Outcome.Failed).failure)
        assertTrue(deletion.await())
        assertIs<Outcome.Failed>(late.await())
        assertIs<Outcome.Failed>(lateHistory.await())
        assertEquals(0, payloads.inner.size, "the drained payload is collected")
        assertNull(prefs.bool(marker("a")))
        assertNull(prefs.string(key("a", "pending")))
        assertNull(prefs.string(key("a", "late")))
        assertNull(prefs.string(PromptShelf.historyKey("a")))
    }

    @Test
    fun `B-SEC-172 profile deletion drains a pending migration`() = runTest {
        val shelf = shelf()
        putLegacy("a", prompt("pending"))
        putLegacy("a", prompt("second"))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        payloads.writeEntered = entered
        payloads.writeGate = release
        val migration = async { shelf.migrateAttachments("a") }
        entered.await()
        val deletion = async { shelf.clearForProfile("a") }
        testScheduler.runCurrent()
        release.complete(Unit)
        assertEquals(2, migration.await().size, "nothing was published after admission closed")
        assertTrue(deletion.await())
        assertEquals(1, payloads.writes)
        assertEquals(0, payloads.inner.size)
        assertNull(prefs.string(key("a", "pending")))
        assertNull(prefs.bool(marker("a")))
    }

    @Test
    fun `B-SEC-172 failed cleanup retains the marker so the caller keeps the profile, and a restart retries orphans`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("saved"))
        shelf.recordSent("a", "Old sent history")
        shelf.stash("b", prompt("saved"))
        payloads.failLists = true
        // False: the deletion coordinator keeps the profile and its secret (B-SEC-172 "never deletes the secret early").
        assertFalse(shelf.clearForProfile("a"))
        assertEquals(true, prefs.bool(marker("a")))
        assertNull(prefs.string(key("a", "saved")))
        assertNull(prefs.string(PromptShelf.historyKey("a")))
        assertEquals(2, payloads.inner.size)
        payloads.failLists = false
        assertTrue(shelf().clearForProfile("a"))
        assertNull(prefs.bool(marker("a")))
        assertEquals(1, payloads.inner.size)
        assertEquals(data.url, restore(shelf(), "b", "saved").attachments.single().url)
    }

    @Test
    fun `B-SEC-172 pre-marker references survive failed admission and keep their saved work`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("old"))
        prefs.inner.rawPut(marker("a"), null)
        val original = prefs.string(key("a", "old"))
        prefs.refused += marker("a")
        assertFalse(shelf.clearForProfile("a"))
        assertEquals(original, prefs.string(key("a", "old")))
        shelf.reopenProfile("a")
        assertEquals(1, list(shelf, "a").single().attachmentCount)
        assertEquals(data.url, restore(shelf, "a", "old").attachments.single().url)
        prefs.refused.clear()
        assertTrue(shelf.clearForProfile("a"))
        assertEquals(0, payloads.inner.size)
    }
}
