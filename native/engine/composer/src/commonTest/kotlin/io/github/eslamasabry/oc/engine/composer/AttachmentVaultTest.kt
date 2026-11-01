package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.engine.composer.vault.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AttachmentVaultTest {
    private val store = MemoryPayloadStore()
    private val vault = AttachmentVault(store)

    private suspend fun stored(owner: String, vararg a: PromptAttachment): List<AttachmentRef> =
        (vault.store(owner, a.toList()) as Outcome.Ok).value

    @Test
    fun `SHA-256 matches the Dart crypto digest`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun `B-STATE-016 attachments come back with exact Unicode content and no payload in metadata`() = runTest {
        val text = "مرحبا 👋 naïve ✓"
        val attachment = dataAttachment("note.txt", text)
        val refs = stored("alpha", attachment)
        val ref = refs.single()
        assertEquals(Sha256.hex(attachment.url), ref.blob)
        assertEquals(attachment.url.encodeToByteArray().size.toLong(), ref.bytes)
        assertEquals(null, ref.url, "the payload never sits in metadata")
        val back = vault.restore("alpha", refs, sameLocation = true)
        assertEquals(listOf(attachment), back.attachments)
        assertEquals(emptyList(), back.unavailable)
    }

    @Test
    fun `B-STATE-100 file and web URLs stay as metadata - other schemes cannot survive a restart`() = runTest {
        val refs = stored("alpha", fileAttachment("a.dart"), PromptAttachment("image/png", "b.png", "https://x.test/b.png"))
        assertEquals(listOf(null, null), refs.map { it.blob })
        assertEquals(0, store.size)
        val refused = vault.store("alpha", listOf(PromptAttachment("image/png", "c.png", "content://media/1")))
        assertEquals(Failure.StorageRefused(), (refused as Outcome.Failed).failure)
        // A file URL is restored only at the same location.
        val moved = vault.restore("alpha", refs, sameLocation = false)
        assertEquals(listOf("a.dart"), moved.unavailable)
        assertEquals(1, moved.attachments.size)
    }

    @Test
    fun `B-STATE-016 a changed or missing payload is named, never restored`() = runTest {
        val refs = stored("alpha", dataAttachment("a.txt", "one"), dataAttachment("b.txt", "two"))
        store.tamper(AttachmentVault.ownerKey("alpha"), refs[0].blob!!, "data:text/plain;base64,AAAA".encodeToByteArray().copyOf(refs[0].bytes.toInt()))
        store.delete(AttachmentVault.ownerKey("alpha"), refs[1].blob!!)
        val back = vault.restore("alpha", refs, sameLocation = true)
        assertEquals(emptyList(), back.attachments)
        assertEquals(listOf("a.txt", "b.txt"), back.unavailable)
        // Another owner's payload is not this owner's.
        val other = stored("beta", dataAttachment("c.txt", "three"))
        assertEquals(listOf("c.txt"), vault.restore("alpha", other, sameLocation = true).unavailable)
    }

    @Test
    fun `B-STATE-101 a corrupt reference never becomes a path or a key`() = runTest {
        val bad = AttachmentRef("x", "text/plain", blob = "../../etc/passwd", bytes = 4)
        val negative = AttachmentRef("y", "text/plain", blob = "a".repeat(64), bytes = -5)
        assertEquals(listOf("x", "y"), vault.restore("alpha", listOf(bad, negative), sameLocation = true).unavailable)
    }

    @Test
    fun `B-STATE-100 limits per prompt, per draft and per root`() = runTest {
        val six = (1..6).map { dataAttachment("$it.txt", "$it") }
        assertIs<Outcome.Failed>(vault.store("alpha", six))
        val big = PromptAttachment("text/plain", "big", "data:text/plain;base64," + "A".repeat(17 * 1024 * 1024))
        val twice = vault.store("alpha", listOf(big, big.copy(filename = "big2", url = big.url + "B")))
        assertEquals(Failure.StorageRefused(), (twice as Outcome.Failed).failure)
        store.failWrites = io.github.eslamasabry.oc.engine.composer.vault.PayloadFailure.Quota
        val quota = vault.store("alpha", listOf(dataAttachment("q.txt", "q")))
        assertEquals(Failure.StorageRefused(), (quota as Outcome.Failed).failure)
    }

    @Test
    fun `B-STATE-136 collection deletes only orphans and partial writes, per owner`() = runTest {
        val keep = stored("alpha", dataAttachment("keep.txt", "keep"))
        stored("alpha", dataAttachment("orphan.txt", "orphan"))
        val beta = stored("beta", dataAttachment("beta.txt", "beta"))
        store.addPartial(AttachmentVault.ownerKey("alpha"), "f".repeat(64), 10)
        assertTrue(vault.collect(mapOf("alpha" to keep), owner = "alpha"))
        assertEquals(
            setOf(
                "${AttachmentVault.ownerKey("alpha")}/${keep.single().blob}",
                "${AttachmentVault.ownerKey("beta")}/${beta.single().blob}",
            ),
            store.keys(),
        )
        store.failLists = true
        assertEquals(false, vault.collect(emptyMap()))
    }
}
