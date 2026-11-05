package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.words.res.chatUiJumpAnywhereForkRestoresAPromptFor
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
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

/** Real messages, newest first; a row jumps and a saved prompt has its own Fork action. */
@Composable
internal fun ChatTimelineSheet(timeline: TimelineUi?, actions: ChatActions, nowMs: Long = 0) {
    KitSheet(
        visible = timeline != null,
        onDismissRequest = actions::closeTimeline,
        title = stringResource(Res.string.chatUiMessageTimeline),
        closeLabel = stringResource(Res.string.kitSheetDismiss),
        subtitle = if (timeline?.entries?.any { it.forkable } == true) stringResource(Res.string.chatUiJumpAnywhereForkRestoresAPromptFor) else null,
    ) {
        if (timeline == null) return@KitSheet
        val field = rememberTextFieldState(timeline.query)
        val mask = rememberKitMask()
        val platform = LocalKitPlatform.current
        // Flutter filters every keystroke; the search field settles only its announcement.
        LaunchedEffect(field, actions) {
            snapshotFlow { field.text.toString() }.drop(1).collect(actions::timelineQuery)
        }
        Column(
            Modifier.fillMaxWidth().testTag("timeline-sheet"),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitSearchField(
                label = stringResource(Res.string.chatUiSearchMessages),
                state = field,
                modifier = Modifier.testTag("timeline-search"),
                onSearch = {},
                resultCount = if (timeline.query.isBlank()) null else timeline.entries.size,
                partial = timeline.partial,
            )
            if (timeline.partial && timeline.olderFailure == null) {
                KitNotice(
                    message = stringResource(Res.string.historyLoadedOnly),
                    icon = KitIcons.history,
                    actions = listOf(KitLineAction(
                        stringResource(Res.string.historyLoadOlder), actions::loadOlder,
                        enabled = !timeline.loadingOlder,
                        disabledReason = if (timeline.loadingOlder) stringResource(Res.string.historyLoadedOnly) else null,
                        testTag = "timeline-load-older",
                    )),
                )
            }
            timeline.olderFailure?.let { HistoryReadFailure(it, actions::loadOlder) }
            if (timeline.entries.isEmpty()) {
                if (timeline.query.isNotBlank() && !timeline.partial) {
                    KitSearchNoMatch(timeline.query, { field.edit { replace(0, length, "") }; actions.timelineQuery("") }, Modifier.testTag("timeline-no-match"))
                } else {
                    KitStateView(kind = KitStateKind.Empty, title = stringResource(Res.string.chatUiNoMatchingMessages), icon = KitIcons.history, modifier = Modifier.testTag("timeline-empty"))
                }
            }
        }
        if (timeline.entries.isNotEmpty()) KitRowGroup {
        for (entry in timeline.entries) {
            KitRow(
                title = mask(entry.preview.ifBlank { stringResource(Res.string.chatUiMessage) }),
                titleMaxLines = 2, // F: lib/ui/screens/chat/timeline_sheet.dart:242
                leading = KitRowLeading.Glyph(if (entry.prompt) KitIcons.person else KitIcons.sparkle),
                supporting =
                    listOfNotNull(
                            if (entry.prompt) stringResource(Res.string.chatUiYou) else "OpenCode",
                            entry.createdMs?.let { platform.messageTime(it, nowMs) },
                        )
                        .joinToString(" · "),
                trailing =
                    if (entry.forkable)
                        KitRowTrailing.IconAction(
                            KitLineAction(
                                stringResource(Res.string.chatUiForkFromThisPrompt),
                                { actions.timelineSelect(entry.messageId, fork = true) },
                                testTag = "timeline-fork-${entry.messageId}",
                                icon = KitIcons.branch,
                            )
                        )
                    else KitRowTrailing.None,
                modifier = Modifier.testTag("timeline-row-${entry.messageId}"),
                onClick = { actions.timelineSelect(entry.messageId) },
            )
        }
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
