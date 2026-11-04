package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.StyleSpec
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.Tokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState

private fun missing(what: String): Nothing = error("$what is provided by KitTheme")

/** Plan 06 §5.1. Static: theme values change rarely and cost no read tracking. Kit parts only. */
public val LocalKitAppearance: ProvidableCompositionLocal<KitAppearance> = staticCompositionLocalOf { missing("KitAppearance") }
public val LocalKitStyle: ProvidableCompositionLocal<StyleSpec> = staticCompositionLocalOf { StyleSpec.of(KitAppearance.Default) }
public val LocalKitRoles: ProvidableCompositionLocal<Roles> = staticCompositionLocalOf { missing("Roles") }
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
