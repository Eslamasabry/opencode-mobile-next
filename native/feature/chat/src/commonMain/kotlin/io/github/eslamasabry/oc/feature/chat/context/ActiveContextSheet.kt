package io.github.eslamasabry.oc.feature.chat.context

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import io.github.eslamasabry.oc.engine.agents.context.ActiveContextMessage
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyModelNames
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eslamasabry.oc.engine.agents.context.ContextContent
import io.github.eslamasabry.oc.engine.agents.context.ContextContentKind
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitContextMessagePart
import io.github.eslamasabry.oc.ui.words.HandWords
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The list calls it "User prompt"; the opened page calls it "User message". */
@Composable
private fun contextTypeLabel(type: String): String = when (type) {
    "user" -> stringResource(Res.string.activeContextUser)
    "assistant" -> stringResource(Res.string.activeContextAssistant)
    "system" -> stringResource(Res.string.activeContextSystem)
    "synthetic" -> stringResource(Res.string.activeContextSynthetic)
    "skill" -> stringResource(Res.string.activeContextSkill)
    "shell" -> stringResource(Res.string.activeContextShell)
    "compaction" -> stringResource(Res.string.activeContextCompaction)
    "agent-switched", "model-switched", "location-switched" -> stringResource(Res.string.activeContextChange)
    // A kind the app has no words for ("idle"): never the server's own word.
    else -> stringResource(HandWords.activeContextMessageTitle("other"))
}

/**
 * A row's one line. The model's private reasoning is not what a row says when the entry has words of its own; a change of
 * model reads as the model's name ("Big Pickle"), never its id. NV (W4, sweep-a P49): Flutter shows the first part's
 * text as it is (often the reasoning) and an unknown kind by the server's own word; native says "Message" for an
 * unknown kind and leads with the entry's own words. An entry that holds only reasoning still shows it: this page is
 * where a person goes to read what the model was given, and opening a row shows every part.
 */
private fun rowPreview(message: ActiveContextMessage, query: String): String {
    val spoken = message.content.filter { it.kind != ContextContentKind.Reasoning }
    val shown = if (query.isNotEmpty() || spoken.isEmpty() || spoken.size == message.content.size) message else ActiveContextMessage(message.id, message.type, spoken)
    val line = shown.previewFor(query)
    return if (message.type == "model-switched" && line.isNotBlank()) ReplyModelNames.fromId(line) else line
}

private fun titleRole(type: String): String = when (type) {
    "agent-switched", "model-switched", "location-switched" -> "change"
    else -> type
}

/** A full-page inspector over the chat; Back from a message returns to its filtered list. */
@Composable
public fun ActiveContextSheet(model: ActiveContextModel) {
    val visible by model.visible.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    ActiveContextScreen(visible, state, model::close, model::refresh)
}

@Composable
internal fun ActiveContextScreen(visible: Boolean, state: ActiveContextUi, onClose: () -> Unit, onRefresh: () -> Unit) {
    val field = remember(visible) { TextFieldState() }
    var type by remember(visible) { mutableStateOf<String?>(null) }
    var openId by remember(visible) { mutableStateOf<String?>(null) }
    // Flutter onChanged filters on each edit; the kit's settled callback is only for announcements.
    val query = field.text.toString().trim().lowercase()
    val ready = state as? ActiveContextUi.Ready
    val opened = ready?.messages?.firstOrNull { it.id == openId }
    val title = stringResource(Res.string.activeContextTitle)
    val typeLabel = type?.let { contextTypeLabel(it) }
    val shown = ready?.messages?.filter { (type == null || it.type == type) && it.matches(query) }.orEmpty()
    val tokens = LocalKitTokens.current
    val listScroll = rememberScrollState()
    LaunchedEffect(ready) {
        if (ready != null && type != null && ready.messages.none { it.type == type }) type = null
    }
    val back = { if (openId != null) openId = null else onClose() }
    KitPageOverlay(visible, title, back) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = opened?.let { stringResource(HandWords.activeContextMessageTitle(titleRole(it.type))) } ?: title,
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = back,
                    actions = if (openId != null || state == ActiveContextUi.Changed || state == ActiveContextUi.Unsupported) emptyList() else listOf(
                        KitLineAction(stringResource(Res.string.activeContextRefresh), onRefresh, icon = KitIcons.retry),
                    ),
                )
            },
            width = KitScreenWidth.List,
            search = if (openId != null || ready == null || ready.messages.isEmpty()) null else ({
                KitSearchField(
                    label = stringResource(Res.string.activeContextSearch),
                    state = field,
                    onSearch = {},
                    modifier = Modifier.testTag("active-context-search"),
                    resultCount = shown.size,
                    filters = listOf(KitMenuItem(stringResource(Res.string.activeContextAllCount, ready.messages.size), { type = null }, checked = type == null)) +
                        ready.messages.groupingBy { it.type }.eachCount().map { (kind, count) ->
                            KitMenuItem(stringResource(Res.string.activeContextTypeCount, contextTypeLabel(kind), count), { type = kind }, checked = type == kind)
                        },
                    activeFilter = typeLabel,
                    removeFilterLabel = stringResource(Res.string.kitSearchClear),
                    onClearFilter = { type = null },
                )
            }),
            edges = KitScreenEdges.Bleed,
        ) {
            // Kept or previous messages stay on screen; Ready.refreshing only lights the kit's quiet refresh line.
            KitRefresh(refreshing = ready?.refreshing == true, onRefresh = onRefresh, enabled = ready != null && openId == null) {
                key(openId) {
                    val scroll = if (openId == null) listScroll else rememberScrollState()
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = if (openId == null) Space.S2.dp else Space.S3.dp)) {
                        when (state) {
                            ActiveContextUi.Loading -> KitStateView(KitStateKind.Loading, stringResource(Res.string.activeContextLoading), icon = KitIcons.layers)
                            ActiveContextUi.Unsupported -> KitStateView(KitStateKind.Missing, title, body = stringResource(Res.string.activeContextUnsupported))
                            ActiveContextUi.Changed -> ChangedContext()
                            ActiveContextUi.Invalid, ActiveContextUi.Failed -> KitStateView(
                                KitStateKind.Error, stringResource(Res.string.activeContextFailedTitle),
                                body = if (state == ActiveContextUi.Invalid) stringResource(Res.string.activeContextInvalid) else null,
                                primary = KitAction(stringResource(Res.string.commonRetry), onRefresh),
                            )
                            is ActiveContextUi.Ready -> if (openId != null) {
                                if (opened == null) ChangedContext()
                                else Column(Modifier.padding(horizontal = tokens.gutter.dp)) {
                                    opened.content.forEach { part ->
                                        PartBlock(part)
                                        Spacer(Modifier.height(tokens.sectionGap.dp))
                                    }
                                    KitDetailsFold(
                                        label = stringResource(Res.string.kitDetails),
                                        copyLabel = stringResource(Res.string.kitCopyAll),
                                        notes = listOf(stringResource(Res.string.activeContextContentHelp)),
                                        values = listOf(KitTechnicalValue(stringResource(Res.string.activeContextMessageId), LocalKitRedactor.current.details(opened.id))),
                                    )
                                }
                            } else {
                                if (state.refreshFailed) KitNotice(
                                    stringResource(Res.string.activeContextRefreshFailed, stringResource(Res.string.activeContextFailedTitle)),
                                    modifier = Modifier.padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
                                    actions = listOf(KitLineAction(stringResource(Res.string.commonRetry), onRefresh)),
                                )
                                KitText(stringResource(Res.string.activeContextIntro), Modifier.padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp), role = TypeRole.Secondary, tone = TextTone.Secondary)
                                when {
                                    state.messages.isEmpty() -> KitStateView(KitStateKind.Empty, stringResource(Res.string.activeContextEmpty), body = stringResource(Res.string.activeContextEmptyDetail), icon = KitIcons.layers)
                                    shown.isEmpty() -> KitSearchNoMatch(field.text.toString().trim().ifEmpty { typeLabel.orEmpty() }, {
                                        field.edit { replace(0, length, "") }; type = null
                                    }, what = stringResource(Res.string.activeContextWhat))
                                    else -> KitRowGroup {
                                        shown.forEach { message ->
                                            val label = contextTypeLabel(message.type)
                                            KitRow(
                                                title = label,
                                                supporting = rowPreview(message, query).ifEmpty { stringResource(Res.string.activeContextNoText) },
                                                supportingMaxLines = 3, // F: lib/ui/screens/active_context_screen.dart:350
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
    }
}

@Composable
private fun ChangedContext() {
    KitStateView(KitStateKind.Missing, stringResource(Res.string.activeContextChangedTitle), body = stringResource(Res.string.activeContextChanged), icon = KitIcons.info)
}

@Composable
private fun PartBlock(part: ContextContent) {
    val kind = stringResource(when (part.kind) {
        ContextContentKind.Text -> Res.string.activeContextText
        ContextContentKind.Reasoning -> Res.string.transcriptFindReasoning
        ContextContentKind.ToolInput -> Res.string.activeContextToolInput
        ContextContentKind.ToolOutput -> Res.string.activeContextToolOutput
        ContextContentKind.File -> Res.string.activeContextFile
        ContextContentKind.Notice -> Res.string.activeContextNotice
        ContextContentKind.Pruned -> Res.string.activeContextPruned
        ContextContentKind.Truncated -> Res.string.activeContextTruncated
    })
    val heading = part.name?.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.activeContextPartHeading, kind, it) } ?: kind
    KitContextMessagePart(
        heading = heading,
        text = part.text,
        copyLabel = stringResource(Res.string.activeContextCopyPart, heading),
        emptyLabel = stringResource(Res.string.activeContextNoText),
        technical = part.kind == ContextContentKind.ToolInput || part.kind == ContextContentKind.ToolOutput,
        dropped = part.kind == ContextContentKind.Pruned || part.kind == ContextContentKind.Truncated,
    )
}
