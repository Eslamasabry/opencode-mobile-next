package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import org.jetbrains.compose.resources.StringResource

/**
 * One look of the live background (Settings › Appearance › Background; Dart `KitLiveStyle`,
 * `lib/ui/kit/live_background/kit_live_style.dart`).
 *
 * A style only paints one frame over the flat ground, which is already drawn; the machinery around it (the page ground,
 * the shared [KitLiveClock] of 12 ticks a second that rests when nobody touches the app, the still frame under reduced
 * motion, the brighten while a reply runs) is [KitLiveBackground]'s and is the same for every style. Rules, as in Dart:
 *
 * - no blur of what is under it, nothing see-through a card relies on; cards, rows and sheets stay solid on top;
 * - at [KitLiveFrame.strength] 1 the look stays within [KitLivePalette.ceiling] (or measures its brightest spot
 *   against the theme's own text colours, as the aurora does);
 * - never red; amber is not used;
 * - a style that keeps state (particles, a trail, cached geometry) keeps it in [KitLiveFrame.memory], never in fields,
 *   so one instance serves many tiles at once;
 * - a frame allocates nothing: geometry, paths, brushes and bitmaps are built once in [KitLiveFrame.memory] and only
 *   moved, recoloured or redrawn.
 */
public abstract class KitLiveStyle {
    /** Stable storage key (`oc.effectsLiveStyle`), byte for byte the Dart id; never renamed. */
    public abstract val id: String

    /** The name shown in the picker. */
    public abstract val label: StringResource

    /**
     * How far past [KitLivePalette.ceiling] this look may go. Sparse looks (thin lines, glyphs, a few dots) override
     * it, up to 2.0, so they can be seen.
     */
    public open val ceilingFactor: Double get() = 1.0 // F: lib/ui/kit/live_background/kit_live_style.dart:40

    /** Whether the quieter text over this look keeps a halo of the ground in [palette]. */
    public open fun wantsTextHalo(palette: KitLivePalette): Boolean = false

    /** How often a second this look is drawn while it moves, at most the shared clock's 12. */
    public open val framesPerSecond: Int get() = 12 // F: lib/ui/kit/live_background/kit_live_style.dart:51

    /** Paints one frame of [size] into [canvas] over the ground, which is already drawn. */
    public abstract fun paint(canvas: DrawScope, size: Size, frame: KitLiveFrame)
}

/** What a style is given for one frame (Dart `KitLiveFrame`). */
public class KitLiveFrame(
    /** Seconds on the shared clock. Styles loop on their own period; the clock never resets. */
    public val t: Double,
    /** 0 idle .. 1 a reply is running, eased. */
    public val running: Double,
    /** 1 = the strength that ships. Previews may differ; styles scale their alpha by it. */
    public val strength: Double,
    public val palette: KitLivePalette,
    public val memory: KitLiveMemory,
    /** How a look that writes characters (blueprint's measures, matrix rain) lays them out; null draws none. */
    public val text: KitLiveText? = null,
)

/** The text a look may draw: Compose's measurer and the app's mono family (Dart `AppMono`). */
public class KitLiveText(public val measurer: TextMeasurer, public val mono: FontFamily)

/** The theme's colours a style may use (Dart `KitLivePalette`). */
@Immutable
public data class KitLivePalette(
    val ground: Color,
    /** Primary text colour. */
    val ink: Color,
    /** The secondary and the faintest text colours (text2, text3): a bright look must keep them readable too. */
    val muted: Color,
    val faint: Color,
    /** The theme's accent: the composer glow's hue. */
    val green: Color,
    val teal: Color,
    val blue: Color,
    /** The running glow's colour (the composer's). */
    val glow: Color,
    val dark: Boolean,
) {
    /** The most any single layer may be opaque at strength 1. */
    val ceiling: Double get() = if (dark) 0.06 else 0.045 // F: lib/ui/kit/live_background/kit_live_style.dart:117

    public companion object {
        /** The colours a look may use under [c] (Dart `KitLiveBackground.paletteOfRoles`, kit_live_background.dart:94-107). */
        public fun of(c: KitColors): KitLivePalette =
            KitLivePalette(
                ground = c.ground,
                ink = c.text1,
                muted = c.text2,
                faint = c.text3,
                green = c.accent,
                teal = liveMix(c.accent, c.codeKeyword, .35), // F: lib/ui/kit/kit_live_background.dart:102
                blue = c.codeKeyword,
                glow = c.accent,
                dark = c.dark,
            )
    }
}

/** Per-style mutable state for one painted surface (Dart `KitLiveMemory`). */
public class KitLiveMemory {
    private val slots = HashMap<String, Any?>()

    /** The value kept under [key], made by [make] the first time. */
    @Suppress("UNCHECKED_CAST")
    public fun <T> of(key: String, make: () -> T): T = slots.getOrPut(key) { make() } as T

    public fun clear() {
        slots.clear()
    }
}
