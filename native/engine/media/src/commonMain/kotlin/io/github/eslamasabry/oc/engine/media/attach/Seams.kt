package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.PickedFile

// Platform seams of the attachment pipeline (plan 10 §4.5). Android implementations live in :android:media
// (`image/AndroidImageCodec`, `clipboard/AndroidClipboardImages`); desktop binds the Unavailable* objects below.

/** Re-encode rules for prompt photos (plan 10 §4.6). */
public data class PhotoRules(val maxEdgePx: Int, val jpegQuality: Int, val maxBytes: Int) {
    public companion object {
        public val Prompt: PhotoRules = PhotoRules(maxEdgePx = 2048, jpegQuality = 85, maxBytes = 10 * 1024 * 1024)
    }
}

public class PreparedImage(public val bytes: ByteArray, public val mime: String, public val width: Int, public val height: Int)

/** Decode with a memory cap, scale so the long edge ≤ [PhotoRules.maxEdgePx], re-encode (plan 10 §10.3 step 2). */
public interface ImageCodec {
    public suspend fun preparePhoto(source: PickedFile, rules: PhotoRules): Outcome<PreparedImage>

    public suspend fun prepareBytes(bytes: ByteArray, declaredMime: String?, rules: PhotoRules): Outcome<PreparedImage>
}

public class ClipboardImage(public val mime: String, public val bytes: ByteArray)

/** Plan 10 §10.8: [hasImage] reads only the clip description (no Android 12+ toast); [read] reads the picture. */
public interface ClipboardImages {
    public suspend fun hasImage(): Boolean

    /** null = no picture; `Failure.Media(ClipboardImageTooLarge)` when it is over [maxBytes]. */
    public suspend fun read(maxBytes: Int): Outcome<ClipboardImage?>
}

/**
 * Bounded reads of picked files. Plan 10 §4.3 names this `ContentAccess.readBounded` in `:core:domain`; it is not
 * there yet, so the pipeline depends on this one-method seam (core request in the S3-MEDIAATT report).
 */
public fun interface PickedFileReader {
    /** At most [limit] bytes; `Failure.Media(AttachmentTooLarge)` when more exist (one byte past is read to know). */
    public suspend fun readBounded(file: PickedFile, limit: Long): Outcome<ByteArray>
}

/** Words the pipeline writes into attachment text (the Office header). Supplied by `:ui:words`; never built here. */
public interface AttachmentWords {
    public fun officeHeader(name: String): String

    public fun officeTruncated(): String
}

public object UnavailableClipboardImages : ClipboardImages {
    override suspend fun hasImage(): Boolean = false

    override suspend fun read(maxBytes: Int): Outcome<ClipboardImage?> = Outcome.Ok(null)
}
