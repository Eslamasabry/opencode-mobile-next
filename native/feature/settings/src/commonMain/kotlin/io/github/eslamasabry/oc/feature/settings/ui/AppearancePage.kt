package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.prefs.GlowStyle
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.color.Accents
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatchGrid
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.style.KitSignalSample
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentMatchShort
import io.github.eslamasabry.oc.ui.words.res.appearanceAskAccent
import io.github.eslamasabry.oc.ui.words.res.appearanceAskMode
import io.github.eslamasabry.oc.ui.words.res.appearanceAskMotion
import io.github.eslamasabry.oc.ui.words.res.appearanceAskShape
import io.github.eslamasabry.oc.ui.words.res.appearanceAskStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceAskTheme
import io.github.eslamasabry.oc.ui.words.res.appearanceFineTuneCorners
import io.github.eslamasabry.oc.ui.words.res.appearanceFineTuneReset
import io.github.eslamasabry.oc.ui.words.res.appearanceModeAutoNote
import io.github.eslamasabry.oc.ui.words.res.appearancePickerThemeApplied
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverHoldsStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverMotion
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverNote
import io.github.eslamasabry.oc.ui.words.res.appearanceSeeAllThemes
import io.github.eslamasabry.oc.ui.words.res.appearanceShapeSpacing
import io.github.eslamasabry.oc.ui.words.res.effectsMotionSystemOff
import io.github.eslamasabry.oc.ui.words.res.kitSwatchInUse
import io.github.eslamasabry.oc.ui.words.res.kitUndoAction
import io.github.eslamasabry.oc.ui.words.res.textSizeTitle
import io.github.eslamasabry.oc.ui.words.res.appearanceAskGlow
import io.github.eslamasabry.oc.ui.words.res.appearanceGlowOff
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideReset
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideResetDo
import io.github.eslamasabry.oc.ui.words.res.effectsGlowStyleClassic
import io.github.eslamasabry.oc.ui.words.res.effectsGlowStyleSoft
import io.github.eslamasabry.oc.ui.words.res.frostGlassBalanced
import io.github.eslamasabry.oc.ui.words.res.frostGlassHeldMotion
import io.github.eslamasabry.oc.ui.words.res.frostGlassHeldSaver
import io.github.eslamasabry.oc.ui.words.res.frostGlassHeldStyle
import io.github.eslamasabry.oc.ui.words.res.frostGlassHint
import io.github.eslamasabry.oc.ui.words.res.frostGlassOff
import io.github.eslamasabry.oc.ui.words.res.frostGlassQuestion
import io.github.eslamasabry.oc.ui.words.res.frostGlassStrong
import io.github.eslamasabry.oc.ui.words.res.frostGlassWeak
import io.github.eslamasabry.oc.ui.words.res.settings40_reset_body
import io.github.eslamasabry.oc.ui.words.res.settings40_reset_question
import io.github.eslamasabry.oc.ui.words.res.tourMakeItYoursComfort
import org.jetbrains.compose.resources.stringResource

/**
 * I04, one page (plan 06 K4): light or dark, the colour pack (most used first, "See all 31 themes"
 * for the rest), the five styles and Motion. A colour pack opens a contained preview with Apply and
 * then Undo (Material You applies at once); every other choice applies at once. Saver holds
 * Style and Motion and says so. Text size is the last question (B-KIT-059). Accent is a swatch grid
 * (B-KIT-049) and Fine-tune is Corners and Spacing over the style's own (B-KIT-017). Glow and the
 * guided first visit are S4-APPEAR's.
 * The guide offers the controls the host actually draws; its record retains every migrated
 * Flutter question. Glass (frost) and the activity glow use the same acknowledged preferences as
 * the app root; Reset is one atomic engine commit. What's new › Show me arrives on Text size
 * ([showArrival]).
 */
@Composable
internal fun ColumnScope.AppearanceBody(
    ui: AppearanceUi,
    actions: SettingsActions,
    showArrival: Boolean = false,
    dismissArrival: () -> Unit = {},
) {
    var preview by remember { mutableStateOf<PackId?>(null) }
    var undo by remember { mutableStateOf<Pair<PackId, PackId>?>(null) }
    preview?.let { pack ->
        ThemePreviewSheet(pack, ui, { preview = null }) { selected ->
            undo = ui.pack to selected
            actions.onPack(selected)
            preview = null
        }
    }
    undo
        ?.takeIf { ui.pack == it.second }
        ?.let { (previous, applied) ->
            KitNotice(
                stringResource(Res.string.appearancePickerThemeApplied, packName(applied)),
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.kitUndoAction),
                            {
                                actions.onPack(previous)
                                undo = null
                            },
                        )
                    ),
            )
        }
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)

    if (ui.guide != null) {
        AppearanceGuideHeader(ui, actions)
        if (!ui.guide.done && ui.question == null) {
            AppearanceGuideFooter(ui, actions)
            return
        }
    }
    if (ui.question == null || ui.question == AppearanceQuestion.Mode) {
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
    }

    if (ui.question == null || ui.question == AppearanceQuestion.Theme) {
        KitSectionLabel(stringResource(Res.string.appearanceAskTheme))
        KitChoiceList(
            choices = ui.packs.map { KitChoice(it, packName(it)) },
            selected = ui.pack,
            semanticsLabel = stringResource(Res.string.appearanceAskTheme),
            modifier = Modifier.testTag("appearance-packs"),
            // Material You has no contained preview (its colours come from the system), so it applies
            // at once like before; every other pack is previewed first.
            onSelect = { if (it == PackId.Dynamic) actions.onPack(it) else preview = it },
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
        Box(gutter) {
            val dark = LocalKitColors.current.dark
            val inUse = stringResource(Res.string.kitSwatchInUse)
            val match = stringResource(Res.string.appearanceAccentMatchShort)
            KitSwatchGrid(
                label = stringResource(Res.string.appearanceAskAccent),
                inUseLabel = inUse,
                modifier = Modifier.testTag("appearance-accent"),
                swatches =
                    listOf(
                        KitSwatch(
                            "match",
                            match,
                            null,
                            ui.accent == null,
                            onPressed = { actions.onAccent(null) },
                        )
                    ) +
                        Accents.swatches.map { sw ->
                            KitSwatch(
                                sw.id,
                                accentWords(sw.id),
                                Color(sw.of(dark).v),
                                ui.accent == sw.id,
                                onPressed = { actions.onAccent(sw.id) },
                            )
                        },
            )
        }
    }

    if (ui.question == null || ui.question == AppearanceQuestion.Style) {
        // A style the app does not draw would be a choice that changes nothing: listed only where it is
        // drawn.
        if (ui.styles) {
            KitSectionLabel(stringResource(Res.string.appearanceAskStyle))
            val styleHeld =
                if (ui.heldBySaver)
                    KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsStyle))
                else KitEnabled.Enabled
            KitChoiceList(
                choices =
                    // All five are drawn (the style resolver); the Signal sample below previews Signal.
                    SurfaceStyle.entries.map {
                        KitChoice(
                            it,
                            styleWords(it),
                            enabled = if (it == ui.style) KitEnabled.Enabled else styleHeld,
                        )
                    },
                selected = ui.style,
                semanticsLabel = stringResource(Res.string.appearanceAskStyle),
                modifier = Modifier.testTag("appearance-styles"),
                onSelect = actions::onStyle,
            )
            KitSignalSample(modifier = gutter)
            if (ui.heldBySaver) KitGroupNote(stringResource(Res.string.appearanceSaverHoldsStyle))

            KitSectionLabel(stringResource(Res.string.appearanceAskShape))
            val held =
                if (ui.heldBySaver)
                    KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsStyle))
                else KitEnabled.Enabled
            Box(gutter) {
                KitSegmented(
                    segments = Corners.entries.map { KitSegment(it, cornersWords(it)) },
                    selected = ui.corners,
                    semanticsLabel = stringResource(Res.string.appearanceFineTuneCorners),
                    modifier = Modifier.testTag("appearance-corners"),
                    enabled = held,
                    onChange = actions::onCorners,
                )
            }
            Box(
                Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
                    .padding(top = Space.S2.dp)
            ) {
                KitSegmented(
                    segments = Density.entries.map { KitSegment(it, spacingWords(it)) },
                    selected = ui.spacing,
                    semanticsLabel = stringResource(Res.string.appearanceShapeSpacing),
                    modifier = Modifier.testTag("appearance-spacing"),
                    enabled = held,
                    onChange = actions::onSpacing,
                )
            }
            if (ui.fineTuned && !ui.heldBySaver) {
                KitRow(
                    title = stringResource(Res.string.appearanceFineTuneReset, styleWords(ui.style)),
                    modifier = Modifier.testTag("appearance-fine-tune-reset"),
                    onClick = actions::onResetFineTune,
                )
            }
        }
    }

    if (ui.question == null || ui.question == AppearanceQuestion.Motion) {
        KitSectionLabel(stringResource(Res.string.appearanceAskMotion))
        Box(gutter) {
            KitSegmented(
                segments = MotionLevel.entries.map { KitSegment(it, motionWords(it)) },
                selected = ui.motion,
                semanticsLabel = stringResource(Res.string.appearanceAskMotion),
                modifier = Modifier.testTag("appearance-motion"),
                enabled =
                    if (ui.heldBySaver)
                        KitEnabled.Disabled(stringResource(Res.string.appearanceSaverMotion))
                    else KitEnabled.Enabled,
                onChange = actions::onMotion,
            )
        }
        if (!ui.heldBySaver) KitGroupNote(motionHint(ui.motion))
        if (ui.systemMotionOff) KitGroupNote(stringResource(Res.string.effectsMotionSystemOff))
    }

    if (ui.question == null || ui.question == AppearanceQuestion.Strength) {
        KitSectionLabel(stringResource(Res.string.frostGlassQuestion))
        KitChoiceList(
            choices = FrostStrength.entries.map { KitChoice(it, frostWords(it)) },
            selected = ui.frost,
            semanticsLabel = stringResource(Res.string.frostGlassQuestion),
            modifier = Modifier.testTag("appearance-glass"),
            onSelect = actions::onFrost,
        )
        KitGroupNote(
            stringResource(
                when {
                    ui.heldBySaver -> Res.string.frostGlassHeldSaver
                    ui.systemMotionOff || ui.motion == MotionLevel.Off ->
                        Res.string.frostGlassHeldMotion
                    ui.styles && ui.style != SurfaceStyle.Solid -> Res.string.frostGlassHeldStyle
                    else -> Res.string.frostGlassHint
                }
            )
        )
    }

    if (ui.question == null || ui.question == AppearanceQuestion.Text) {
        // B-KIT-059: Default until chosen; applies at the app root at once, this page included.
        KitSectionLabel(stringResource(Res.string.textSizeTitle))
        val arrivalAnchor = remember { BringIntoViewRequester() }
        LaunchedEffect(showArrival) { if (showArrival) arrivalAnchor.bringIntoView() }
        KitTip(
            stringResource(Res.string.tourMakeItYoursComfort),
            showArrival,
            dismissArrival,
            modifier = gutter.bringIntoViewRequester(arrivalAnchor),
        ) {
            Box {
                KitSegmented(
                    segments = AppTextSize.entries.map { KitSegment(it, textSizeWords(it)) },
                    selected = ui.textSize,
                    semanticsLabel = stringResource(Res.string.textSizeTitle),
                    modifier = Modifier.testTag("appearance-text-size"),
                    onChange = actions::onTextSize,
                )
            }
        }
    }

    if (ui.question == null || ui.question == AppearanceQuestion.Glow) {
        KitSectionLabel(stringResource(Res.string.appearanceAskGlow))
        KitChoiceList(
            choices =
                listOf(
                    KitChoice<GlowStyle?>(null, stringResource(Res.string.appearanceGlowOff)),
                    KitChoice<GlowStyle?>(
                        GlowStyle.Classic,
                        stringResource(Res.string.effectsGlowStyleClassic),
                    ),
                    KitChoice<GlowStyle?>(
                        GlowStyle.SoftRing,
                        stringResource(Res.string.effectsGlowStyleSoft),
                    ),
                ),
            selected = if (ui.activityGlow) ui.glowStyle else null,
            semanticsLabel = stringResource(Res.string.appearanceAskGlow),
            modifier = Modifier.testTag("appearance-reply-glow"),
            onSelect = actions::onGlow,
        )
    }
    if (ui.guide != null) AppearanceGuideFooter(ui, actions)

    if (ui.question == null) {
        var reset by remember { mutableStateOf(false) }
        KitRow(
            title = stringResource(Res.string.appearanceGuideReset),
            modifier = Modifier.testTag("appearance-reset"),
            onClick = { reset = true },
        )
        KitConfirm(
            spec =
                if (reset)
                    KitConfirmSpec(
                        title = stringResource(Res.string.settings40_reset_question),
                        confirmLabel = stringResource(Res.string.appearanceGuideResetDo),
                        body = stringResource(Res.string.settings40_reset_body),
                        kind = KitConfirmKind.Destructive,
                    )
                else null,
            onConfirm = {
                reset = false
                actions.onResetAppearance()
            },
            onCancel = { reset = false },
        )
    }
}

@Composable
private fun frostWords(value: FrostStrength): String =
    stringResource(
        when (value) {
            FrostStrength.Off -> Res.string.frostGlassOff
            FrostStrength.Weak -> Res.string.frostGlassWeak
            FrostStrength.Balanced -> Res.string.frostGlassBalanced
            FrostStrength.Strong -> Res.string.frostGlassStrong
        }
    )
