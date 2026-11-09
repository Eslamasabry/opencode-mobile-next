package io.github.eslamasabry.oc.ui.kit.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

internal actual fun decodeBounds(bytes: ByteArray): Pair<Int, Int>? {
    val image = Image.makeFromEncoded(bytes)
    return try {
        image.width to image.height
    } finally {
        image.close()
    }
}

internal actual fun decodeSampled(bytes: ByteArray, maxEdge: Int): ImageBitmap? {
    val image = Image.makeFromEncoded(bytes)
    try {
        val edge = maxOf(image.width, image.height)
        if (edge <= maxEdge) return Bitmap.makeFromImage(image).asComposeImageBitmap()
        val scale = maxEdge.toFloat() / edge
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        val target = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(w, h)) }
        Canvas(target).drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            SamplingMode.LINEAR,
            null,
            true,
        )
        return target.asComposeImageBitmap()
    } finally {
        image.close()
    }
}

internal actual fun encodeThumb(bitmap: ImageBitmap): ByteArray? {
    return Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
}

internal actual fun imageMemoryBudgetBytes(): Long =
    (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L shl 20, 64L shl 20)
