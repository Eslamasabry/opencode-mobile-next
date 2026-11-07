package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import org.jetbrains.compose.resources.StringResource

/**
 * One look of the live background (Dart `KitLiveStyle`, `lib/ui/kit/live_background/kit_live_style.dart`). A look only
 * paints one frame over the flat ground; the clock, the still frame, the cap and the layer are [KitLiveBackground]'s
 * and the same for every look. A look keeps its moving state (particles, trails, cached geometry) in
 * [KitLiveFrame.memory], never in fields, so one instance serves every page and every picker tile at once.
 *
 * Interim copy of order 31's contract (orders 31-33 port the looks in parallel): the shape follows the Dart API so
 * order 31's file replaces this one without touching a look.
 */
public abstract class KitLiveStyle {
    /** Stable storage key (`oc.effectsLiveStyle`); never renamed, byte for byte the Dart id. */
    public abstract val id: String

    /** The name shown in the picker. */
    public abstract val label: StringResource

    /**
     * How far past [KitLivePalette.ceiling] this look may go (sparse looks up to 2). Dart `ceilingFactor`.
     */
    public open val ceilingFactor: Double
        get() = 1.0

    /** How often a second this look is drawn while it moves, at most [KitLiveClock.FRAMES_PER_SECOND]. */
    public open val framesPerSecond: Int
        get() = KitLiveClock.FRAMES_PER_SECOND

    /** Whether the quieter text over this look keeps a halo of the ground (Dart `wantsTextHalo`). */
    public open fun wantsTextHalo(palette: KitLivePalette): Boolean = false

    /**
     * Paints one frame over the ground, which is already drawn. [size] is in logical pixels (dp) and [scope] is scaled
     * so one unit is one dp, exactly as Flutter's canvas: every number of the Dart look keeps its meaning.
     */
    public abstract fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame)
}

/** What a look is given for one frame (Dart `KitLiveFrame`, field for field, plus the text tools a look needs). */
@Suppress("LongParameterList") // mirrors the Dart class
public class KitLiveFrame(
    /** Seconds on the shared clock; never resets, so a page change carries on where the last one was. */
    public val t: Double,
    /** 0 idle .. 1 a reply is running, eased. */
    public val running: Double,
    /** 1 = the strength that ships. Previews may differ. */
    public val strength: Double,
    public val palette: KitLivePalette,
    public val memory: KitLiveMemory,
    /** Lays out the glyphs a look writes (matrix rain); null where no text is drawn. */
    public val text: TextMeasurer? = null,
    /** The app's mono face (Dart `AppMono`, Geist Mono). */
    public val mono: FontFamily = FontFamily.Monospace,
)

/** The theme's colours a look may use (Dart `KitLivePalette`, field for field). */
@Suppress("LongParameterList") // mirrors the Dart class
public class KitLivePalette(
    public val ground: Color,
    /** Primary text colour. */
    public val ink: Color,
    /** text2 and text3. */
    public val muted: Color,
    public val faint: Color,
    /** The accent. */
    public val green: Color,
    public val teal: Color,
    public val blue: Color,
    /** The running glow's colour (the composer's). */
    public val glow: Color,
    public val dark: Boolean,
) {
    /** The most any single layer may be opaque at strength 1. */
    public val ceiling: Double
        get() = if (dark) .06 else .045 // F: lib/ui/kit/live_background/kit_live_style.dart:117

    override fun equals(other: Any?): Boolean =
        other is KitLivePalette && ground == other.ground && ink == other.ink && muted == other.muted &&
            faint == other.faint && green == other.green && teal == other.teal && blue == other.blue &&
            glow == other.glow && dark == other.dark

    override fun hashCode(): Int = listOf(ground, ink, muted, faint, green, teal, blue, glow, dark).hashCode()

    public companion object {
        /** Dart `KitLiveBackground.paletteOfRoles` (`lib/ui/kit/kit_live_background.dart:94-107`). */
        public fun of(colors: KitColors): KitLivePalette =
            KitLivePalette(
                ground = colors.ground,
                ink = colors.text1,
                muted = colors.text2,
                faint = colors.text3,
                green = colors.accent,
                teal = mixColor(colors.accent, colors.codeKeyword, .35), // F: lib/ui/kit/kit_live_background.dart:102
                blue = colors.codeKeyword,
                glow = colors.accent,
                dark = colors.dark,
            )
    }
}

/** Per-look mutable state for one painted surface (Dart `KitLiveMemory`). */
public class KitLiveMemory {
    private val slots = HashMap<String, Any?>()

    /** The value kept under [key], made by [make] the first time. */
    @Suppress("UNCHECKED_CAST")
    public fun <T> of(key: String, make: () -> T): T = slots.getOrPut(key) { make() } as T

    public fun clear(): Unit = slots.clear()
}
