package io.github.eslamasabry.oc.feature.chat.composer.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatHistoryEmpty
import io.github.eslamasabry.oc.ui.words.res.chatHistoryLoadFailed
import io.github.eslamasabry.oc.ui.words.res.chatHistoryLoading
import io.github.eslamasabry.oc.ui.words.res.chatHistorySearch
import io.github.eslamasabry.oc.ui.words.res.chatHistorySubtitle
import io.github.eslamasabry.oc.ui.words.res.chatHistoryTitle
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import org.jetbrains.compose.resources.stringResource

/** The visible reuse path: search sent words, then append them to the draft without sending. */
@Composable
public fun ComposerHistorySheet(
    visible: Boolean,
    state: ComposerHistoryUi,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val search = remember(visible) { TextFieldState() }
    var query by remember(visible) { mutableStateOf("") }
    val matches = state.prompts.filter { it.contains(query.trim(), ignoreCase = true) }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.chatHistoryTitle),
        subtitle = stringResource(Res.string.chatHistorySubtitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        KitSearchField(
            label = stringResource(Res.string.chatHistorySearch),
            state = search,
            onSearch = { query = it },
            resultCount = if (query.isBlank()) null else matches.size,
            modifier = Modifier.fillMaxWidth().testTag("prompt-history-search"),
        )
        if (state.readFailure != null)
            KitNotice(
                message = stringResource(Res.string.chatHistoryLoadFailed),
                tone = StatusTone.Failure,
                actions = listOf(KitLineAction(stringResource(Res.string.commonRetry), onRetry)),
            )
        when {
            state.loading && state.prompts.isEmpty() ->
                KitText(stringResource(Res.string.chatHistoryLoading))
            matches.isEmpty() && query.isNotBlank() ->
                KitSearchNoMatch(
                    query,
                    onClear = {
                        search.edit { replace(0, length, "") }
                        query = ""
                    },
                )
            matches.isEmpty() -> KitText(stringResource(Res.string.chatHistoryEmpty))
            else ->
                KitRowGroup {
                    matches.forEachIndexed { index, text ->
                        KitRow(
                            title = text,
                            onClick = { onChoose(text) },
                            modifier = Modifier.testTag("reuse-prompt-$index"),
                        )
                    }
                }
        }
    }
}
