package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.type.TypeFamily
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.type.TypeScale
import io.github.eslamasabry.oc.ui.design.effects.StyleSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.SurfaceStyle
import io.github.eslamasabry.oc.ui.kit.res.Res
import io.github.eslamasabry.oc.ui.kit.res.geist_italic_variable
import io.github.eslamasabry.oc.ui.kit.res.geist_mono_italic_variable
import io.github.eslamasabry.oc.ui.kit.res.geist_mono_variable
import io.github.eslamasabry.oc.ui.kit.res.geist_variable
import org.jetbrains.compose.resources.Font

@Immutable
public class KitFontFamilies(public val sans: FontFamily, public val mono: FontFamily)

/**
 * Geist and Geist Mono variable fonts, one instance per weight through `FontVariation.weight` (plan 06 §4.4). Arabic:
 * the system sans (Dart `AppTheme.forLocale`), Geist Mono kept.
 */
@Composable
internal fun rememberKitFonts(arabic: Boolean): KitFontFamilies {
    val sans =
        if (arabic) {
            FontFamily.SansSerif
        } else {
            FontFamily(
                TypeScale.sansWeights.flatMap { w ->
                    listOf(
                        Font(Res.font.geist_variable, FontWeight(w), FontStyle.Normal, variation(w)),
                        Font(Res.font.geist_italic_variable, FontWeight(w), FontStyle.Italic, variation(w)),
                    )
                },
            )
        }
    val mono =
        FontFamily(
            TypeScale.monoWeights.flatMap { w ->
                listOf(
                    Font(Res.font.geist_mono_variable, FontWeight(w), FontStyle.Normal, variation(w)),
                    Font(Res.font.geist_mono_italic_variable, FontWeight(w), FontStyle.Italic, variation(w)),
                )
            },
        )
    return remember(arabic, sans, mono) { KitFontFamilies(sans, mono) }
}

private fun variation(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

/** One `TextStyle` per role, built once per (fonts, locale). Proportional leading = Flutter's default distribution. */
@Immutable
public class KitTypography internal constructor(private val fonts: KitFontFamilies, private val arabic: Boolean, private val appearance: StyleSpec = StyleSpec.of(KitAppearance.Default)) {
    private val styles: Map<TypeRole, TextStyle> = TypeRole.entries.associateWith(::build)

    public fun style(role: TypeRole): TextStyle = styles.getValue(role)

    private fun build(role: TypeRole): TextStyle {
        var spec = TypeScale.forLocale(role, arabic)
        if (appearance.firmType) spec = spec.copy(weight = when (role) {
            TypeRole.Title, TypeRole.RowTitle -> 650
            TypeRole.LargeTitle -> when (appearance.style) { SurfaceStyle.Brutal -> 800; SurfaceStyle.Paper -> 750; SurfaceStyle.Signal -> 760; else -> 650 }
            TypeRole.Headline, TypeRole.Label, TypeRole.Caption, TypeRole.Button -> 600
            TypeRole.Secondary -> 500
            TypeRole.Body -> if (appearance.style == SurfaceStyle.Brutal) 500 else spec.weight
            else -> spec.weight
        })
        if (role == TypeRole.LargeTitle && appearance.style == SurfaceStyle.Paper) spec = spec.copy(sizeSp = 34, lineHeightSp = 40)
        if (role == TypeRole.LargeTitle && appearance.style == SurfaceStyle.Signal) spec = spec.copy(sizeSp = 32, lineHeightSp = 38)
        if (!arabic && role == TypeRole.LargeTitle && appearance.style != SurfaceStyle.Solid) spec = spec.copy(trackingSp = when (appearance.style) { SurfaceStyle.Brutal, SurfaceStyle.Paper -> -.03f * spec.sizeSp; SurfaceStyle.Signal -> -.02f * spec.sizeSp; else -> spec.trackingSp })
        return TextStyle(
            fontFamily = if (spec.family == TypeFamily.Mono) fonts.mono else fonts.sans,
            fontSize = spec.sizeSp.sp,
            lineHeight = spec.lineHeightSp.sp,
            fontWeight = FontWeight(spec.weight),
            letterSpacing = spec.trackingSp.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
        )
    }
}
