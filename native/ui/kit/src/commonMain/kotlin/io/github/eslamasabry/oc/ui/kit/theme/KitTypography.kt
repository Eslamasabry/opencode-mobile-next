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
public class KitTypography internal constructor(private val fonts: KitFontFamilies, private val arabic: Boolean) {
    private val styles: Map<TypeRole, TextStyle> = TypeRole.entries.associateWith(::build)

    public fun style(role: TypeRole): TextStyle = styles.getValue(role)

    private fun build(role: TypeRole): TextStyle {
        val spec = TypeScale.forLocale(role, arabic)
        return TextStyle(
            fontFamily = if (spec.family == TypeFamily.Mono) fonts.mono else fonts.sans,
            fontSize = spec.sizeSp.sp,
            lineHeight = spec.lineHeightSp.sp,
            fontWeight = FontWeight(spec.weight),
            letterSpacing = spec.trackingSp.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
            // Meta (times, counts, sizes) lines up in columns: tabular figures.
            fontFeatureSettings = if (role == TypeRole.Caption) "tnum" else null,
        )
    }
}
