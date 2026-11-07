package io.github.eslamasabry.oc.ui.kit.rb.o30

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnFrame
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnPalette
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnScene
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Paths and bitmaps need Compose's graphics backend, which desktop registers when a composition first starts; a
 * parity test that draws without a UI test starts one empty composition first.
 */
@OptIn(ExperimentalTestApi::class)
private val graphicsReady: Unit by lazy { runComposeUiTest { setContent {} } }

internal fun ensureGraphics(): Unit = graphicsReady

/** A theme whose every role is a different, opaque colour, so a test can tell which role painted a pixel. */
internal val testColors: KitColors =
    KitColors(
        dark = true,
        ground = Color(0xFF000000),
        surface1 = Color(0xFF101010),
        surface2 = Color(0xFF202020),
        surface3 = Color(0xFF303030),
        hairline = Color(0xFF404040),
        text1 = Color(0xFFF0F0F0),
        text2 = Color(0xFFA0A0A0),
        text3 = Color(0xFF707070),
        accent = Color(0xFF00C060),
        onAccent = Color(0xFF001000),
        partner = Color(0xFF0060C0),
        onPartner = Color(0xFF000010),
        attention = Color(0xFFE0A000),
        attentionFill = Color(0xFFE0A010),
        onAttentionFill = Color(0xFF100A00),
        attentionSurface = Color(0xFF201400),
        attentionLine = Color(0xFF603C00),
        success = Color(0xFF40D080),
        scrim = Color(0x80000000),
        codeKeyword = Color(0xFFC080FF),
        codeString = Color(0xFF80FFC0),
        codeType = Color(0xFFFFC080),
        elevationShadow = Color(0x40000000),
    )

internal val testPalette: KitDrawnPalette = KitDrawnPalette.of(testColors)

internal fun frameAt(entrance: Float, loop: Float = 0f, looping: Boolean = false): KitDrawnFrame =
    KitDrawnFrame(entrance, loop, looping, testPalette)

/** Paints [scene] at [frame] into a bitmap of its own box at [scale] px per scene unit, on transparent. */
internal fun paint(scene: KitDrawnScene, frame: KitDrawnFrame, scale: Int = 2): ImageBitmap {
    ensureGraphics()
    val w = (scene.box.width * scale).toInt()
    val h = (scene.box.height * scale).toInt()
    val image = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
        drawContext.transform.scale(scale.toFloat(), scale.toFloat(), androidx.compose.ui.geometry.Offset.Zero)
        with(scene) { paint(frame) }
    }
    return image
}

/** How many pixels of [image] are painted at all. */
internal fun inked(image: ImageBitmap): Int {
    val px = image.toPixelMap()
    var n = 0
    for (y in 0 until px.height) for (x in 0 until px.width) if (px[x, y].alpha > 0f) n++
    return n
}

/** How many pixels of [image] are (nearly) [color], opaque. */
internal fun pixelsOf(image: ImageBitmap, color: Color): Int {
    val px = image.toPixelMap()
    var n = 0
    for (y in 0 until px.height) for (x in 0 until px.width) {
        val c = px[x, y]
        if (c.alpha > .95f && sameRgb(c, color)) n++
    }
    return n
}

private fun sameRgb(a: Color, b: Color): Boolean =
    maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) < .02f

internal fun assertNear(expected: Float, actual: Float, eps: Float = 1e-3f, what: String = "") =
    assertTrue(abs(expected - actual) <= eps, "$what expected $expected, got $actual")

/** Whether the pixel at the middle of [image] is painted opaque. */
internal fun centre(image: ImageBitmap): Boolean = image.toPixelMap()[image.width / 2, image.height / 2].alpha > .9f
