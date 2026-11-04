package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.TimelineUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatHistoryReadFailed
import io.github.eslamasabry.oc.ui.words.res.chatUiForkFromThisPrompt
import io.github.eslamasabry.oc.ui.words.res.chatUiMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiMessageTimeline
import io.github.eslamasabry.oc.ui.words.res.chatUiNoMatchingMessages
import io.github.eslamasabry.oc.ui.words.res.chatUiSearchMessages
import io.github.eslamasabry.oc.ui.words.res.chatUiYou
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.historyLoadOlder
import io.github.eslamasabry.oc.ui.words.res.historyLoadedOnly
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/** Real messages, newest first; a row jumps and a saved prompt has its own Fork action. */
@Composable
internal fun ChatTimelineSheet(timeline: TimelineUi?, actions: ChatActions) {
    KitSheet(
        visible = timeline != null,
        onDismissRequest = actions::closeTimeline,
        title = stringResource(Res.string.chatUiMessageTimeline),
        closeLabel = stringResource(Res.string.kitSheetDismiss),
    ) {
        if (timeline == null) return@KitSheet
        val field = rememberTextFieldState(timeline.query)
        val mask = rememberKitMask()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp).testTag("timeline-sheet"),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            KitField(
                field,
                stringResource(Res.string.chatUiSearchMessages),
                Modifier.testTag("timeline-search"),
                onTextChanged = actions::timelineQuery,
            )
            if (timeline.partial) {
                KitText(stringResource(Res.string.historyLoadedOnly))
                KitButton(
                    stringResource(Res.string.historyLoadOlder),
                    actions::loadOlder,
                    Modifier.testTag("timeline-load-older"),
                    role = KitButtonRole.Tertiary,
                    working = timeline.loadingOlder,
                )
            }
            timeline.olderFailure?.let { HistoryReadFailure(it, actions::loadOlder) }
            if (timeline.entries.isEmpty())
                KitText(stringResource(Res.string.chatUiNoMatchingMessages))
        }
        for (entry in timeline.entries) {
            KitRow(
                title = mask(entry.preview.ifBlank { stringResource(Res.string.chatUiMessage) }),
                supporting =
                    listOfNotNull(
                            if (entry.prompt) stringResource(Res.string.chatUiYou) else "OpenCode",
                            entry.createdMs?.let {
                                Instant.fromEpochMilliseconds(it)
                                    .toString()
                                    .replace('T', ' ')
                                    .removeSuffix("Z")
                            },
                        )
                        .joinToString(" · "),
                trailing =
                    if (entry.forkable)
                        KitRowTrailing.Action(
                            KitLineAction(
                                stringResource(Res.string.chatUiForkFromThisPrompt),
                                { actions.timelineSelect(entry.messageId, fork = true) },
                                testTag = "timeline-fork-${entry.messageId}",
                            )
                        )
                    else KitRowTrailing.None,
                modifier = Modifier.testTag("timeline-row-${entry.messageId}"),
                onClick = { actions.timelineSelect(entry.messageId) },
            )
        }
    }
}

@Composable
internal fun HistoryReadFailure(failure: Failure, retry: () -> Unit) {
    KitErrorNotice(
        message = stringResource(Res.string.chatHistoryReadFailed),
        failure = failure,
        retry = KitLineAction(stringResource(Res.string.commonRetry), retry),
        modifier = Modifier.testTag("transcript-find-older-error"),
    )
}
