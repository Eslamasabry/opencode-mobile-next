package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.references.ReviewReference
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferenceKind
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.stash.StashedPrompt
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class StashTest {
    private val prefs = MemoryComposerPrefs()
    private val payloads = MemoryPayloadStore()
    private val draftPayloads = MemoryPayloadStore()
    private val profiles = TestProfiles("a", "b")

    private fun shelf() = PromptShelf(prefs, AttachmentVault(payloads), profiles, Redactor())

    private fun prompt(
        id: String,
        text: String = "prompt $id",
        createdAt: Long = id.hashCode().toLong(),
    ) = StashedPrompt(id = id, text = text, createdAt = createdAt, directory = "/srv/app")

    private fun list(shelf: PromptShelf, profile: String) =
        (shelf.stashes(profile) as Outcome.Ok).value

    @Test
    fun `B-CHAT-088 saved prompts survive reload with full attachments and references, apart from drafts and other profiles`() =
        runTest {
            val shelf = shelf()
            val photo = dataAttachment("p.png", "png", "image/png")
            val reference =
                ReviewReference(
                    "r1",
                    ReviewReferenceKind.Hunk,
                    "lib/a.dart",
                    lineLabel = "new line 3",
                    snippet = "+x",
                )
            assertIs<Outcome.Ok<Unit>>(
                shelf.stash(
                    "a",
                    prompt("1")
                        .copy(
                            attachments = listOf(photo, fileAttachment("a.dart")),
                            references = listOf(reference),
                        ),
                )
            )
            val reloaded = list(shelf(), "a").single()
            assertEquals(2, reloaded.attachmentRefs.size)
            assertEquals(listOf(reference), reloaded.references)
            assertTrue(reloaded.locationBound)
            assertEquals(
                listOf(photo, fileAttachment("a.dart")),
                shelf().restoreAttachments("a", "1", sameLocation = true).getOrNull()!!.attachments,
            )
            assertEquals(emptyList(), list(shelf, "b"))
            assertEquals(null, prefs.string(Drafts.KEY))
            assertEquals(0, draftPayloads.size, "the stash never writes into the draft vault")
        }

    @Test
    fun `B-CHAT-088 50 saved prompts never evict one and history is bounded separately`() =
        runTest {
            val shelf = shelf()
            repeat(PromptShelf.CAPACITY) {
                assertIs<Outcome.Ok<Unit>>(shelf.stash("a", prompt("$it", createdAt = it.toLong())))
            }
            assertEquals(
                Failure.StorageRefused(),
                (shelf.stash("a", prompt("extra")) as Outcome.Failed).failure,
            )
            assertEquals(PromptShelf.CAPACITY, list(shelf, "a").size)
            repeat(60) { shelf.recordSent("a", "sent $it") }
            assertEquals(50, shelf.history("a").size)
            assertEquals("sent 59", shelf.history("a").first())
            assertEquals(PromptShelf.CAPACITY, list(shelf, "a").size)
            shelf.recordSent("a", "sent 10")
            assertEquals("sent 10", shelf.history("a").first())
            assertEquals(1, shelf.history("a").count { it == "sent 10" })
        }

    @Test
    fun `B-CHAT-088 a failed save keeps the acknowledged state and a later write recovers`() =
        runTest {
            val shelf = shelf()
            prefs.refuse = true
            assertIs<Outcome.Failed>(
                shelf.stash(
                    "a",
                    prompt("1").copy(attachments = listOf(dataAttachment("x.txt", "x"))),
                )
            )
            prefs.refuse = false
            assertEquals(emptyList(), list(shelf, "a"))
            assertIs<Outcome.Ok<Unit>>(shelf.stash("a", prompt("2")))
            assertEquals(listOf("2"), list(shelf, "a").map { it.id })
        }

    @Test
    fun `B-CHAT-088 late sends do not recreate deleted history`() = runTest {
        val shelf = shelf()
        shelf.recordSent("a", "hello")
        assertTrue(shelf.clearForProfile("a"))
        assertEquals(
            Failure.Refused(RefusalReason.ProfileDeleted),
            (shelf.recordSent("a", "late") as Outcome.Failed).failure,
        )
        assertFalse(prefs.contains(PromptShelf.historyKey("a")))
    }

    @Test
    fun `B-CHAT-088 a Flutter saved prompt with inline legacy attachments reads unchanged`() =
        runTest {
            prefs.rawPut(
                PromptShelf.stashKey("a", "old"),
                """{"id":"old","text":"legacy","createdAt":7,"directory":null,"workspace":null,""" +
                    """"attachments":[{"type":"file","mime":"text/plain","filename":"n.txt","url":"data:text/plain;base64,aGk="}],"references":[]}""",
            )
            val legacy = list(shelf(), "a").single()
            assertEquals("n.txt", legacy.attachments.single().filename)
            assertEquals(
                listOf("n.txt"),
                shelf()
                    .restoreAttachments("a", "old", sameLocation = false)
                    .getOrNull()!!
                    .attachments
                    .map { it.filename },
            )
        }

    @Test
    fun `B-STATE-136 an unreadable shelf is refused and nothing it references is collected`() =
        runTest {
            val shelf = shelf()
            shelf.stash("a", prompt("1").copy(attachments = listOf(dataAttachment("x.txt", "x"))))
            assertEquals(1, payloads.size)
            prefs.rawPut(PromptShelf.stashKey("a", "bad"), "{not json")
            assertEquals(Failure.StorageRefused(), (shelf.stashes("a") as Outcome.Failed).failure)
            assertIs<Outcome.Failed>(shelf.stash("a", prompt("2")))
            assertIs<Outcome.Failed>(shelf.remove("a", "1"))
            assertEquals(1, payloads.size, "corrupt metadata is no permission to discard payloads")
        }

    @Test
    fun `B-STATE-136 an unknown metadata version or a corrupt blob reference suppresses collection`() =
        runTest {
            val shelf = shelf()
            shelf.stash("a", prompt("1").copy(attachments = listOf(dataAttachment("x.txt", "x"))))
            prefs.rawPut(
                PromptShelf.stashKey("a", "v2"),
                """{"id":"v2","text":"t","createdAt":1,"attachments":[],"attachmentFormat":2,"attachmentRefs":[],"references":[]}""",
            )
            assertIs<Outcome.Failed>(shelf.stashes("a"))
            prefs.rawPut(PromptShelf.stashKey("a", "v2"), null)
            prefs.rawPut(
                PromptShelf.stashKey("a", "evil"),
                """{"id":"evil","text":"t","createdAt":1,"attachments":[],"attachmentFormat":1,""" +
                    """"attachmentRefs":[{"filename":"e","mime":"text/plain","blob":"../x","bytes":1}],"references":[]}""",
            )
            assertIs<Outcome.Failed>(shelf.remove("a", "1"))
            assertEquals(1, payloads.size)
        }

    @Test
    fun `B-STATE-136 orphans are collected and the stash and draft collectors never touch each other`() =
        runTest {
            val shelf = shelf()
            val drafts = Drafts(prefs, AttachmentVault(draftPayloads), profiles, TestClock())
            drafts.save("a", "s1", "draft", listOf(dataAttachment("d.txt", "draft file")), null)
            shelf.stash("a", prompt("1").copy(attachments = listOf(dataAttachment("x.txt", "x"))))
            shelf.stash("a", prompt("2").copy(attachments = listOf(dataAttachment("y.txt", "y"))))
            assertEquals(2, payloads.size)
            shelf.remove("a", "1")
            assertEquals(1, payloads.size)
            assertEquals(1, draftPayloads.size)
            drafts.save("a", "s1", "", emptyList())
            assertEquals(0, draftPayloads.size)
            assertEquals(1, payloads.size)
        }

    @Test
    fun `B-STATE-138 input is snapshotted before the queued write`() = runTest {
        val gated = GatedPrefs(prefs)
        val shelf = PromptShelf(gated, AttachmentVault(payloads), profiles, Redactor())
        gated.gate = CompletableDeferred()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { shelf.stash("a", prompt("0")) }
        val files = mutableListOf(dataAttachment("x.txt", "x"))
        val second =
            launch(start = CoroutineStart.UNDISPATCHED) {
                shelf.stash("a", prompt("1").copy(attachments = files))
            }
        files.add(dataAttachment("y.txt", "y")) // the composer keeps editing while the write waits
        gated.gate!!.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("x.txt"), list(shelf, "a").first { it.id == "1" }.attachmentNames)
    }

    @Test
    fun `B-STATE-140 save, restart, recover and profile deletion work end to end without consuming the source`() =
        runTest {
            shelf().stash("a", prompt("1").copy(attachments = listOf(dataAttachment("x.txt", "x"))))
            val restarted = shelf()
            val once = restarted.restoreAttachments("a", "1", sameLocation = true).getOrNull()!!
            val twice = restarted.restoreAttachments("a", "1", sameLocation = true).getOrNull()!!
            assertEquals(once, twice)
            assertEquals(1, list(restarted, "a").size)
            assertTrue(restarted.clearForProfile("a"))
            assertEquals(
                emptySet(),
                prefs.keys().filter { it.endsWith(".a") || ".a." in it }.toSet(),
            )
            assertEquals(0, payloads.size)
            assertEquals(
                Failure.Refused(RefusalReason.ProfileDeleted),
                (restarted.stash("a", prompt("2")) as Outcome.Failed).failure,
            )
        }

    @Test
    fun `B-STATE-141 stale actions are rejected without side effects`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("1"))
        assertEquals(
            Failure.Refused(RefusalReason.StaleScope),
            (shelf.stash("", prompt("2")) as Outcome.Failed).failure,
        )
        profiles.ids.remove("b")
        assertEquals(
            Failure.Refused(RefusalReason.ProfileDeleted),
            (shelf.stash("b", prompt("3")) as Outcome.Failed).failure,
        )
        // A stale removal of an id that no longer exists cannot delete another saved prompt.
        shelf.remove("a", "gone")
        assertEquals(listOf("1"), list(shelf, "a").map { it.id })
        // A secret-like identity is refused, never rerouted.
        assertIs<Outcome.Failed>(
            shelf.stash("a", prompt("sk-ant-api03-abcdefghijklmnopqrstuvwxyz"))
        )
    }

    @Test
    fun `B-STATE-085 deleting is durable at once and Undo restores exact metadata and bytes, once`() =
        runTest {
            val shelf = shelf()
            val photo = dataAttachment("p.png", "png bytes", "image/png")
            val original = prompt("1", createdAt = 42).copy(attachments = listOf(photo))
            shelf.stash("a", original)
            val undo = shelf.deleteWithUndo("a", "1").getOrNull()!!
            assertEquals(emptyList(), list(shelf(), "a"))
            assertEquals(0, payloads.size)
            assertIs<Outcome.Ok<Unit>>(undo.undo())
            val back = list(shelf(), "a").single()
            assertEquals(42, back.createdAt)
            assertEquals("prompt 1", back.text)
            assertEquals(
                listOf(photo),
                shelf.restoreAttachments("a", "1", true).getOrNull()!!.attachments,
            )
            assertEquals(
                Failure.Refused(RefusalReason.AlreadyAnswered),
                (undo.undo() as Outcome.Failed).failure,
            )
        }

    @Test
    fun `B-STATE-085 a refused deletion keeps the prompt and a retry works`() = runTest {
        val shelf = shelf()
        shelf.stash("a", prompt("1"))
        prefs.refuse = true
        assertIs<Outcome.Failed>(shelf.deleteWithUndo("a", "1"))
        prefs.refuse = false
        assertEquals(1, list(shelf, "a").size)
        assertIs<Outcome.Ok<*>>(shelf.deleteWithUndo("a", "1"))
        assertEquals(0, list(shelf, "a").size)
    }

    @Test
    fun `B-SEC-151 saved copy metadata and decoded text payloads are sanitized before publication`() =
        runTest {
            val canary = List(3) { "fixture" }.joinToString("-")
            val redactor = Redactor().apply { registerSecret(canary) }
            val shelf = PromptShelf(prefs, AttachmentVault(payloads), profiles, redactor)
            val text = "keep \u03bb $canary"
            val encoded = dataAttachment("$canary.txt", text)
            val percent =
                PromptAttachment(
                    "application/json",
                    "data.json",
                    "data:application/json,keep%20%CE%BB%20$canary",
                )
            val clean = dataAttachment("clean.txt", "clean \u03bb")
            val binary = dataAttachment("photo.png", "opaque image bytes", "image/png")
            val reference =
                ReviewReference(
                    "r1",
                    ReviewReferenceKind.Hunk,
                    "lib/$canary.kt",
                    lineLabel = canary,
                    snippet = text,
                    comment = canary,
                    status = canary,
                )
            assertIs<Outcome.Ok<Unit>>(
                shelf.stash(
                    "a",
                    prompt("safe", text)
                        .copy(
                            attachments = listOf(encoded, percent, clean, binary),
                            references = listOf(reference),
                        ),
                )
            )
            val metadata = prefs.string(PromptShelf.stashKey("a", "safe"))!!
            assertFalse(canary in metadata, "metadata must contain no registered canary")
            for (entry in payloads.list()) {
                val stored = payloads.read(entry.ownerKey, entry.id, entry.bytes).decodeToString()
                val body = stored.substringAfter(',')
                val decoded =
                    if (";base64," in stored) Base64.decode(body).decodeToString() else body
                assertFalse(
                    canary in decoded,
                    "decoded vault payload must contain no registered canary",
                )
            }
            val restored = shelf.restoreAttachments("a", "safe", true).getOrNull()!!.attachments
            assertEquals("${Redactor.MASK}.txt", restored[0].filename)
            assertEquals(
                "keep \u03bb ${Redactor.MASK}",
                Base64.decode(restored[0].url.substringAfter(',')).decodeToString(),
            )
            assertEquals(
                "keep \u03bb ${Redactor.MASK}",
                Base64.decode(restored[1].url.substringAfter(',')).decodeToString(),
            )
            assertEquals(clean, restored[2], "clean text URLs retain their exact representation")
            assertEquals(binary, restored[3], "binary media remains byte for byte")
            val saved = list(shelf, "a").single()
            assertEquals("keep \u03bb ${Redactor.MASK}", saved.text)
            assertEquals(
                reference.copy(
                    path = "lib/${Redactor.MASK}.kt",
                    lineLabel = Redactor.MASK,
                    snippet = "keep \u03bb ${Redactor.MASK}",
                    comment = Redactor.MASK,
                    status = Redactor.MASK,
                ),
                saved.references.single(),
            )
            assertEquals(
                text,
                encoded.url.let { Base64.decode(it.substringAfter(',')).decodeToString() },
                "caller input remains unchanged",
            )
        }

    @Test
    fun `B-SEC-151 sensitive saved prompt routing identities and URLs are refused before publication`() =
        runTest {
            val canary = List(3) { "fixture" }.joinToString("-")
            val redactor = Redactor().apply { registerSecret(canary) }
            val shelf = PromptShelf(prefs, AttachmentVault(payloads), profiles, redactor)
            val candidates =
                listOf(
                    prompt(canary),
                    prompt("directory").copy(directory = "/srv/$canary"),
                    prompt("workspace").copy(workspace = canary),
                    prompt("reference")
                        .copy(
                            references =
                                listOf(ReviewReference(canary, ReviewReferenceKind.File, "a.kt"))
                        ),
                    prompt("url")
                        .copy(attachments = listOf(fileAttachment("a.kt", "/srv/$canary"))),
                    prompt("mime-header")
                        .copy(
                            attachments =
                                listOf(
                                    PromptAttachment(
                                        "text/plain",
                                        "a.txt",
                                        "data:text/$canary,$canary",
                                    )
                                )
                        ),
                )
            for (candidate in candidates) {
                assertIs<Outcome.Failed>(shelf.stash("a", candidate))
                assertTrue(list(shelf, "a").isEmpty())
                assertEquals(0, payloads.size)
            }
        }

    @Test
    fun `B-STATE-085 committed deletion returns Undo when payload collection fails and cleanup retries`() =
        runTest {
            val shelf = shelf()
            val photo = dataAttachment("p.png", "photo bytes", "image/png")
            assertIs<Outcome.Ok<Unit>>(
                shelf.stash("a", prompt("1").copy(attachments = listOf(photo)))
            )
            payloads.failLists = true
            val deleted = assertIs<Outcome.Ok<*>>(shelf.deleteWithUndo("a", "1"))
            val undo =
                deleted.value as io.github.eslamasabry.oc.engine.composer.stash.SavedPromptUndo
            assertTrue(list(shelf(), "a").isEmpty())
            assertEquals(1, payloads.size, "failed collection retains bytes for retry")
            assertTrue(
                prefs.contains(PromptShelf.ownerKey("a")),
                "durable owner marker retains cleanup responsibility",
            )
            payloads.failLists = false
            assertIs<Outcome.Ok<Unit>>(undo.undo())
            assertEquals(
                listOf(photo),
                shelf().restoreAttachments("a", "1", true).getOrNull()!!.attachments,
            )
            assertIs<Outcome.Ok<*>>(shelf.deleteWithUndo("a", "1"))
            assertEquals(0, payloads.size)
        }

    @Test
    fun `B-CHAT-088 temporary attachment schemes refuse the entire stash without acknowledging partial input`() =
        runTest {
            for (scheme in listOf("content", "blob")) {
                val source =
                    prompt(scheme)
                        .copy(
                            attachments =
                                listOf(
                                    dataAttachment("first.txt", "first"),
                                    PromptAttachment(
                                        "text/plain",
                                        "temporary.txt",
                                        "$scheme:temporary",
                                    ),
                                )
                        )
                assertIs<Outcome.Failed>(shelf().stash("a", source))
                assertTrue(list(shelf(), "a").isEmpty())
                assertEquals(2, source.attachments.size, "the caller can retry the complete input")
                assertEquals(0, payloads.size, "partial externalization is collected")
            }
        }

    @Test
    fun `B-STATE-086 profile removal sweeps saved prompts and an outstanding Undo cannot resurrect them`() =
        runTest {
            val shelf = shelf()
            shelf.stash("a", prompt("1"))
            val undo = shelf.deleteWithUndo("a", "1").getOrNull()!!
            assertTrue(shelf.clearForProfile("a"))
            assertEquals(
                Failure.Refused(RefusalReason.ProfileDeleted),
                (undo.undo() as Outcome.Failed).failure,
            )
            assertEquals(emptyList(), list(shelf, "a"))
        }
}
