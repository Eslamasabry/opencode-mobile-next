package io.github.eslamasabry.oc.ui.kit.rb.o33

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.KitLivePalette
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles

/** Compose's Skia graphics, registered once for tests that paint without a Compose window. */
private val skiko: Unit by lazy {
    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    androidx.compose.ui.platform.registerSkikoComposeImplementation()
}

internal fun graphicsReady() = skiko

/** The clock of test/kit_live_styles_industrial_test.dart (`_t`). */
internal const val GOLDEN_T: Double = 7.3

/**
 * The golden scene of test/kit_live_styles_industrial_test.dart (`_Painter`): the ground, then [style] at t 7.3, idle,
 * strength 1, in the theme's own colours, filling the 412 x 915 view at density 1.
 */
@Composable
internal fun LiveGoldenScene(style: KitLiveStyle) {
    val palette = KitLivePalette.of(LocalKitRoles.current)
    val memory = KitLiveMemory()
    Spacer(
        Modifier.fillMaxSize().drawBehind {
            drawRect(palette.ground)
            drawIntoCanvas { style.paint(it, size, KitLiveFrame(GOLDEN_T, 0.0, 1.0, palette, memory)) }
        }
    )
}

internal val darkPalette: KitLivePalette get() = KitLivePalette.of(Graphite.dark)
internal val lightPalette: KitLivePalette get() = KitLivePalette.of(Graphite.light)

/** Dart `_render`: paints [style] over its ground into a Skia bitmap and returns its ARGB pixels. */
internal fun render(
    style: KitLiveStyle,
    size: Size,
    p: KitLivePalette,
    running: Double = 0.0,
    strength: Double = 1.0,
    t: Double = GOLDEN_T,
    memory: KitLiveMemory = KitLiveMemory(),
): IntArray {
    graphicsReady()
    val w = size.width.toInt()
    val h = size.height.toInt()
    val bitmap = org.jetbrains.skia.Bitmap().apply { allocN32Pixels(w, h) }
    val canvas = org.jetbrains.skia.Canvas(bitmap).asComposeCanvas()
    canvas.drawRect(0f, 0f, size.width, size.height, Paint().apply { color = p.ground })
    style.paint(canvas, size, KitLiveFrame(t, running, strength, p, memory))
    return IntArray(w * h) { i -> bitmap.getColor(i % w, i / w) }
}

/** A Compose canvas over a fresh Skia bitmap of [size] (no Compose window needed). */
internal fun skiaCanvas(size: Size): Canvas {
    graphicsReady()
    val bitmap = org.jetbrains.skia.Bitmap().apply { allocN32Pixels(size.width.toInt(), size.height.toInt()) }
    return org.jetbrains.skia.Canvas(bitmap).asComposeCanvas()
}

/** Dart `_maxDelta`: the largest channel difference from the ground. */
internal fun maxDelta(px: IntArray, p: KitLivePalette): Int {
    val gr = (p.ground.red * 255 + .5f).toInt()
    val gg = (p.ground.green * 255 + .5f).toInt()
    val gb = (p.ground.blue * 255 + .5f).toInt()
    var worst = 0
    for (v in px) {
        worst = maxOf(worst, kotlin.math.abs(((v shr 16) and 0xFF) - gr), kotlin.math.abs(((v shr 8) and 0xFF) - gg), kotlin.math.abs((v and 0xFF) - gb))
    }
    return worst
}

internal val PHONE = Size(412f, 915f)
internal val WIDE = Size(1280f, 800f)
