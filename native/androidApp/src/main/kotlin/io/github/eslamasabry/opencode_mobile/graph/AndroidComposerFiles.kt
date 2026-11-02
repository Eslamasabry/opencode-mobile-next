package io.github.eslamasabry.opencode_mobile.graph

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.media.image.AndroidImageCodec
import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.domain.platform.ActivityResults
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.DocumentTarget
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import io.github.eslamasabry.oc.core.domain.platform.PhotoTarget
import io.github.eslamasabry.oc.core.domain.platform.PickedFile
import io.github.eslamasabry.oc.core.domain.platform.PromptFilePicker
import io.github.eslamasabry.oc.core.domain.platform.TreeTarget
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.media.attach.AttachTarget
import io.github.eslamasabry.oc.engine.media.attach.AttachmentIntake
import io.github.eslamasabry.oc.engine.media.attach.ClipboardImage
import io.github.eslamasabry.oc.engine.media.attach.ClipboardImages
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerAttachSource
import io.github.eslamasabry.oc.engine.media.attach.AttachmentWords
import io.github.eslamasabry.oc.engine.media.attach.CaptureFiles
import io.github.eslamasabry.oc.engine.media.attach.PhotoRecord
import io.github.eslamasabry.oc.engine.media.attach.PickedFileReader
import io.github.eslamasabry.oc.engine.media.attach.PendingPhotoInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

/**
 * The Android `PromptFilePicker` of the full-composer slice (2026-11-02). The root activity calls
 * [attach] before rendering (the OpenDocument launcher must exist before STARTED) and [detach] on
 * destroy. [pickFile] reuses the shared `AttachmentIntake` limits, MIME gate, office conversion and
 * data-URL encoding; a cancel from the system picker is a null success. Failures carry no file
 * name, URI or exception text, and nothing is logged. The system photo picker and the clipboard
 * picture are offered through [AndroidComposerMedia]; the camera stays closed (no FileProvider).
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PromptFilePicker>())
@Inject
class AndroidComposerFiles(
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
) : PromptFilePicker {
    private val lock = Any()

    @Volatile private var host: Host? = null

    /** Register the OpenDocument launcher on [activity]; replacing a previous host retires it. */
    fun attach(activity: ComponentActivity) {
        val previous =
            synchronized(lock) {
                val current = host
                if (current?.activity === activity) return
                host = newHost(activity)
                current
            }
        previous?.close()
    }

    /** The pick of a destroyed activity resolves as cancelled; its launcher never answers again. */
    fun detach(activity: ComponentActivity) {
        val retired =
            synchronized(lock) {
                val current = host ?: return
                if (current.activity !== activity) return
                host = null
                current
            }
        retired.close()
    }

    /** The live intake, or null between activities. */
    internal fun intake(): AttachmentIntake? = synchronized(lock) { host }?.intake

    override suspend fun pickFile(current: List<PromptAttachment>): Outcome<PromptAttachment?> {
        val attached = synchronized(lock) { host } ?: return Outcome.Failed(Failure.Device())
        return when (val picked = attached.intake.pickFile(current)) {
            is Outcome.Ok -> Outcome.Ok(picked.value.added.firstOrNull())
            is Outcome.Failed -> picked
        }
    }

    // ---- the activity-bound state

    private fun newHost(activity: ComponentActivity): Host {
        val requests = PickRequests<Uri>()
        val requestRef = AtomicReference<CompletableDeferred<Uri?>?>()
        val launcher =
            activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                // Only the request this launcher was last launched with accepts the result; a
                // result arriving for a retired or detached request is dropped.
                val request = requestRef.getAndSet(null) ?: return@registerForActivityResult
                requests.resolve(request, uri)
            }
        val photoRequests = PickRequests<List<Uri>>()
        val photoRef = AtomicReference<CompletableDeferred<List<Uri>?>?>()
        val photoLauncher =
            activity.registerForActivityResult(
                ActivityResultContracts.PickMultipleVisualMedia(AttachmentLimitsMaxCount)
            ) { uris ->
                val request = photoRef.getAndSet(null) ?: return@registerForActivityResult
                photoRequests.resolve(request, uris)
            }
        val intake =
            AttachmentIntake(
                activity =
                    PickerActivityResults(
                        activity,
                        dispatchers,
                        launcher,
                        requests,
                        requestRef,
                        photoLauncher,
                        photoRequests,
                        photoRef,
                    ),
                permissions = UnavailablePermissions,
                reader = ContentReader(activity, dispatchers),
                codec = AndroidImageCodec(activity.contentResolver, dispatchers),
                clipboard = AndroidClipboardImages(activity, dispatchers),
                captures = UnavailableCaptures,
                photos = GalleryPhotos,
                words = ComposerFileWords,
                dispatchers = dispatchers,
                clock = clock,
            )
        return Host(activity, intake, requests, launcher, photoRequests, photoLauncher)
    }

    private class Host(
        val activity: ComponentActivity,
        val intake: AttachmentIntake,
        private val requests: PickRequests<Uri>,
        private val launcher: ActivityResultLauncher<Array<String>>,
        private val photoRequests: PickRequests<List<Uri>>,
        private val photoLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
    ) {
        fun close() {
            requests.cancelActive()
            photoRequests.cancelActive()
            launcher.unregister()
            photoLauncher.unregister()
        }
    }

    // ---- the seams the intake is constructed from

    /** The real document path; every other ActivityResults surface stays refused. */
    private class PickerActivityResults(
        private val activity: ComponentActivity,
        private val dispatchers: AppDispatchers,
        private val launcher: ActivityResultLauncher<Array<String>>,
        private val requests: PickRequests<Uri>,
        private val requestRef: AtomicReference<CompletableDeferred<Uri?>?>,
        private val photoLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
        private val photoRequests: PickRequests<List<Uri>>,
        private val photoRef: AtomicReference<CompletableDeferred<List<Uri>?>?>,
    ) : ActivityResults {
        override suspend fun openDocument(mimeTypes: List<String>): Outcome<PickedFile?> {
            val request = requests.begin() ?: return Outcome.Failed(Failure.Busy(BusyKind.Starting))
            var launched = false
            try {
                return attempt({ it.asPickFailure() }) {
                    requestRef.set(request)
                    withContext(dispatchers.main) {
                        launcher.launch(mimeTypes.toTypedArray())
                        launched = true
                    }
                    when (val uri = request.await()) {
                        null -> null
                        else -> withContext(dispatchers.io) { uri.asPickedFile(activity.contentResolver) }
                    }
                }
            } finally {
                if (launched) {
                    // A cancelled caller cannot identify which later callback belongs to a new
                    // launch. Keep this slot until the old OS result is drained or the host dies.
                    requests.retire(request)
                } else {
                    requestRef.compareAndSet(request, null)
                    requests.releaseUnlaunched(request)
                }
            }
        }

        /** The system photo picker (no storage permission); the same one-pick-per-host rules. */
        override suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>> {
            val request = photoRequests.begin() ?: return Outcome.Failed(Failure.Busy(BusyKind.Starting))
            var launched = false
            try {
                return attempt({ it.asPickFailure() }) {
                    photoRef.set(request)
                    withContext(dispatchers.main) {
                        photoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                        launched = true
                    }
                    val uris = request.await().orEmpty().take(limit)
                    withContext(dispatchers.io) {
                        uris.map { it.asPickedFile(activity.contentResolver) }.toImmutableList()
                    }
                }
            } finally {
                if (launched) photoRequests.retire(request)
                else {
                    photoRef.compareAndSet(request, null)
                    photoRequests.releaseUnlaunched(request)
                }
            }
        }

        override suspend fun takePhoto(target: PhotoTarget): Outcome<PickedFile?> = Outcome.Failed(Failure.Device())

        override suspend fun createDocument(suggestedName: String, mime: String): Outcome<DocumentTarget?> =
            Outcome.Failed(Failure.Device())

        override suspend fun openDocumentTree(): Outcome<TreeTarget?> = Outcome.Failed(Failure.Device())
    }

    /** Bounded streaming reads of a picked document off the main thread, stream always closed. */
    private class ContentReader(
        private val activity: ComponentActivity,
        private val dispatchers: AppDispatchers,
    ) : PickedFileReader {
        override suspend fun readBounded(file: PickedFile, limit: Long): Outcome<ByteArray> =
            withContext(dispatchers.io) {
                attempt({ it.asReadFailure() }) {
                    val stream = activity.contentResolver.openInputStream(Uri.parse(file.uri)) ?: throw IOException()
                    stream.use { DocumentRead.atMost(it, limit) }
                }
            }
    }
}

/** Names and sizes are hints: a provider that cannot describe a document must not lose the pick. */
private fun Uri.asPickedFile(resolver: ContentResolver): PickedFile {
    var name: String? = null
    var size: Long? = null
    try {
        resolver
            .query(this, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        val reported = cursor.getLong(sizeIndex)
                        if (reported >= 0) size = reported
                    }
                }
            }
    } catch (_: Exception) {
        // The bounded read decides whether the document is really readable.
    }
    val mime =
        try {
            resolver.getType(this)
        } catch (_: Exception) {
            null
        }
    return PickedFile(uri = toString(), displayName = name, mime = mime, sizeBytes = size)
}

/** One system pick at a time per host; a result only ever completes the request that is still active. */
internal class PickRequests<T : Any> {
    private var active: CompletableDeferred<T?>? = null

    /** null while another pick of this host is still open. */
    @Synchronized
    fun begin(): CompletableDeferred<T?>? {
        if (active != null) return null
        return CompletableDeferred<T?>().also { active = it }
    }

    /** Only the still-active request accepts a value; a retired one drops it (no stale replay). */
    @Synchronized
    fun resolve(request: CompletableDeferred<T?>, value: T?) {
        if (active !== request) return
        active = null
        request.complete(value)
    }

    /** Abandon this result, but keep its OS slot occupied until the callback drains it. */
    @Synchronized
    fun retire(request: CompletableDeferred<T?>): Boolean {
        if (active !== request) return false
        request.complete(null)
        return true
    }

    /** No activity was launched, so no callback can arrive for this request. */
    @Synchronized
    fun releaseUnlaunched(request: CompletableDeferred<T?>) {
        if (active !== request) return
        active = null
        request.complete(null)
    }

    /** The host is closing: a still-open pick resolves as cancelled instead of hanging. */
    @Synchronized
    fun cancelActive() {
        val current = active ?: return
        active = null
        current.complete(null)
    }
}

/**
 * Blocking bounded read of a picked document on the IO dispatcher: at most [limit] bytes are kept plus
 * the single extra byte that proves more exist (same contract as engine/media's suspend
 * `BoundedRead`, which cannot wrap a blocking stream); the stream is closed on every path.
 */
internal object DocumentRead {
    class TooLarge : Exception()

    fun atMost(stream: InputStream, limit: Long): ByteArray {
        require(limit >= 0)
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var kept = 0L
            while (true) {
                val wanted = minOf(buffer.size.toLong(), limit - kept + 1).toInt()
                val read = input.read(buffer, 0, wanted)
                if (read < 0) break
                kept += read
                if (kept > limit) throw TooLarge()
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }
}

/** A pick that never reaches the provider becomes plain words, never exception text. */
internal fun Throwable.asPickFailure(): Failure =
    when (this) {
        is ActivityNotFoundException -> Failure.Media(MediaProblem.NoAppToOpen)
        else -> asReadFailure()
    }

/** Read-side classifier shared by the picker and the bounded reader. */
internal fun Throwable.asReadFailure(): Failure =
    when (this) {
        is DocumentRead.TooLarge -> Failure.Media(MediaProblem.AttachmentTooLarge)
        is SecurityException -> Failure.Media(MediaProblem.AttachmentContentMissing)
        is IOException -> Failure.Media(MediaProblem.AttachmentContentMissing)
        else -> Failure.Device()
    }

/** The camera and photo paths stay closed in this slice; the intake constructor needs them present. */
private object UnavailablePermissions : PermissionGate {
    override suspend fun status(permission: AppPermission): PermissionStatus = PermissionStatus.NotApplicable

    override suspend fun request(permission: AppPermission): PermissionStatus = PermissionStatus.Denied

    override fun openSettings(permission: AppPermission) {}
}

private object UnavailableCaptures : CaptureFiles {
    override fun target(captureId: String): PhotoTarget = throw IllegalStateException("camera capture is not part of this slice")

    override suspend fun existing(captureId: String): PickedFile? = null

    override suspend fun delete(captureId: String) {}
}

private object UnavailablePhotos : PhotoRecord {
    override val pending: PendingPhotoInfo? = null

    override suspend fun begin(profileId: String, sessionId: String, directory: String?, workspace: String?): Outcome<String> =
        Outcome.Failed(Failure.Device())

    override suspend fun complete(id: String, name: String, bytes: ByteArray): Outcome<Boolean> =
        Outcome.Failed(Failure.Device())

    override suspend fun markFailed(id: String): Outcome<Unit> = Outcome.Failed(Failure.Device())

    override suspend fun discard(id: String): Outcome<Unit> = Outcome.Ok(Unit)
}

/**
 * Office header copy until `:ui:words` provides the shared `AttachmentWords` binding; the words are
 * the final user-visible text of a converted document, not placeholders.
 */
private object ComposerFileWords : AttachmentWords {
    override fun officeHeader(name: String): String = "Contents of $name"

    override fun officeTruncated(): String = "The text was shortened to fit the attachment size limit."
}

/** Photos and the clipboard picture for the composer's Add sheet; the camera needs a FileProvider. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ComposerAttachSource>())
@Inject
class AndroidComposerMedia(private val files: AndroidComposerFiles) : ComposerAttachSource {
    override val kinds: Set<AttachKind> = setOf(AttachKind.Photos, AttachKind.Clipboard)

    override suspend fun attach(kind: AttachKind, current: List<PromptAttachment>): Outcome<List<PromptAttachment>> {
        val intake = files.intake() ?: return Outcome.Failed(Failure.Device())
        val result =
            when (kind) {
                AttachKind.Photos -> intake.pickPhotos(GalleryTarget, current)
                AttachKind.Clipboard -> intake.fromClipboard(current)
                AttachKind.Camera -> return Outcome.Failed(Failure.Media(MediaProblem.NoCamera))
            }
        return when (result) {
            is Outcome.Ok ->
                if (result.value.added.isEmpty() && result.value.skipped != null) Outcome.Failed(result.value.skipped!!)
                else Outcome.Ok(result.value.added.toList())
            is Outcome.Failed -> result
        }
    }

    private companion object {
        /** The gallery's pending record is discarded when the picker closes; it never needs a target. */
        val GalleryTarget =
            AttachTarget(
                io.github.eslamasabry.oc.core.common.ids.ConversationRef(
                    io.github.eslamasabry.oc.core.common.ids.ProfileId("composer"),
                    io.github.eslamasabry.oc.core.common.ids.SessionId("gallery"),
                )
            )
    }
}

/** The clipboard's first picture, read bounded off the main thread; nothing is logged. */
private class AndroidClipboardImages(
    private val activity: ComponentActivity,
    private val dispatchers: AppDispatchers,
) : ClipboardImages {
    private fun pictureUri(): Uri? {
        val manager = activity.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val uri = clip.getItemAt(i).uri ?: continue
            val type =
                try {
                    activity.contentResolver.getType(uri)
                } catch (_: Exception) {
                    null
                }
            if (type?.startsWith("image/") == true) return uri
        }
        return null
    }

    override suspend fun hasImage(): Boolean = withContext(dispatchers.main) { pictureUri() != null }

    override suspend fun read(maxBytes: Int): Outcome<ClipboardImage?> {
        val uri = withContext(dispatchers.main) { pictureUri() } ?: return Outcome.Ok(null)
        return withContext(dispatchers.io) {
            attempt({
                if (it is DocumentRead.TooLarge) Failure.Media(MediaProblem.ClipboardImageTooLarge)
                else it.asReadFailure()
            }) {
                val mime = activity.contentResolver.getType(uri) ?: "image/png"
                val stream = activity.contentResolver.openInputStream(uri) ?: throw IOException()
                ClipboardImage(mime, DocumentRead.atMost(stream, maxBytes.toLong()))
            }
        }
    }
}

/** The gallery path keeps no recovery record: nothing is pending once the picker has answered. */
private object GalleryPhotos : PhotoRecord {
    override val pending: PendingPhotoInfo? = null

    override suspend fun begin(profileId: String, sessionId: String, directory: String?, workspace: String?): Outcome<String> =
        Outcome.Ok("gallery")

    override suspend fun complete(id: String, name: String, bytes: ByteArray): Outcome<Boolean> = Outcome.Ok(true)

    override suspend fun markFailed(id: String): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun discard(id: String): Outcome<Unit> = Outcome.Ok(Unit)
}

private const val AttachmentLimitsMaxCount = 5
