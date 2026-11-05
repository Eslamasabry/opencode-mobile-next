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
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
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
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)

    if (ui.guide != null) {
        AppearanceGuideHeader(ui, actions)
        if (!ui.guide.done && ui.question == null) {
            AppearanceGuideFooter(ui, actions)
            return
        }
    }
    if (ui.question == null || ui.question == AppearanceQuestion.Mode) {
        AppearanceQuestionHeading(stringResource(Res.string.appearanceAskMode))
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

    if (ui.question == null || ui.question == AppearanceQuestion.Theme) {
        AppearanceQuestionHeading(stringResource(Res.string.appearanceAskTheme))
        val guided = ui.question != null
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

        AppearanceQuestionHeading(stringResource(Res.string.appearanceAskAccent))
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
        // A style the app does not draw would be a choice that changes nothing: listed only where
        // it is
        // drawn.
        if (ui.styles) {
            AppearanceQuestionHeading(stringResource(Res.string.appearanceAskStyle))
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

            AppearanceQuestionHeading(stringResource(Res.string.appearanceAskShape))
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
                    title =
                        stringResource(Res.string.appearanceFineTuneReset, styleWords(ui.style)),
                    modifier = Modifier.testTag("appearance-fine-tune-reset"),
                    onClick = actions::onResetFineTune,
                )
            }
        }
    }

    if (ui.question == null || ui.question == AppearanceQuestion.Text) {
        // B-KIT-059: Default until chosen; applies at the app root at once, this page included.
        AppearanceQuestionHeading(stringResource(Res.string.textSizeTitle))
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
    if (ui.question == null || ui.question == AppearanceQuestion.Motion) {
        AppearanceQuestionHeading(stringResource(Res.string.appearanceAskMotion))
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

    if (ui.guide != null) AppearanceGuideFooter(ui, actions)
}

/** The one-page question is a reading heading, not a muted section caption. */
@Composable
private fun AppearanceQuestionHeading(title: String) {
    KitText(
        title,
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            .padding(top = Space.S6.dp, bottom = Space.S3.dp)
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
