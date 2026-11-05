package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingKind
import io.github.eslamasabry.oc.feature.voice.presentation.TeachingActions
import io.github.eslamasabry.oc.feature.voice.presentation.TeachingUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
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
        LazyColumn(
            Modifier.testTag("help-tips"),
            contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        ) {
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
            item(key = "intro") {
                KitText(
                    stringResource(Res.string.tipsIntro),
                    Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                    role = TypeRole.Secondary,
                )
            }
            state.replay
                ?.takeIf { it.kind == TeachingKind.Tour }
                ?.let { entry ->
                    item(key = "replay") {
                        val last = entry.steps.isEmpty() || state.step >= entry.steps.lastIndex
                        KitNotice(
                            message =
                                teachingWords(
                                    entry.steps.getOrNull(state.step)?.messageKey
                                        ?: entry.messageKey
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
            TeachingKind.entries.forEach { kind ->
                item(key = "group-${kind.name}") {
                    KitRowGroup(
                        label =
                            stringResource(
                                if (kind == TeachingKind.Tip) Res.string.tipsTipsLabel
                                else Res.string.tipsToursLabel
                            ),
                        leadingTiles = true,
                    ) {
                        state.entries
                            .filter { it.kind == kind }
                            .forEach { entry ->
                                KitTip(
                                    message = teachingWords(entry.messageKey),
                                    visible =
                                        kind == TeachingKind.Tip && state.replay?.id == entry.id,
                                    onDismissed = actions::dismiss,
                                ) {
                                    KitRow(
                                        title = teachingWords(entry.titleKey),
                                        leading =
                                            KitRowLeading.Glyph(
                                                if (kind == TeachingKind.Tour) KitIcons.playCircle
                                                else KitIcons.idea
                                            ),
                                        modifier = Modifier.testTag("tips-row-${entry.id.raw}"),
                                        trailing =
                                            KitRowTrailing.Action(
                                                KitLineAction(
                                                    stringResource(Res.string.tipsShowMe),
                                                    { actions.replay(entry.id) },
                                                )
                                            ),
                                    )
                                }
                            }
                    }
                }
            }
            item(key = "reset") {
                Column {
                    if (state.resetFailed)
                        KitNotice(stringResource(Res.string.voiceConversationNativeTourResetFailed))
                    Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                    KitRowGroup(leadingTiles = true) {
                        KitRow(
                            title = stringResource(Res.string.discoverShowTipsAgain),
                            supporting = stringResource(Res.string.discoverShowTipsSubtitle),
                            supportingMaxLines =
                                2, // F: lib/ui/screens/settings/tips_screen.dart:138
                            leading = KitRowLeading.Glyph(KitIcons.idea),
                            onClick = actions::reset,
                            modifier = Modifier.testTag("tips-reset"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun teachingWords(key: String): String =
    stringResource(
        when (key) {
            "terminal40TipTitle" -> Res.string.terminal40TipTitle
            "terminal40Tip" -> Res.string.terminal40Tip
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
