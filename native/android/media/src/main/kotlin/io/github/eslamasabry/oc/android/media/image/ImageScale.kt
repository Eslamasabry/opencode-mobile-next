package io.github.eslamasabry.oc.android.media.image

/**
 * Pure sizing and memory rules for prompt photos (plan 10 §10.3 step 2). No Android types, so plain JVM tests cover
 * them; [AndroidImageCodec] applies them to `ImageDecoder` (API 28+) and `BitmapFactory` (26–27).
 */
internal object ImageScale {
    /** Bytes a picture may occupy while it is read from its source (a camera JPEG is rarely over 20 MB). */
    const val MAX_SOURCE_BYTES: Int = 64 * 1024 * 1024

    /** Largest ARGB_8888 bitmap BitmapFactory (API 26–27) decodes at once; ImageDecoder (28+) decodes at the target size. */
    const val MAX_DECODE_BYTES: Long = 32L * 1024 * 1024

    /** A source whose header claims more pixels than this is refused before any decode (a decompression bomb). */
    const val MAX_SOURCE_PIXELS: Long = 200_000_000

    data class Size(val width: Int, val height: Int)

    /** The size that keeps the aspect and puts the long edge at most [maxEdge]; never upscales. */
    fun target(width: Int, height: Int, maxEdge: Int): Size {
        require(width > 0 && height > 0)
        val long = maxOf(width, height)
        if (long <= maxEdge) return Size(width, height)
        val scale = maxEdge.toDouble() / long
        return Size(
            (width * scale).toInt().coerceIn(1, maxEdge),
            (height * scale).toInt().coerceIn(1, maxEdge),
        )
    }

    /**
     * The largest power-of-two `inSampleSize` whose decode still covers [target] (so the exact scale after it only
     * shrinks), raised further if that decode would exceed [MAX_DECODE_BYTES].
     */
    fun sampleSize(width: Int, height: Int, target: Size): Int {
        var sample = 1
        while (width / (sample * 2) >= target.width && height / (sample * 2) >= target.height) sample *= 2
        while (decodeBytes(width / sample, height / sample) > MAX_DECODE_BYTES) sample *= 2
        return sample
    }

    fun decodeBytes(width: Int, height: Int): Long = width.toLong() * height.toLong() * 4

    /** Whether the header's dimensions are sane enough to decode at all. */
    fun acceptable(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS

    /** Encoding chosen for a decoded bitmap (`image_picker` rule): alpha → PNG, else JPEG at the rules' quality. */
    enum class Encoding { Png, Jpeg }

    fun encodingFor(hasAlpha: Boolean): Encoding = if (hasAlpha) Encoding.Png else Encoding.Jpeg

    /** GIF and WebP are kept byte for byte (animation survives) when small enough on both counts. */
    fun keepOriginal(mime: String?, bytes: Int, width: Int, height: Int, maxBytes: Int, maxEdge: Int): Boolean =
        (mime == "image/gif" || mime == "image/webp") && bytes <= maxBytes && maxOf(width, height) <= maxEdge
}
