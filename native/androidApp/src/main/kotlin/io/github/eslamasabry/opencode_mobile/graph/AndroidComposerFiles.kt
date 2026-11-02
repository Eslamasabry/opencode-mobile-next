package io.github.eslamasabry.opencode_mobile.graph

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
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
import io.github.eslamasabry.oc.engine.media.attach.AttachmentIntake
import io.github.eslamasabry.oc.engine.media.attach.AttachmentWords
import io.github.eslamasabry.oc.engine.media.attach.CaptureFiles
import io.github.eslamasabry.oc.engine.media.attach.PhotoRecord
import io.github.eslamasabry.oc.engine.media.attach.PickedFileReader
import io.github.eslamasabry.oc.engine.media.attach.PendingPhotoInfo
import io.github.eslamasabry.oc.engine.media.attach.UnavailableClipboardImages
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

/**
 * The Android `PromptFilePicker` of the full-composer slice (2026-11-02). The root activity calls
 * [attach] before rendering (the OpenDocument launcher must exist before STARTED) and [detach] on
 * destroy. [pickFile] reuses the shared `AttachmentIntake` limits, MIME gate, office conversion and
 * data-URL encoding; a cancel from the system picker is a null success. Failures carry no file
 * name, URI or exception text, and nothing is logged. Only the document picker is offered: the
 * camera, gallery and clipboard seams exist solely to construct the intake and stay unadvertised.
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
        val intake =
            AttachmentIntake(
                activity = PickerActivityResults(activity, dispatchers, launcher, requests, requestRef),
                permissions = UnavailablePermissions,
                reader = ContentReader(activity, dispatchers),
                codec = AndroidImageCodec(activity.contentResolver, dispatchers),
                clipboard = UnavailableClipboardImages,
                captures = UnavailableCaptures,
                photos = UnavailablePhotos,
                words = ComposerFileWords,
                dispatchers = dispatchers,
                clock = clock,
            )
        return Host(activity, intake, requests, launcher)
    }

    private class Host(
        val activity: ComponentActivity,
        val intake: AttachmentIntake,
        private val requests: PickRequests<Uri>,
        private val launcher: ActivityResultLauncher<Array<String>>,
    ) {
        fun close() {
            requests.cancelActive()
            launcher.unregister()
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

        override suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>> = Outcome.Failed(Failure.Device())

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
