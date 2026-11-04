package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Flutter `test/draft_attachments_test.dart` rows on the native vault (BUILD-REST). */
class DraftAttachmentVaultRowsTest {
    private val store = MemoryPayloadStore()
    private val vault = AttachmentVault(store)
    private val attachment = dataAttachment("ملاحظة.txt", "Hello مرحبا 🌍")

    private suspend fun stored(owner: String, vararg a: PromptAttachment): List<AttachmentRef> =
        (vault.store(owner, a.toList()) as Outcome.Ok).value

    @Test
    fun `B-STATE-017 shared bytes stay until the last reference is removed and each owner cleans up on its own`() = runTest {
        val a = stored("a", attachment)
        val b = stored("b", attachment)
        // Duplicate references in one owner, and the same bytes in another owner, all keep.
        assertTrue(vault.collect(mapOf("a" to a + a, "b" to b)))
        assertTrue(vault.collect(mapOf("a" to a, "b" to b)))
        assertEquals(1, vault.restore("a", a, sameLocation = true).attachments.size)
        // Owner a drops its reference: only a's copy goes; b's copy is untouched.
        assertTrue(vault.collect(mapOf("b" to b), owner = "a"))
        assertEquals(1, vault.restore("a", a, sameLocation = true).unavailable.size)
        assertEquals(listOf(attachment), vault.restore("b", b, sameLocation = true).attachments)
    }

    @Test
    fun `B-STATE-018 corrupt bytes are reported and a later save repairs them`() = runTest {
        val refs = stored("a", attachment)
        val ref = refs.single()
        val owner = AttachmentVault.ownerKey("a")
        store.tamper(owner, ref.blob!!, ByteArray(ref.bytes.toInt()) { 120 })
        assertEquals(listOf(attachment.filename), vault.restore("a", refs, sameLocation = true).unavailable)
        // A save of the same attachment rewrites the payload.
        stored("a", attachment)
        assertEquals(attachment.url, vault.restore("a", refs, sameLocation = true).attachments.single().url)
        // A deleted payload is reported again.
        store.delete(owner, ref.blob!!)
        assertEquals(listOf(attachment.filename), vault.restore("a", refs, sameLocation = true).unavailable)
    }

    @Test
    fun `B-SEC-061 project file references need the same location and are never read locally`() = runTest {
        val refs =
            stored(
                "a",
                PromptAttachment("text/plain", "server.txt", "file:///server/project/server.txt"),
                PromptAttachment("text/plain", "web.txt", "https://example.com/web.txt"),
            )
        assertEquals(2, vault.restore("a", refs, sameLocation = true).attachments.size)
        val moved = vault.restore("a", refs, sameLocation = false)
        assertEquals(listOf("server.txt"), moved.unavailable)
        assertEquals("web.txt", moved.attachments.single().filename)
        // Nothing was written or read locally for a reference.
        assertEquals(0, store.size)
    }

    @Test
    fun `B-SEC-061 invalid identities and temporary references cannot escape the attachment store`() = runTest {
        val bad = AttachmentRef("bad", "text/plain", blob = "../../outside", bytes = 2)
        assertEquals(listOf("bad"), vault.restore("a", listOf(bad), sameLocation = true).unavailable)
        val temporary = vault.store("a", listOf(PromptAttachment("text/plain", "temporary", "content://temporary/file")))
        assertIs<Outcome.Failed>(temporary)
        assertEquals(0, store.size)
        // Collection only ever touches the vault's own payload entries.
        val kept = stored("b", attachment)
        assertTrue(vault.collect(emptyMap(), owner = "a"))
        assertEquals(listOf(attachment), vault.restore("b", kept, sameLocation = true).attachments)
    }
}
