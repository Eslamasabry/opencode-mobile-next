package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleSpec
import io.github.eslamasabry.oc.ui.design.style.SolidSpec
import io.github.eslamasabry.oc.ui.design.tokens.Tokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState

private fun missing(what: String): Nothing = error("$what is provided by KitTheme")

/** Plan 06 §5.1. Static: theme values change rarely and cost no read tracking. Kit parts only. */
public val LocalKitAppearance: ProvidableCompositionLocal<KitAppearance> = staticCompositionLocalOf { missing("KitAppearance") }
public val LocalKitColors: ProvidableCompositionLocal<KitColors> = staticCompositionLocalOf { missing("KitColors") }
public val LocalKitTypography: ProvidableCompositionLocal<KitTypography> = staticCompositionLocalOf { missing("KitTypography") }
public val LocalKitTokens: ProvidableCompositionLocal<Tokens> = staticCompositionLocalOf { missing("Tokens") }
public val LocalKitMotion: ProvidableCompositionLocal<KitMotionState> = staticCompositionLocalOf { missing("KitMotionState") }

/**
 * The root text density (system × in-app Text size, cap 2.5×) before any frame cap. Frame `KitText` reads it to size
 * its words the Flutter way (plan 06 §9); `KitFrameText` does not change it.
 */
public val LocalKitAppDensity: ProvidableCompositionLocal<Density?> = staticCompositionLocalOf { null }

/**
 * The window a screen is in (plan 06 §5.1, §10.1; Dart `KitWindow` + `MediaQuery.size`): measured on the window, never
 * the device. Kit parts read only this, so there is one answer. Dynamic: it changes on resize and rotation.
 */
@Immutable
public data class KitWindowInfo(
    val widthDp: Float,
    val heightDp: Float,
    val finePointer: Boolean = false,
) {
    val cls: WindowClass get() = WindowClass.of(widthDp)
    val short: Boolean get() = WindowClass.isShort(heightDp)

    /** Modal parts stay at Medium on a wide but short window. */
    val modal: WindowClass get() = WindowClass.modal(widthDp, heightDp)

    public companion object {
        /** A phone (412 × 915), the default before the first measure. */
        public val Phone: KitWindowInfo = KitWindowInfo(412f, 915f)
    }
}

public val LocalKitWindow: ProvidableCompositionLocal<KitWindowInfo> = compositionLocalOf { KitWindowInfo.Phone }

/**
 * The style in force and the Fine-tune knobs in force (plan 06 §5.1): Saver and platform high contrast are already
 * applied, so a part reads one answer. Kit parts only.
 */
@Immutable
public data class KitStyleState(val style: Style, val spec: StyleSpec, val knobs: Knobs) {
    public companion object {
        public val Solid: KitStyleState = KitStyleState(Style.Solid, SolidSpec, Knobs())
    }
}

public val LocalKitStyle: ProvidableCompositionLocal<KitStyleState> = staticCompositionLocalOf { KitStyleState.Solid }

/** The resolved roles (after the style's `adjustRoles`), for the style resolver; parts read [LocalKitColors]. */
public val LocalKitRoles: ProvidableCompositionLocal<Roles> = staticCompositionLocalOf { missing("Roles") }
