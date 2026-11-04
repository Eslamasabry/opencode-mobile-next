package io.github.eslamasabry.oc.feature.chat.context

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.agents.context.ActiveContextMessage
import io.github.eslamasabry.oc.engine.agents.context.ContextContent
import io.github.eslamasabry.oc.engine.agents.context.ContextContentKind
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.HandWords
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.activeContextAllCount
import io.github.eslamasabry.oc.ui.words.res.activeContextChangedTitle
import io.github.eslamasabry.oc.ui.words.res.activeContextChanged
import io.github.eslamasabry.oc.ui.words.res.activeContextContentHelp
import io.github.eslamasabry.oc.ui.words.res.activeContextEmpty
import io.github.eslamasabry.oc.ui.words.res.activeContextEmptyDetail
import io.github.eslamasabry.oc.ui.words.res.activeContextFailedTitle
import io.github.eslamasabry.oc.ui.words.res.activeContextFile
import io.github.eslamasabry.oc.ui.words.res.activeContextIntro
import io.github.eslamasabry.oc.ui.words.res.activeContextInvalid
import io.github.eslamasabry.oc.ui.words.res.activeContextLoading
import io.github.eslamasabry.oc.ui.words.res.activeContextMessageId
import io.github.eslamasabry.oc.ui.words.res.activeContextNoText
import io.github.eslamasabry.oc.ui.words.res.activeContextNotice
import io.github.eslamasabry.oc.ui.words.res.activeContextOpenMessage
import io.github.eslamasabry.oc.ui.words.res.activeContextPartHeading
import io.github.eslamasabry.oc.ui.words.res.activeContextPruned
import io.github.eslamasabry.oc.ui.words.res.activeContextRefresh
import io.github.eslamasabry.oc.ui.words.res.activeContextRefreshFailed
import io.github.eslamasabry.oc.ui.words.res.activeContextSearch
import io.github.eslamasabry.oc.ui.words.res.activeContextSubtitle
import io.github.eslamasabry.oc.ui.words.res.activeContextText
import io.github.eslamasabry.oc.ui.words.res.activeContextTitle
import io.github.eslamasabry.oc.ui.words.res.activeContextToolInput
import io.github.eslamasabry.oc.ui.words.res.activeContextToolOutput
import io.github.eslamasabry.oc.ui.words.res.activeContextTruncated
import io.github.eslamasabry.oc.ui.words.res.activeContextUnsupported
import io.github.eslamasabry.oc.ui.words.res.activeContextWhat
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.usageReasoning
import org.jetbrains.compose.resources.stringResource

/** The role word the title reads by; the switch notices share one "conversation change" title. */
private fun titleRole(type: String): String =
    when (type) {
        "agent-switched", "model-switched", "location-switched" -> "change"
        else -> type
    }

/**
 * The active-context inspector (B-CHAT-001..003; Flutter `ActiveContextScreen`, here a sheet over the chat so Back
 * returns to the conversation): every message the next turn will read after the latest summary, searchable, each
 * opening to its text-only parts. Binary bodies, addresses and metadata are never shown.
 */
@Composable
public fun ActiveContextSheet(model: ActiveContextModel) {
    val visible by model.visible.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    var query by remember(visible) { mutableStateOf("") }
    var openId by remember(visible) { mutableStateOf<String?>(null) }
    val ready = state as? ActiveContextUi.Ready
    val opened = ready?.messages?.firstOrNull { it.id == openId }
    KitSheet(
        visible = visible,
        onDismissRequest = model::close,
        title = stringResource(Res.string.activeContextTitle),
        subtitle = stringResource(Res.string.activeContextSubtitle),
        closeLabel = stringResource(Res.string.kitSheetDismiss),
        height = KitSheetHeight.Full,
        secondary =
            if (state == ActiveContextUi.Unsupported || state == ActiveContextUi.Changed) null
            else KitAction(stringResource(Res.string.activeContextRefresh), model::refresh),
    ) {
        when (val shown = state) {
            ActiveContextUi.Loading -> KitStateView(KitStateKind.Loading, stringResource(Res.string.activeContextLoading))
            ActiveContextUi.Unsupported ->
                KitStateView(KitStateKind.Missing, stringResource(Res.string.activeContextTitle), body = stringResource(Res.string.activeContextUnsupported))
            ActiveContextUi.Changed ->
                KitStateView(KitStateKind.Missing, stringResource(Res.string.activeContextChangedTitle), body = stringResource(Res.string.activeContextChanged))
            ActiveContextUi.Invalid ->
                KitStateView(
                    KitStateKind.Error,
                    stringResource(Res.string.activeContextFailedTitle),
                    body = stringResource(Res.string.activeContextInvalid),
                    primary = KitAction(stringResource(Res.string.commonRetry), model::refresh),
                )
            ActiveContextUi.Failed ->
                KitStateView(
                    KitStateKind.Error,
                    stringResource(Res.string.activeContextFailedTitle),
                    primary = KitAction(stringResource(Res.string.commonRetry), model::refresh),
                )
            is ActiveContextUi.Ready ->
                if (opened != null) {
                    MessageDetail(opened) { openId = null }
                } else {
                    MessageList(shown, query, { query = it }) { openId = it }
                }
        }
    }
}

@Composable
private fun MessageList(ready: ActiveContextUi.Ready, query: String, onQuery: (String) -> Unit, onOpen: (String) -> Unit) {
    if (ready.refreshFailed) {
        KitNotice(
            stringResource(Res.string.activeContextRefreshFailed, stringResource(Res.string.activeContextFailedTitle)),
            modifier = Modifier.padding(horizontal = Space.S4.dp),
        )
    }
    if (ready.messages.isEmpty()) {
        KitStateView(
            KitStateKind.Empty,
            stringResource(Res.string.activeContextEmpty),
            body = stringResource(Res.string.activeContextEmptyDetail),
        )
        return
    }
    KitText(
        stringResource(Res.string.activeContextIntro),
        role = TypeRole.Caption,
        tone = TextTone.Secondary,
        modifier = Modifier.padding(horizontal = Space.S4.dp),
    )
    KitSearchField(
        label = stringResource(Res.string.activeContextSearch),
        onSearch = onQuery,
        modifier = Modifier.fillMaxWidth().testTag("active-context-search"),
        resultCount = if (query.isBlank()) null else ready.messages.count { it.matches(query.trim()) },
    )
    val shown = ready.messages.filter { it.matches(query.trim()) }
    KitSectionLabel(stringResource(Res.string.activeContextAllCount, Numbers.format(shown.size.toLong())))
    if (shown.isEmpty()) {
        KitSearchNoMatch(query.trim(), onClear = { onQuery("") }, what = stringResource(Res.string.activeContextWhat))
        return
    }
    for (message in shown) {
        val title = stringResource(HandWords.activeContextMessageTitle(titleRole(message.type)))
        val preview = message.previewFor(query.trim())
        KitRow(
            title = title,
            supporting = preview.ifEmpty { stringResource(Res.string.activeContextNoText) },
            supportingMaxLines = 3,
            trailing = KitRowTrailing.Value(Numbers.format(message.content.size.toLong())),
            onClickLabel = stringResource(Res.string.activeContextOpenMessage, title),
            onClick = { onOpen(message.id) },
            modifier = Modifier.testTag("active-context-row"),
        )
    }
}

@Composable
private fun MessageDetail(message: ActiveContextMessage, onBack: () -> Unit) {
    val title = stringResource(HandWords.activeContextMessageTitle(titleRole(message.type)))
    KitRow(
        title = title,
        supporting = stringResource(Res.string.activeContextMessageId) + " · " + message.id,
        onClickLabel = stringResource(Res.string.kitSheetDismiss),
        onClick = onBack,
    )
    KitText(
        stringResource(Res.string.activeContextContentHelp),
        role = TypeRole.Caption,
        tone = TextTone.Secondary,
        modifier = Modifier.padding(horizontal = Space.S4.dp),
    )
    if (message.content.none { it.text.isNotEmpty() || it.kind == ContextContentKind.Pruned || it.kind == ContextContentKind.Truncated }) {
        KitText(
            stringResource(Res.string.activeContextNoText),
            tone = TextTone.Secondary,
            modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
        )
    }
    for (part in message.content) PartBlock(part)
}

@Composable
private fun PartBlock(part: ContextContent) {
    val kind =
        stringResource(
            when (part.kind) {
                ContextContentKind.Text -> Res.string.activeContextText
                ContextContentKind.Reasoning -> Res.string.usageReasoning
                ContextContentKind.ToolInput -> Res.string.activeContextToolInput
                ContextContentKind.ToolOutput -> Res.string.activeContextToolOutput
                ContextContentKind.File -> Res.string.activeContextFile
                ContextContentKind.Notice -> Res.string.activeContextNotice
                ContextContentKind.Pruned -> Res.string.activeContextPruned
                ContextContentKind.Truncated -> Res.string.activeContextTruncated
            },
        )
    val name = part.name?.takeIf { it.isNotEmpty() }
    val heading = if (name == null) kind else stringResource(Res.string.activeContextPartHeading, kind, name)
    // Pruned and truncated parts carry no text: their label is the whole statement.
    KitSectionLabel(heading)
    if (part.text.isNotEmpty()) {
        KitText(part.text, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp, vertical = Space.S1.dp))
    }
}
