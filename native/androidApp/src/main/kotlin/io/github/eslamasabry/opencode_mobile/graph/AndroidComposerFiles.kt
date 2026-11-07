package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
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
 * name, URI or exception text, and nothing is logged. The system photo picker, the clipboard
 * picture and the camera (E05-S01) are offered through [AndroidComposerMedia]. The camera asks for
 * its permission first, records the pending photo (`oc.pendingPromptPhoto`) before it opens, and
 * writes into an app-owned file shared through the FileProvider ([ComposerCameraWiring]).
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PromptFilePicker>())
@Inject
class AndroidComposerFiles(
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val camera: ComposerCameraWiring,
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

    /** The camera's intake: the same limits, with the real pending-photo record. */
    internal fun cameraIntake(): AttachmentIntake? = synchronized(lock) { host }?.cameraIntake

    /** "Open settings" after a camera refusal: the app's page in Android settings. */
    internal fun openCameraSettings() {
        val activity = synchronized(lock) { host }?.activity
        val context = activity ?: camera.application()
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // A phone without that settings page: the words already say where to allow it.
        }
    }

    /** The camera launcher of whichever activity is live now (a recreated one included). */
    private fun cameraLauncher(): ActivityResultLauncher<Uri>? = synchronized(lock) { host }?.cameraLauncher

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
        // E05-S01: the camera's answer goes to the process's waiting request, whichever activity
        // launched it; with none waiting (the app was killed meanwhile) it starts the recovery.
        val cameraLauncher =
            activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
                if (!camera.requests.deliver(saved)) camera.orphanResult(saved)
            }
        val permissionRequests = PickRequests<Boolean>()
        val permissionRef = AtomicReference<CompletableDeferred<Boolean?>?>()
        val permissionLauncher =
            activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                val request = permissionRef.getAndSet(null) ?: return@registerForActivityResult
                permissionRequests.resolve(request, granted)
            }
        // Added after the launchers: their pending results are delivered first on ON_START.
        val started =
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) camera.hostStarted() }
        activity.lifecycle.addObserver(started)
        val cameraIntake =
            AttachmentIntake(
                activity = CameraActivityResults(dispatchers, camera.requests, ::cameraLauncher),
                permissions =
                    CameraPermissionGate(activity, dispatchers, permissionLauncher, permissionRequests, permissionRef),
                reader = ContentReader(activity, dispatchers),
                codec = AndroidImageCodec(activity.contentResolver, dispatchers),
                clipboard = AndroidClipboardImages(activity, dispatchers),
                captures = camera.captures,
                photos = camera.record,
                words = ComposerFileWords,
                dispatchers = dispatchers,
                clock = clock,
            )
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
        return Host(
            activity,
            intake,
            cameraIntake,
            requests,
            launcher,
            photoRequests,
            photoLauncher,
            cameraLauncher,
            permissionRequests,
            permissionLauncher,
            started,
        )
    }

    private class Host(
        val activity: ComponentActivity,
        val intake: AttachmentIntake,
        val cameraIntake: AttachmentIntake,
        private val requests: PickRequests<Uri>,
        private val launcher: ActivityResultLauncher<Array<String>>,
        private val photoRequests: PickRequests<List<Uri>>,
        private val photoLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
        val cameraLauncher: ActivityResultLauncher<Uri>,
        private val permissionRequests: PickRequests<Boolean>,
        private val permissionLauncher: ActivityResultLauncher<String>,
        private val started: LifecycleEventObserver,
    ) {
        /**
         * The camera request is the process's, not this activity's: a photo taken while the
         * activity is recreated reaches the next activity's launcher and the same waiting request.
         */
        fun close() {
            requests.cancelActive()
            photoRequests.cancelActive()
            permissionRequests.cancelActive()
            launcher.unregister()
            photoLauncher.unregister()
            cameraLauncher.unregister()
            permissionLauncher.unregister()
            activity.lifecycle.removeObserver(started)
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

    /**
     * The system camera (TakePicture) into the FileProvider target; every other surface is
     * refused. One camera request per process ([CameraRequests]); the launcher is looked up at
     * launch time, so a recreated activity's launcher is used.
     */
    private class CameraActivityResults(
        private val dispatchers: AppDispatchers,
        private val requests: CameraRequests,
        private val launcher: () -> ActivityResultLauncher<Uri>?,
    ) : ActivityResults {
        override suspend fun takePhoto(target: PhotoTarget): Outcome<PickedFile?> {
            if (target.uri.isEmpty()) return Outcome.Failed(Failure.Media(MediaProblem.CameraFailed))
            val request = requests.begin() ?: return Outcome.Failed(Failure.Busy(BusyKind.Starting))
            var launched = false
            try {
                return attempt({ it.asCameraFailure() }) {
                    withContext(dispatchers.main) {
                        val live = launcher() ?: throw IllegalStateException()
                        live.launch(Uri.parse(target.uri))
                        launched = true
                    }
                    if (request.await() == true) PickedFile(target.uri, null, "image/jpeg", null) else null
                }
            } finally {
                if (launched) requests.retire(request) else requests.releaseUnlaunched(request)
            }
        }

        override suspend fun openDocument(mimeTypes: List<String>): Outcome<PickedFile?> =
            Outcome.Failed(Failure.Device())

        override suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>> =
            Outcome.Failed(Failure.Device())

        override suspend fun createDocument(suggestedName: String, mime: String): Outcome<DocumentTarget?> =
            Outcome.Failed(Failure.Device())

        override suspend fun openDocumentTree(): Outcome<TreeTarget?> = Outcome.Failed(Failure.Device())
    }

    /**
     * The camera permission, asked only from the Take photo tap ([CameraPermissionRules]). A
     * refusal Android will not ask about again is [PermissionStatus.PermanentlyDenied]; the composer
     * then offers Open settings.
     */
    private class CameraPermissionGate(
        private val activity: ComponentActivity,
        private val dispatchers: AppDispatchers,
        private val launcher: ActivityResultLauncher<String>,
        private val requests: PickRequests<Boolean>,
        private val requestRef: AtomicReference<CompletableDeferred<Boolean?>?>,
    ) : PermissionGate {
        private fun granted() =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        private fun declared(): Boolean =
            try {
                val info: PackageInfo =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        activity.packageManager.getPackageInfo(
                            activity.packageName,
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
                    }
                info.requestedPermissions.orEmpty().contains(Manifest.permission.CAMERA)
            } catch (_: PackageManager.NameNotFoundException) {
                true // ask rather than open a camera that may refuse us
            }

        override suspend fun status(permission: AppPermission): PermissionStatus {
            if (permission != AppPermission.Camera) return PermissionStatus.NotApplicable
            return withContext(dispatchers.main) {
                CameraPermissionRules.before(declared(), granted()) ?: PermissionStatus.Denied
            }
        }

        override suspend fun request(permission: AppPermission): PermissionStatus {
            if (permission != AppPermission.Camera) return PermissionStatus.NotApplicable
            withContext(dispatchers.main) { CameraPermissionRules.before(declared(), granted()) }?.let { return it }
            val request = requests.begin() ?: return PermissionStatus.Denied
            var launched = false
            try {
                requestRef.set(request)
                withContext(dispatchers.main) {
                    launcher.launch(Manifest.permission.CAMERA)
                    launched = true
                }
                val answer = request.await() ?: return PermissionStatus.Denied
                return withContext(dispatchers.main) {
                    CameraPermissionRules.after(
                        answer,
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA),
                    )
                }
            } catch (_: ActivityNotFoundException) {
                return PermissionStatus.Denied
            } finally {
                if (launched) requests.retire(request)
                else {
                    requestRef.compareAndSet(request, null)
                    requests.releaseUnlaunched(request)
                }
            }
        }

        override fun openSettings(permission: AppPermission) {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
            )
        }
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

    @Synchronized fun isActive(): Boolean = active != null

    /**
     * The launcher's answer for whichever request is open, even one launched by an activity that
     * was since recreated. False when nobody still waits (none open, or it was retired): the caller
     * treats the answer as an orphan.
     */
    @Synchronized
    fun resolveCurrent(value: T?): Boolean {
        val current = active ?: return false
        active = null
        return current.complete(value)
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

/** A launch that never opened the camera: no camera app is a missing camera, not a file error. */
internal fun Throwable.asCameraFailure(): Failure =
    when (this) {
        is ActivityNotFoundException -> Failure.Media(MediaProblem.NoCamera)
        is SecurityException -> Failure.Media(MediaProblem.CameraDenied)
        else -> Failure.Media(MediaProblem.CameraFailed)
    }

/** Read-side classifier shared by the picker and the bounded reader. */
internal fun Throwable.asReadFailure(): Failure =
    when (this) {
        is DocumentRead.TooLarge -> Failure.Media(MediaProblem.AttachmentTooLarge)
        is SecurityException -> Failure.Media(MediaProblem.AttachmentContentMissing)
        is IOException -> Failure.Media(MediaProblem.AttachmentContentMissing)
        else -> Failure.Device()
    }

/** The gallery, file and clipboard intake never asks for a permission or opens the camera. */
private object UnavailablePermissions : PermissionGate {
    override suspend fun status(permission: AppPermission): PermissionStatus = PermissionStatus.NotApplicable

    override suspend fun request(permission: AppPermission): PermissionStatus = PermissionStatus.Denied

    override fun openSettings(permission: AppPermission) {}
}

private object UnavailableCaptures : CaptureFiles {
    /** Never reached: only the camera intake takes photos, and it has the real capture files. */
    override fun target(captureId: String): PhotoTarget = PhotoTarget("")

    override suspend fun existing(captureId: String): PickedFile? = null

    override suspend fun delete(captureId: String) {}
}

/**
 * Office header copy until `:ui:words` provides the shared `AttachmentWords` binding; the words are
 * the final user-visible text of a converted document, not placeholders.
 */
private object ComposerFileWords : AttachmentWords {
    override fun officeHeader(name: String): String = "Contents of $name"

    override fun officeTruncated(): String = "The text was shortened to fit the attachment size limit."
}

/**
 * Photos, the camera and the clipboard picture for the composer's Add sheet. Take photo is offered
 * only on a phone with a camera; its photo is recorded for the conversation it was asked from.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ComposerAttachSource>())
@Inject
class AndroidComposerMedia(private val files: AndroidComposerFiles, application: Application) : ComposerAttachSource {
    override val kinds: Set<AttachKind> =
        buildSet {
            add(AttachKind.Photos)
            if (application.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) add(AttachKind.Camera)
            add(AttachKind.Clipboard)
        }

    override suspend fun attach(kind: AttachKind, current: List<PromptAttachment>): Outcome<List<PromptAttachment>> =
        attach(kind, current, null)

    override suspend fun attach(
        kind: AttachKind,
        current: List<PromptAttachment>,
        conversation: ConversationRef?,
    ): Outcome<List<PromptAttachment>> {
        val result =
            when (kind) {
                AttachKind.Photos -> (files.intake() ?: return noHost()).pickPhotos(GalleryTarget, current)
                AttachKind.Clipboard -> (files.intake() ?: return noHost()).fromClipboard(current)
                AttachKind.Camera -> {
                    // Without its conversation a recovered photo could not find its draft.
                    val ref = conversation ?: return Outcome.Failed(Failure.Device())
                    (files.cameraIntake() ?: return noHost()).takePhoto(AttachTarget(ref), current)
                }
            }
        return when (result) {
            is Outcome.Ok ->
                if (result.value.added.isEmpty() && result.value.skipped != null) Outcome.Failed(result.value.skipped!!)
                else Outcome.Ok(result.value.added.toList())
            is Outcome.Failed -> result
        }
    }

    override fun openSettings(kind: AttachKind) {
        if (kind == AttachKind.Camera) files.openCameraSettings()
    }

    private fun noHost(): Outcome<List<PromptAttachment>> = Outcome.Failed(Failure.Device())

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
