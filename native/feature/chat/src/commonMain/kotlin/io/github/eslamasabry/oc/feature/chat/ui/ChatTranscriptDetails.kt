package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundAgentResult
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundShellOutcome
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundShellResult
import io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundTaskName
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Full thoughts, typed server notices and background results. All text and actions use the kit. */
@Composable
internal fun TranscriptDetail(
    detail: TranscriptDetailUi,
    folds: TranscriptFolds,
    reasoningExpanded: Boolean,
    reply: ReplyActions?,
    onCompactAgain: (() -> Unit)?,
    onOpenSession: ((String) -> Unit)?,
) {
    val part = detail.part
    val key = detail.key
    val kind = part.noticeMetadata["kind"] ?: part.toolName
    val mask = rememberKitMask()
    val shell = remember(part) { BackgroundShellResult.fromPart(part) }
    if (shell != null) {
        val failed = shell.outcome == BackgroundShellOutcome.Failed
        val outcome =
            stringResource(
                when (shell.outcome) {
                    BackgroundShellOutcome.Finished -> Res.string.kitToolDone
                    BackgroundShellOutcome.Failed -> Res.string.kitToolFailed
                    BackgroundShellOutcome.Stopped -> Res.string.kitToolStopped
                }
            )
        KitExpandRow(
            title = mask(backgroundTaskName(shell.command)),
            supporting = outcome,
            expanded = folds.open[key] ?: failed,
            onExpandedChange = { folds.open[key] = it },
            modifier = Modifier.testTag("background-shell-result"),
        ) {
            KitCommandBox(
                command = shell.command,
                output = shell.output,
                maxLines = Int.MAX_VALUE,
                moreLabel = stringResource(Res.string.chatUiMessage),
                onMore = {},
                exitLabel = shell.exitCode?.let { stringResource(Res.string.workExitCode, it) },
            )
        }
        return
    }
    val agent = remember(part) { BackgroundAgentResult.fromPart(part) }
    if (agent != null) {
        Column(Modifier.testTag("background-agent-result")) {
            KitText(agent.description.ifBlank { agent.agent })
            KitQuietLine(
                listOf(
                        agent.agent,
                        stringResource(
                            when (agent.state) {
                                "completed" -> Res.string.chatUiBackgroundComplete
                                "error" -> Res.string.chatUiBackgroundError
                                else -> Res.string.chatUiBackgroundCancelled
                            }
                        ),
                    )
                    .joinToString(" · ")
            )
            DetailMarkdown(
                key,
                agent.body.ifBlank { stringResource(Res.string.chatUiNoResultText) },
                reply,
            )
            if (onOpenSession != null)
                KitButton(
                    stringResource(Res.string.chatUiResultOpenChild),
                    { onOpenSession(agent.childId) },
                    role = KitButtonRole.Tertiary,
                )
            KitDetailsFold(
                label = stringResource(Res.string.chatUiResultSourceDetails),
                copyLabel = stringResource(Res.string.chatReplyFailedCopy),
                notes = listOf(mask(part.text)),
            )
        }
        return
    }
    if (part.type == PartType.Text) {
        DetailMarkdown(key, part.text, reply)
        return
    }
    if (part.type == PartType.Reasoning) {
        val plain = part.text.replace(Regex("[*_`#]"), "").trim()
        if (
            plain.isBlank() ||
                Regex(
                        "(?i)^\\W*(no|empty)\\s+(reasoning|thinking|thoughts?)(\\s+(available|provided|recorded|to show))?\\W*$"
                    )
                    .matches(plain)
        )
            return
        val defaultKey = "$key@default"
        if (folds.open[defaultKey] != reasoningExpanded) {
            folds.open.remove(key)
            folds.open[defaultKey] = reasoningExpanded
        }
        KitExpandRow(
            title = plain.lineSequence().first().take(80),
            expanded = folds.open[key] ?: reasoningExpanded,
            onExpandedChange = { folds.open[key] = it },
            modifier = Modifier.testTag("assistant-reasoning-block"),
        ) {
            DetailMarkdown(key, part.text, reply)
        }
        return
    }
    if (part.type == PartType.Switch) {
        val prefix =
            stringResource(
                when (kind) {
                    "model" -> Res.string.chatUiModel
                    "agent" -> Res.string.chatUiAgent
                    else -> Res.string.chatUiMoved
                }
            )
        KitQuietLine("$prefix → ${part.text}", Modifier.testTag("transcript-marker-$key"))
        return
    }
    if (part.type == PartType.Compaction) {
        when (kind) {
            "passed" -> return
            "running" -> KitQuietLine(stringResource(Res.string.chatUiCompactingConversation))
            "failed" ->
                Column {
                    KitQuietLine(stringResource(Res.string.chatUiCompactionFailed))
                    KitText(stringResource(Res.string.chatUiCompactionFailedHint))
                    if (detail.retryCompaction && onCompactAgain != null)
                        KitButton(
                            stringResource(Res.string.chatUiCompactAgain),
                            onCompactAgain,
                            role = KitButtonRole.Tertiary,
                        )
                }
            else -> {
                val meta = part.tool?.metadata
                val trigger = meta?.get("trigger")?.toString()?.trim('"')
                val tokens = meta?.get("preTokens")?.toString()?.toLongOrNull()
                val body =
                    listOfNotNull(
                            when (trigger) {
                                "auto" -> stringResource(Res.string.chatUiCompactedAutomatically)
                                "manual" -> stringResource(Res.string.chatUiCompactedWhenAsked)
                                else -> null
                            },
                            tokens
                                ?.takeIf { it > 0 }
                                ?.let {
                                    stringResource(Res.string.chatUiCompactedFrom, it.toString())
                                },
                        )
                        .joinToString(" ")
                ExpandNotice(
                    key,
                    stringResource(Res.string.chatUiContextCompacted),
                    body,
                    false,
                    folds,
                    reply,
                )
            }
        }
        return
    }
    if (kind == "unknown" && Regex("^[a-z][a-z0-9._-]*$").matches(part.text.trim())) return
    val header =
        when (kind) {
            "agent-info" -> stringResource(Res.string.chatUiAgentNote)
            "agent-warning" -> stringResource(Res.string.chatUiAgentWarning)
            "agent-error" -> stringResource(Res.string.chatUiAgentProblem)
            "instructions" -> stringResource(Res.string.sessionInstructionsUpdated)
            "skill" ->
                stringResource(Res.string.chatUiSkill) + " · " + mask(part.filename ?: part.text)
            "synthetic" -> part.filename ?: stringResource(Res.string.chatUiContextAdded)
            "system" -> part.filename ?: stringResource(Res.string.chatUiSystemUpdate)
            else ->
                part.filename
                    ?: part.text.lineSequence().firstOrNull()?.trim()?.takeIf { it.length in 1..60 }
                    ?: stringResource(Res.string.chatUiServerMessage)
        }
    val body =
        when (kind) {
            "instructions" -> stringResource(Res.string.sessionInstructionsApplied)
            "skill" -> if (part.filename == null) "" else part.text
            "agent-error" -> ""
            else -> part.text
        }
    ExpandNotice(key, header, body, kind?.startsWith("agent-") == true, folds, reply)
    if (kind == "agent-error" && part.text.isNotBlank())
        KitDetailsFold(
            label = stringResource(Res.string.chatReplyFailedDetails),
            copyLabel = stringResource(Res.string.chatReplyFailedCopy),
            notes = listOf(mask(part.text)),
        )
}

@Composable
private fun ExpandNotice(
    key: String,
    header: String,
    body: String,
    initiallyOpen: Boolean,
    folds: TranscriptFolds,
    reply: ReplyActions?,
) {
    if (body.isBlank()) KitQuietLine(header)
    else
        KitExpandRow(
            title = header,
            expanded = folds.open[key] ?: initiallyOpen,
            onExpandedChange = { folds.open[key] = it },
            modifier = Modifier.testTag("transcript-notice-$key"),
        ) {
            DetailMarkdown(key, body, reply)
        }
}

@Composable
private fun DetailMarkdown(key: String, text: String, reply: ReplyActions?) {
    val blocks = remember(key, text) { KitMdSource.blocks(key, text, streaming = false) }
    KitMarkdown(blocks, reply?.markdown ?: KitMarkdownActions(onLink = {}))
}

@Composable
internal fun TurnMetadata(
    metadata: io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
) {
    val words =
        listOfNotNull(
            metadata.models.takeIf { it.isNotEmpty() }?.joinToString(" → "),
            metadata.tokens
                .takeIf { metadata.latest && it > 0 }
                ?.let {
                    stringResource(
                        Res.string.chatTranscriptTokens,
                        io.github.eslamasabry.oc.core.common.text.Numbers.format(it),
                    )
                },
            metadata.cost
                .takeIf { metadata.latest && it > 0 }
                ?.let {
                    stringResource(
                        Res.string.chatTranscriptCost,
                        io.github.eslamasabry.oc.core.common.text.Numbers.decimal(it, 6),
                    )
                },
        )
    if (words.isNotEmpty())
        KitQuietLine(words.joinToString(" · "), Modifier.testTag("chat-turn-metadata"))
}

@Composable
internal fun TurnEndNote(note: io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote) {
    KitQuietLine(
        stringResource(
            when (note) {
                io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote.Silent ->
                    Res.string.chatNoReplyCameBack
                io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote.Stopped ->
                    Res.string.kitToolStopped
                io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote.Failed ->
                    Res.string.chatReplyFailedOther
            }
        )
    )
}
