package io.github.eslamasabry.oc.ui.kit.content.image

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

internal actual fun encodeMarkupPng(image: ImageBitmap): ByteArray {
    val out = ByteArrayOutputStream()
    check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)) { "png" } // NV: PNG ignores quality
    return out.toByteArray()
}
