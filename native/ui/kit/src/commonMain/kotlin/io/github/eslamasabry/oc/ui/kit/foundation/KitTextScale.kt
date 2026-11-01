package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy

/**
 * A density whose `sp → dp` keeps the platform's own curve (Android 14+ non-linear scaling, kept by asking [base]) and
 * then caps the result at [capOfSpec] × the spec size (plan 06 §9.2). [multiplier] is the in-app Text size.
 */
@Immutable
public class ScaledFontDensity(
    private val base: Density,
    private val multiplier: Float,
    private val capOfSpec: Float,
) : Density {
    override val density: Float get() = base.density

    /** For code that reads only the number. Text sizes go through [toDp]. */
    override val fontScale: Float get() = minOf(base.fontScale * multiplier, capOfSpec)

    override fun TextUnit.toDp(): Dp {
        if (!isSp) return with(base) { this@toDp.toDp() }
        val platform = with(base) { (value * multiplier).sp.toDp() }
        val ceiling = (value * capOfSpec).dp
        return if (platform > ceiling) ceiling else platform
    }

    override fun Dp.toSp(): TextUnit = with(base) { (this@toSp / multiplier).toSp() }

    override fun equals(other: Any?): Boolean =
        other is ScaledFontDensity && other.base == base && other.multiplier == multiplier &&
            other.capOfSpec == capOfSpec

    override fun hashCode(): Int = (base.hashCode() * 31 + multiplier.hashCode()) * 31 + capOfSpec.hashCode()

    override fun toString(): String = "ScaledFontDensity(base=$base, multiplier=$multiplier, cap=$capOfSpec)"
}

/** The root density: in-app Text size × system, root cap 2.5×. `KitTheme` provides it once. */
@Composable
internal fun rememberAppTextDensity(textSize: AppTextSize): Density {
    val base = LocalDensity.current
    return remember(base, textSize) { ScaledFontDensity(base, textSize.factor, TextScalePolicy.ROOT_CAP) }
}

/** The effective scale at body size. Layout decisions compare with `TextScalePolicy.isLargeText` (≥ 1.29). */
@Composable
public fun kitTextScale(): Float = with(LocalDensity.current) { 16.sp.toDp().value / 16f }
