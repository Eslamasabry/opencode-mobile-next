package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.rb34LiveStyleAuroraImage
import org.jetbrains.compose.resources.StringResource

/**
 * The modifier that paints [style] (null: nothing) as the page's moving ground, in its own clipped graphics layer
 * (Dart: the `RepaintBoundary` around `_StylePainter`). [seconds] is read only while drawing, so a tick of the clock
 * redraws this layer alone: nothing recomposes, and the page laid over it is neither re-laid out nor redrawn.
 * The look draws in dp, as Flutter's canvas does; its memory lives as long as the look stays the same.
 */
@Composable
internal fun rememberKitLiveLayer(
    style: KitLiveStyle?,
    strength: Double,
    running: Double = 0.0,
    seconds: () -> Double,
): Modifier {
    if (style == null) return Modifier
    val colors = LocalKitColors.current
    val palette = remember(colors) { KitLivePalette.of(colors) }
    val memory = remember(style) { KitLiveMemory() }
    val measurer = rememberTextMeasurer()
    val mono = LocalKitTypography.current.style(TypeRole.Mono).fontFamily ?: FontFamily.Monospace
    return Modifier.graphicsLayer { clip = true }.drawWithCache {
        val unit = density
        val logical = Size(size.width / unit, size.height / unit)
        onDrawBehind {
            val frame = KitLiveFrame(seconds(), running, strength, palette, memory, measurer, mono)
            scale(unit, unit, pivot = Offset.Zero) { style.paint(this, logical, frame) }
        }
    }
}

/**
 * The looks a person can choose, in the picker's order (GLASS_REF `kit_live_styles.dart`): the painted aurora second
 * after the aurora when that is drawn, else first; then every look the native app draws.
 */
public fun kitLiveLookIds(): List<String> {
    val ids = kitLiveStyles.map { it.id }
    val at = ids.indexOf(KIT_LIVE_DEFAULT_STYLE_ID) + 1
    return ids.take(at) + AuroraImage.ID + ids.drop(at)
}

/** The picker's name for the look stored as [id], or null for an id the native app does not draw. */
public fun kitLiveLookLabel(id: String): StringResource? =
    if (id == AuroraImage.ID) Res.string.rb34LiveStyleAuroraImage else kitLiveStyles.firstOrNull { it.id == id }?.label
