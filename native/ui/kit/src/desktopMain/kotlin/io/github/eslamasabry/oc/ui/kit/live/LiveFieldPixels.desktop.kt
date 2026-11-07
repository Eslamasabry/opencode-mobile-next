package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.ColorType

internal actual fun liveFieldScratch(w: Int, h: Int): Any? = ByteArray(w * h * Int.SIZE_BYTES)

/** Premultiplies [argb] into the bitmap's own 8888 layout and installs it (desktop and the gate render). */
internal actual fun liveWritePixels(image: ImageBitmap, argb: IntArray, scratch: Any?) {
    val bitmap = image.asSkiaBitmap()
    val bytes = scratch as ByteArray
    val bgra = bitmap.imageInfo.colorType == ColorType.BGRA_8888
    var i = 0
    for (c in argb) {
        // Unpremultiplied ARGB in, premultiplied 8888 out, in the bitmap's byte order.
        val a = c ushr 24; val r = ((c shr 16) and 0xFF) * a / 255; val g = ((c shr 8) and 0xFF) * a / 255; val b = (c and 0xFF) * a / 255 // NV: 8-bit ARGB channels, premultiplied
        bytes[i] = (if (bgra) b else r).toByte(); bytes[i + 1] = g.toByte(); bytes[i + 2] = (if (bgra) r else b).toByte(); bytes[i + 3] = a.toByte(); i += Int.SIZE_BYTES // NV: the 8888 byte order
    }
    bitmap.installPixels(bytes)
    bitmap.notifyPixelsChanged()
}

internal actual fun liveSettle(image: ImageBitmap) {
    image.asSkiaBitmap().setImmutable()
}
