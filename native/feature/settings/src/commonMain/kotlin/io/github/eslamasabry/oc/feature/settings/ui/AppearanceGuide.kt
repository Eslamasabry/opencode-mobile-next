package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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

/**
 * Visible group and question chips: every gesture-free path stays available during and after the
 * guide.
 */
@Composable
internal fun ColumnScope.AppearanceGuideHeader(ui: AppearanceUi, actions: SettingsActions) {
    val record = ui.guide ?: return
    val question = ui.question
    val questions = ui.questions
    val next =
        AppearanceQuestion.after(question ?: record.seen.lastOrNull(), ui.heldBySaver, questions)
    if (question == null) {
        val groups = questions.map { it.group }.distinct()
        KitGuideRail(
            items =
                groups.map { group ->
                    KitGuideItem(
                        group.name,
                        appearanceGroupWords(group),
                        appearanceGroupValue(ui, group),
                        done =
                            group.questions
                                .filter { it in questions }
                                .all { it in record.seen || (ui.heldBySaver && it.heldBySaver) },
                        next = !record.done && next?.group == group,
                        held = ui.heldBySaver && group == AppearanceGroup.Style,
                    )
                },
            onItem = { id -> actions.onAppearanceGroup(AppearanceGroup.valueOf(id)) },
        )
        KitText(
            stringResource(
                if (record.seen.isEmpty()) Res.string.appearanceGuideStart
                else Res.string.appearanceGuideResume
            ),
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
                .padding(top = Space.S4.dp),
            role = TypeRole.Secondary,
            tone = TextTone.Secondary, // F: lib/ui/screens/settings/appearance_screen.dart:658
        )
        // Skip is a row that acts in place, so its words take the accent (appearance_screen.dart:661-670); it is
        // offered only before the first question, as in Flutter.
        if (record.seen.isEmpty()) {
            Spacer(Modifier.height(Space.S2.dp)) // F: lib/ui/screens/settings/appearance_screen.dart:663
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.appearanceGuideSkip),
                    titleAccent = true,
                    onClick = actions::onAppearanceSkip,
                )
            }
        }
    } else {
        KitGuideRail(
            items =
                questions
                    .filter { it.group == question.group }
                    .map { item ->
                        KitGuideItem(
                            item.name,
                            appearanceQuestionWords(item),
                            appearanceQuestionValue(ui, item),
                            done = item != question && item in record.seen,
                            next = item == next,
                            held = ui.heldBySaver && item.heldBySaver,
                        )
                    },
            selected = question.name,
            group = appearanceGroupWords(question.group),
            // P-027: no "← All" crumb; the bar's Back (and system Back) returns to all groups (SettingsPresenter.onBack).
            onItem = { id -> actions.onAppearanceQuestion(AppearanceQuestion.valueOf(id)) },
        )
    }
}

/** The guide bar stays visible while its question scrolls. */
@Composable
internal fun AppearanceGuideNext(ui: AppearanceUi, actions: SettingsActions) {
    val record = ui.guide ?: return
    val question = ui.question
    val from = question ?: AppearanceQuestion.entries.lastOrNull { it in record.seen }
    val next = AppearanceQuestion.after(from, ui.heldBySaver, ui.questions)
    val count =
        when {
            question != null ->
                stringResource(
                    Res.string.appearanceGuideCountAt,
                    ui.questions.indexOf(question) + 1,
                    ui.questions.size,
                )
            record.seen.isEmpty() ->
                stringResource(Res.string.appearanceGuideCountIntro, ui.questions.size)
            else -> {
                // The overview lists groups, so it counts groups: one is seen when all its questions are.
                val groups = ui.questions.groupBy { it.group }
                stringResource(
                    Res.string.appearanceGuideCountSeen,
                    groups.count { (_, asked) -> asked.all { it in record.seen } },
                    groups.size,
                )
            }
        }
    val label =
        when {
            next == null -> stringResource(Res.string.appearanceGuideFinish)
            question == null && record.seen.isEmpty() ->
                stringResource(Res.string.appearanceGuideStartButton)
            question == null ->
                stringResource(Res.string.appearanceGuideContinue, appearanceGroupWords(next.group))
            else ->
                stringResource(
                    Res.string.appearanceGuideNext,
                    if (next.group == question.group) appearanceQuestionWords(next)
                    else appearanceGroupWords(next.group),
                )
        }
    KitGuideNextBar(count, label, actions::onAppearanceNext, ready = question == null)
}

@Composable
private fun appearanceGroupValue(ui: AppearanceUi, group: AppearanceGroup): String =
    when (group) {
        AppearanceGroup.Colours ->
            stringResource(Res.string.appearanceGroupPair, modeWords(ui.mode), packName(ui.pack))
        AppearanceGroup.Style ->
            if (ui.heldBySaver) stringResource(Res.string.appearanceOffInSaver)
            else styleWords(ui.style)
        AppearanceGroup.Comfort ->
            stringResource(
                Res.string.appearanceGroupPair,
                textSizeWords(ui.textSize),
                stringResource(
                    when (if (ui.heldBySaver) MotionLevel.Off else ui.motion) {
                        MotionLevel.Full -> Res.string.appearanceMotionPhraseFull
                        MotionLevel.Calm -> Res.string.appearanceMotionPhraseCalm
                        MotionLevel.Off -> Res.string.appearanceMotionPhraseOff
                    }
                ),
            )
        // No Background group is offered until its questions are exposed by presentation.
        AppearanceGroup.Background -> ""
    }

@Composable
private fun appearanceQuestionValue(ui: AppearanceUi, question: AppearanceQuestion): String {
    if (ui.heldBySaver && question.heldBySaver)
        return stringResource(Res.string.appearanceOffInSaver)
    return when (question) {
        AppearanceQuestion.Mode -> modeWords(ui.mode)
        AppearanceQuestion.Theme -> packName(ui.pack)
        AppearanceQuestion.AccentColour ->
            ui.accent?.let { accentWords(it) }
                ?: stringResource(Res.string.appearanceAccentMatchShort)
        AppearanceQuestion.Style -> styleWords(ui.style)
        AppearanceQuestion.Shape ->
            stringResource(
                Res.string.appearanceShapeValue,
                cornersWords(ui.corners),
                spacingWords(ui.spacing),
            )
        AppearanceQuestion.Text -> textSizeWords(ui.textSize)
        AppearanceQuestion.Motion -> motionWords(if (ui.heldBySaver) MotionLevel.Off else ui.motion)
        AppearanceQuestion.Look,
        AppearanceQuestion.Strength,
        AppearanceQuestion.Glow -> ""
    }
}

@Composable
internal fun ColumnScope.AppearanceGuideFooter(ui: AppearanceUi, actions: SettingsActions) {
    val record = ui.guide ?: return
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    if (record.done) {
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.appearanceGuideAgain),
                onClick = actions::onAppearanceAgain,
            )
        }
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
