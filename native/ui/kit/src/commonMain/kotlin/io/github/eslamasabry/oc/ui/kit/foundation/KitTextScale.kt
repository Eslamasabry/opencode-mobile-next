package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy

/**
 * [base] with its font scale multiplied by [multiplier] (the in-app Text size) and capped at [cap].
 *
 * Only the **font scale** is changed, never `sp → dp` itself. P7 measured why: Compose lays text out with the layout
 * node's coordinator as its density, and the coordinator forwards only `density` and `fontScale`; a custom `toDp`
 * (plan 06 §9.2's sketch) is skipped by text on Android, which maps `fontScale` through the platform's non-linear
 * `FontScaleConverter` itself. Setting the scale keeps text, icons and [kitTextScale] on the same curve: at or below the
 * cap the platform's own size, above it the platform's curve at the cap — never more than `cap` × the spec size.
 */
public fun scaledFontDensity(base: Density, multiplier: Float, cap: Float): Density =
    Density(base.density, minOf(base.fontScale * multiplier, cap))

/** The root density: in-app Text size × system, root cap 2.5×. `KitTheme` provides it once. */
@Composable
internal fun rememberAppTextDensity(textSize: AppTextSize): Density {
    val base = LocalDensity.current
    return remember(base, textSize) { scaledFontDensity(base, textSize.factor, TextScalePolicy.ROOT_CAP) }
}

/** The effective scale at body size. Layout decisions compare with `TextScalePolicy.isLargeText` (≥ 1.29). */
@Composable
public fun kitTextScale(): Float = with(LocalDensity.current) { 16.sp.toDp().value / 16f }

/**
 * The person's text size as they chose it: the system font size times the in-app Text size, before the platform's
 * non-linear curve (Android 14 and later draw 16 sp at a 1.3 setting as about 19.5 dp, so [kitTextScale] reads about
 * 1.22 there). Layout rules that speak of "1.3x text" compare with this, never with [kitTextScale]: the agents grid
 * at a 1.3 setting kept two columns on a 346 dp phone because its rule read 1.22 (look of build 10086).
 */
@Composable
public fun kitFontScale(): Float = LocalDensity.current.fontScale
