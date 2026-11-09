package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.model.PromptAttachment
import kotlinx.collections.immutable.ImmutableList

/** One shared item, after the platform copied it into private storage (or could not). */
public sealed interface SharedInput {
    /** [bytes] are bounded by the platform to [AttachmentLimits.MAX_BYTES]; [name] and [mime] are only hints. */
    public class Ready(public val name: String?, public val mime: String?, public val bytes: ByteArray) : SharedInput {
        override fun toString(): String = "Ready(bytes=${bytes.size})" // never the name
    }

    /** The platform already refused this item (too large, unreadable, a kind it never reads). */
    public data class Refused(val problem: ShareInProblem) : SharedInput
}

/** Why items were left out. The first problem of a run wins, except [OverLimit], which always wins. */
public enum class ShareInProblem {
    OverLimit,
    TooLarge,
    Unsupported,
    Unreadable,
    ;

    public companion object {
        public fun of(failure: Failure): ShareInProblem =
            when ((failure as? Failure.Media)?.problem) {
                MediaProblem.DraftFull -> OverLimit
                MediaProblem.AttachmentTooLarge,
                MediaProblem.AttachmentsTotalTooLarge,
                MediaProblem.PhotoTooLarge,
                MediaProblem.ClipboardImageTooLarge,
                -> TooLarge
                MediaProblem.AttachmentUnsupported,
                MediaProblem.PhotoUnsupported,
                MediaProblem.OfficeUnreadable,
                MediaProblem.OfficeEmpty,
                -> Unsupported
                else -> Unreadable
            }
    }
}

/** What a share added. [total] is how many items arrived (after the platform's own count cap). */
public data class ShareInResult(
    val added: ImmutableList<PromptAttachment>,
    val total: Int,
    val problem: ShareInProblem?,
) {
    /** Everything that arrived is now in the draft. */
    public val complete: Boolean get() = problem == null && added.size == total

    /**
     * The draft's attachments once these are added after [have] (what the composer already holds is kept, in order),
     * or null when there is nothing to write: nothing was added, or a retry finds them already saved. Never sends.
     */
    public fun draftWith(have: List<PromptAttachment>): List<PromptAttachment>? {
        if (added.isEmpty() || added.all { one -> have.any { it.url == one.url } }) return null
        return have + added.filter { one -> have.none { it.url == one.url } }
    }
}

/** The two coarse facts a share-in event may carry (`share_in`): a count range and image/file. Nothing else. */
public object ShareInFacts {
    /** Same words as the health count ranges: `0`, `1`, `2-5`, `6-20`, `>20`. */
    public fun countRange(n: Int): String =
        when {
            n <= 0 -> "0"
            n == 1 -> "1"
            n <= 5 -> "2-5"
            n <= 20 -> "6-20"
            else -> ">20"
        }

    /** True when every readable item is a picture (by its type or its bytes); a mix or no items counts as files. */
    public fun picturesOnly(inputs: List<SharedInput>): Boolean {
        val ready = inputs.filterIsInstance<SharedInput.Ready>()
        return ready.isNotEmpty() && ready.all { it.mime?.startsWith("image/") == true || MagicBytes.imageMime(it.bytes) != null }
    }
}
