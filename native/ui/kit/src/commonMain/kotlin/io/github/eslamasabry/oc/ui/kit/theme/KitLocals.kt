package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
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
