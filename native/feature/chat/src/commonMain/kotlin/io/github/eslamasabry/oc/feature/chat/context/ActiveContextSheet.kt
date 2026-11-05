package io.github.eslamasabry.oc.feature.chat.context

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.TextFieldState
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitKeyValue
import io.github.eslamasabry.oc.ui.kit.row.KitKeyValueRow
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownSelection
import io.github.eslamasabry.oc.ui.words.res.kitSearchClear
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.activeContextTypeCount
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
    ActiveContextScreen(visible, state, model::close, model::refresh)
}

@Composable
internal fun ActiveContextScreen(visible: Boolean, state: ActiveContextUi, onClose: () -> Unit, onRefresh: () -> Unit) {
    val field = remember(visible) { TextFieldState() }
    var query by remember(visible) { mutableStateOf("") }
    var type by remember(visible) { mutableStateOf<String?>(null) }
    var openId by remember(visible) { mutableStateOf<String?>(null) }
    val ready = state as? ActiveContextUi.Ready
    val opened = ready?.messages?.firstOrNull { it.id == openId }
    val title = stringResource(Res.string.activeContextTitle)
    val typeLabel = type?.let { stringResource(HandWords.activeContextMessageTitle(titleRole(it))) }
    val shown = ready?.messages?.filter { (type == null || it.type == type) && it.matches(query.trim()) }.orEmpty()
    KitPageOverlay(visible, title, { if (openId != null) openId = null else onClose() }) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = opened?.let { stringResource(HandWords.activeContextMessageTitle(titleRole(it.type))) } ?: title,
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = { if (openId != null) openId = null else onClose() },
                    actions = if (opened != null || state == ActiveContextUi.Changed || state == ActiveContextUi.Unsupported) emptyList() else listOf(
                        KitLineAction(stringResource(Res.string.activeContextRefresh), onRefresh, icon = KitIcons.retry),
                    ),
                )
            },
            width = KitScreenWidth.List,
            search = if (opened != null || ready == null || ready.messages.isEmpty()) null else ({
                KitSearchField(
                    label = stringResource(Res.string.activeContextSearch),
                    state = field,
                    onSearch = { query = it },
                    modifier = Modifier.testTag("active-context-search"),
                    resultCount = shown.size,
                    filters = listOf(KitMenuItem(stringResource(Res.string.activeContextAllCount, ready.messages.size), { type = null }, checked = type == null)) +
                        ready.messages.groupingBy { it.type }.eachCount().map { (kind, count) ->
                            KitMenuItem(stringResource(Res.string.activeContextTypeCount, stringResource(HandWords.activeContextMessageTitle(titleRole(kind))), count), { type = kind }, checked = type == kind)
                        },
                    activeFilter = typeLabel,
                    removeFilterLabel = stringResource(Res.string.kitSearchClear),
                    onClearFilter = { type = null },
                )
            }),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.S2.dp)) {
                when (state) {
                    ActiveContextUi.Loading -> KitStateView(KitStateKind.Loading, stringResource(Res.string.activeContextLoading), icon = KitIcons.layers)
                    ActiveContextUi.Unsupported -> KitStateView(KitStateKind.Missing, title, body = stringResource(Res.string.activeContextUnsupported))
                    ActiveContextUi.Changed -> KitStateView(KitStateKind.Missing, stringResource(Res.string.activeContextChangedTitle), body = stringResource(Res.string.activeContextChanged))
                    ActiveContextUi.Invalid, ActiveContextUi.Failed -> KitStateView(
                        KitStateKind.Error, stringResource(Res.string.activeContextFailedTitle),
                        body = if (state == ActiveContextUi.Invalid) stringResource(Res.string.activeContextInvalid) else null,
                        primary = KitAction(stringResource(Res.string.commonRetry), onRefresh),
                    )
                    is ActiveContextUi.Ready -> if (opened != null) {
                        KitKeyValue(listOf(KitKeyValueRow(stringResource(Res.string.activeContextMessageId), opened.id)))
                        KitText(stringResource(Res.string.activeContextContentHelp), role = TypeRole.Secondary, tone = TextTone.Secondary)
                        if (opened.content.isEmpty()) KitText(stringResource(Res.string.activeContextNoText), tone = TextTone.Secondary)
                        KitMarkdownSelection { opened.content.forEach { PartBlock(it) } }
                    } else {
                        if (state.refreshFailed) KitNotice(stringResource(Res.string.activeContextRefreshFailed, stringResource(Res.string.activeContextFailedTitle)), actions = listOf(KitLineAction(stringResource(Res.string.commonRetry), onRefresh)))
                        KitText(stringResource(Res.string.activeContextIntro), Modifier.padding(vertical = Space.S2.dp), role = TypeRole.Secondary, tone = TextTone.Secondary)
                        when {
                            state.messages.isEmpty() -> KitStateView(KitStateKind.Empty, stringResource(Res.string.activeContextEmpty), body = stringResource(Res.string.activeContextEmptyDetail), icon = KitIcons.layers)
                            shown.isEmpty() -> KitSearchNoMatch(query.trim().ifEmpty { typeLabel.orEmpty() }, {
                                field.edit { replace(0, length, "") }; query = ""; type = null
                            }, what = stringResource(Res.string.activeContextWhat))
                            else -> KitRowGroup {
                                shown.forEach { message ->
                                    val label = stringResource(HandWords.activeContextMessageTitle(titleRole(message.type)))
                                    KitRow(
                                        title = label,
                                        supporting = message.previewFor(query.trim()).ifEmpty { stringResource(Res.string.activeContextNoText) },
                                        supportingMaxLines = 3, // F: lib/ui/screens/active_context_screen.dart:354
                                        trailing = KitRowTrailing.Chevron,
                                        onClickLabel = stringResource(Res.string.activeContextOpenMessage, label),
                                        onClick = { openId = message.id },
                                        modifier = Modifier.testTag("active-context-row"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
