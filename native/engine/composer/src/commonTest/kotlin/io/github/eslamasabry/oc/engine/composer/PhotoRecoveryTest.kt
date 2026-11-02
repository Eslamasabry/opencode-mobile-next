package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.photo.DraftPhotoRecovery
import io.github.eslamasabry.oc.engine.composer.photo.PendingPhoto
import io.github.eslamasabry.oc.engine.composer.photo.PhotoProblem
import io.github.eslamasabry.oc.engine.composer.photo.PhotoRecoveryResult
import io.github.eslamasabry.oc.engine.composer.photo.PhotoRecoveryStore
import io.github.eslamasabry.oc.engine.composer.photo.PhotoResult
import io.github.eslamasabry.oc.engine.composer.photo.PhotoSniff
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PhotoRecoveryTest {
    private val prefs = MemoryComposerPrefs()
    private val photoPayloads = MemoryPayloadStore()
    private val draftPayloads = MemoryPayloadStore()
    private val profiles = TestProfiles("alpha", "beta")
    private val clock = TestClock()
    private val redactor = Redactor()
    private val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10, 1, 2, 3)

    private fun photos() =
        PhotoRecoveryStore(prefs, AttachmentVault(photoPayloads), redactor, clock)

    private fun drafts() = Drafts(prefs, AttachmentVault(draftPayloads), profiles, clock)

    private fun recovery(photos: PhotoRecoveryStore = photos(), drafts: Drafts = drafts()) =
        DraftPhotoRecovery(
            photos,
            drafts,
            AttachmentVault(draftPayloads),
            prefs,
            profiles,
            redactor,
            clock,
        )

    private suspend fun begun(store: PhotoRecoveryStore, session: String = "s1"): PendingPhoto =
        (store.begin("alpha", session, "/srv/app", null) as PhotoResult.Ok).value

    @Test
    fun `B-MEDIA-008 the request is recorded before launch and a failed record never launches`() =
        runTest {
            val store = photos()
            val request = begun(store)
            assertEquals(request, photos().pending, "on disk before the picker opens")
            assertEquals(
                PhotoResult.Refused(PhotoProblem.Pending),
                photos().begin("alpha", "s2", null, null),
                "a pending photo blocks another picker",
            )
            store.discard(request.id)
            prefs.refuse = true
            assertEquals(
                PhotoResult.Refused(PhotoProblem.Storage),
                store.begin("alpha", "s1", null, null),
            )
            prefs.refuse = false
            assertNull(store.pending)
        }

    @Test
    fun `B-MEDIA-008 cancel leaves no blocking photo and selected bytes survive source deletion`() =
        runTest {
            val store = photos()
            val request = begun(store)
            store.discard(request.id)
            assertNull(store.pending)
            val second = begun(store)
            val source = png.copyOf()
            assertIs<PhotoResult.Ok<*>>(store.complete(second.id, "p.png", source))
            source.fill(0) // the picker's temporary file is gone
            val back = (store.readPending(second.id) as PhotoResult.Ok).value
            assertEquals("image/png", back.mime)
            assertEquals(PhotoSniff.attachment("p.png", png), PhotoResult.Ok(back))
        }

    @Test
    fun `B-MEDIA-008 fake extensions and oversize images are refused`() = runTest {
        assertEquals(
            PhotoResult.Refused(PhotoProblem.Unsupported),
            PhotoSniff.attachment("x.png", "not a picture".encodeToByteArray()),
        )
        assertEquals(
            PhotoResult.Refused(PhotoProblem.TooLarge),
            PhotoSniff.attachment("x.png", ByteArray(PhotoSniff.MAX_BYTES + 1)),
        )
        assertEquals("image/jpeg", PhotoSniff.mimeOf(byteArrayOf(-1, -40, -1, 0)))
        assertEquals("image/gif", PhotoSniff.mimeOf("GIF89a....".encodeToByteArray()))
        assertEquals(
            "image/webp",
            PhotoSniff.mimeOf("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray()),
        )
    }

    @Test
    fun `B-MEDIA-008 a late result cannot resurrect a removed profile's photo`() = runTest {
        val store = photos()
        val request = begun(store)
        assertEquals(true, store.clearForProfile("alpha"))
        assertEquals(PhotoResult.Ok(null), store.complete(request.id, "p.png", png))
        assertNull(store.pending)
        assertEquals(0, photoPayloads.size)
    }

    @Test
    fun `B-STATE-088 a photo returned after a kill joins its own conversation's draft with its text and place`() =
        runTest {
            val drafts = drafts()
            drafts.save(
                "alpha",
                "s1",
                "look at this",
                listOf(fileAttachment("a.dart")),
                DraftLocation("/srv/app", null),
            )
            drafts.save("alpha", "other", "another chat")
            val request = begun(photos())
            // The app is killed while the camera is open; a new process gets the bytes back.
            assertIs<PhotoResult.Ok<*>>(
                photos().complete(request.id, "camera.jpg", byteArrayOf(-1, -40, -1, 9))
            )
            assertEquals(PhotoRecoveryResult.Attached, recovery().recover())
            val draft = drafts().draft("alpha", "s1")!!
            assertEquals("look at this", draft.text)
            assertEquals(listOf("a.dart", "camera.jpg"), draft.attachments.map { it.filename })
            assertEquals("/srv/app", draft.directory)
            assertEquals("another chat", drafts().draft("alpha", "other")?.text)
            assertNull(photos().pending)
            assertEquals(PhotoRecoveryResult.NothingPending, recovery().recover())
        }

    @Test
    fun `B-STATE-088 a failed save keeps the record and a restart after a failed discard does not duplicate`() =
        runTest {
            val request = begun(photos())
            photos().complete(request.id, "p.png", png)
            prefs.refuse = true
            assertEquals(PhotoRecoveryResult.RetryRequired, recovery().recover())
            prefs.refuse = false
            assertNotNull(photos().pending, "kept for the next start")
            // The draft commits but removing the record fails (the record stays).
            val gated = GatedPrefs(prefs)
            val failingDiscard =
                object : io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs by gated {
                    override suspend fun commit(
                        edit: io.github.eslamasabry.oc.engine.composer.store.PrefsEdit
                    ): Boolean =
                        if (PhotoRecoveryStore.KEY in edit.entries) false else gated.commit(edit)
                }
            val failing =
                DraftPhotoRecovery(
                    PhotoRecoveryStore(
                        failingDiscard,
                        AttachmentVault(photoPayloads),
                        redactor,
                        clock,
                    ),
                    Drafts(prefs, AttachmentVault(draftPayloads), profiles, clock),
                    AttachmentVault(draftPayloads),
                    prefs,
                    profiles,
                    redactor,
                    clock,
                )
            assertEquals(PhotoRecoveryResult.RetryRequired, failing.recover())
            assertEquals(PhotoRecoveryResult.Attached, recovery().recover())
            assertEquals(1, drafts().draft("alpha", "s1")!!.attachments.size)
        }

    @Test
    fun `B-STATE-088 deleting the profile during the copy cannot recreate its draft`() = runTest {
        val request = begun(photos())
        photos().complete(request.id, "p.png", png)
        profiles.ids.remove("alpha")
        assertEquals(PhotoRecoveryResult.ProfileRemoved, recovery().recover())
        assertNull(drafts().draft("alpha", "s1"))
        assertNull(photos().pending)
        assertEquals(0, photoPayloads.size)
        assertEquals(0, draftPayloads.size)
    }

    @Test
    fun `B-STATE-088 COMPE-6 discard after failed photo publication collects unreferenced bytes`() =
        runTest {
            val request = begun(photos())
            prefs.refuse = true
            assertEquals(
                PhotoResult.Refused(PhotoProblem.Storage),
                photos().complete(request.id, "p.png", png),
            )
            prefs.refuse = false
            assertNull(photos().pending?.ref)
            assertEquals(1, photoPayloads.size, "publication preceded the refused reference commit")
            assertEquals(PhotoResult.Ok(Unit), photos().discard(request.id))
            assertNull(photos().pending)
            assertEquals(0, photoPayloads.size)
        }

    @Test
    fun `B-STATE-088 COMPE-6 failed orphan cleanup keeps the photo record for retry`() = runTest {
        val request = begun(photos())
        prefs.refuse = true
        photos().complete(request.id, "p.png", png)
        prefs.refuse = false
        photoPayloads.failDeletes = true
        assertEquals(PhotoResult.Refused(PhotoProblem.Storage), photos().discard(request.id))
        assertEquals(request.id, photos().pending?.id)
        photoPayloads.failDeletes = false
        assertEquals(PhotoResult.Ok(Unit), photos().discard(request.id))
        assertNull(photos().pending)
        assertEquals(0, photoPayloads.size)
    }

    @Test
    fun `B-STATE-088 COMPE-6 profile cleanup collects orphan photos without a pending record`() =
        runTest {
            val vault = AttachmentVault(photoPayloads)
            vault.store("alpha", listOf(dataAttachment("old.png", "orphan")))
            vault.store("beta", listOf(dataAttachment("other.png", "other owner")))
            assertNull(photos().pending)
            assertEquals(true, photos().clearForProfile("alpha"))
            assertEquals(1, photoPayloads.size)
            assertEquals(
                setOf(AttachmentVault.ownerKey("beta")),
                photoPayloads.list().map { it.ownerKey }.toSet(),
            )
        }

    @Test
    fun `B-STATE-088 COMPE-6 profile cleanup preserves another profile pending photo`() = runTest {
        val vault = AttachmentVault(photoPayloads)
        vault.store("alpha", listOf(dataAttachment("old.png", "orphan")))
        val beta = (photos().begin("beta", "s2", null, null) as PhotoResult.Ok).value
        photos().complete(beta.id, "p.png", png)
        assertEquals(true, photos().clearForProfile("alpha"))
        assertEquals(beta.id, photos().pending?.id)
        assertEquals(1, photoPayloads.size)
        assertIs<PhotoResult.Ok<*>>(photos().readPending(beta.id))
    }

    @Test
    fun `B-STATE-088 a Flutter record reads unchanged`() = runTest {
        prefs.rawPut(
            PhotoRecoveryStore.KEY,
            """{"id":"1730000000000000","profileID":"alpha","sessionID":"s1","directory":"/srv/app","workspace":null,""" +
                """"ref":null,"path":"/data/cache/x.jpg","name":"x.jpg","failed":true}""",
        )
        val record = photos().pending!!
        assertEquals("s1", record.sessionId)
        assertEquals(true, record.failed)
        assertEquals("/data/cache/x.jpg", record.path)
        assertEquals(
            PhotoRecoveryResult.RetryRequired,
            recovery().recover(),
            "the platform must hand the bytes back first",
        )
    }
}
