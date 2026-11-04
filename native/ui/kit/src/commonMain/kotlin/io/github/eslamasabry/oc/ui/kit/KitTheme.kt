package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionSignals
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.motion.resolveMotion
import io.github.eslamasabry.oc.ui.design.tokens.Alphas
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.Tokens
import io.github.eslamasabry.oc.ui.kit.foundation.rememberAppTextDensity
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitTypography
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.KitStyleState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kit.theme.MaterialBridge
import io.github.eslamasabry.oc.ui.kit.theme.rememberKitFonts
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors

/**
 * The theme root (plan 06 §5.1). It resolves motion from the system signals, resolves the roles
 * (the pack — Material You from the platform's tones — then the Accent swatch as partner, then
 * Saver), builds colours, Geist typography and tokens, sets the layout direction from [localeTag],
 * applies the root text density (in-app Text size × system, cap 2.5×), measures the window, sets
 * the system bars, and wraps the M3 parts the kit uses in the bridged `MaterialTheme`. Nothing else
 * in the app does any of this.
 *
 * Slice 0 keeps it in the `…ui.kit` package (the shell imports it there); plan 06's `…ui.kit.theme`
 * move rides with the contract freeze.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun KitTheme(
    appearance: KitAppearance = KitAppearance.Default,
    platform: KitPlatform = LocalKitPlatform.current,
    localeTag: String = defaultLocaleTag(),
    stillFrame: Boolean = false,
    content: @Composable () -> Unit,
) {
    val signals by platform.signals.collectAsState()
    val dark =
        when (appearance.mode) {
            AppearanceMode.System -> isSystemInDarkTheme()
            AppearanceMode.Light -> false
            AppearanceMode.Dark,
            AppearanceMode.Saver -> true
        }
    // Called once per composition of the theme, and only for Material You (plan 06 §5.5).
    val dynamic =
        if (appearance.pack == PackId.Dynamic) remember(platform, dark) { platform.dynamicSource() }
        else null
    // The style actually drawn: Solid under Saver and under platform high contrast unless the style keeps itself.
    val styleState =
        remember(appearance.style, appearance.fineTune, appearance.density, appearance.saver, signals.highContrast) {
            val style = StyleResolver.effectiveStyle(appearance.style, signals.highContrast, appearance.saver)
            // The older `density` field is a Fine-tune Spacing choice too.
            val tune =
                if (appearance.fineTune.density == null && appearance.density != Density.Comfortable) {
                    appearance.fineTune.copy(density = appearance.density)
                } else {
                    appearance.fineTune
                }
            KitStyleState(
                style,
                StyleResolver.spec(style),
                StyleResolver.knobsFor(appearance.style, tune, signals.highContrast, appearance.saver),
            )
        }
    // Parts that read `LocalKitAppearance.current.style` see the style actually drawn.
    val effectiveAppearance = appearance.copy(style = styleState.style)
    val roles =
        remember(appearance.pack, appearance.accent, dark, appearance.saver, dynamic, styleState.style) {
            // The style's own role adjustment (Signal's signal-box palette among them; hook H6).
            StyleResolver.adjust(
                ThemeResolver.roles(appearance.pack, appearance.accent, dark, appearance.saver, dynamic),
                styleState.style,
            )
        }
    val colors = remember(roles) { roles.toKitColors() }
    val arabic = isArabic(localeTag)
    val fonts = rememberKitFonts(arabic)
    val typography = remember(fonts, arabic) { KitTypography(fonts, arabic) }
    // Saver makes the effective knobs none (Dart KitEffects.underSaver).
    val tokens = Tokens.of(styleState.knobs.density)
    val level =
        resolveMotion(
            MotionSignals(signals.animatorScale, signals.powerSave, signals.severeHeat),
            appearance.motion,
            appearance.saver,
        )
    val motion = remember(level, stillFrame) { KitMotionState(level, stillFrame) }
    val direction = if (isRtl(localeTag)) LayoutDirection.Rtl else LayoutDirection.Ltr
    val ripple = remember { kitRippleConfiguration() }
    val selection =
        remember(colors) { TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.30f)) }
    val scheme = remember(roles) { MaterialBridge.scheme(roles) }
    val appDensity = rememberAppTextDensity(appearance.textSize)
    val container = LocalWindowInfo.current.containerSize
    val window =
        remember(container, appDensity.density, signals.finePointer) {
            if (container.width == 0) KitWindowInfo.Phone.copy(finePointer = signals.finePointer)
            else
                KitWindowInfo(
                    container.width / appDensity.density,
                    container.height / appDensity.density,
                    signals.finePointer,
                )
        }
    platform.SystemBars(darkContent = !roles.dark, ground = colors.ground)
    CompositionLocalProvider(
        LocalKitPlatform provides platform,
        LocalKitAppearance provides effectiveAppearance,
        LocalKitStyle provides styleState,
        LocalKitRoles provides roles,
        LocalKitLocaleTag provides localeTag,
        LocalKitColors provides colors,
        LocalKitTypography provides typography,
        LocalKitTokens provides tokens,
        LocalKitMotion provides motion,
        LocalKitWindow provides window,
        LocalLayoutDirection provides direction,
        LocalDensity provides appDensity,
        LocalKitAppDensity provides appDensity,
        LocalIndication provides platform.pressIndication(colors.text1),
        LocalRippleConfiguration provides ripple,
        LocalTextSelectionColors provides selection,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = MaterialBridge.shapes, content = content)
    }
}

/**
 * Dart `KitInk.alpha`: the ripple is the part's ink at 12 % (M3's own default is 10 %). M3 1.5
 * deprecates the per-state alphas with no replacement that sets them, so the deprecated constructor
 * is kept on purpose (P7 report, D-list).
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
private fun kitRippleConfiguration(): RippleConfiguration =
    RippleConfiguration(
        rippleAlpha =
            RippleAlpha(
                draggedAlpha = 0.16f,
                focusedAlpha = Alphas.FOCUSED,
                hoveredAlpha = Alphas.HOVERED,
                pressedAlpha = Alphas.PRESSED,
            )
    )

/**
 * The default when the caller passes no tag: the platform locale. CMP has no snapshot-backed common
 * replacement for `Locale.current`; `:ui:shell` passes the resolved app locale (`oc.appLocale`, P6)
 * instead of relying on this.
 */
@Suppress("DEPRECATION") private fun defaultLocaleTag(): String = Locale.current.toLanguageTag()

private val rtlLanguages = setOf("ar", "fa", "he", "iw", "ur", "ps", "ckb", "yi")

internal fun languageOf(tag: String): String =
    tag.substringBefore('-').substringBefore('_').lowercase()

internal fun isRtl(tag: String): Boolean = languageOf(tag) in rtlLanguages

internal fun isArabic(tag: String): Boolean = languageOf(tag) == "ar"
