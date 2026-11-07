package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import io.github.eslamasabry.oc.ui.kit.content.image.encodeMarkupPng

/**
 * The 800 x 600 picture the gallery and markup goldens draw (kit_image_gallery_golden_test.dart:18-33,
 * kit_image_markup_golden_test.dart:18-33): a blue field and a yellow disc of radius 140 at its centre, as PNG.
 */
internal object GalleryPictures {
    val png: ByteArray by lazy {
        val image = ImageBitmap(800, 600)
        val canvas = Canvas(image)
        canvas.drawRect(Rect(0f, 0f, 800f, 600f), Paint().apply { color = Color(0xFF2F5D8A) })
        canvas.drawCircle(Offset(400f, 300f), 140f, Paint().apply { color = Color(0xFFE8B84A) })
        encodeMarkupPng(image)
    }
}
