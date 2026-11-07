package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/** The kit gallery's view pixel ratio (test/goldens/kit/kit_gallery.dart:177-178). */
internal const val IMAGE_VIEW_RATIO: Int = 3

/** kitGalleryShot: view DPR 3, captured at 1x (a copy of rb/o06/FlutterCapture for this slice). */
@Composable
internal fun ImageCapture(content: @Composable () -> Unit) {
    val output = LocalDensity.current
    val ratio = IMAGE_VIEW_RATIO
    val view = Density(output.density * ratio, output.fontScale)
    Layout(content = { CompositionLocalProvider(LocalDensity provides view, LocalKitAppDensity provides view, content = content) }) { measurables, constraints ->
        fun up(v: Int): Int = if (v == Constraints.Infinity) v else v * ratio
        val input = Constraints(up(constraints.minWidth), up(constraints.maxWidth), up(constraints.minHeight), up(constraints.maxHeight))
        val child = measurables.single().measure(input)
        layout(child.width / ratio, child.height / ratio) {
            child.placeWithLayer(0, 0) {
                scaleX = 1f / ratio
                scaleY = 1f / ratio
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * The Dart tests' `_picture()` (kit_image_gallery_golden_test.dart:18-33, kit_image_markup_golden_test.dart:18-33): an
 * 800x600 PNG, filled 0xFF2F5D8A, with a 140 px 0xFFE8B84A disc at its centre, both anti-aliased as Flutter paints.
 */
internal val testPicture: ByteArray by lazy {
    val surface = Surface.makeRasterN32Premul(800, 600)
    val canvas = surface.canvas
    canvas.drawRect(Rect.makeXYWH(0f, 0f, 800f, 600f), Paint().apply { color = 0xFF2F5D8A.toInt(); isAntiAlias = true })
    canvas.drawCircle(400f, 300f, 140f, Paint().apply { color = 0xFFE8B84A.toInt(); isAntiAlias = true })
    surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}

/** The desktop host's PNG encoder (Skia). */
internal suspend fun encodeTestPng(bitmap: ImageBitmap): ByteArray =
    Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
