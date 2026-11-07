package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.platform.ActivityResults
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.DocumentTarget
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import io.github.eslamasabry.oc.core.domain.platform.PhotoTarget
import io.github.eslamasabry.oc.core.domain.platform.PickedFile
import io.github.eslamasabry.oc.core.domain.platform.TreeTarget
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.photo.DraftPhotoRecovery
import io.github.eslamasabry.oc.engine.composer.photo.PhotoRecoveryStore
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.engine.media.attach.AttachTarget
import io.github.eslamasabry.oc.engine.media.attach.AttachmentIntake
import io.github.eslamasabry.oc.engine.media.attach.AttachmentWords
import io.github.eslamasabry.oc.engine.media.attach.ImageCodec
import io.github.eslamasabry.oc.engine.media.attach.LauncherOutcome
import io.github.eslamasabry.oc.engine.media.attach.PendingPhotoRecovery
import io.github.eslamasabry.oc.engine.media.attach.PhotoRules
import io.github.eslamasabry.oc.engine.media.attach.PreparedImage
import io.github.eslamasabry.oc.engine.media.attach.RecoveryResult
import io.github.eslamasabry.oc.engine.media.attach.UnavailableClipboardImages
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import io.github.eslamasabry.oc.android.camera.capture.CaptureFiles as CaptureFolder

/**
 * E05-S01 composer camera: the pending-photo record in the Flutter app's format, recovery after
 * the app was killed with the camera open, the camera request that outlives a recreated activity,
 * and camera-permission refusals. The launchers themselves (TakePicture, RequestPermission) are
 * proved on the emulator by the coordinator (steps in the E05-S01 report).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerCameraTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers: AppDispatchers = TestDispatchers(dispatcher)
    private val clock = TestClock()
    private val redactor = Redactor()
    private val files: File = Files.createTempDirectory("files").toFile()
    private val cache: File = Files.createTempDirectory("cache").toFile()
    private val profiles =
        object : ComposerProfiles {
            override fun exists(profileId: String) = profileId == "alpha"

            override fun count() = 1
        }
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

    private class World(val prefs: MemoryComposerPrefs) {
        val photoPayloads = MemoryPayloadStore()
        val draftPayloads = MemoryPayloadStore()
    }

    private fun world(initial: Map<String, Any> = emptyMap()) = World(MemoryComposerPrefs(initial))

    private fun store(w: World) =
        PhotoRecoveryStore(w.prefs, AttachmentVault(w.photoPayloads), redactor, clock)

    private fun record(w: World, ready: Boolean = true) = ComposerPhotoRecord(store(w)) { ready }

    private fun drafts(w: World) = Drafts(w.prefs, AttachmentVault(w.draftPayloads), profiles, clock)

    private fun captures(w: World) =
        ComposerCaptureFiles(
            folder = CaptureFolder(files),
            contentUri = { "content://test.files/camera/${it.name}" },
            pending = { record(w).pending },
            legacyRoots = listOf(cache, files),
            io = dispatchers.io,
        )

    private fun recovery(w: World) =
        PendingPhotoRecovery(
            photos = record(w),
            drafts =
                ComposerDraftJoin(
                    DraftPhotoRecovery(
                        store(w),
                        drafts(w),
                        AttachmentVault(w.draftPayloads),
                        w.prefs,
                        profiles,
                        redactor,
                        clock,
                    )
                ),
            captures = captures(w),
            codec = FileCodec,
        )

    private fun capture(id: String): File =
        File(files, "${CaptureFolder.DIR}/$id.jpg").also {
            it.parentFile.mkdirs()
            it.writeBytes(jpeg)
        }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    // ---- the record (B-MEDIA-008, Flutter format)

    /** B-MEDIA-008 the camera's request is the Flutter `oc.pendingPromptPhoto` record, written before launch. */
    @Test
    fun `B-MEDIA-008 the request is recorded in the Flutter format before the camera opens`() = run {
        val w = world()
        val begun = record(w).begin("alpha", "s1", "/srv/app", null)
        val id = assertIs<Outcome.Ok<String>>(begun).value
        val raw = assertNotNull(w.prefs.string(PhotoRecoveryStore.KEY))
        // The Flutter field names, exactly (PendingPromptPhoto.toJson).
        for (field in listOf("\"id\":\"$id\"", "\"profileID\":\"alpha\"", "\"sessionID\":\"s1\"", "\"directory\":\"/srv/app\"")) {
            assertTrue(field in raw, "missing $field")
        }
        assertTrue(id.all { it.isDigit() }, "the id names a capture file")
        assertEquals(
            Outcome.Failed(Failure.Media(MediaProblem.PhotoPending)),
            record(w).begin("alpha", "s2", null, null),
            "a pending photo blocks another camera",
        )
    }

    /** B-MEDIA-008 stores that cannot be read refuse the record, so the camera never opens. */
    @Test
    fun `B-MEDIA-008 a store that is not ready refuses and writes nothing`() = run {
        val w = world()
        assertEquals(
            Outcome.Failed(Failure.Media(MediaProblem.PhotoStorage)),
            record(w, ready = false).begin("alpha", "s1", null, null),
        )
        assertNull(w.prefs.string(PhotoRecoveryStore.KEY))
    }

    /** B-STATE-088 a record written by the Flutter app (with its picker path) reads as pending. */
    @Test
    fun `B-STATE-088 a Flutter record is read with its origin and path`() = run {
        val legacy = File(cache, "image_picker_123.jpg").apply { writeBytes(jpeg) }
        val w = world(mapOf(PhotoRecoveryStore.KEY to flutterRecord("1760000000000123", path = legacy.path)))
        val pending = assertNotNull(record(w).pending)
        assertEquals("1760000000000123", pending.id)
        assertEquals("alpha", pending.profileId)
        assertEquals("s1", pending.sessionId)
        assertEquals(legacy.path, pending.path)
        assertFalse(pending.hasBytes)
    }

    // ---- recovery after the app was killed (B-STATE-088)

    /** B-STATE-088 a photo the camera saved while the app was dead joins its own conversation's draft. */
    @Test
    fun `B-STATE-088 camera return after a kill puts the photo in the right draft`() = run {
        val w = world()
        val id = assertIs<Outcome.Ok<String>>(record(w).begin("alpha", "s1", "/srv/app", null)).value
        val shot = capture(id)
        // New process: everything is rebuilt from disk, then the re-registered launcher answers.
        assertEquals(RecoveryResult.Attached, recovery(w).recover(LauncherOutcome.Captured))
        val draft = assertNotNull(drafts(w).draft("alpha", "s1"))
        assertEquals(1, draft.attachments.size)
        assertEquals("/srv/app", draft.directory, "the draft keeps the location the photo was asked from")
        assertNull(record(w).pending, "nothing is left blocking the camera")
        assertFalse(shot.exists(), "the capture file is removed once the photo is in the draft")
        assertNull(drafts(w).draft("alpha", "s2"))
    }

    /** B-STATE-088 the start-up pass finds the saved photo even before the launcher answers. */
    @Test
    fun `B-STATE-088 the start-up pass recovers a saved photo without a launcher result`() = run {
        val w = world()
        val id = assertIs<Outcome.Ok<String>>(record(w).begin("alpha", "s1", null, null)).value
        capture(id)
        assertEquals(RecoveryResult.Attached, recovery(w).recover(null, launchersSettled = true))
        assertEquals(1, drafts(w).draft("alpha", "s1")?.attachments?.size)
    }

    /** B-MEDIA-008 a cancelled camera after a kill leaves no blocking photo and no file. */
    @Test
    fun `B-MEDIA-008 a cancel after a kill clears the record and the file`() = run {
        val w = world()
        val id = assertIs<Outcome.Ok<String>>(record(w).begin("alpha", "s1", null, null)).value
        val shot = capture(id)
        assertEquals(RecoveryResult.NothingPending, recovery(w).recover(LauncherOutcome.Cancelled))
        assertNull(record(w).pending)
        assertFalse(shot.exists())
        assertNull(drafts(w).draft("alpha", "s1"))
    }

    /** B-STATE-088 a migrated phone keeps the photo Flutter's picker had already saved in the cache. */
    @Test
    fun `B-STATE-088 a Flutter record with a cached photo is recovered after migration`() = run {
        val legacy = File(cache, "image_picker_123.jpg").apply { writeBytes(jpeg) }
        val w = world(mapOf(PhotoRecoveryStore.KEY to flutterRecord("1760000000000123", path = legacy.path)))
        assertEquals(RecoveryResult.Attached, recovery(w).recover(null, launchersSettled = true))
        assertEquals(1, drafts(w).draft("alpha", "s1")?.attachments?.size)
        assertNull(record(w).pending)
    }

    /** B-MEDIA-008 a record cannot point recovery at a file outside the app's own folders. */
    @Test
    fun `B-MEDIA-008 a record path outside the app folders is never read`() = run {
        val outside = Files.createTempDirectory("elsewhere").toFile()
        val foreign = File(outside, "secret.jpg").apply { writeBytes(jpeg) }
        val w = world(mapOf(PhotoRecoveryStore.KEY to flutterRecord("1760000000000124", path = foreign.path)))
        assertNull(captures(w).existing("1760000000000124"))
        val sneaky = File(cache, "../${outside.name}/secret.jpg").path
        val w2 = world(mapOf(PhotoRecoveryStore.KEY to flutterRecord("1760000000000125", path = sneaky)))
        assertNull(captures(w2).existing("1760000000000125"), "a .. path is resolved before the check")
        assertEquals(RecoveryResult.Unavailable, recovery(w).recover(null, launchersSettled = true))
        assertTrue(foreign.exists(), "a file outside the app is never deleted")
        assertNull(drafts(w).draft("alpha", "s1"))
    }

    /** The FileProvider target names the record's own capture file. */
    @Test
    fun `the camera target is the record's capture file through the FileProvider`() = run {
        val w = world()
        assertEquals(PhotoTarget("content://test.files/camera/1760000000000123.jpg"), captures(w).target("1760000000000123"))
        assertEquals(PhotoTarget(""), captures(w).target("../x"), "an id that is not digits never names a file")
    }

    // ---- the camera request across a recreated activity

    /** B-MEDIA-008 a photo answered to a recreated activity's launcher still reaches the waiting request. */
    @Test
    fun `B-MEDIA-008 the camera answer reaches the request after the activity was recreated`() {
        val requests = CameraRequests()
        val request = assertNotNull(requests.begin())
        assertNull(requests.begin(), "one camera at a time")
        // The old activity closes its host: the camera request is the process's and stays open.
        assertTrue(requests.open)
        assertTrue(requests.deliver(true), "the new activity's launcher hands the photo over")
        assertEquals(true, request.getCompleted())
        assertFalse(requests.deliver(true), "a second answer is an orphan")
    }

    /** B-STATE-088 an answer nobody waits for becomes a recovery outcome. */
    @Test
    fun `B-STATE-088 an orphan answer maps to captured or cancelled`() {
        assertFalse(CameraRequests().deliver(true))
        assertEquals(LauncherOutcome.Captured, CameraRequests.orphan(true))
        assertEquals(LauncherOutcome.Cancelled, CameraRequests.orphan(false))
        val requests = CameraRequests()
        val retired = assertNotNull(requests.begin())
        requests.retire(retired) // the composer went away while the camera was open
        assertFalse(requests.deliver(true), "a retired request does not take the photo")
        assertFalse(requests.open)
    }

    // ---- permission (E05-S01 denial)

    @Test
    fun `B-MEDIA-008 camera permission is asked only when declared and not granted`() {
        assertEquals(PermissionStatus.NotApplicable, CameraPermissionRules.before(declared = false, granted = false))
        assertEquals(PermissionStatus.Granted, CameraPermissionRules.before(declared = true, granted = true))
        assertNull(CameraPermissionRules.before(declared = true, granted = false))
        assertEquals(PermissionStatus.Granted, CameraPermissionRules.after(granted = true, showRationale = false))
        assertEquals(PermissionStatus.Denied, CameraPermissionRules.after(granted = false, showRationale = true))
        assertEquals(
            PermissionStatus.PermanentlyDenied,
            CameraPermissionRules.after(granted = false, showRationale = false),
            "Android will not ask again: only app settings can allow it",
        )
    }

    /** B-MEDIA-008 denial leaves no blocking photo and never opens the camera. */
    @Test
    fun `B-MEDIA-008 a refused camera permission explains itself and leaves nothing pending`() = run {
        for ((status, problem) in
            listOf(
                PermissionStatus.Denied to MediaProblem.CameraDenied,
                PermissionStatus.PermanentlyDenied to MediaProblem.CameraDeniedForever,
            )) {
            val w = world()
            val camera = CountingCamera()
            val result = intake(w, camera, gate(status)).takePhoto(target, emptyList())
            val failure = assertIs<Failure.Media>(assertIs<Outcome.Failed>(result).failure)
            assertEquals(problem, failure.problem)
            assertEquals(0, camera.launches, "the camera never opens")
            assertNull(record(w).pending, "no blocking photo")
        }
    }

    /** B-MEDIA-008 the happy path: the photo joins the composer and nothing stays pending. */
    @Test
    fun `B-MEDIA-008 a taken photo is attached and the record is gone`() = run {
        val w = world()
        val camera = CountingCamera { target -> File(files, "${CaptureFolder.DIR}/${target.uri.substringAfterLast('/')}").writeBytes(jpeg) }
        val result = intake(w, camera, gate(PermissionStatus.Granted)).takePhoto(target, emptyList())
        val added = assertIs<Outcome.Ok<io.github.eslamasabry.oc.engine.media.attach.IntakeResult>>(result).value.added
        assertEquals(1, added.size)
        assertEquals("image/jpeg", added.single().mime)
        assertEquals(1, camera.launches)
        assertNull(record(w).pending)
        assertTrue(File(files, CaptureFolder.DIR).listFiles().orEmpty().isEmpty(), "no capture file is left")
    }

    @Test
    fun `the FileProvider shares the camera folder and nothing wider of the app files`() {
        val xml = File("src/main/res/xml/oc_files_paths.xml").readText()
        assertTrue(
            Regex("""<files-path\s+name="camera"\s+path="${CaptureFolder.DIR}/"\s*/>""").containsMatchIn(xml),
            "the camera root must match CaptureFiles.DIR",
        )
        assertFalse(Regex("""<files-path[^>]*path="\.?/?"""").containsMatchIn(xml), "never the whole files folder")
    }

    // ---- helpers

    private val target =
        AttachTarget(ConversationRef(ProfileId("alpha"), SessionId("s1")), directory = "/srv/app")

    private fun gate(status: PermissionStatus) =
        object : PermissionGate {
            override suspend fun status(permission: AppPermission) = status

            override suspend fun request(permission: AppPermission) = status

            override fun openSettings(permission: AppPermission) {}
        }

    private fun intake(w: World, camera: ActivityResults, permissions: PermissionGate) =
        AttachmentIntake(
            activity = camera,
            permissions = permissions,
            reader = { _, _ -> Outcome.Failed(Failure.Device()) },
            codec = FileCodec,
            clipboard = UnavailableClipboardImages,
            captures = captures(w),
            photos = record(w),
            words =
                object : AttachmentWords {
                    override fun officeHeader(name: String) = name

                    override fun officeTruncated() = ""
                },
            dispatchers = dispatchers,
            clock = clock,
        )

    private class CountingCamera(private val write: (PhotoTarget) -> Unit = {}) : ActivityResults {
        var launches = 0

        override suspend fun takePhoto(target: PhotoTarget): Outcome<PickedFile?> {
            launches++
            write(target)
            return Outcome.Ok(PickedFile(target.uri, null, "image/jpeg", null))
        }

        override suspend fun openDocument(mimeTypes: List<String>): Outcome<PickedFile?> = Outcome.Failed(Failure.Device())

        override suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>> = Outcome.Failed(Failure.Device())

        override suspend fun createDocument(suggestedName: String, mime: String): Outcome<DocumentTarget?> =
            Outcome.Failed(Failure.Device())

        override suspend fun openDocumentTree(): Outcome<TreeTarget?> = Outcome.Failed(Failure.Device())
    }

    /** Reads the picked file as-is (the Android codec's scaling is covered in :android:media). */
    private object FileCodec : ImageCodec {
        override suspend fun preparePhoto(source: PickedFile, rules: PhotoRules): Outcome<PreparedImage> {
            val file = File(URI(source.uri))
            if (!file.isFile) return Outcome.Failed(Failure.Media(MediaProblem.PhotoUnavailable))
            return Outcome.Ok(PreparedImage(file.readBytes(), "image/jpeg", 1, 1))
        }

        override suspend fun prepareBytes(bytes: ByteArray, declaredMime: String?, rules: PhotoRules): Outcome<PreparedImage> =
            Outcome.Ok(PreparedImage(bytes, "image/jpeg", 1, 1))
    }

    private fun flutterRecord(id: String, path: String?): String =
        buildString {
            append("""{"id":"$id","profileID":"alpha","sessionID":"s1","directory":null,"workspace":null,"ref":null,""")
            append(""""path":${path?.let { "\"$it\"" } ?: "null"},"name":"image_picker_123.jpg","failed":false}""")
        }
}
