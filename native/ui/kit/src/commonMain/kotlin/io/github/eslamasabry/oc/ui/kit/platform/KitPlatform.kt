package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.color.DynamicSource
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.scaledFontDensity
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import kotlinx.coroutines.flow.StateFlow

public enum class KitPlatformFamily {
    Android,
    Desktop,
    Ios,
}

/**
 * What the system says (plan 06 §6). Read only by `KitTheme`, which resolves motion and contrast
 * from it.
 */
public data class KitSystemSignals(
    val animatorScale: Float = 1f,
    val powerSave: Boolean = false,
    val severeHeat: Boolean = false,
    val highContrast: Boolean = false,
    val is24Hour: Boolean = true,
    val finePointer: Boolean = false,
    val accessibleNavigation: Boolean = false,
)

/**
 * The kit's one platform seam (plan 06 §6, plan 03 §12.2). Only kit parts read it; features never
 * see it (Konsist K1). P7 carries the members its seams use: press feedback, haptics, frost, the
 * frame-text density and the system signals. The rest of plan 06 §6 (system bars, frame rate,
 * formats, clipboard, links, content slots) freezes in `contracts/kit-v1.md` and lands with the
 * parts that use it.
 */
@Stable
public interface KitPlatform {
    public val name: String
    public val family: KitPlatformFamily
    public val signals: StateFlow<KitSystemSignals>

    /** Debug, test and preview builds: kit assertions run (plan 06 §16.2). */
    public val isDebug: Boolean
        get() = false

    /**
     * Press feedback bounded to the part. Android: the M3 platform ripple; desktop: a hover/press
     * fill.
     */
    public fun pressIndication(ink: Color): Indication

    /**
     * Plays [kind] (plan 06 §4.9). Android honours the system Touch feedback setting; desktop is
     * silent.
     */
    public fun haptic(kind: KitHaptic)

    /**
     * A blur + saturation effect for frost, or null to draw the surface solid (below API 31,
     * desktop).
     */
    public fun frostEffect(spec: FrostSpec, density: Float): RenderEffect?

    /**
     * Material You tones (plan 06 §4.2, §5.5): Android API 31+ builds them from the system's
     * dynamic schemes; null below API 31, on desktop and in tests that do not set one, where
     * Graphite stands in.
     */
    public fun dynamicSource(): DynamicSource? = null

    /** A static SVG picture at a bounded tile size; no renderer may resolve external resources. */
    public fun svgPainter(text: String, pixels: Int): Painter? = null

    /** The platform's short local date/time; omit the date for a message from the same local day. */
    public fun messageTime(createdMs: Long, nowMs: Long): String? = null

    /** User-perceived characters for measured labels. Null keeps the full label as a conservative fit probe. */
    public fun graphemePrefix(text: String, count: Int): String? = null

    /** A full local medium date and locale-preferred short time (Flutter yMMMd + jm). */
    public fun dateTime(atMs: Long, localeTag: String): String? = null

    /**
     * System bars (plan 06 §5.6; Dart `KitSystemBars`): a transparent status bar whose icons
     * contrast with [ground] ([darkContent] = dark icons), and no navigation-bar contrast scrim.
     * `KitTheme` calls it once; desktop does nothing.
     */
    @Composable public fun SystemBars(darkContent: Boolean, ground: Color) {}

    /**
     * The density frame text renders with: [base]'s font scale capped at [cap], on the platform's
     * own curve.
     */
    public fun frameDensity(base: Density, cap: Float): Density = scaledFontDensity(base, 1f, cap)

    /**
     * Compiles the live backgrounds' shaders (AGSL on Android 13+, SkSL on desktop); null where the platform has no
     * runtime shaders (Android 8–12): the page then draws the look's still fallback.
     */
    public val liveShaders: LiveShaderCompiler?
        get() = null
}

/**
 * The phone's own bars for one theme (Dart `KitSystemBars.of`, `lib/ui/kit/kit_system_bars.dart`):
 * a clear status bar over the page, the navigation bar in the page's [navigationBarColor] ground
 * with no divider, and icons light on a dark theme and dark on a light one. [statusBarDark] is the
 * bar's own brightness, as iOS names it.
 */
public data class KitSystemBarsLook(
    val statusBarColor: Color,
    val statusBarDarkIcons: Boolean,
    val statusBarDark: Boolean,
    val navigationBarColor: Color,
    val navigationBarDividerColor: Color,
    val navigationBarDarkIcons: Boolean,
)

/** Dart `KitSystemBars` (kit-v2 §7.1): every page announces the look for its theme. */
public object KitSystemBars {
    /** Dart `KitSystemBars.of`: [dark] is `ThemeRoles.isDark`, [ground] is `roles.ground`. */
    public fun of(dark: Boolean, ground: Color): KitSystemBarsLook {
        val darkIcons = !dark // F: lib/ui/kit/kit_system_bars.dart:17
        return KitSystemBarsLook(
            statusBarColor = Color.Transparent,
            statusBarDarkIcons = darkIcons,
            statusBarDark = dark,
            navigationBarColor = ground,
            navigationBarDividerColor = Color.Transparent,
            navigationBarDarkIcons = darkIcons,
        )
    }
}

public val LocalKitPlatform: ProvidableCompositionLocal<KitPlatform> = staticCompositionLocalOf {
    error("KitPlatform is provided once at the app root")
}
