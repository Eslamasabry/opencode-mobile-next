package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGroup
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.guide.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource
import io.github.eslamasabry.oc.ui.words.res.appearanceStrengthNoLook
import io.github.eslamasabry.oc.ui.words.res.appearanceLookOff
import io.github.eslamasabry.oc.ui.kit.live.LiveLooks

import androidx.compose.runtime.MutableIntState

/**
 * The tour's bar (owner 2026-11-10, Appearance redesign): a new person sees the same page as everyone, with this bar
 * at its foot. It says which tab of how many this is and leads to the next one; on the last it finishes the tour. No
 * landing page and no second row of chips: the page's own tabs are the map. Everything saves as it is picked, so
 * leaving at any point loses nothing.
 */
@Composable
internal fun AppearanceGuideNext(ui: AppearanceUi, actions: SettingsActions, tab: MutableIntState) {
    val groups = appearanceTabs(ui)
    val at = tab.intValue.coerceIn(groups.indices)
    val next = groups.getOrNull(at + 1)
    KitGuideNextBar(
        count = stringResource(Res.string.appearanceGuideCountAt, at + 1, groups.size),
        label =
            if (next == null) stringResource(Res.string.appearanceGuideFinish)
            else stringResource(Res.string.appearanceGuideNext, appearanceGroupWords(next)),
        onNext = { if (next == null) actions.onAppearanceSkip() else tab.intValue = at + 1 },
        ready = true,
    )
}

@Composable
internal fun appearanceGroupWords(group: AppearanceGroup): String =
    stringResource(
        when (group) {
            AppearanceGroup.Colours -> Res.string.appearanceGroupColours
            AppearanceGroup.Style -> Res.string.appearanceGroupStyle
            AppearanceGroup.Background -> Res.string.appearanceGroupBackground
            AppearanceGroup.Comfort -> Res.string.appearanceGroupComfort
        }
    )

@Composable
internal fun appearanceQuestionWords(question: AppearanceQuestion): String =
    stringResource(
        when (question) {
            AppearanceQuestion.Mode -> Res.string.appearanceChipMode
            AppearanceQuestion.Theme -> Res.string.appearanceChipTheme
            AppearanceQuestion.AccentColour -> Res.string.appearanceChipAccent
            AppearanceQuestion.Style -> Res.string.appearanceChipStyle
            AppearanceQuestion.Shape -> Res.string.appearanceChipShape
            AppearanceQuestion.Look -> Res.string.appearanceChipLook
            AppearanceQuestion.Strength -> Res.string.appearanceChipStrength
            AppearanceQuestion.Text -> Res.string.appearanceChipText
            AppearanceQuestion.Motion -> Res.string.appearanceChipMotion
            AppearanceQuestion.Glow -> Res.string.appearanceChipGlow
        }
    )
