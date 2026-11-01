package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.foundation.Indication
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.ScaledFontDensity
import kotlinx.coroutines.flow.StateFlow

public enum class KitPlatformFamily { Android, Desktop, Ios }

/** What the system says (plan 06 §6). Read only by `KitTheme`, which resolves motion and contrast from it. */
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
 * The kit's one platform seam (plan 06 §6, plan 03 §12.2). Only kit parts read it; features never see it (Konsist K1).
 * P7 carries the members its seams use: press feedback, haptics, frost, the frame-text density and the system signals.
 * The rest of plan 06 §6 (system bars, frame rate, formats, clipboard, links, content slots) freezes in
 * `contracts/kit-v1.md` and lands with the parts that use it.
 */
@Stable
public interface KitPlatform {
    public val name: String
    public val family: KitPlatformFamily
    public val signals: StateFlow<KitSystemSignals>

    /** Debug, test and preview builds: kit assertions run (plan 06 §16.2). */
    public val isDebug: Boolean get() = false

    /** Press feedback bounded to the part. Android: the M3 platform ripple; desktop: a hover/press fill. */
    public fun pressIndication(ink: Color): Indication

    /** Plays [kind] (plan 06 §4.9). Android honours the system Touch feedback setting; desktop is silent. */
    public fun haptic(kind: KitHaptic)

    /** A blur + saturation effect for frost, or null to draw the surface solid (below API 31, desktop). */
    public fun frostEffect(spec: FrostSpec, density: Float): RenderEffect?

    /** The density frame text renders with: [base] capped at [cap] × spec size, the platform curve kept below. */
    public fun frameDensity(base: Density, cap: Float): Density = ScaledFontDensity(base, 1f, cap)
}

public val LocalKitPlatform: ProvidableCompositionLocal<KitPlatform> =
    staticCompositionLocalOf { error("KitPlatform is provided once at the app root") }
