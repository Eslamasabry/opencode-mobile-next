package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.design.color.Accents
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.engine.settings.prefs.Corners
import io.github.eslamasabry.oc.engine.settings.prefs.Density
import io.github.eslamasabry.oc.engine.settings.prefs.GlowStyle
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.words.res.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appearanceAskMode
import io.github.eslamasabry.oc.ui.words.res.appearanceAskMotion
import io.github.eslamasabry.oc.ui.words.res.appearanceAskStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceAskTheme
import io.github.eslamasabry.oc.ui.words.res.appearanceModeAutoNote
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverHoldsStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverMotion
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverNote
import io.github.eslamasabry.oc.ui.words.res.appearanceSeeAllThemes
import io.github.eslamasabry.oc.ui.words.res.effectsMotionSystemOff
import io.github.eslamasabry.oc.ui.words.res.textSizeTitle
import org.jetbrains.compose.resources.stringResource

/**
 * I04, one page (plan 06 K4): light or dark, the colour pack (most used first, "See all 31 themes" for the rest), the
 * five styles and Motion. Every choice applies at once (no Apply). Saver holds Style and Motion and says so.
 * Accent and frost use the same acknowledged preferences as the app root. Reset is one atomic engine commit.
 */
@Composable
internal fun ColumnScope.AppearanceBody(ui: AppearanceUi, actions: SettingsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val guide = ui.guide?.takeUnless { it.done }
    fun shown(group: Int) = guide == null || guide.step == group
    if (guide != null) {
        KitGroupNote(stringResource(Res.string.appearanceGuideCountAt, guide.step + 1, 4))
        KitChoiceList(
            choices = listOf(
                KitChoice(0, stringResource(Res.string.appearanceGroupColours)),
                KitChoice(1, stringResource(Res.string.appearanceGroupStyle)),
                KitChoice(2, stringResource(Res.string.appearanceGroupComfort)),
                KitChoice(3, stringResource(Res.string.appearanceChipGlow)),
            ),
            selected = guide.step, semanticsLabel = stringResource(Res.string.e7AppearanceTitle),
            onSelect = { actions.onAppearanceGuide(it, false) },
        )
    }
    if (shown(0)) {

    KitSectionLabel(stringResource(Res.string.appearanceAskMode))
    Box(gutter) {
        KitSegmented(
            segments = AppearanceMode.entries.map { KitSegment(it, modeWords(it)) },
            selected = ui.mode,
            semanticsLabel = stringResource(Res.string.appearanceAskMode),
            modifier = Modifier.testTag("appearance-mode"),
            onChange = actions::onMode,
        )
    }
    when (ui.mode) {
        AppearanceMode.System -> KitGroupNote(stringResource(Res.string.appearanceModeAutoNote))
        AppearanceMode.Saver -> KitGroupNote(stringResource(Res.string.appearanceSaverNote))
        else -> Unit
    }

    KitSectionLabel(stringResource(Res.string.appearanceAskTheme))
    KitChoiceList(
        choices = ui.packs.map { KitChoice(it, packName(it)) },
        selected = ui.pack,
        semanticsLabel = stringResource(Res.string.appearanceAskTheme),
        modifier = Modifier.testTag("appearance-packs"),
        onSelect = actions::onPack,
    )
    if (ui.seeAllCount > 0) {
        KitRow(
            title = stringResource(Res.string.appearanceSeeAllThemes, ui.seeAllCount),
            modifier = Modifier.testTag("appearance-see-all"),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::onSeeAllPacks,
        )
    }

    KitSectionLabel(stringResource(Res.string.appearanceAskAccent))
    KitChoiceList(
        choices = listOf(KitChoice<String?>(null, stringResource(Res.string.appearanceAccentMatchShort))) +
            Accents.swatches.map { KitChoice<String?>(it.id, accentWords(it.id)) },
        selected = ui.accent, semanticsLabel = stringResource(Res.string.appearanceAccentTitle),
        modifier = Modifier.testTag("appearance-accent"), onSelect = actions::onAccent,
    )

    }
    if (shown(1)) {
    // A style the app does not draw would be a choice that changes nothing: listed only where it is drawn.
    if (ui.styles) {
        KitSectionLabel(stringResource(Res.string.appearanceAskStyle))
        val styleHeld = if (ui.heldBySaver) KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsStyle)) else KitEnabled.Enabled
        KitChoiceList(
            choices = SurfaceStyle.entries.map { KitChoice(it, styleWords(it), enabled = if (it == ui.style) KitEnabled.Enabled else styleHeld) },
            selected = ui.style,
            semanticsLabel = stringResource(Res.string.appearanceAskStyle),
            modifier = Modifier.testTag("appearance-styles"),
            onSelect = actions::onStyle,
        )
        if (ui.heldBySaver) KitGroupNote(stringResource(Res.string.appearanceSaverHoldsStyle))
    }
    if (!ui.heldBySaver) {
            KitSectionLabel(stringResource(Res.string.appearanceFineTuneCorners))
            KitChoiceList(
                choices = listOf(KitChoice<Corners?>(null, stringResource(Res.string.settings40_style_default))) +
                    Corners.entries.map { KitChoice<Corners?>(it, cornerWords(it)) },
                selected = ui.corners, semanticsLabel = stringResource(Res.string.appearanceFineTuneCorners),
                onSelect = { actions.onShape(it, ui.density) },
            )
            KitSectionLabel(stringResource(Res.string.appearanceShapeSpacing))
            KitChoiceList(
                choices = listOf(KitChoice<Density?>(null, stringResource(Res.string.settings40_style_default))) +
                    Density.entries.map { KitChoice<Density?>(it, densityWords(it)) },
                selected = ui.density, semanticsLabel = stringResource(Res.string.appearanceShapeSpacing),
                onSelect = { actions.onShape(ui.corners, it) },
            )
    }

    KitSectionLabel(stringResource(Res.string.frostGlassQuestion))
    KitChoiceList(
        choices = FrostStrength.entries.map { KitChoice(it, frostWords(it)) },
        selected = ui.frost, semanticsLabel = stringResource(Res.string.frostGlassQuestion),
        modifier = Modifier.testTag("appearance-glass"), onSelect = actions::onFrost,
    )
    KitGroupNote(stringResource(when {
        ui.heldBySaver -> Res.string.frostGlassHeldSaver
        ui.systemMotionOff || ui.motion == MotionLevel.Off -> Res.string.frostGlassHeldMotion
        ui.styles && ui.style != SurfaceStyle.Solid -> Res.string.frostGlassHeldStyle
        else -> Res.string.frostGlassHint
    }))

    }
    if (shown(2)) {
    KitSectionLabel(stringResource(Res.string.appearanceAskMotion))
    Box(gutter) {
        KitSegmented(
            segments = MotionLevel.entries.map { KitSegment(it, motionWords(it)) },
            selected = ui.motion,
            semanticsLabel = stringResource(Res.string.appearanceAskMotion),
            modifier = Modifier.testTag("appearance-motion"),
            enabled = if (ui.heldBySaver) KitEnabled.Disabled(stringResource(Res.string.appearanceSaverMotion)) else KitEnabled.Enabled,
            onChange = actions::onMotion,
        )
    }
    if (!ui.heldBySaver) KitGroupNote(motionHint(ui.motion))
    if (ui.systemMotionOff) KitGroupNote(stringResource(Res.string.effectsMotionSystemOff))

    // B-KIT-059: Default until chosen; applies at the app root at once, this page included.
    KitSectionLabel(stringResource(Res.string.textSizeTitle))
    Box(gutter) {
        KitSegmented(
            segments = AppTextSize.entries.map { KitSegment(it, textSizeWords(it)) },
            selected = ui.textSize,
            semanticsLabel = stringResource(Res.string.textSizeTitle),
            modifier = Modifier.testTag("appearance-text-size"),
            onChange = actions::onTextSize,
        )
    }
    }
    if (shown(3)) {
    KitSectionLabel(stringResource(Res.string.appearanceAskGlow))
    KitChoiceList(
        choices = listOf(
            KitChoice<GlowStyle?>(null, stringResource(Res.string.appearanceGlowOff)),
            KitChoice<GlowStyle?>(GlowStyle.Classic, stringResource(Res.string.effectsGlowStyleClassic)),
            KitChoice<GlowStyle?>(GlowStyle.SoftRing, stringResource(Res.string.effectsGlowStyleSoft)),
        ),
        selected = if (ui.activityGlow) ui.glowStyle else null,
        semanticsLabel = stringResource(Res.string.appearanceAskGlow),
        modifier = Modifier.testTag("appearance-reply-glow"), onSelect = actions::onGlow,
    )

    }
    if (guide != null) {
        if (guide.step > 0) KitRow(title = stringResource(Res.string.whatsNewBack), onClick = { actions.onAppearanceGuide(guide.step - 1, false) })
        KitRow(title = stringResource(if (guide.step == 3) Res.string.appearanceGuideFinish else Res.string.kitTourNext),
            onClick = { actions.onAppearanceGuide(if (guide.step == 3) 0 else guide.step + 1, guide.step == 3) })
        KitRow(title = stringResource(Res.string.appearanceGuideSkip), onClick = { actions.onAppearanceGuide(0, true) })
    } else {
        KitRow(title = stringResource(Res.string.appearanceGuideAgain), modifier = Modifier.testTag("appearance-guide-again"),
            onClick = { actions.onAppearanceGuide(0, false) })
    }
    var reset by remember { mutableStateOf(false) }
    KitRow(
        title = stringResource(Res.string.appearanceGuideReset),
        modifier = Modifier.testTag("appearance-reset"),
        onClick = { reset = true },
    )
    KitConfirm(
        spec = if (reset) KitConfirmSpec(
            title = stringResource(Res.string.settings40_reset_question),
            confirmLabel = stringResource(Res.string.appearanceGuideResetDo),
            body = stringResource(Res.string.settings40_reset_body),
            kind = KitConfirmKind.Destructive,
        ) else null,
        onConfirm = { reset = false; actions.onResetAppearance() },
        onCancel = { reset = false },
    )

}

@Composable
private fun accentWords(id: String): String = stringResource(when (id) {
    "mint" -> Res.string.appearanceAccentMint
    "teal" -> Res.string.appearanceAccentTeal
    "blue" -> Res.string.appearanceAccentBlue
    "violet" -> Res.string.appearanceAccentViolet
    "pink" -> Res.string.appearanceAccentPink
    "mono" -> Res.string.appearanceAccentMono
    else -> Res.string.appearanceAccentSilver
})

@Composable
private fun frostWords(value: FrostStrength): String = stringResource(when (value) {
    FrostStrength.Off -> Res.string.frostGlassOff
    FrostStrength.Weak -> Res.string.frostGlassWeak
    FrostStrength.Balanced -> Res.string.frostGlassBalanced
    FrostStrength.Strong -> Res.string.frostGlassStrong
})

@Composable
private fun cornerWords(value: Corners): String = stringResource(when (value) {
    Corners.Round -> Res.string.appearanceFineTuneCornersRound
    Corners.Soft -> Res.string.appearanceFineTuneCornersSoft
    Corners.Sharp -> Res.string.appearanceFineTuneCornersSharp
})

@Composable
private fun densityWords(value: Density): String = stringResource(when (value) {
    Density.Comfortable -> Res.string.appearanceSpacingRoomy
    Density.Compact -> Res.string.appearanceSpacingCompact
})
