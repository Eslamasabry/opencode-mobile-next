package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitFindExcerpt
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Flutter chat/transcript_find.dart: search stays by the composer, with two compact rows. */
@Composable
internal fun TranscriptFindBar(find: FindUi, actions: ChatActions) {
    val field = rememberTextFieldState(find.query)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(find.query) {
        if (field.text.toString() != find.query) field.edit { replace(0, length, find.query) }
    }
    val none = stringResource(Res.string.transcriptFindNone)
    val matches = if (find.count == 0) none else pluralStringResource(
        Res.plurals.transcriptFindCount, find.count, find.count, find.current + 1,
    )
    val older = KitLineAction(
        stringResource(Res.string.transcriptFindAll), actions::searchAllHistory,
        enabled = !find.loadingOlder,
        disabledReason = if (find.loadingOlder) stringResource(Res.string.chatLoadingConversation) else null,
        testTag = "transcript-find-older",
    )
    Column(
        Modifier.fillMaxWidth()
            .padding(start = Space.S4.dp, end = Space.S1.dp, top = Space.S2.dp, bottom = Space.S2.dp)
            .testTag("transcript-find-bar")
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && field.text.isEmpty()) {
                    actions.find(null)
                    true
                } else false
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitSearchField(
                label = stringResource(Res.string.transcriptFindHint),
                state = field,
                onSearch = { actions.find(it) },
                onSubmit = { actions.findNext() },
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("transcript-find-input"),
            )
            KitIconButton(KitIcons.close, stringResource(Res.string.transcriptFindClose), { actions.find(null) })
        }
        if (field.text.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(end = Space.S1.dp), verticalAlignment = Alignment.CenterVertically) {
                KitText(matches, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, role = TypeRole.Secondary, tone = TextTone.Secondary)
                val enabled = if (find.count > 0) KitEnabled.Enabled else KitEnabled.Disabled(none)
                KitIconButton(KitIcons.chevronUp, stringResource(Res.string.transcriptFindPrevious), actions::findPrevious, Modifier.testTag("transcript-find-previous"), enabled = enabled)
                KitIconButton(KitIcons.chevronDown, stringResource(Res.string.transcriptFindNext), actions::findNext, Modifier.testTag("transcript-find-next"), enabled = enabled)
            }
        }
        when {
            find.olderFailure != null -> HistoryReadFailure(find.olderFailure, actions::searchAllHistory)
            find.searchingAll -> KitNotice(
                message = stringResource(Res.string.transcriptFindPartial),
                actions = listOf(KitLineAction(stringResource(Res.string.transcriptFindStopSearchingAll), actions::cancelSearchAll, testTag = "transcript-find-cancel-all")),
                modifier = Modifier.testTag("transcript-find-searching-all"),
            )
            find.partial -> KitNotice(
                message = stringResource(Res.string.transcriptFindPartial),
                actions = listOf(older),
                modifier = Modifier.testTag("transcript-find-partial"),
            )
        }
        find.excerpt?.let { KitFindExcerpt(it, find.query) }
    }
}
