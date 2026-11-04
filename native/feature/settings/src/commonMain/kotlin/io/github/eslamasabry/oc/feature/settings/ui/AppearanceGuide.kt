package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGroup
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.chip.KitStepChip
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appearanceChipAccent
import io.github.eslamasabry.oc.ui.words.res.appearanceChipGlow
import io.github.eslamasabry.oc.ui.words.res.appearanceChipLook
import io.github.eslamasabry.oc.ui.words.res.appearanceChipMode
import io.github.eslamasabry.oc.ui.words.res.appearanceChipMotion
import io.github.eslamasabry.oc.ui.words.res.appearanceChipShape
import io.github.eslamasabry.oc.ui.words.res.appearanceChipStrength
import io.github.eslamasabry.oc.ui.words.res.appearanceChipStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceChipText
import io.github.eslamasabry.oc.ui.words.res.appearanceChipTheme
import io.github.eslamasabry.oc.ui.words.res.appearanceGroupBackground
import io.github.eslamasabry.oc.ui.words.res.appearanceGroupColours
import io.github.eslamasabry.oc.ui.words.res.appearanceGroupComfort
import io.github.eslamasabry.oc.ui.words.res.appearanceGroupStyle
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideAgain
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideAllSet
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideContinue
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideCountAt
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideCountIntro
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideCountSeen
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideFinish
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideNext
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideResume
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideSkip
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideStart
import io.github.eslamasabry.oc.ui.words.res.appearanceGuideStartButton
import io.github.eslamasabry.oc.ui.words.res.stateAppearanceChangedLast
import io.github.eslamasabry.oc.ui.words.res.stateAppearanceOverview
import org.jetbrains.compose.resources.stringResource

/**
 * Visible group and question chips: every gesture-free path stays available during and after the
 * guide.
 */
@Composable
internal fun ColumnScope.AppearanceGuideHeader(ui: AppearanceUi, actions: SettingsActions) {
    val record = ui.guide ?: return
    val question = ui.question
    val questions = ui.questions
    val groups = questions.map { it.group }.distinct()
    KitFilterChips {
        groups.forEach { group ->
            KitChip(
                label = appearanceGroupWords(group),
                kind = KitChipKind.Action,
                selected = question?.group == group,
                onClick = { actions.onAppearanceGroup(group) },
            )
        }
    }
    if (question != null) {
        KitFilterChips {
            questions
                .filter { it.group == question.group }
                .forEach { item ->
                    KitStepChip(
                        label = appearanceQuestionWords(item),
                        selected = question == item,
                        checked = !record.done && item in record.seen,
                        onClick = { actions.onAppearanceQuestion(item) },
                    )
                }
        }
        if (!record.done)
            KitGroupNote(
                stringResource(
                    Res.string.appearanceGuideCountAt,
                    questions.indexOf(question) + 1,
                    questions.size,
                )
            )
    } else if (!record.done) {
        KitGroupNote(
            stringResource(
                if (record.seen.isEmpty()) Res.string.appearanceGuideStart
                else Res.string.appearanceGuideResume
            )
        )
        KitGroupNote(
            if (record.seen.isEmpty())
                stringResource(Res.string.appearanceGuideCountIntro, questions.size)
            else
                stringResource(
                    Res.string.appearanceGuideCountSeen,
                    questions.count { it in record.seen },
                    questions.size,
                )
        )
    } else KitGroupNote(stringResource(Res.string.appearanceGuideAllSet))
    if (record.done && question != null)
        KitButton(
            stringResource(Res.string.stateAppearanceOverview),
            { actions.onAppearanceQuestion(null) },
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            role = KitButtonRole.Tertiary,
        )
    record.last?.let {
        KitGroupNote(
            stringResource(Res.string.stateAppearanceChangedLast, appearanceQuestionWords(it))
        )
    }
}

@Composable
internal fun ColumnScope.AppearanceGuideFooter(ui: AppearanceUi, actions: SettingsActions) {
    val record = ui.guide ?: return
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    if (record.done) {
        KitButton(
            stringResource(Res.string.appearanceGuideAgain),
            actions::onAppearanceAgain,
            gutter,
            role = KitButtonRole.Tertiary,
        )
        return
    }
    val from = ui.question ?: AppearanceQuestion.entries.lastOrNull { it in record.seen }
    val next = AppearanceQuestion.after(from, ui.heldBySaver, ui.questions)
    val label =
        when {
            next == null -> stringResource(Res.string.appearanceGuideFinish)
            ui.question != null ->
                stringResource(Res.string.appearanceGuideNext, appearanceQuestionWords(next))
            record.seen.isEmpty() -> stringResource(Res.string.appearanceGuideStartButton)
            else ->
                stringResource(Res.string.appearanceGuideContinue, appearanceQuestionWords(next))
        }
    KitButton(label, actions::onAppearanceNext, gutter.testTag("appearance-guide-next"))
    KitButton(
        stringResource(Res.string.appearanceGuideSkip),
        actions::onAppearanceSkip,
        gutter,
        role = KitButtonRole.Tertiary,
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
