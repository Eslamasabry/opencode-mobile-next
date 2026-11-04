package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.voice.presentation.TeachingActions
import io.github.eslamasabry.oc.feature.voice.presentation.TeachingUi
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Every registered tip is visible in Help and replay consumes no automatic-offer count. */
@Composable
public fun TeachingScreen(state: TeachingUi, actions: TeachingActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.tipsTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(Modifier.testTag("help-tips")) {
            if (!state.ready) {
                item {
                    if (state.failed)
                        KitNotice(
                            stringResource(Res.string.voiceConversationNativeTourLoadFailed),
                            actions =
                                listOf(
                                    KitLineAction(
                                        stringResource(Res.string.kitTryAgain),
                                        actions::retry,
                                    )
                                ),
                        )
                    else KitSkeletonRows()
                }
                return@LazyColumn
            }
            state.replay?.let { entry ->
                item(key = "replay") {
                    val last = entry.steps.isEmpty() || state.step >= entry.steps.lastIndex
                    KitNotice(
                        message =
                            teachingWords(
                                entry.steps.getOrNull(state.step)?.messageKey ?: entry.messageKey
                            ),
                        title = teachingWords(entry.titleKey),
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(
                                        if (last) Res.string.voiceConversationNativeTourDone
                                        else Res.string.voiceConversationNativeTourNext
                                    ),
                                    actions::next,
                                )
                            ),
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.e7VoiceUiNotNow),
                                actions::dismiss,
                            ),
                    )
                }
            }
            state.entries.forEach { entry ->
                item(key = entry.id.raw) {
                    KitRow(
                        title = teachingWords(entry.titleKey),
                        supporting = teachingWords(entry.messageKey),
                        supportingMaxLines = 4,
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.replay(entry.id) },
                    )
                }
            }
            item(key = "reset") {
                Column {
                    if (state.resetFailed)
                        KitNotice(stringResource(Res.string.voiceConversationNativeTourResetFailed))
                    KitButton(
                        stringResource(Res.string.voiceConversationNativeTourReset),
                        actions::reset,
                        role = KitButtonRole.Tertiary,
                        modifier = Modifier.testTag("tips-reset"),
                    )
                }
            }
        }
    }
}

@Composable
private fun teachingWords(key: String): String =
    stringResource(
        when (key) {
            "tipTerminalLinksTitle" -> Res.string.tipTerminalLinksTitle
            "tipTerminalLinks" -> Res.string.tipTerminalLinks
            "tipStopAfterStepTitle" -> Res.string.tipStopAfterStepTitle
            "tipStopAfterStep" -> Res.string.tipStopAfterStep
            "tipSendLaterTitle" -> Res.string.tipSendLaterTitle
            "tipSendLater" -> Res.string.tipSendLater
            "tipHoldMicTitle" -> Res.string.tipHoldMicTitle
            "tipHoldMic" -> Res.string.tipHoldMic
            "tourMakeItYoursTitle" -> Res.string.tourMakeItYoursTitle
            "tourMakeItYoursOffer" -> Res.string.tourMakeItYoursOffer
            "tourMakeItYoursColours" -> Res.string.tourMakeItYoursColours
            "tourMakeItYoursStyle" -> Res.string.tourMakeItYoursStyle
            "tourMakeItYoursBackground" -> Res.string.tourMakeItYoursBackground
            "tourMakeItYoursComfort" -> Res.string.tourMakeItYoursComfort
            "whatsNewTipsTitle" -> Res.string.whatsNewTipsTitle
            "whatsNewRowDetail" -> Res.string.whatsNewRowDetail
            "voiceConversationTitle" -> Res.string.voiceConversationTitle
            "voiceConversationNativeTourSpeak" -> Res.string.voiceConversationNativeTourSpeak
            "voiceConversationNativeTourReplies" -> Res.string.voiceConversationNativeTourReplies
            "voiceConversationNativeTourLeave" -> Res.string.voiceConversationNativeTourLeave
            else -> Res.string.tipsTitle
        }
    )
