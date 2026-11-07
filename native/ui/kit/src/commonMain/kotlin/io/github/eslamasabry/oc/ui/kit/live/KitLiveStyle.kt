package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.design.color.Roles

/**
 * One look of the live background (Dart `KitLiveStyle`, `lib/ui/kit/live_background/kit_live_style.dart`). A look only
 * paints one frame over the flat ground; the clock, the brighten while a reply runs and the page ground belong to
 * [KitLiveBackground] and are the same for every look. A look that keeps state (particles, a trail, a cached path)
 * keeps it in [KitLiveFrame.memory], never in fields, so one instance can serve many tiles at once.
 *
 * Shared with orders 31 and 32: this file follows the Dart API shape so each order adds its looks to [kitLiveStyles]
 * with one line.
 */
public abstract class KitLiveStyle {
    /** Stable storage key (`oc.effectsLiveStyle`); never renamed. Byte for byte the Dart `KitLiveStyle.id`. */
    public abstract val id: String

    /** How far past [KitLivePalette.ceiling] this look may go (kit_live_style.dart:41). */
    public open val ceilingFactor: Double get() = 1.0

    /** Whether the quieter text over this look keeps a halo of the ground (kit_live_style.dart:47). */
    public open fun wantsTextHalo(palette: KitLivePalette): Boolean = false

    /** How often a second this look is drawn while it moves, at most [KitLiveClock.FRAMES_PER_SECOND]. */
    public open val framesPerSecond: Int get() = KitLiveClock.FRAMES_PER_SECOND

    /** Paints one frame over the ground, which is already drawn. */
    public abstract fun paint(canvas: Canvas, size: Size, frame: KitLiveFrame)
}

/**
 * What a look is given for one frame (Dart `KitLiveFrame`). [t] and [running] are `var` so a background reuses one
 * frame object for every tick: nothing is allocated per frame.
 */
public class KitLiveFrame(
    /** Seconds on the shared clock; the clock never resets. */
    public var t: Double,
    /** 0 idle .. 1 a reply is running, eased. */
    public var running: Double,
    /** 1 = the strength that ships. */
    public var strength: Double,
    public var palette: KitLivePalette,
    public val memory: KitLiveMemory,
)

/** The theme's colours a look may use (Dart `KitLivePalette`). */
public class KitLivePalette(
    public val ground: Color,
    /** Primary text colour. */
    public val ink: Color,
    /** text2. */
    public val muted: Color,
    /** text3. */
    public val faint: Color,
    /** The theme's accent. */
    public val green: Color,
    public val teal: Color,
    public val blue: Color,
    /** The running glow's colour (the composer's). */
    public val glow: Color,
    public val dark: Boolean,
) {
    /** The most any single layer may be opaque at strength 1 (kit_live_style.dart:117). */
    public val ceiling: Double get() = if (dark) CEILING_DARK else CEILING_LIGHT

    override fun equals(other: Any?): Boolean =
        other is KitLivePalette && ground == other.ground && ink == other.ink && muted == other.muted &&
            faint == other.faint && green == other.green && teal == other.teal && blue == other.blue &&
            glow == other.glow && dark == other.dark

    override fun hashCode(): Int = ground.hashCode() xor green.hashCode() xor dark.hashCode()

    public companion object {
        internal const val CEILING_DARK: Double = 0.06 // F: lib/ui/kit/live_background/kit_live_style.dart:117
        internal const val CEILING_LIGHT: Double = 0.045 // F: lib/ui/kit/live_background/kit_live_style.dart:117
        private const val TEAL_MIX: Float = .35f // F: lib/ui/kit/kit_live_background.dart:102

        /** Dart `KitLiveBackground.paletteOfRoles` (`kit_live_background.dart:94-107`). */
        public fun of(r: Roles): KitLivePalette {
            val accent = Color(r.accent.v)
            val keyword = Color(r.codeKeyword.v)
            return KitLivePalette(
                ground = Color(r.ground.v),
                ink = Color(r.text1.v),
                muted = Color(r.text2.v),
                faint = Color(r.text3.v),
                green = accent,
                teal = liveLerp(accent, keyword, TEAL_MIX.toDouble()),
                blue = keyword,
                glow = accent,
                dark = r.dark,
            )
        }
    }
}

/** Per-look mutable state for one painted surface (Dart `KitLiveMemory`). */
public class KitLiveMemory {
    private val slots = HashMap<String, Any?>()

    /** The value kept under [key], made by [make] the first time. */
    @Suppress("UNCHECKED_CAST")
    public fun <T> of(key: String, make: () -> T): T = slots.getOrPut(key) { make() } as T

    public fun clear() {
        slots.clear()
    }
}
