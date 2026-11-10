package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundAgentResult
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundShellOutcome
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundShellResult
import io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundTaskName
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepFold
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepSentence
import io.github.eslamasabry.oc.ui.kitchat.tool.kitOnStepRail
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Markdown marks stripped before judging whether a thought has any words. */
private val MARKDOWN_MARKS = Regex("[*_`#]")

/** A thought that only says there was none. */
private val NO_THOUGHT =
    Regex(
        "(?i)^\\W*(no|empty)\\s+(reasoning|thinking|thoughts?)(\\s+(available|provided|recorded|to show))?\\W*$"
    )

/** Full thoughts, typed server notices and background results. All text and actions use the kit. */
@Composable
internal fun TranscriptDetail(
    detail: TranscriptDetailUi,
    folds: TranscriptFolds,
    reasoningExpanded: Boolean,
    reply: ReplyActions?,
    onCompactAgain: (() -> Unit)?,
    onOpenSession: ((String) -> Unit)?,
    /** A thought the agent is still writing: its line reads "Thinking…" (Flutter `_Reasoning.working`). */
    thinking: Boolean = false,
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
            // Flutter: a server-authored result is labelled apart from the parent's own reply.
            KitQuietLine(stringResource(Res.string.chatUiBackgroundResult))
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
        // Inside an opened work line the words are a sentence on the rail (a dot, the steps' text edge, Flutter
        // `KitStepTimeline.sentence`); anywhere else they sit on the reply's 16 dp edge, never on the screen's.
        if (kitOnStepRail()) KitStepSentence { DetailMarkdown(key, part.text, reply) }
        else Box(Modifier.padding(horizontal = Space.S4.dp)) { DetailMarkdown(key, part.text, reply) }
        return
    }
    if (part.type == PartType.Reasoning) {
        val plain = remember(part.text) { part.text.replace(MARKDOWN_MARKS, "").trim() }
        val noThought = remember(plain) { plain.isBlank() || NO_THOUGHT.matches(plain) }
        if (noThought) return
        val defaultKey = "$key@default"
        // The thought opens by the chat's reasoning setting until the person opens or closes it themselves; a changed
        // setting starts over. The reset is written after composition (a write here would be a backwards write); until
        // it lands the thought already reads as the setting says.
        val settled = folds.open[defaultKey] == reasoningExpanded
        SideEffect {
            if (folds.open[defaultKey] != reasoningExpanded) {
                folds.open.remove(key)
                folds.open[defaultKey] = reasoningExpanded
            }
        }
        // Flutter `_Reasoning` (transcript_rows.dart:651-723): the line is the thought's first line ("Thinking…" while
        // it is still being written) and the body is the thought in the secondary size and tone. The opened body starts
        // after that first line, so an opened thought never says its first line twice (owner, 2026-11-09: grey line,
        // then the same words again in white).
        val firstLine = remember(plain) { plain.lineSequence().first { it.isNotBlank() }.trim() }
        val thoughtTitle = if (thinking) stringResource(Res.string.kitMessageThinking) else firstLine
        val body = remember(part.text, thinking) { if (thinking) part.text else thoughtBody(part.text) }
        val open = (if (settled) folds.open[key] else null) ?: reasoningExpanded
        val bodyPart: @Composable () -> Unit = { DetailMarkdown(key, body, reply, thought = true) }
        // On an opened work line's rail a thought is one line like every step: a dot, its words, no chevron (Flutter
        // `_Fold` on a timeline); anywhere else it keeps its row with the kit's chevron. A one-line thought has nothing
        // more to open: it is its line alone.
        if (body.isBlank())
            if (kitOnStepRail())
                KitStepSentence(Modifier.testTag("assistant-reasoning-block")) {
                    KitText(thoughtTitle, role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
            else KitQuietLine(thoughtTitle, Modifier.testTag("assistant-reasoning-block"))
        else if (kitOnStepRail())
            KitStepFold(
                title = thoughtTitle,
                expanded = open,
                onToggle = { folds.open[key] = !open },
                modifier = Modifier.testTag("assistant-reasoning-block"),
                body = bodyPart,
            )
        else
            KitExpandRow(
                // The whole first line, cut by the layout with an ellipsis (a character count cut words in half).
                title = thoughtTitle,
                titleMaxLines = 2, // NV: unchanged off the rail (two lines of the thought's first line)
                expanded = open,
                onExpandedChange = { folds.open[key] = it },
                modifier = Modifier.testTag("assistant-reasoning-block"),
            ) {
                bodyPart()
            }
        return
    }
    // U6: changes of model or agent are kept in message Details.
    if (part.type == PartType.Switch) return
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
                                    stringResource(
                                        Res.string.chatUiCompactedFrom,
                                        io.github.eslamasabry.oc.core.common.text.Numbers.format(
                                            it
                                        ),
                                    )
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
    // N-45: a notice with no words and no name of its own (an unknown server entry) has nothing to show; it must not
    // become a bare "Server message" row under the turn.
    if ((kind == null || kind == "unknown") && part.filename == null && part.text.isBlank()) return
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
private fun DetailMarkdown(key: String, text: String, reply: ReplyActions?, thought: Boolean = false) {
    val blocks = remember(key, text) { KitMdSource.blocks(key, text, streaming = false) }
    // F: transcript_turns.dart:246-257 a thought is prose in the secondary size and tone, never the reply's ink.
    KitMarkdown(
        blocks,
        reply?.markdown ?: KitMarkdownActions(onLink = {}),
        role = if (thought) TypeRole.Secondary else TypeRole.Body,
    )
}

/** A thought without its first line (that line is the fold's title), leading blank lines dropped. */
internal fun thoughtBody(text: String): String {
    val lines = text.trim().lines()
    val first = lines.indexOfFirst { it.isNotBlank() }
    if (first < 0) return ""
    return lines.drop(first + 1).joinToString("\n").trim()
}

@Composable
internal fun TurnMetadata(
    metadata: io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
) {
    val mask = rememberKitMask()
    val words =
        listOfNotNull(
            metadata.models.takeIf { it.isNotEmpty() }?.joinToString(" → ") { mask(it) },
            metadata.tokens
                .takeIf { it > 0 }
                ?.let {
                    stringResource(
                        Res.string.chatTranscriptTokens,
                        io.github.eslamasabry.oc.core.common.text.Numbers.format(it),
                    )
                },
            metadata.cost
                .takeIf { it > 0 }
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
internal fun TurnEndNote(
    note: io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote,
    onContinue: (() -> Unit)? = null,
) {
    // Flutter `_stoppedLine`: a pause or restart ends the reply on its own line, with a way to carry on from there.
    androidx.compose.foundation.layout.Column {
        TurnEndLine(note)
        if (note.canContinue && onContinue != null)
            KitButton(
                stringResource(Res.string.messageViewContinueReply),
                onContinue,
                role = KitButtonRole.Tertiary,
                modifier = Modifier.testTag("stopped-continue"),
            )
    }
}

@Composable
private fun TurnEndLine(note: io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote) {
    KitQuietLine(
        stringResource(
            when (note) {
                io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote.PausedInactive ->
                    Res.string.chatTurnPausedInactive
                io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote.StoppedServerRestart ->
                    Res.string.chatTurnStoppedServerRestart
                io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote.Superseded ->
                    Res.string.chatTurnSuperseded
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

/**
 * "No reply came back" under a prompt whose turn ended with nothing (Flutter no_reply_reason.dart:198-215): a
 * neutral notice on the reply's rails, with "Send again" where the newest prompt can be sent once more.
 */
@Composable
internal fun NoReplyNotice(onSendAgain: (() -> Unit)?) {
    val again = stringResource(Res.string.chatUiSendPromptAgain)
    KitNotice(
        message = stringResource(Res.string.chatNoReplyCameBack),
        modifier =
            Modifier.fillMaxWidth()
                .widthIn(max = REPLY_CAP_DP.dp) // F: lib/ui/kit/kit_layout.dart:48
                .padding(horizontal = Space.S4.dp)
                .testTag("chat-no-reply"),
        actions = listOfNotNull(onSendAgain?.let { KitLineAction(again, it, testTag = "chat-no-reply-send-again") }),
    )
}

private const val REPLY_CAP_DP = 700 // F: lib/ui/kit/kit_layout.dart:48
