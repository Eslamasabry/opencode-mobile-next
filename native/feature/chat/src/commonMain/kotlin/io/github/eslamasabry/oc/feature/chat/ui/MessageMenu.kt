package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.MessageMenuUi
import io.github.eslamasabry.oc.feature.chat.presentation.TargetKind
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MessageMenu(
    menu: MessageMenuUi?,
    actions: ChatActions,
    modifier: Modifier,
    reads: Boolean = false,
    facts: TurnFactsUi = TurnFactsUi.Empty,
) {
    val teaching = LocalMessageTeaching.current
    var detailsTurn by remember { mutableStateOf<String?>(null) }
    val feel = menu?.actions?.contains(MessageAction.Share) == true
    val prompt = menu?.target?.kind == TargetKind.Prompt
    val items =
        menu
            ?.actions
            .orEmpty()
            .filter {
                // Only replies have Copy beside More. A prompt must keep its copy action here.
                (feel || prompt || it != MessageAction.Copy) &&
                    (reads || it != MessageAction.ReadAloud)
            }
            // S28 story 7: where the app reads replies, the same reply can be read with another voice.
            .flatMap {
                if (it == MessageAction.ReadAloud) listOf(it, MessageAction.ReadAloudOtherVoice) else listOf(it)
            }
            .map { action ->
                KitMenuItem(
                    label =
                        when (action) {
                            MessageAction.Copy -> stringResource(Res.string.chatUiCopyMessageText)
                            MessageAction.Share -> stringResource(Res.string.chatFeelShare)
                            MessageAction.SelectText ->
                                stringResource(Res.string.chatFeelSelectText)
                            MessageAction.EditAndResend ->
                                stringResource(Res.string.chatFeelEditAndResend)
                            MessageAction.ReadAloud ->
                                stringResource(Res.string.voiceNativeReadAloud)
                            MessageAction.ReadAloudOtherVoice ->
                                stringResource(Res.string.readAloudOtherVoice)
                            MessageAction.SendAgain ->
                                stringResource(Res.string.chatUiSendPromptAgain)
                            MessageAction.Fork ->
                                stringResource(Res.string.chatUiForkFromThisPrompt)
                            MessageAction.Changes -> stringResource(Res.string.chatUiChanges)
                            MessageAction.UndoFromHere -> stringResource(Res.string.revertFromHere)
                            MessageAction.Delete -> stringResource(Res.string.chatUiDeleteMessage)
                        },
                    onSelect = { menu?.let { actions.messageAction(it.target, action) } },
                    icon =
                        when (action) {
                            MessageAction.Copy -> KitIcons.copy
                            MessageAction.Share -> KitIcons.externalLink
                            MessageAction.SelectText -> KitIcons.text
                            MessageAction.EditAndResend -> KitIcons.edit
                            MessageAction.ReadAloud -> KitIcons.volume
                            MessageAction.ReadAloudOtherVoice -> KitIcons.speakUser
                            MessageAction.SendAgain -> KitIcons.send
                            MessageAction.Fork -> KitIcons.fork
                            MessageAction.Changes -> KitIcons.review
                            MessageAction.UndoFromHere -> KitIcons.history
                            MessageAction.Delete -> KitIcons.delete
                        },
                    destructive = action == MessageAction.Delete,
                )
            }
    val target = menu?.target
    val hasDetails =
        target != null &&
            (facts.reading.metadata[target.turnId] != null ||
                !facts.reading.switches[target.turnId].isNullOrEmpty() ||
                facts.results[target.turnId]?.timing != null)
    val detailItem =
        if (hasDetails)
            listOf(
                KitMenuItem(
                    label = stringResource(Res.string.kitDetails),
                    icon = KitIcons.info,
                    onSelect = {
                        detailsTurn = target?.turnId
                        teaching
                            ?.controller
                            ?.markUsed(
                                io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
                                    .MessageDetails
                            )
                        actions.closeMessageMenu()
                    },
                )
            )
        else emptyList()
    KitMenu(
        expanded = menu != null,
        items = detailItem + items,
        onDismissRequest = actions::closeMessageMenu,
        title = stringResource(Res.string.kitMessageActions),
        modifier = modifier.testTag("message-menu"),
    ) {}
    KitSheet(
        visible = detailsTurn != null,
        title = stringResource(Res.string.kitDetails),
        closeLabel = stringResource(Res.string.kitSheetClose),
        onDismissRequest = { detailsTurn = null },
    ) {
        val turn = detailsTurn
        val mask = rememberKitMask()
        // KitSheet already scrolls its body (N-38: a nested vertical scroll crashes).
        Column(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp)) {
            if (turn != null) {
                facts.reading.metadata[turn]?.let { TurnMetadata(it) }
                facts.reading.switches[turn].orEmpty().forEach { detail ->
                    val part = detail.part
                    val kind = part.noticeMetadata["kind"] ?: part.toolName
                    val label =
                        stringResource(
                            when (kind) {
                                "model" -> Res.string.chatUiModel
                                "agent" -> Res.string.chatUiAgent
                                else -> Res.string.chatUiMoved
                            }
                        )
                    KitQuietLine("$label → ${mask(part.text)}")
                }
                facts.results[turn]?.timing?.let { KitQuietLine(timingWords(it)) }
            }
        }
    }
}
