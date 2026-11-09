package io.github.eslamasabry.oc.ui.kit.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

internal actual fun decodeBounds(bytes: ByteArray): Pair<Int, Int>? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
}

internal actual fun decodeSampled(bytes: ByteArray, maxEdge: Int): ImageBitmap? {
    val (w, h) = decodeBounds(bytes) ?: return null
    val longest = maxOf(w, h)
    var sample = 1
    while (longest / (sample * 2) >= maxEdge) sample *= 2
    val o = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o) ?: return null
    val edge = maxOf(decoded.width, decoded.height)
    if (edge <= maxEdge) return decoded.asImageBitmap()
    val scale = maxEdge.toFloat() / edge
    val scaled =
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    if (scaled !== decoded) decoded.recycle()
    return scaled.asImageBitmap()
}

internal actual fun encodeThumb(bitmap: ImageBitmap): ByteArray? {
    val out = ByteArrayOutputStream()
    @Suppress("DEPRECATION")
    val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    return if (bitmap.asAndroidBitmap().compress(format, 85, out)) out.toByteArray() else null
}

internal actual fun imageMemoryBudgetBytes(): Long =
    (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L shl 20, 64L shl 20)
