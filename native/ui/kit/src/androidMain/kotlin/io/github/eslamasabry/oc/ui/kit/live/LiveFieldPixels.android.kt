package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

internal actual fun liveFieldScratch(w: Int, h: Int): Any? = null

/** `Bitmap.setPixels` takes unpremultiplied ARGB, as the field writes it; the next draw uploads the new pixels. */
internal actual fun liveWritePixels(image: ImageBitmap, argb: IntArray, scratch: Any?) {
    image.asAndroidBitmap().setPixels(argb, 0, image.width, 0, 0, image.width, image.height)
}

internal actual fun liveSettle(image: ImageBitmap) {
    image.prepareToDraw()
}
