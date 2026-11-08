package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionScheme
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
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitThemeCover
import io.github.eslamasabry.oc.ui.kit.theme.kitThemeCover
import io.github.eslamasabry.oc.ui.kit.theme.rememberKitThemeFade
import io.github.eslamasabry.oc.ui.kit.theme.rememberSettledScheme
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val targetColors = remember(roles) { roles.toKitColors() }
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
    // P-029, M-019: a theme change fades the old screen's picture out over the new colours in 200 ms; the parts'
    // own moves keep running underneath.
    val fade = rememberKitThemeFade(targetColors, fades = level != MotionLevel.Off && !stillFrame)
    val colors = fade.colors
    val motion = remember(level, stillFrame) { KitMotionState(level, stillFrame) }
    val direction = if (isRtl(localeTag)) LayoutDirection.Rtl else LayoutDirection.Ltr
    val ripple = remember { kitRippleConfiguration() }
    val selection =
        remember(colors) { TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.30f)) }
    // P-116: Material keeps its colours in a static local holding an identity-compared ColorScheme, so swapping it
    // recomposes everything under the theme. The kit's own colours (dynamic locals) change at once; Material's scheme
    // settles after a theme switch's motion has finished ([rememberSettledScheme]), so its one full recomposition
    // never lands on a frame that moves.
    val scheme = rememberSettledScheme(roles, defers = level != MotionLevel.Off && !stillFrame)
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
    platform.SystemBars(darkContent = !colors.dark, ground = colors.ground)
    CompositionLocalProvider(
        LocalKitPlatform provides platform,
        LocalKitAppearance provides effectiveAppearance,
        LocalKitStyle provides styleState,
        LocalKitRoles provides roles,
        LocalKitLocaleTag provides localeTag,
        LocalKitColors provides colors,
        LocalKitThemeCover provides fade.cover,
        LocalKitTypography provides typography,
        LocalKitTokens provides tokens,
        LocalKitMotion provides motion,
        LocalKitWindow provides window,
        LocalLayoutDirection provides direction,
        LocalDensity provides appDensity,
        LocalKitAppDensity provides appDensity,
        // Material's theme provides its own ripple under this one; a value that changed with the colours only cost
        // a full recomposition (static local), so it is made once per platform.
        LocalIndication provides remember(platform) { platform.pressIndication(Color.Unspecified) },
        LocalRippleConfiguration provides ripple,
        LocalTextSelectionColors provides selection,
    ) {
        // One motion scheme for kit and M3 parts alike (motion lead 2026-11-07): springs, snapping under Off.
        val motionScheme = remember(motion) { KitMotionScheme(motion) }
        MaterialTheme(
            colorScheme = scheme,
            motionScheme = motionScheme,
            shapes = MaterialBridge.shapes,
            typography = remember(typography) { MaterialBridge.typography(typography) },
        ) {
            Box(Modifier.kitThemeCover(fade.cover), propagateMinConstraints = true) { content() }
        }
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
