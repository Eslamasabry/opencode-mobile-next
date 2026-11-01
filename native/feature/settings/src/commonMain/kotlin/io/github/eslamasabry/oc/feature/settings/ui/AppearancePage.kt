package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
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
import org.jetbrains.compose.resources.stringResource

/**
 * I04, one page (plan 06 K4): light or dark, the colour pack (most used first, "See all 31 themes" for the rest), the
 * five styles and Motion. Every choice applies at once (no Apply). Saver holds Style and Motion and says so.
 * Fine-tune, accent, text size, glow and the guided first visit are S4-APPEAR's.
 */
@Composable
internal fun ColumnScope.AppearanceBody(ui: AppearanceUi, actions: SettingsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)

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
}
