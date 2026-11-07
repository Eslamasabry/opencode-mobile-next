package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import androidx.core.content.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.media.image.AndroidImageCodec
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.photo.DraftPhotoRecovery
import io.github.eslamasabry.oc.engine.composer.photo.PhotoRecoveryStore
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.FilePayloadStore
import io.github.eslamasabry.oc.engine.media.attach.LauncherOutcome
import io.github.eslamasabry.oc.engine.media.attach.PendingPhotoRecovery
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import io.github.eslamasabry.oc.android.camera.capture.CaptureFiles as CaptureFolder

/**
 * E05-S01: the composer camera's process-wide parts. The pending-photo record is the composer's
 * `oc.pendingPromptPhoto` (Flutter format) with its photo vault `files/prompt-photo-recovery-v1`
 * (Flutter's application-support folder is `files/` on Android); captures go to
 * `files/camera-capture-v1`, shared through the app's FileProvider (`<applicationId>.files`,
 * root `camera`). A photo the camera hands back after Android killed the app joins the draft of
 * the conversation that asked for it, on the first start of the next process.
 */
@SingleIn(AppScope::class)
@Inject
class ComposerCameraWiring(
    private val application: Application,
    private val stores: PreviewStores,
    private val drafts: Drafts,
    private val redactor: Redactor,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
) {
    private val store by lazy {
        PhotoRecoveryStore(
            PreviewComposerPrefs(stores),
            AttachmentVault(vaultFiles(AttachmentVault.PHOTO_ROOT)),
            redactor,
            clock,
        )
    }

    internal val record: ComposerPhotoRecord by lazy { ComposerPhotoRecord(store, ::ready) }

    internal val captures: ComposerCaptureFiles by lazy {
        ComposerCaptureFiles(
            folder = CaptureFolder(application.filesDir),
            contentUri = ::shareable,
            pending = { record.pending },
            // Flutter's image_picker kept its camera file in the cache folder.
            legacyRoots = listOf(application.cacheDir, application.filesDir),
            io = dispatchers.io,
        )
    }

    /** The one camera request of the process (it outlives a recreated activity). */
    internal val requests = CameraRequests()

    private val recovery by lazy {
        PendingPhotoRecovery(
            photos = record,
            drafts =
                ComposerDraftJoin(
                    DraftPhotoRecovery(
                        store,
                        drafts,
                        // The drafts' own root; the join runs inside the drafts lane.
                        AttachmentVault(vaultFiles(AttachmentVault.DRAFT_ROOT)),
                        PreviewComposerPrefs(stores),
                        PreviewComposerProfiles(stores),
                        redactor,
                        clock,
                    )
                ),
            captures = captures,
            codec = AndroidImageCodec(application.contentResolver, dispatchers),
        )
    }

    private val settledOnce = AtomicBoolean(false)
    private val lane = Mutex()

    /** The context for Open settings when no activity is live. */
    internal fun application(): Application = application

    /** A camera answer no request waits for: the app was killed while the camera was open. */
    fun orphanResult(saved: Boolean) {
        recoverLater(CameraRequests.orphan(saved), settled = false)
    }

    /**
     * The first ON_START of this process: every launcher result has been delivered by now, so a
     * record whose photo never arrived is reported once and dropped. Skipped while a camera
     * request of this process is still open.
     */
    fun hostStarted() {
        if (!settledOnce.compareAndSet(false, true)) return
        recoverLater(null, settled = true)
    }

    /**
     * Runs in the order the activity reported: the launcher's answer (delivered first on ON_START)
     * before the start-up pass, so a cancel is never mistaken for a lost photo. UNDISPATCHED takes
     * the (fair) lane on the calling thread before the first suspension.
     */
    private fun recoverLater(outcome: LauncherOutcome?, settled: Boolean) {
        appScope.launch(dispatchers.io, start = CoroutineStart.UNDISPATCHED) {
            lane.withLock {
                if (requests.open || !ready()) return@withLock
                recovery.recover(outcome, launchersSettled = settled)
                if (settled) captures.sweep()
            }
        }
    }

    private suspend fun ready(): Boolean = stores.warm() is Outcome.Ok

    private fun vaultFiles(root: String) =
        FilePayloadStore(Path(application.filesDir.absolutePath, root), dispatchers.io)

    /** null when the file is outside the provider's roots: the camera then never opens. */
    private fun shareable(file: File): String? =
        try {
            FileProvider.getUriForFile(application, "${application.packageName}$AUTHORITY_SUFFIX", file)
                .toString()
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        /** The app's one FileProvider (manifest, `res/xml/oc_files_paths.xml`). */
        const val AUTHORITY_SUFFIX = ".files"
    }
}
