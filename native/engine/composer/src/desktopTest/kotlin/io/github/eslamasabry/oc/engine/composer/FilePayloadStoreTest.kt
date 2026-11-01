package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.FilePayloadStore
import io.github.eslamasabry.oc.engine.composer.vault.Sha256
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.files.Path

class FilePayloadStoreTest {
    private val root: File =
        Files.createTempDirectory("vault").toFile().resolve(AttachmentVault.DRAFT_ROOT)

    private fun TestScope.store() =
        FilePayloadStore(Path(root.path), StandardTestDispatcher(testScheduler))

    @Test
    fun `B-STATE-016 a vault written by the Flutter build reads unchanged`() = runTest {
        // What FileDraftAttachmentStorage wrote: <root>/<sha256(owner)>/<sha256(url)>.blob holding
        // the data URL bytes.
        val url =
            "data:text/plain;base64,${java.util.Base64.getEncoder().encodeToString("مرحبا ✓".toByteArray())}"
        val ownerDir = root.resolve(Sha256.hex("alpha")).apply { mkdirs() }
        ownerDir.resolve("${Sha256.hex(url)}.blob").writeBytes(url.toByteArray())
        ownerDir
            .resolve("${"f".repeat(64)}.tmp")
            .writeBytes(byteArrayOf(1, 2)) // a write the process died on
        val prefs = MemoryComposerPrefs()
        prefs.rawPut(Drafts.VAULT_FLAG, true)
        prefs.rawPut(
            Drafts.KEY,
            """[{"sessionID":"s1","profileID":"alpha","text":"t","updatedAt":1,"attachments":[{"filename":"n.txt",""" +
                """"mime":"text/plain","blob":"${Sha256.hex(url)}","bytes":${url.toByteArray().size}}],"directory":"/srv","workspace":null}]""",
        )
        val drafts = Drafts(prefs, AttachmentVault(store()), TestProfiles("alpha"), TestClock())
        val back = drafts.restoreAttachments("alpha", "s1", DraftLocation("/srv", null))
        assertEquals(listOf(PromptAttachment("text/plain", "n.txt", url)), back.attachments)
        assertEquals(listOf(false, true), store().list().map { it.committed }.sorted())
    }

    @Test
    fun `B-STATE-136 writes publish atomically in the Flutter layout and collection removes partial files`() =
        runTest {
            val vault = AttachmentVault(store())
            val attachment = dataAttachment("a.txt", "hello")
            val ref = vault.storeOrThrowForTest("beta", attachment)
            val file = root.resolve(Sha256.hex("beta")).resolve("${ref}.blob")
            assertTrue(file.isFile)
            assertEquals(attachment.url, file.readText())
            assertFalse(
                root.resolve(Sha256.hex("beta")).listFiles()!!.any { it.name.endsWith(".tmp") }
            )
            root
                .resolve(Sha256.hex("beta"))
                .resolve("${"e".repeat(64)}.tmp")
                .writeBytes(byteArrayOf(0))
            root
                .resolve(Sha256.hex("beta"))
                .resolve("notes.txt")
                .writeText("not ours") // never a payload
            assertTrue(
                vault.collect(
                    mapOf(
                        "beta" to
                            listOf(
                                io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef(
                                    "a.txt",
                                    "text/plain",
                                    ref,
                                    null,
                                    attachment.url.length.toLong(),
                                )
                            )
                    )
                )
            )
            assertEquals(
                setOf("$ref.blob", "notes.txt"),
                root.resolve(Sha256.hex("beta")).list()!!.toSet(),
            )
        }

    @Test
    fun `COMPE-5 filesystem listing IO failure is a refused draft save`() = runTest {
        assertSaveRefusedOnIo("list")
    }

    @Test
    fun `COMPE-5 filesystem metadata IO failure is a refused draft save`() = runTest {
        assertSaveRefusedOnIo("length")
    }

    @Test
    fun `COMPE-5 filesystem source IO failure is a refused draft save`() = runTest {
        assertSaveRefusedOnIo("read")
    }

    private suspend fun TestScope.assertSaveRefusedOnIo(operation: String) {
        val attachment = dataAttachment("a.txt", "hello")
        if (operation == "read") AttachmentVault(store()).storeOrThrowForTest("alpha", attachment)
        root.mkdirs()
        val failing = ThrowingFileSystem(operation, IOException("Read refused"))
        val prefs = MemoryComposerPrefs()
        val vault =
            AttachmentVault(
                FilePayloadStore(Path(root.path), StandardTestDispatcher(testScheduler), failing)
            )
        val drafts = Drafts(prefs, vault, TestProfiles("alpha"), TestClock())
        val result = drafts.save("alpha", "s1", "keep this input", listOf(attachment))
        assertIs<Failure.StorageRefused>(assertIs<Outcome.Failed>(result).failure)
        assertEquals(
            null,
            Drafts(prefs, AttachmentVault(store()), TestProfiles("alpha"), TestClock())
                .draft("alpha", "s1"),
        )
    }

    @Test
    fun `COMPE-5 filesystem cancellation propagates from payload operations`() = runTest {
        val cancelled = ThrowingFileSystem("exists", CancellationException("Cancelled"))
        val store =
            FilePayloadStore(Path(root.path), StandardTestDispatcher(testScheduler), cancelled)
        val owner = "a".repeat(64)
        val id = "b".repeat(64)
        assertFailsWith<CancellationException> { store.list() }
        assertFailsWith<CancellationException> {
            FilePayloadStore(
                    Path(root.path),
                    StandardTestDispatcher(testScheduler),
                    ThrowingFileSystem("length", CancellationException("Cancelled")),
                )
                .length(owner, id)
        }
        assertFailsWith<CancellationException> { store.read(owner, id, 1) }
    }

    private suspend fun AttachmentVault.storeOrThrowForTest(
        owner: String,
        a: PromptAttachment,
    ): String =
        (store(owner, listOf(a)) as io.github.eslamasabry.oc.core.common.Outcome.Ok)
            .value
            .single()
            .blob!!
}
