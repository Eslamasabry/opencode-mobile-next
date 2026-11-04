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
    private val styles: Map<TypeRole, TextStyle> = TypeRole.entries.associateWith { build(it, false, false) }
    public val isArabic: Boolean get() = arabic

    public fun style(role: TypeRole, pageTitle: Boolean = false, sectionLabel: Boolean = false): TextStyle =
        if (!pageTitle && !sectionLabel) styles.getValue(role) else build(role, pageTitle, sectionLabel)

    private fun build(role: TypeRole, pageTitle: Boolean, sectionLabel: Boolean): TextStyle {
        var spec = TypeScale.forLocale(role, arabic)
        val display = role == TypeRole.LargeTitle || role == TypeRole.Title || role == TypeRole.Headline
        if (appearance.firmType) {
            spec = spec.copy(weight = when (role) {
                TypeRole.LargeTitle, TypeRole.Title -> 650
                TypeRole.Headline, TypeRole.Label, TypeRole.Caption, TypeRole.Button -> 600
                TypeRole.Secondary -> 500
                else -> spec.weight
            })
            if (!arabic && role == TypeRole.LargeTitle) spec = spec.copy(trackingSp = -.8f)
            if (!arabic && role == TypeRole.Title) spec = spec.copy(trackingSp = -.48f)
        }
        if (display) {
            val weight = when (appearance.style) { SurfaceStyle.Brutal -> 800; SurfaceStyle.Paper -> 750; SurfaceStyle.Signal -> if (role == TypeRole.LargeTitle) 760 else 700; else -> spec.weight }
            spec = spec.copy(weight = weight)
        }
        if ((role == TypeRole.Body || role == TypeRole.RowTitle) && appearance.style == SurfaceStyle.Brutal) spec = spec.copy(weight = 500)
        if (role == TypeRole.LargeTitle && appearance.style == SurfaceStyle.Paper) spec = spec.copy(sizeSp = 34, lineHeightSp = 41)
        if (role == TypeRole.LargeTitle && appearance.style == SurfaceStyle.Signal) spec = spec.copy(sizeSp = 32, lineHeightSp = 39)
        if (pageTitle && role == TypeRole.Headline && appearance.style != SurfaceStyle.Solid && appearance.style != SurfaceStyle.Soft) {
            val size = if (appearance.style == SurfaceStyle.Paper) 24 else 22
            spec = spec.copy(sizeSp = size, lineHeightSp = size + 6)
        }
        if (!arabic && display && role != TypeRole.Headline) spec = spec.copy(trackingSp = when (appearance.style) {
            SurfaceStyle.Brutal, SurfaceStyle.Paper -> -.03f * spec.sizeSp
            SurfaceStyle.Signal -> -.02f * spec.sizeSp
            else -> spec.trackingSp
        })
        if (!arabic && appearance.monoLabels && ((role == TypeRole.Label && sectionLabel) || role == TypeRole.Caption))
            spec = spec.copy(family = TypeFamily.Mono, trackingSp = 0f)
        if (!arabic && appearance.engravedLabels && role == TypeRole.Label && sectionLabel)
            spec = spec.copy(sizeSp = 12, lineHeightSp = 18, weight = 600, trackingSp = 1.44f)
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
