package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import io.github.eslamasabry.oc.core.domain.platform.PhotoTarget
import io.github.eslamasabry.oc.core.domain.platform.PickedFile
import io.github.eslamasabry.oc.engine.composer.photo.DraftPhotoRecovery
import io.github.eslamasabry.oc.engine.composer.photo.PhotoProblem
import io.github.eslamasabry.oc.engine.composer.photo.PhotoRecoveryResult
import io.github.eslamasabry.oc.engine.composer.photo.PhotoRecoveryStore
import io.github.eslamasabry.oc.engine.composer.photo.PhotoResult
import io.github.eslamasabry.oc.engine.media.attach.CaptureFiles
import io.github.eslamasabry.oc.engine.media.attach.DraftPhotoJoin
import io.github.eslamasabry.oc.engine.media.attach.LauncherOutcome
import io.github.eslamasabry.oc.engine.media.attach.PendingPhotoInfo
import io.github.eslamasabry.oc.engine.media.attach.PhotoRecord
import io.github.eslamasabry.oc.engine.media.attach.RecoveryResult
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import io.github.eslamasabry.oc.android.camera.capture.CaptureFiles as CaptureFolder

// E05-S01 camera capture in the composer. These parts hold no Android type, so the JVM unit tests
// run them against the real composer stores; the launchers and the permission dialog live in
// AndroidComposerFiles.

/**
 * The media pipeline's [PhotoRecord] over the composer's [PhotoRecoveryStore], which owns the
 * `oc.pendingPromptPhoto` record in the Flutter app's format (`lib/state/prompt_photos.dart`): a
 * phone moved from the Flutter app keeps its pending photo. [ready] warms the stores first; a store
 * that cannot be read refuses, so the camera never opens without its record.
 */
internal class ComposerPhotoRecord(
    private val store: PhotoRecoveryStore,
    private val ready: suspend () -> Boolean,
) : PhotoRecord {
    override val pending: PendingPhotoInfo?
        get() =
            try {
                store.pending?.let {
                    PendingPhotoInfo(
                        id = it.id,
                        profileId = it.profileId,
                        sessionId = it.sessionId,
                        hasBytes = it.ref != null,
                        path = it.path,
                        name = it.name,
                        failed = it.failed,
                    )
                }
            } catch (_: IllegalStateException) {
                null // the stores are not warm yet: nothing can be recovered before they are
            }

    override suspend fun begin(
        profileId: String,
        sessionId: String,
        directory: String?,
        workspace: String?,
    ): Outcome<String> {
        if (!ready()) return Outcome.Failed(Failure.Media(MediaProblem.PhotoStorage))
        return when (val begun = store.begin(profileId, sessionId, directory, workspace)) {
            is PhotoResult.Ok -> Outcome.Ok(begun.value.id)
            is PhotoResult.Refused -> Outcome.Failed(begun.problem.asMediaFailure())
        }
    }

    override suspend fun complete(id: String, name: String, bytes: ByteArray): Outcome<Boolean> =
        when (val done = store.complete(id, name, bytes)) {
            is PhotoResult.Ok -> Outcome.Ok(done.value != null)
            is PhotoResult.Refused -> Outcome.Failed(done.problem.asMediaFailure())
        }

    override suspend fun markFailed(id: String): Outcome<Unit> =
        when (val marked = store.markFailed(id)) {
            is PhotoResult.Ok -> Outcome.Ok(Unit)
            is PhotoResult.Refused -> Outcome.Failed(marked.problem.asMediaFailure())
        }

    override suspend fun discard(id: String): Outcome<Unit> =
        when (val gone = store.discard(id)) {
            is PhotoResult.Ok -> Outcome.Ok(Unit)
            is PhotoResult.Refused -> Outcome.Failed(gone.problem.asMediaFailure())
        }
}

/** A secret-looking identity is a storage refusal to the person: the record was not written. */
internal fun PhotoProblem.asMediaFailure(): Failure =
    Failure.Media(
        when (this) {
            PhotoProblem.Storage,
            PhotoProblem.SensitiveIdentity -> MediaProblem.PhotoStorage
            PhotoProblem.Pending -> MediaProblem.PhotoPending
            PhotoProblem.TooLarge -> MediaProblem.PhotoTooLarge
            PhotoProblem.Unsupported -> MediaProblem.PhotoUnsupported
            PhotoProblem.Unavailable -> MediaProblem.PhotoUnavailable
        }
    )

/** The composer's draft join, as the media pipeline's recovery calls it. */
internal class ComposerDraftJoin(private val recovery: DraftPhotoRecovery) : DraftPhotoJoin {
    override suspend fun join(): RecoveryResult =
        when (recovery.recover()) {
            PhotoRecoveryResult.NothingPending -> RecoveryResult.NothingPending
            PhotoRecoveryResult.Attached -> RecoveryResult.Attached
            PhotoRecoveryResult.ProfileRemoved -> RecoveryResult.ProfileRemoved
            PhotoRecoveryResult.RetryRequired -> RecoveryResult.RetryRequired
        }
}

/**
 * Camera photos land in `files/camera-capture-v1/<record id>.jpg` ([CaptureFolder]), shared with
 * the camera app through the FileProvider root `camera` ([contentUri]). A record written by the
 * Flutter app may instead name the file its picker kept ([PendingPhotoInfo.path]); that file is
 * used only when it is a non-empty file inside one of the app's own [legacyRoots].
 */
internal class ComposerCaptureFiles(
    private val folder: CaptureFolder,
    private val contentUri: (File) -> String?,
    private val pending: () -> PendingPhotoInfo?,
    private val legacyRoots: List<File>,
    private val io: CoroutineDispatcher,
) : CaptureFiles {
    /** An empty uri means "no shareable target"; the camera launch refuses it without opening. */
    override fun target(captureId: String): PhotoTarget =
        PhotoTarget(folder.fileFor(captureId)?.let(contentUri).orEmpty())

    override suspend fun existing(captureId: String): PickedFile? =
        withContext(io) {
            if (folder.hasPhoto(captureId)) {
                val file = folder.fileFor(captureId) ?: return@withContext null
                PickedFile(file.toURI().toString(), photoName(captureId), JPEG, file.length())
            } else {
                legacy(captureId)?.let {
                    PickedFile(it.toURI().toString(), pendingName(captureId) ?: it.name, null, it.length())
                }
            }
        }

    override suspend fun delete(captureId: String) {
        withContext(io) {
            folder.discard(captureId)
            legacy(captureId)?.delete()
        }
    }

    /** At start, after recovery: capture files no record names any more are removed. */
    suspend fun sweep(): Int = withContext(io) { folder.sweepStale(setOfNotNull(pending()?.id)) }

    private fun pendingName(captureId: String): String? = pending()?.takeIf { it.id == captureId }?.name

    private fun legacy(captureId: String): File? {
        val path = pending()?.takeIf { it.id == captureId }?.path ?: return null
        val file =
            try {
                File(path).canonicalFile
            } catch (_: IOException) {
                return null
            }
        val inside =
            legacyRoots.any { root ->
                val base =
                    try {
                        root.canonicalFile
                    } catch (_: IOException) {
                        return@any false
                    }
                file.path.startsWith(base.path + File.separator)
            }
        return file.takeIf { inside && it.isFile && it.length() > 0 }
    }

    private companion object {
        const val JPEG = "image/jpeg"

        fun photoName(captureId: String) = "photo-$captureId.jpg"
    }
}

/**
 * The one camera request of the process. It belongs to the app, not to an activity: when the
 * activity is recreated while the camera is open (the phone was turned), the new activity's
 * launcher receives the photo and hands it to the request that is still waiting. A result nobody
 * waits for (the app was killed and restarted) is an [orphan] for the pending-photo recovery.
 */
internal class CameraRequests {
    private val requests = PickRequests<Boolean>()

    /** null while another camera request is open. */
    fun begin() = requests.begin()

    val open: Boolean
        get() = requests.isActive()

    /** The launcher's answer: true when a waiting request took it. */
    fun deliver(saved: Boolean): Boolean = requests.resolveCurrent(saved)

    fun retire(request: kotlinx.coroutines.CompletableDeferred<Boolean?>) = requests.retire(request)

    fun releaseUnlaunched(request: kotlinx.coroutines.CompletableDeferred<Boolean?>) =
        requests.releaseUnlaunched(request)

    companion object {
        fun orphan(saved: Boolean): LauncherOutcome =
            if (saved) LauncherOutcome.Captured else LauncherOutcome.Cancelled
    }
}

/**
 * Camera permission after the system dialog (E05-S01). Not declared in the manifest means the
 * camera app needs none from us. A refusal is "for good" when Android will no longer show the
 * dialog: it then offers no rationale, so the person has to allow it in app settings.
 */
internal object CameraPermissionRules {
    fun before(declared: Boolean, granted: Boolean): PermissionStatus? =
        when {
            !declared -> PermissionStatus.NotApplicable
            granted -> PermissionStatus.Granted
            else -> null // ask
        }

    fun after(granted: Boolean, showRationale: Boolean): PermissionStatus =
        when {
            granted -> PermissionStatus.Granted
            showRationale -> PermissionStatus.Denied
            else -> PermissionStatus.PermanentlyDenied
        }
}
