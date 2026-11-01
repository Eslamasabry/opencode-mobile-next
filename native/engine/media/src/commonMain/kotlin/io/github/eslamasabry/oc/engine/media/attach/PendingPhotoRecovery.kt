package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.PickedFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a launcher re-registered after process death reports (plan 10 §4.6). It does nothing else. */
public sealed interface LauncherOutcome {
    public data object Captured : LauncherOutcome

    public data object Cancelled : LauncherOutcome

    public data class Picked(val files: ImmutableList<PickedFile>) : LauncherOutcome
}

public enum class RecoveryResult { NothingPending, Attached, ProfileRemoved, Unavailable, RetryRequired }

/**
 * The single owner of a photo whose caller is gone (plan 10 §10.5; B-MEDIA-008, B-STATE-088). Bootstrap calls
 * [recover] with no outcome; a launcher re-registered after process death calls it with its outcome. One [Mutex];
 * idempotent: the draft join dedupes on the vault blob, so a launcher result racing the bootstrap call attaches the
 * photo once.
 *
 * This class owns the launcher rules (§10.5 steps 4–6): the bytes still with the platform are prepared and stored in
 * the record, a record that can never succeed is not kept, and a photo that cannot be opened is reported once
 * ([RecoveryResult.Unavailable]). The record (steps 1–3, 7, 8) and the draft join are the composer's ([PhotoRecord],
 * [DraftPhotoJoin]).
 */
public class PendingPhotoRecovery(
    private val photos: PhotoRecord,
    private val drafts: DraftPhotoJoin,
    private val captures: CaptureFiles,
    private val codec: ImageCodec,
) {
    private val lane = Mutex()

    /** [launchersSettled] = the host activity reached ON_START once in this process (pending results are delivered). */
    public suspend fun recover(outcome: LauncherOutcome? = null, launchersSettled: Boolean = false): RecoveryResult =
        lane.withLock { recoverLocked(outcome, launchersSettled) }

    private suspend fun recoverLocked(outcome: LauncherOutcome?, settled: Boolean): RecoveryResult {
        val photo = photos.pending ?: return RecoveryResult.NothingPending
        if (photo.failed) return discard(photo.id, RecoveryResult.Unavailable)
        if (outcome == LauncherOutcome.Cancelled) {
            captures.delete(photo.id)
            return discard(photo.id, RecoveryResult.NothingPending)
        }
        if (photo.hasBytes) return finish(photo.id)
        // The bytes are still with the platform: a capture file or a picked gallery item.
        val file =
            when (outcome) {
                is LauncherOutcome.Picked -> outcome.files.firstOrNull() // parity: files.first
                else -> captures.existing(photo.id)
            }
        if (file == null) {
            return when {
                // No result yet: the launcher may still deliver before the host's first ON_START.
                outcome == null && !settled -> RecoveryResult.RetryRequired
                // The camera said it captured, or nothing ever came back: tell the person once.
                outcome == LauncherOutcome.Captured || outcome == null -> unavailable(photo.id)
                // An empty pick is a cancel.
                else -> {
                    captures.delete(photo.id)
                    discard(photo.id, RecoveryResult.NothingPending)
                }
            }
        }
        val prepared =
            when (val p = codec.preparePhoto(file, PhotoRules.Prompt)) {
                is Outcome.Ok -> p.value
                is Outcome.Failed -> return unavailable(photo.id)
            }
        val name = AttachmentName.clean(file.displayName ?: photo.name ?: "photo.${MagicBytes.extensionFor(prepared.mime)}")
        when (val stored = photos.complete(photo.id, name, prepared.bytes)) {
            is Outcome.Ok -> if (!stored.value) return RecoveryResult.NothingPending
            is Outcome.Failed -> {
                val problem = (stored.failure as? Failure.Media)?.problem
                // Too large or a fake type can never succeed: report once and drop. Anything else keeps it for a retry.
                return if (problem == MediaProblem.PhotoTooLarge || problem == MediaProblem.PhotoUnsupported) {
                    unavailable(photo.id)
                } else {
                    RecoveryResult.RetryRequired
                }
            }
        }
        return finish(photo.id)
    }

    private suspend fun finish(id: String): RecoveryResult {
        val result = drafts.join()
        if (result == RecoveryResult.Attached || result == RecoveryResult.ProfileRemoved) captures.delete(id)
        return result
    }

    private suspend fun unavailable(id: String): RecoveryResult {
        captures.delete(id)
        if (photos.markFailed(id) !is Outcome.Ok) return RecoveryResult.RetryRequired
        return discard(id, RecoveryResult.Unavailable)
    }

    private suspend fun discard(id: String, result: RecoveryResult): RecoveryResult =
        if (photos.discard(id) is Outcome.Ok) result else RecoveryResult.RetryRequired
}
