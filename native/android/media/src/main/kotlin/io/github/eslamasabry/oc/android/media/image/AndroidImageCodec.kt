package io.github.eslamasabry.oc.android.media.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.domain.platform.PickedFile
import io.github.eslamasabry.oc.engine.media.attach.ImageCodec
import io.github.eslamasabry.oc.engine.media.attach.MagicBytes
import io.github.eslamasabry.oc.engine.media.attach.PhotoRules
import io.github.eslamasabry.oc.engine.media.attach.PreparedImage
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prompt photos on Android (plan 10 §10.3 step 2). Reads the source with a byte cap, checks the header's pixel count,
 * decodes straight to the target size (ImageDecoder `setTargetSize` on API 28+, `inSampleSize` + exact scale on 26–27,
 * so a 50 MP photo never becomes a 200 MB bitmap), applies EXIF orientation, and re-encodes: alpha → PNG, else JPEG 85.
 * GIF and WebP within the limits are kept as they are. A PNG over the byte limit falls back to JPEG on white.
 *
 * Failures carry no path, name or exception text: only `Redacted.shape`.
 */
public class AndroidImageCodec(
    private val resolver: ContentResolver,
    private val dispatchers: AppDispatchers,
) : ImageCodec {
    override suspend fun preparePhoto(source: PickedFile, rules: PhotoRules): Outcome<PreparedImage> =
        withContext(dispatchers.io) {
            val bytes =
                try {
                    resolver.openInputStream(Uri.parse(source.uri))?.use { readCapped(it, ImageScale.MAX_SOURCE_BYTES) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    return@withContext unavailable(e)
                } catch (e: IOException) {
                    return@withContext unavailable(e)
                } catch (e: TooLarge) {
                    return@withContext Outcome.Failed(Failure.Media(MediaProblem.PhotoTooLarge))
                } ?: return@withContext unavailable(null)
            prepare(bytes, source.mime, rules)
        }

    override suspend fun prepareBytes(bytes: ByteArray, declaredMime: String?, rules: PhotoRules): Outcome<PreparedImage> =
        withContext(dispatchers.default) { prepare(bytes, declaredMime, rules) }

    internal fun prepare(bytes: ByteArray, declaredMime: String?, rules: PhotoRules): Outcome<PreparedImage> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sniffed = MagicBytes.imageMime(bytes)
        val width = bounds.outWidth
        val height = bounds.outHeight
        val haveBounds = width > 0 && height > 0
        if (haveBounds && !ImageScale.acceptable(width, height)) return unsupported()
        if (haveBounds && ImageScale.keepOriginal(sniffed, bytes.size, width, height, rules.maxBytes, rules.maxEdgePx)) {
            return Outcome.Ok(PreparedImage(bytes, sniffed!!, width, height))
        }
        val bitmap =
            try {
                decode(bytes, width, height, rules.maxEdgePx)
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                return Outcome.Failed(Failure.Media(MediaProblem.PhotoTooLarge))
            } catch (_: Exception) {
                null
            } ?: return unsupported()
        try {
            return encode(bitmap, rules)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(bytes: ByteArray, width: Int, height: Int, maxEdge: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val size = info.size
                if (!ImageScale.acceptable(size.width, size.height)) throw IOException("too many pixels")
                val target = ImageScale.target(size.width, size.height, maxEdge)
                if (target.width != size.width || target.height != size.height) decoder.setTargetSize(target.width, target.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        if (width <= 0 || height <= 0) return null // HEIC and friends before API 28
        val target = ImageScale.target(width, height, maxEdge)
        val options = BitmapFactory.Options().apply { inSampleSize = ImageScale.sampleSize(width, height, target) }
        val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val rotation = exifRotation(bytes)
        val scale = minOf(target.width.toFloat() / sampled.width, target.height.toFloat() / sampled.height, 1f)
        if (rotation == 0 && scale == 1f) return sampled
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val result = Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
        if (result !== sampled) sampled.recycle()
        return result
    }

    private fun exifRotation(bytes: ByteArray): Int =
        try {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: IOException) {
            0
        }

    private fun encode(bitmap: Bitmap, rules: PhotoRules): Outcome<PreparedImage> {
        if (ImageScale.encodingFor(bitmap.hasAlpha()) == ImageScale.Encoding.Png) {
            val png = compress(bitmap, Bitmap.CompressFormat.PNG, 100)
            if (png.size <= rules.maxBytes) return Outcome.Ok(PreparedImage(png, "image/png", bitmap.width, bitmap.height))
            // New: a PNG over the limit is drawn on white and sent as JPEG instead of failing.
            val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(flat).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
                return jpeg(flat, rules)
            } finally {
                flat.recycle()
            }
        }
        return jpeg(bitmap, rules)
    }

    private fun jpeg(bitmap: Bitmap, rules: PhotoRules): Outcome<PreparedImage> {
        val bytes = compress(bitmap, Bitmap.CompressFormat.JPEG, rules.jpegQuality)
        if (bytes.size > rules.maxBytes) return Outcome.Failed(Failure.Media(MediaProblem.PhotoTooLarge))
        return Outcome.Ok(PreparedImage(bytes, "image/jpeg", bitmap.width, bitmap.height))
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.toByteArray()
        }

    private fun unsupported(): Outcome<Nothing> = Outcome.Failed(Failure.Media(MediaProblem.PhotoUnsupported))

    private fun unavailable(e: Throwable?): Outcome<Nothing> =
        Outcome.Failed(Failure.Media(MediaProblem.PhotoUnavailable, e?.let { Redacted.shape(it) }))

    private class TooLarge : Exception()

    private fun readCapped(stream: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            total += n
            if (total > max) throw TooLarge()
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
