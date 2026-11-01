package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AttachmentIntakeTest {
    private val files = FakeFiles()
    private val photos = FakePhotoStore("alpha", "beta")
    private var pendingAtLaunch: Boolean? = null
    private val activity = FakeActivity { pendingAtLaunch = photos.pending != null }
    private val permissions = FakePermissions()
    private val reader = FakeReader(files)
    private val codec = FakeCodec(files)
    private val clipboard = FakeClipboard()
    private val captures = FakeCaptures(files)
    private val target = AttachTarget(ConversationRef(ProfileId("alpha"), SessionId("s1")), "/srv/app", null)

    private fun TestScope.intake() =
        AttachmentIntake(
            activity, permissions, reader, codec, clipboard, captures, photos, TestWords,
            TestDispatchers(StandardTestDispatcher(testScheduler)), FixedClock(1_000),
        )

    private fun attachments(n: Int, bytesEach: Int = 10): List<PromptAttachment> =
        List(n) { PromptAttachment("text/plain", "f$it.txt", DataUrl.encode("text/plain", ByteArray(bytesEach) { 65 })) }

    private fun <T> ok(o: Outcome<T>): T = (o as? Outcome.Ok)?.value ?: fail("expected Ok, got $o")

    private fun problem(o: Outcome<*>): MediaProblem? = ((o as? Outcome.Failed)?.failure as? Failure.Media)?.problem

    private fun text(a: PromptAttachment): String = Base64.decode(a.url.substringAfter(",")).decodeToString()

    // ---- B-MEDIA-007 gallery

    @Test
    fun `B-MEDIA-007 photos are scaled by the codec before they are attached`() =
        runTest {
            val file = files.file("content://a", png(1), "a.png")
            codec.rewrite = { png(9) } // the Android codec re-encodes at ≤ 2048 px
            activity.images = { Outcome.Ok(persistentListOf(file)) }
            val result = ok(intake().pickPhotos(target, emptyList()))
            assertEquals(listOf("content://a"), codec.prepared)
            assertEquals(DataUrl.encode("image/png", png(9)), result.added.single().url)
            assertEquals(2048, PhotoRules.Prompt.maxEdgePx)
            assertEquals(85, PhotoRules.Prompt.jpegQuality)
        }

    @Test
    fun `B-MEDIA-007 the gallery picks several up to the room left and nothing stays pending`() =
        runTest {
            val picked = listOf("a", "b", "c").mapIndexed { i, n -> files.file("content://$n", png(i), "$n.png") }
            activity.images = { Outcome.Ok(persistentListOf(*picked.toTypedArray())) }
            val result = ok(intake().pickPhotos(target, attachments(3)))
            assertEquals(2, activity.lastLimit)
            assertEquals(true, pendingAtLaunch, "a second picker cannot start while this one is open")
            // A picker that ignores the limit cannot overfill the draft.
            assertEquals(listOf("a.png", "b.png"), result.added.map { it.filename })
            assertEquals("image/png", result.added.first().mime)
            assertNull(photos.pending)
        }

    @Test
    fun `B-MEDIA-007 one slot left uses the single picker`() {
        assertEquals(PhotoPickerChoice.Single, PhotoPickerChoice.forRoom(1))
        assertEquals(PhotoPickerChoice.Multiple(4), PhotoPickerChoice.forRoom(4))
        assertNull(PhotoPickerChoice.forRoom(0))
    }

    @Test
    fun `B-MEDIA-007 cancelling a pick leaves nothing pending and a full draft never opens the picker`() =
        runTest {
            assertTrue(ok(intake().pickPhotos(target, emptyList())).added.isEmpty())
            assertNull(photos.pending)
            assertEquals(MediaProblem.DraftFull, problem(intake().pickPhotos(target, attachments(5))))
            assertEquals(listOf("images"), activity.launches)
        }

    @Test
    fun `B-MEDIA-007 duplicates are skipped and a fake picture is refused by its bytes`() =
        runTest {
            val a = files.file("content://a", png(1), "a.png")
            val again = files.file("content://a2", png(1), "again.png")
            val fake = files.file("content://f", "not a picture".encodeToByteArray(), "fake.png")
            activity.images = { Outcome.Ok(persistentListOf(a, again, fake)) }
            val result = ok(intake().pickPhotos(target, emptyList()))
            assertEquals(listOf("a.png"), result.added.map { it.filename })
            assertEquals(Failure.Media(MediaProblem.PhotoUnsupported), result.skipped)
        }

    // ---- B-MEDIA-008 camera

    @Test
    fun `B-MEDIA-008 camera denial never launches and leaves no blocking photo`() =
        runTest {
            permissions.status = PermissionStatus.Denied
            assertEquals(MediaProblem.CameraDenied, problem(intake().takePhoto(target, emptyList())))
            permissions.status = PermissionStatus.PermanentlyDenied
            assertEquals(MediaProblem.CameraDeniedForever, problem(intake().takePhoto(target, emptyList())))
            assertTrue(activity.launches.isEmpty())
            assertNull(photos.pending)
        }

    @Test
    fun `B-MEDIA-008 failed request persistence never launches the camera`() =
        runTest {
            photos.refuse = true
            assertEquals(MediaProblem.PhotoStorage, problem(intake().takePhoto(target, emptyList())))
            assertTrue(activity.launches.isEmpty())
        }

    @Test
    fun `B-MEDIA-008 the request is recorded before launch, a capture attaches and cleans up, cancel leaves nothing`() =
        runTest {
            activity.photo = { t -> Outcome.Ok(files.file(t.uri, png(3), "shot.jpg")) }
            val result = ok(intake().takePhoto(target, emptyList()))
            assertEquals(true, pendingAtLaunch)
            assertEquals("image/png", result.added.single().mime)
            assertNull(photos.pending)
            assertEquals(1, captures.deleted.size)
            activity.photo = { Outcome.Ok(null) }
            assertTrue(ok(intake().takePhoto(target, emptyList())).added.isEmpty())
            assertNull(photos.pending)
        }

    @Test
    fun `B-MEDIA-008 a pending photo blocks another picker`() =
        runTest {
            photos.begin("beta", "other", null, null)
            assertEquals(MediaProblem.PhotoPending, problem(intake().pickPhotos(target, emptyList())))
            assertEquals(MediaProblem.PhotoPending, problem(intake().takePhoto(target, emptyList())))
            assertTrue(activity.launches.isEmpty())
        }

    @Test
    fun `B-MEDIA-008 oversize images are rejected`() =
        runTest {
            val big = files.file("content://big", png(1), "big.png")
            codec.rewrite = { PNG + ByteArray(10 * 1024 * 1024) }
            activity.images = { Outcome.Ok(persistentListOf(big)) }
            val result = ok(intake().pickPhotos(target, emptyList()))
            assertTrue(result.added.isEmpty())
            assertEquals(Failure.Media(MediaProblem.PhotoTooLarge), result.skipped)
        }

    // ---- B-CHAT-009 / 010 files

    @Test
    fun `B-CHAT-010 limits are checked in order and the read is bounded by the room left`() =
        runTest {
            assertEquals(MediaProblem.DraftFull, problem(intake().pickFile(attachments(5))))
            assertEquals(MediaProblem.AttachmentsTotalTooLarge, problem(intake().pickFile(attachments(2, 10 * 1024 * 1024))))
            assertTrue(activity.launches.isEmpty(), "nothing opens when nothing fits")

            activity.document = Outcome.Ok(files.file("content://big", ByteArray(4), "big.txt", size = 11L * 1024 * 1024))
            assertEquals(MediaProblem.AttachmentTooLarge, problem(intake().pickFile(emptyList())))

            // 15 MiB already attached: the read may keep at most 5 MiB, and an overflow says "total".
            val current = attachments(1, 15 * 1024 * 1024)
            activity.document = Outcome.Ok(files.file("content://grew", ByteArray(6 * 1024 * 1024) { 65 }, "grew.txt", size = 3))
            assertEquals(MediaProblem.AttachmentsTotalTooLarge, problem(intake().pickFile(current)))
            assertEquals(5L * 1024 * 1024, reader.lastLimit)

            activity.document = Outcome.Ok(files.file("content://exact", ByteArray(10 * 1024 * 1024) { 65 }, "exact.txt"))
            assertEquals(1, ok(intake().pickFile(emptyList())).added.size)
        }

    @Test
    fun `B-CHAT-009 attached files are normalized or refused by the MIME gate`() =
        runTest {
            activity.document = Outcome.Ok(files.file("content://md", "# hi".encodeToByteArray(), "notes.md", "text/markdown"))
            val md = ok(intake().pickFile(emptyList())).added.single()
            assertEquals("text/plain", md.mime)
            assertEquals("notes.md", md.filename)
            activity.document = Outcome.Ok(files.file("content://bin", byteArrayOf(0, 1, 2), "a.bin", "application/octet-stream"))
            assertEquals(MediaProblem.AttachmentUnsupported, problem(intake().pickFile(emptyList())))
            activity.document = Outcome.Ok(null)
            assertTrue(ok(intake().pickFile(emptyList())).added.isEmpty())
        }

    @Test
    fun `B-MEDIA-006 Office files attach as text with a header and a note, broken ones say so`() =
        runTest {
            val bad = intake().fromBytes(emptyList(), "old.xlsx", null, ByteArray(64) { 7 })
            assertEquals(MediaProblem.OfficeUnreadable, problem(bad))
            val docx = minimalDocx("Hello there")
            val result = ok(intake().fromBytes(emptyList(), "brief.docx", null, docx))
            val attachment = result.added.single()
            assertEquals("brief.docx.txt", attachment.filename)
            assertEquals("text/plain", attachment.mime)
            assertEquals("Contents of brief.docx\n\nHello there", text(attachment))
            assertEquals(IntakeNote.DocumentAttached("brief.docx"), result.note)
        }

    // ---- clipboard and keyboard

    @Test
    fun `B-MEDIA-007 a clipboard picture attaches as pasted-image with the cap and an empty clipboard says so`() =
        runTest {
            clipboard.image = Outcome.Ok(ClipboardImage("image/webp", png(5)))
            val pasted = ok(intake().fromClipboard(emptyList())).added.single()
            assertEquals("pasted-image-1000.webp", pasted.filename)
            assertEquals("image/png", pasted.mime, "the type comes from the bytes")
            assertEquals(10 * 1024 * 1024, clipboard.lastMax)
            clipboard.image = Outcome.Ok(null)
            assertEquals(IntakeNote.ClipboardEmpty, ok(intake().fromClipboard(emptyList())).note)
            clipboard.image = Outcome.Failed(Failure.Media(MediaProblem.ClipboardImageTooLarge))
            assertEquals(MediaProblem.ClipboardImageTooLarge, problem(intake().fromClipboard(emptyList())))
            assertEquals(MediaProblem.PhotoUnsupported, problem(intake().fromInserted(emptyList(), null, "gif?".encodeToByteArray())))
            assertNotNull(ok(intake().fromInserted(emptyList(), null, png(6))).added.singleOrNull()?.takeIf { it.filename.endsWith(".png") })
        }

    /** A one-paragraph .docx built with stored (method 0) entries, so commonTest needs no zip library. */
    private fun minimalDocx(text: String): ByteArray =
        storedZip("word/document.xml" to "<w:document xmlns:w=\"urn:w\"><w:body><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:body></w:document>")
}

/** A ZIP with stored entries (CRC left 0: the reader does not check it, as Flutter's did not). */
fun storedZip(vararg entries: Pair<String, String>): ByteArray {
    val out = ArrayList<Byte>()
    fun u16(v: Int) {
        out += (v and 0xFF).toByte()
        out += ((v shr 8) and 0xFF).toByte()
    }
    fun u32(v: Int) {
        u16(v and 0xFFFF)
        u16((v ushr 16) and 0xFFFF)
    }
    val offsets = ArrayList<Int>()
    val blobs = entries.map { (n, t) -> n.encodeToByteArray() to t.encodeToByteArray() }
    for ((name, data) in blobs) {
        offsets += out.size
        u32(0x04034b50); u16(20); u16(0); u16(0); u16(0); u16(0); u32(0); u32(data.size); u32(data.size)
        u16(name.size); u16(0)
        name.forEach { out += it }
        data.forEach { out += it }
    }
    val central = out.size
    for ((i, pair) in blobs.withIndex()) {
        val (name, data) = pair
        u32(0x02014b50); u16(20); u16(20); u16(0); u16(0); u16(0); u16(0); u32(0); u32(data.size); u32(data.size)
        u16(name.size); u16(0); u16(0); u16(0); u16(0); u32(0); u32(offsets[i])
        name.forEach { out += it }
    }
    val size = out.size - central
    u32(0x06054b50); u16(0); u16(0); u16(blobs.size); u16(blobs.size); u32(size); u32(central); u16(0)
    return out.toByteArray()
}
