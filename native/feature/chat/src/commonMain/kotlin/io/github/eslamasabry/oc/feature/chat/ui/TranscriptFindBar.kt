package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.onFocusChanged
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitSheetHost
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.core.common.text.Numbers
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Find, by the message box (audit 2026-11-14, coordinator's decision): ONE row. The field with the match count at its
 * end, previous and next beside it, then Close. The phrase row folds away while Find is open, and no excerpt row: the
 * transcript brings the match into view. A notice (part of the history searched, a read that failed) shows only while
 * it is true.
 */
@Composable
internal fun TranscriptFindBar(find: FindUi, actions: ChatActions, modifier: Modifier = Modifier) {
    val field = rememberTextFieldState(find.query)
    val focus = remember { FocusRequester() }
    val navigationFocus = remember { FocusRequester() }
    // The phrase row gives Find its place while it is open.
    io.github.eslamasabry.oc.ui.kit.frame.KitFoldPhraseRow(true)
    // A query kept from before is selected for retyping only where keys type it (a hardware keyboard): on a touch
    // screen a selection brings its two handles, which hung across the field's border (audit tl2); the caret then
    // waits at the end of the words.
    val keys = androidx.compose.ui.platform.LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Keyboard
    LaunchedEffect(Unit) {
        field.edit { selection = if (keys) androidx.compose.ui.text.TextRange(0, length) else androidx.compose.ui.text.TextRange(length) }
    }
    // Find takes focus when it opens. Opened while a sheet or dialog is up or leaving (the kit keeps the page out of
    // focus while one shows), it asks again once the sheet has gone, unless its field has held focus uncovered since
    // (w4-focus2): then the person had moved on, and the kit hands back what they had.
    val sheets = LocalKitSheetHost.current
    val covered = sheets != null && (sheets.isShowing || sheets.isDialogShowing)
    val coveredNow by rememberUpdatedState(covered)
    val owned = remember { booleanArrayOf(false) }
    LaunchedEffect(covered) { if (!owned[0]) focus.requestFocus() }
    LaunchedEffect(find.query) {
        if (field.text.toString() != find.query) field.edit { replace(0, length, find.query) }
    }
    val none = stringResource(Res.string.transcriptFindNone)
    val matches = if (find.count == 0) none else pluralStringResource(
        Res.plurals.transcriptFindCount, find.count, find.count, find.current + 1,
    )
    // In the field: "2 of 7" (the words a screen reader hears say "matches" too); none: "No matches".
    val position =
        if (find.count == 0) none
        else stringResource(Res.string.transcriptFindPosition, Numbers.format(find.current + 1), Numbers.format(find.count))
    val older = KitLineAction(
        stringResource(Res.string.transcriptFindAll), { navigationFocus.requestFocus(); actions.searchAllHistory() },
        enabled = !find.loadingOlder,
        disabledReason = if (find.loadingOlder) stringResource(Res.string.chatLoadingConversation) else null,
        testTag = "transcript-find-older",
    )
    Column(
        modifier.fillMaxWidth()
            .padding(start = Space.S4.dp, end = Space.S1.dp, top = Space.S2.dp, bottom = Space.S2.dp)
            .testTag("transcript-find-bar")
            .focusRequester(navigationFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && field.text.isEmpty()) {
                    actions.find(null)
                    true
                } else false
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val typed = field.text.isNotBlank()
            KitSearchField(
                label = stringResource(Res.string.transcriptFindHint),
                state = field,
                onSearch = { actions.find(it) },
                onSubmit = { navigationFocus.requestFocus(); actions.findNext() },
                modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { if (it.hasFocus && !coveredNow) owned[0] = true }
                    .testTag("transcript-find-input"),
                // One X on the row: Close. The count sits where Clear was.
                clearable = false,
                trailing =
                    if (!typed) null
                    else ({
                        KitText(
                            position,
                            Modifier.padding(end = Space.S3.dp)
                                .testTag("transcript-find-count")
                                .semantics {
                                    contentDescription = matches
                                    liveRegion = LiveRegionMode.Polite
                                },
                            role = TypeRole.Secondary,
                            tone = TextTone.Secondary,
                            maxLines = 1,
                        )
                    }),
            )
            if (typed) {
                val enabled = if (find.count > 0) KitEnabled.Enabled else KitEnabled.Disabled(none)
                KitIconButton(KitIcons.chevronUp, stringResource(Res.string.transcriptFindPrevious), { navigationFocus.requestFocus(); actions.findPrevious() }, Modifier.testTag("transcript-find-previous"), enabled = enabled)
                KitIconButton(KitIcons.chevronDown, stringResource(Res.string.transcriptFindNext), { navigationFocus.requestFocus(); actions.findNext() }, Modifier.testTag("transcript-find-next"), enabled = enabled)
            }
            KitIconButton(KitIcons.close, stringResource(Res.string.transcriptFindClose), { actions.find(null) }, Modifier.testTag("transcript-find-close"))
        }
        when {
            find.olderFailure != null -> HistoryReadFailure(find.olderFailure, actions::searchAllHistory)
            find.searchingAll -> KitNotice(
                message = stringResource(Res.string.transcriptFindPartial),
                icon = KitIcons.history,
                tone = StatusTone.Progress,
                actions = listOf(KitLineAction(stringResource(Res.string.transcriptFindStopSearchingAll), actions::cancelSearchAll, testTag = "transcript-find-cancel-all")),
                modifier = Modifier.testTag("transcript-find-searching-all"),
            )
            find.partial -> KitNotice(
                message = stringResource(Res.string.transcriptFindPartial),
                icon = KitIcons.history,
                actions = listOf(older),
                modifier = Modifier.testTag("transcript-find-partial"),
            )
        }
    }
}
