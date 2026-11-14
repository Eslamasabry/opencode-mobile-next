package io.github.eslamasabry.oc.feature.agents.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.HandoffReading
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Read-only review: every conversation is selected explicitly and only its complete masked record
 * can be copied.
 */
@Composable
internal fun AgentDraftPanel(state: AgentsUi, actions: AgentsActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.agentDraftTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::closeDrafts,
            )
        },
        width = KitScreenWidth.Reading,
        edges = KitScreenEdges.Bleed,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(kitPageScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitNotice(stringResource(Res.string.agentDraftReadOnly))
            KitText(stringResource(Res.string.agentDraftGuide))
            if (state.draftLoading) KitText(stringResource(Res.string.agentDraftLoading))
            if (state.draftTooLarge) KitNotice(stringResource(Res.string.agentDraftTooLarge))
            if (state.draftFailed) KitNotice(stringResource(Res.string.agentDraftFailed))
            val record = state.draftRecord
            if (record != null) {
                KitSectionLabel(stringResource(Res.string.agentDraftReview))
                if (record.omittedMessages > 0)
                    KitNotice(
                        pluralStringResource(
                            Res.plurals.agentDraftOmitted,
                            record.omittedMessages,
                            record.omittedMessages,
                        )
                    )
                if (record.redacted) KitNotice(stringResource(Res.string.agentDraftMasked))
                // AG5: what was said, drawn as Markdown under the conversation's title; the record exactly as it
                // will be copied (with the source's labels and ids) only under Details. The record was masked when
                // it was built; both views pass the display redactor again, so nothing it knows can be drawn.
                val you = stringResource(Res.string.chatUiYou)
                val agent = stringResource(Res.string.chatUiAgent)
                val shown = remember(record) { KitRedact.text(record.markdown) }
                val reading = remember(shown, you, agent) { HandoffReading.of(shown, you, agent) }
                reading.title?.let { KitText(it, role = TypeRole.Title, kind = KitTextKind.Frame) }
                val blocks =
                    remember(reading.body) { KitMdSource.blocks("handoff-record", reading.body, streaming = false) }
                if (blocks.isNotEmpty())
                    KitMarkdown(blocks, KitMarkdownActions(onLink = {}), Modifier.testTag("agent-draft-reading"))
                KitDetailsFold(
                    label = stringResource(Res.string.agentDraftAsCopied),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    modifier = Modifier.testTag("agent-draft-record"),
                    child = { KitText(shown, role = TypeRole.Mono, kind = KitTextKind.Reading) },
                )
                KitButton(
                    stringResource(Res.string.agentDraftCopy),
                    actions::copyDraft,
                    role = KitButtonRole.Secondary,
                )
                if (state.draftCopied) KitNotice(stringResource(Res.string.agentDraftCopied))
            }
            KitSectionLabel(state.draftSource?.name.orEmpty())
            if (!state.draftLoading && state.draftConversations.isEmpty() && !state.draftFailed) {
                KitText(stringResource(Res.string.agentDraftEmpty))
            }
            val untitled = stringResource(Res.string.claudeImportUntitled)
            for (conversation in state.draftConversations) {
                KitRow(
                    title = conversation.title.ifBlank { untitled },
                    trailing = KitRowTrailing.Chevron,
                    enabled = !state.draftLoading,
                    disabledReason =
                        if (state.draftLoading) stringResource(Res.string.agentDraftLoading)
                        else null,
                    onClick = { actions.prepareDraft(conversation.id) },
                )
            }
        }
    }
}
