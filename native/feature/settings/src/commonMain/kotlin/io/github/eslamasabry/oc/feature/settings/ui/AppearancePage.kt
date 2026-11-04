package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
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
 * I04, one page (plan 06 K4): light or dark, the colour pack (most used first, "See all 31 themes"
 * for the rest), the five styles and Motion. Every choice applies at once (no Apply). Saver holds
 * Style and Motion and says so. The guide offers the controls the host actually draws. Its record
 * retains every migrated Flutter question.
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
    }

    if (ui.question == null || ui.question == AppearanceQuestion.Style) {
        // A style the app does not draw would be a choice that changes nothing: listed only where
        // it is drawn.
        if (ui.styles) {
            KitSectionLabel(stringResource(Res.string.appearanceAskStyle))
            val styleHeld =
                if (ui.heldBySaver)
                    KitEnabled.Disabled(stringResource(Res.string.appearanceSaverHoldsStyle))
                else KitEnabled.Enabled
            KitChoiceList(
                choices =
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
            if (ui.heldBySaver) KitGroupNote(stringResource(Res.string.appearanceSaverHoldsStyle))
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

    if (ui.question == null || ui.question == AppearanceQuestion.Text) {
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
    if (ui.guide != null) AppearanceGuideFooter(ui, actions)
}
