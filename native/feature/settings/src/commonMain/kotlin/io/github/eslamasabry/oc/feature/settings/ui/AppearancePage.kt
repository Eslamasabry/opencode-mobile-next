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
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.color.Accents
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatchGrid
import io.github.eslamasabry.oc.ui.kit.choice.KitThemeOptionCard
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.guide.KitGuideQuestionHeading
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
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
 * samples. The existing guide record selects a question; Saver holds Style and Motion. Background,
 * glow and Glass selection need the presentation fields recorded in evidence/44.md.
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
                // Background belongs above Comfort once its state/actions exist; see evidence/44.
                AppearanceSection(ui, actions, AppearanceGroup.Comfort, first = true)
            }
        }
    } else {
        Column {
            listOf(AppearanceGroup.Colours, AppearanceGroup.Style, AppearanceGroup.Comfort)
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
    if (group == AppearanceGroup.Style && ui.heldBySaver) {
        AppearanceQuestionHeading(
            appearanceGroupWords(group),
            if (first) Space.S2.dp else Space.S6.dp,
        )
        KitGroupNote(stringResource(Res.string.appearanceSaverHoldsStyle))
        return
    }
    val supported =
        setOf(
            AppearanceQuestion.Mode,
            AppearanceQuestion.Theme,
            AppearanceQuestion.AccentColour,
            AppearanceQuestion.Style,
            AppearanceQuestion.Shape,
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
        if (ui.fineTuned && !ui.heldBySaver) {
            KitRow(
                title = stringResource(Res.string.appearanceFineTuneReset, styleWords(ui.style)),
                modifier = Modifier.testTag("appearance-fine-tune-reset"),
                onClick = actions::onResetFineTune,
            )
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
            KitSegmented(
                segments = AppTextSize.entries.map { KitSegment(it, textSizeWords(it)) },
                selected = ui.textSize,
                semanticsLabel = stringResource(Res.string.textSizeTitle),
                modifier = Modifier.testTag("appearance-text-size"),
                onChange = actions::onTextSize,
            )
        }
    }
    if (question == null || question == AppearanceQuestion.Motion) {
        AppearanceControlHeading(
            stringResource(Res.string.appearanceAskMotion),
            question,
            onePage,
            top,
        )
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
