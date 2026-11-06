package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGroup
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.LiveStrength
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.color.Accents
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitLookOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitPlainOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitSliderStep
import io.github.eslamasabry.oc.ui.kit.choice.KitStepSlider
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatchGrid
import io.github.eslamasabry.oc.ui.kit.choice.KitThemeOptionCard
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.guide.KitGuideQuestionHeading
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.*
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
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverHoldsStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverMotion
import io.github.eslamasabry.oc.ui.words.res.appearanceSaverNote
import io.github.eslamasabry.oc.ui.words.res.appearanceSeeAllThemes
import io.github.eslamasabry.oc.ui.words.res.appearanceShapeSpacing
import io.github.eslamasabry.oc.ui.words.res.effectsMotionSystemOff
import io.github.eslamasabry.oc.ui.words.res.kitSwatchInUse
import io.github.eslamasabry.oc.ui.words.res.textSizeTitle
import org.jetbrains.compose.resources.stringResource

/**
 * I04's supported Appearance questions. Theme cards apply at once and style cards draw their own
 * samples (Glass among them, order 34). The existing guide record selects a question; Saver holds
 * Style, Background and Motion. Background offers the looks the native app draws: Off and the
 * painted aurora (order 35); the glow question still needs its presentation fields (evidence/44.md).
 */
@Composable
internal fun ColumnScope.AppearanceBody(ui: AppearanceUi, actions: SettingsActions) {
    val onePage = ui.question == null && ui.guide?.done != false
    if (onePage) {
        AppearanceOnePage(ui, actions)
        if (ui.guide != null) AppearanceGuideFooter(ui, actions)
        return
    }
    if (ui.guide != null) {
        AppearanceGuideHeader(ui, actions)
        if (!ui.guide.done && ui.question == null) {
            return
        }
    }
    AppearanceControls(ui, actions, ui.question)
}

/** Returning visits use Flutter's two columns from Expanded; each column keeps its own rules. */
@Composable
private fun AppearanceOnePage(ui: AppearanceUi, actions: SettingsActions) {
    if (LocalKitWindow.current.cls.isWide) {
        Row(Modifier.fillMaxWidth().testTag("appearance-columns")) {
            Column(Modifier.weight(1f)) {
                AppearanceSection(ui, actions, AppearanceGroup.Colours, first = true)
                AppearanceSection(ui, actions, AppearanceGroup.Style, first = false)
            }
            Column(Modifier.weight(1f)) {
                AppearanceSection(ui, actions, AppearanceGroup.Background, first = true)
                AppearanceSection(ui, actions, AppearanceGroup.Comfort, first = false)
            }
        }
    } else {
        Column {
            listOf(
                    AppearanceGroup.Colours,
                    AppearanceGroup.Style,
                    AppearanceGroup.Background,
                    AppearanceGroup.Comfort,
                )
                .forEachIndexed { index, group ->
                    AppearanceSection(ui, actions, group, first = index == 0)
                }
        }
    }
}

@Composable
private fun ColumnScope.AppearanceSection(
    ui: AppearanceUi,
    actions: SettingsActions,
    group: AppearanceGroup,
    first: Boolean,
) {
    if (group == AppearanceGroup.Style && !ui.styles) return
    if (!first) {
        Spacer(Modifier.height(Space.S6.dp))
        KitDivider()
    }
    if ((group == AppearanceGroup.Style || group == AppearanceGroup.Background) && ui.heldBySaver) {
        AppearanceQuestionHeading(
            appearanceGroupWords(group),
            if (first) Space.S2.dp else Space.S6.dp,
        )
        KitGroupNote(
            stringResource(
                if (group == AppearanceGroup.Style) Res.string.appearanceSaverHoldsStyle
                else Res.string.appearanceSaverHoldsBackground
            )
        )
        return
    }
    val supported =
        setOf(
            AppearanceQuestion.Mode,
            AppearanceQuestion.Theme,
            AppearanceQuestion.AccentColour,
            AppearanceQuestion.Style,
            AppearanceQuestion.Shape,
            AppearanceQuestion.Look,
            AppearanceQuestion.Strength,
            AppearanceQuestion.Text,
            AppearanceQuestion.Motion,
        )
    group.questions
        .filter { it in supported }
        .forEachIndexed { index, question ->
            AppearanceControls(
                ui,
                actions,
                question,
                onePage = true,
                top = if (first && index == 0) Space.S2.dp else Space.S6.dp,
            )
        }
}

@Composable
private fun ColumnScope.AppearanceControls(
    ui: AppearanceUi,
    actions: SettingsActions,
    question: AppearanceQuestion?,
    onePage: Boolean = false,
    top: Dp = Space.S6.dp,
) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    if (question == null || question == AppearanceQuestion.Mode) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskMode),
            question,
            onePage,
            top,
        )
        Box(gutter) {
            KitSegmented(
                segments =
                    AppearanceMode.entries.map {
                        KitSegment(
                            it,
                            modeWords(it),
                            icon =
                                when (it) {
                                    AppearanceMode.System -> KitIcons.systemTheme
                                    AppearanceMode.Light -> KitIcons.lightMode
                                    AppearanceMode.Dark -> KitIcons.darkMode
                                    AppearanceMode.Saver -> KitIcons.leaf
                                },
                        )
                    },
                tall = true,
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

    if (question == null || question == AppearanceQuestion.Theme) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskTheme),
            question,
            onePage,
            top,
        )
        val guided = !onePage
        val columns = if (guided) 2 else 3 // F: lib/ui/screens/settings/appearance_screen.dart:904
        val base = if (guided) 84 else 64 // F: lib/ui/screens/settings/appearance_screen.dart:905
        val scaleHeight =
            30 * // F: lib/ui/screens/settings/appearance_screen.dart:905
                LocalDensity.current
                    .fontScale // F: lib/ui/screens/settings/appearance_screen.dart:905
        val picks =
            if (!guided && ui.seeAllCount > 0)
                ui.packs.take(6) // F: lib/ui/screens/settings/appearance_screen.dart:896
            else ui.packs // F: lib/ui/screens/settings/appearance_screen.dart:896
        val shown = if (ui.pack in picks || picks.isEmpty()) picks else picks.dropLast(1) + ui.pack
        Box(gutter) {
            AppearanceGrid(shown, columns, (base + scaleHeight).dp) { pack ->
                KitThemeOptionCard(
                    pack = pack,
                    label = packName(pack),
                    selected = ui.pack == pack,
                    onSelect = { actions.onPack(pack) },
                    modifier = Modifier.testTag("appearance-pack-${pack.name}"),
                )
            }
        }
        if (ui.seeAllCount > 0) {
            KitButton(
                stringResource(Res.string.appearanceSeeAllThemes, ui.seeAllCount),
                actions::onSeeAllPacks,
                gutter.padding(top = Space.S3.dp).testTag("appearance-see-all"),
                role = KitButtonRole.Secondary,
            )
        }
    }
    if (question == null || question == AppearanceQuestion.AccentColour) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskAccent),
            question,
            onePage,
            top,
        )
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
        KitGroupNote(stringResource(Res.string.appearanceAccentMatchNote, packName(ui.pack)))
    }

    if (question == null || question == AppearanceQuestion.Style) {
        // A style the app does not draw would be a choice that changes nothing: listed only where
        // it is
        // drawn.
        if (ui.styles) {
            AppearanceControlHeading(
                stringResource(Res.string.appearanceAskStyle),
                question,
                onePage,
                top,
            )
            val styleHeld =
                if (ui.heldBySaver)
                    KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsStyle))
                else KitEnabled.Enabled
            Box(gutter) {
                val columns = 2 // F: lib/ui/screens/settings/appearance_screen.dart:973
                AppearanceGrid(SurfaceStyle.entries, columns) { style ->
                    KitStyleOptionCard(
                        style = Style.valueOf(style.name),
                        label = styleWords(style),
                        selected = style == ui.style,
                        enabled = styleHeld.isEnabled,
                        disabledReason = styleHeld.reason,
                        onSelect = { actions.onStyle(style) },
                        modifier = Modifier.testTag("appearance-style-${style.name}"),
                    )
                }
            }
            if (ui.heldBySaver) KitGroupNote(stringResource(Res.string.appearanceSaverHoldsStyle))
        }
    }
    if ((question == null || question == AppearanceQuestion.Shape) && ui.styles) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskShape),
            question,
            onePage,
            top,
        )
        val held =
            if (ui.heldBySaver)
                KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsStyle))
            else KitEnabled.Enabled
        AppearanceKnobLabel(stringResource(Res.string.appearanceFineTuneCorners))
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
        AppearanceKnobLabel(stringResource(Res.string.appearanceShapeSpacing))
        Box(gutter) {
            KitSegmented(
                segments = Density.entries.map { KitSegment(it, spacingWords(it)) },
                selected = ui.spacing,
                semanticsLabel = stringResource(Res.string.appearanceShapeSpacing),
                modifier = Modifier.testTag("appearance-spacing"),
                enabled = held,
                onChange = actions::onSpacing,
            )
        }
        val defaults = StyleResolver.spec(Style.valueOf(ui.style.name)).tokens.defaults
        KitGroupNote(
            stringResource(
                Res.string.appearanceShapeNote,
                styleWords(ui.style),
                cornersWords(defaults.corners),
                spacingWords(defaults.density),
            )
        )
        if (ui.fineTuned && !ui.heldBySaver) {
            KitButton(
                stringResource(Res.string.appearanceFineTuneReset, styleWords(ui.style)),
                actions::onResetFineTune,
                gutter.testTag("appearance-fine-tune-reset"),
                role = KitButtonRole.Tertiary,
                fullWidth = false,
            )
        }
    }

    if (question == null || question == AppearanceQuestion.Look) {
        // Background › Look (GLASS_REF appearance_screen.dart:1041-1083): Off and every look the app draws, each a
        // still frame behind a tiny conversation. A look the native app does not draw yet is not offered.
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskLook),
            question,
            onePage,
            top,
        )
        val held =
            if (ui.heldBySaver)
                KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsBackground))
            else KitEnabled.Enabled
        val scale = LocalDensity.current.fontScale
        Box(gutter) {
            AppearanceGrid(
                listOf(null, AuroraImage.ID),
                3, // FG: lib/ui/screens/settings/appearance_screen.dart:1055
                (136 + 44 * scale).dp, // FG: lib/ui/screens/settings/appearance_screen.dart:1055
            ) { look ->
                KitLookOptionCard(
                    look = look,
                    label =
                        stringResource(
                            if (look == null) Res.string.appearanceLookOff
                            else Res.string.rb34LiveStyleAuroraImage
                        ),
                    selected = ui.liveLook == look,
                    onSelect = { actions.onLiveLook(look) },
                    modifier = Modifier.fillMaxSize().testTag("appearance-look-${look ?: "off"}"),
                    enabled = held.isEnabled,
                    disabledReason = held.reason,
                )
            }
        }
        if (ui.heldBySaver) KitGroupNote(stringResource(Res.string.appearanceSaverHoldsBackground))
    }
    if (question == null || question == AppearanceQuestion.Strength) {
        // Background › How strong? (GLASS_REF appearance_screen.dart:1085-1117).
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskStrength),
            question,
            onePage,
            top,
        )
        if (ui.liveLook == null) {
            KitGroupNote(stringResource(Res.string.appearanceStrengthNeedsLook))
        } else {
            val held =
                if (ui.heldBySaver)
                    KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsBackground))
                else KitEnabled.Enabled
            Box(gutter) {
                KitSegmented(
                    segments = LiveStrength.entries.map { KitSegment(it, strengthWords(it)) },
                    selected = ui.liveStrength,
                    semanticsLabel = stringResource(Res.string.appearanceAskStrength),
                    modifier = Modifier.testTag("appearance-strength"),
                    enabled = held,
                    onChange = actions::onLiveStrength,
                )
            }
            KitGroupNote(stringResource(Res.string.appearanceIntensityHelp))
        }
    }

    if (question == null || question == AppearanceQuestion.Text) {
        // B-KIT-059: Default until chosen; applies at the app root at once, this page included.
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskText),
            question,
            onePage,
            top,
        )
        Box(gutter) {
            KitStepSlider(
                steps = AppTextSize.entries.map { KitSliderStep(it, textSizeWords(it)) },
                selected = ui.textSize,
                semanticsLabel = stringResource(Res.string.textSizeTitle),
                modifier = Modifier.testTag("appearance-text-size"),
                onChange = actions::onTextSize,
            )
        }
        KitGroupNote(stringResource(Res.string.appearanceTextHelp))
    }
    if (question == null || question == AppearanceQuestion.Motion) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskMotion),
            question,
            onePage,
            top,
        )
        val shownMotion = if (ui.heldBySaver) MotionLevel.Off else ui.motion
        val hold =
            if (ui.heldBySaver)
                KitEnabled.Disabled(stringResource(Res.string.appearanceSaverMotion))
            else KitEnabled.Enabled
        val note =
            when {
                ui.heldBySaver -> stringResource(Res.string.appearanceSaverMotion)
                ui.systemMotionOff && ui.motion != MotionLevel.Off ->
                    stringResource(Res.string.effectsMotionSystemOff)
                else -> null
            }
        Box(gutter) {
            if (onePage)
                KitSegmented(
                    segments =
                        MotionLevel.entries.map {
                            KitSegment(it, motionWords(it), icon = motionIcon(it))
                        },
                    tall = true,
                    selected = shownMotion,
                    semanticsLabel = stringResource(Res.string.appearanceAskMotion),
                    modifier = Modifier.testTag("appearance-motion"),
                    enabled = hold,
                    onChange = actions::onMotion,
                )
            else
                Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    MotionLevel.entries.forEach { level ->
                        KitPlainOptionCard(
                            label = motionWords(level),
                            caption = motionHint(level),
                            icon = motionIcon(level),
                            selected = shownMotion == level,
                            onSelect = { actions.onMotion(level) },
                            enabled = hold,
                            modifier = Modifier.testTag("appearance-motion-${level.name}"),
                        )
                    }
                }
        }
        if (onePage) KitGroupNote(note ?: motionHint(shownMotion))
        else note?.let { KitGroupNote(it) }
    }
}

@Composable
private fun AppearanceKnobLabel(title: String) {
    KitText(
        title,
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            .padding(top = Space.S3.dp, bottom = Space.S2.dp),
        role = TypeRole.Label,
    )
}

@Composable
private fun AppearanceControlHeading(
    title: String,
    question: AppearanceQuestion?,
    onePage: Boolean,
    top: Dp,
) {
    if (onePage || question == null) {
        AppearanceQuestionHeading(title, top)
        return
    }
    val changes =
        when (question) {
            AppearanceQuestion.Mode -> Res.string.appearanceChangesMode
            AppearanceQuestion.Theme -> Res.string.appearanceChangesTheme
            AppearanceQuestion.AccentColour -> Res.string.appearanceChangesAccent
            AppearanceQuestion.Style -> Res.string.appearanceChangesStyle
            AppearanceQuestion.Shape -> Res.string.appearanceChangesShape
            AppearanceQuestion.Look -> Res.string.appearanceChangesLook
            AppearanceQuestion.Strength -> Res.string.appearanceChangesStrength
            AppearanceQuestion.Text -> Res.string.appearanceChangesText
            AppearanceQuestion.Motion -> Res.string.appearanceChangesMotion
            AppearanceQuestion.Glow -> Res.string.appearanceChangesGlow
        }
    KitGuideQuestionHeading(
        title,
        stringResource(Res.string.kitGuideChanges),
        stringResource(changes),
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            .padding(top = Space.S4.dp, bottom = Space.S3.dp),
    )
}

/** The one-page question is a reading heading, not a muted section caption. */
@Composable
private fun AppearanceQuestionHeading(title: String, top: Dp = Space.S6.dp) {
    KitText(
        title,
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            .padding(top = top, bottom = Space.S3.dp)
            .semantics { heading() },
        role = TypeRole.RowTitle,
    )
}

@Composable
private fun <T> AppearanceGrid(
    choices: List<T>,
    columns: Int,
    cellHeight: Dp? = null,
    cell: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        choices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                for (index in 0 until columns) {
                    Box(
                        Modifier.weight(1f)
                            .then(if (cellHeight != null) Modifier.height(cellHeight) else Modifier)
                    ) {
                        row.getOrNull(index)?.let { cell(it) }
                    }
                }
            }
        }
    }
}

private fun motionIcon(level: MotionLevel) =
    when (level) {
        MotionLevel.Full -> KitIcons.sparkle
        MotionLevel.Calm -> KitIcons.waveform
        MotionLevel.Off -> KitIcons.stopCircle
    }

@Composable
private fun strengthWords(strength: LiveStrength): String =
    stringResource(
        when (strength) {
            LiveStrength.Subtle -> Res.string.liveIntensitySubtle
            LiveStrength.Normal -> Res.string.liveIntensityNormal
            LiveStrength.Bold -> Res.string.liveIntensityBold
        }
    )
