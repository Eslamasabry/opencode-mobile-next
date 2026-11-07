package io.github.eslamasabry.oc.ui.kit.rb.o31

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import kotlin.math.abs
import kotlin.math.ceil

internal val PHONE = Size(412f, 915f)
internal val WIDE = Size(1280f, 800f)

/** Runs [block] where Compose's desktop graphics are registered (offscreen bitmaps, paths), as a UI test does. */
@OptIn(ExperimentalTestApi::class)
internal fun <T> withGraphics(block: () -> T): T {
    var out: Result<T>? = null
    runDesktopComposeUiTest(1, 1) { out = runCatching(block) }
    return out!!.getOrThrow()
}

/** Paints [style] over its ground at [size] and returns the ARGB pixels (the Dart test's `_render`). */
internal fun renderLook(style: KitLiveStyle, size: Size, frame: KitLiveFrame): IntArray = withGraphics { renderNow(style, size, frame) }

private fun renderNow(style: KitLiveStyle, size: Size, frame: KitLiveFrame): IntArray {
    val w = size.width.toInt()
    val h = size.height.toInt()
    val image = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size) {
        drawRect(frame.palette.ground)
        style.paint(this, size, frame)
    }
    val px = IntArray(w * h)
    image.readPixels(px)
    return px
}

/** The largest channel difference from the ground (the Dart test's `_maxDelta`). */
internal fun maxDelta(px: IntArray, frame: KitLiveFrame): Int {
    val g = frame.palette.ground
    val gr = (g.red * 255).toInt()
    val gg = (g.green * 255).toInt()
    val gb = (g.blue * 255).toInt()
    var worst = 0
    for (c in px) {
        worst = maxOf(worst, abs(((c shr 16) and 0xFF) - gr), abs(((c shr 8) and 0xFF) - gg), abs((c and 0xFF) - gb))
    }
    return worst
}

/** The ceiling in 8-bit steps a look may reach (the Dart test's `cap`). */
internal fun capOf(style: KitLiveStyle, frame: KitLiveFrame): Int = ceil(frame.palette.ceiling * style.ceilingFactor * 255).toInt()
