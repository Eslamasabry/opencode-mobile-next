package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

public enum class KitHaptic { Send, Done, Commit, LongPress, Select, ToggleOn, ToggleOff }

/**
 * The only composable seam (plan 03 §12.2); features never see it (Konsist K1). Slice 0 carries haptics only; the
 * rest of the §12.2 members (press indication, overscroll, motion signals, frame-text density, frost, link handler,
 * content slots) are frozen in contracts/slice-1.md and land with the kit in slice 1.
 */
public interface KitPlatform {
    public val name: String

    public fun haptic(kind: KitHaptic)
}

public val LocalKitPlatform: ProvidableCompositionLocal<KitPlatform> =
    staticCompositionLocalOf { error("KitPlatform is provided once at the app root") }
