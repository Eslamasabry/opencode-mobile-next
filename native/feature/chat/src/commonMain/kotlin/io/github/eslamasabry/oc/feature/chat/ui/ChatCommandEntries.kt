package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerCommand
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiBrowsePreviewDownloadAndAttachProjectFiles
import io.github.eslamasabry.oc.ui.words.res.chatUiChangeTheTitleShownInTheSession
import io.github.eslamasabry.oc.ui.words.res.chatUiChooseAServerModelByProviderAnd
import io.github.eslamasabry.oc.ui.words.res.chatUiCollapseReasoning
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactContext
import io.github.eslamasabry.oc.ui.words.res.chatUiCurrentSession
import io.github.eslamasabry.oc.ui.words.res.chatUiExpandReasoning
import io.github.eslamasabry.oc.ui.words.res.chatUiFindAMessageJumpToItOr
import io.github.eslamasabry.oc.ui.words.res.chatUiFindSessionsAcrossEveryOpenCodeProject
import io.github.eslamasabry.oc.ui.words.res.chatUiMessageTimeline
import io.github.eslamasabry.oc.ui.words.res.chatUiModel
import io.github.eslamasabry.oc.ui.words.res.chatUiModelAndAgent
import io.github.eslamasabry.oc.ui.words.res.chatUiNavigate
import io.github.eslamasabry.oc.ui.words.res.chatUiProjectFiles
import io.github.eslamasabry.oc.ui.words.res.chatUiReloadMessages
import io.github.eslamasabry.oc.ui.words.res.chatUiRenameSession
import io.github.eslamasabry.oc.ui.words.res.chatUiRetryLastPrompt
import io.github.eslamasabry.oc.ui.words.res.chatUiReviewTheActualDiffForThisSession
import io.github.eslamasabry.oc.ui.words.res.chatUiRevertLastPrompt
import io.github.eslamasabry.oc.ui.words.res.chatUiRunShellCommand
import io.github.eslamasabry.oc.ui.words.res.chatUiSessionChanges
import io.github.eslamasabry.oc.ui.words.res.chatUiStartACleanSessionInThisWorkspace
import io.github.eslamasabry.oc.ui.words.res.chatUiSummarizeTheSessionUsingTheSelectedModel
import io.github.eslamasabry.oc.ui.words.res.chatUiTodos
import io.github.eslamasabry.oc.ui.words.res.chatUiToggleLongReasoningDetailsAcrossTheTranscript
import io.github.eslamasabry.oc.ui.words.res.chatUiToolsAndCapabilities
import io.github.eslamasabry.oc.ui.words.res.chatUiTranscriptDisplay
import io.github.eslamasabry.oc.ui.words.res.commandSheetPlanDescription
import io.github.eslamasabry.oc.ui.words.res.commandSheetReloadDescription
import io.github.eslamasabry.oc.ui.words.res.commandSheetRetryDescription
import io.github.eslamasabry.oc.ui.words.res.commandSheetShellDescription
import io.github.eslamasabry.oc.ui.words.res.revertUndoDescription
import io.github.eslamasabry.oc.ui.words.res.usageSessions
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import org.jetbrains.compose.resources.stringResource

/**
 * The commands this app can run in a conversation, named as Flutter's `_builtinChatCommands` names
 * them (same words, same slash names, same gates on [ChatUi.capabilities]). A command a protocol or
 * this build cannot run is simply not listed, the way the Flutter launcher leaves it out; the ones
 * that were not ported are listed in the parity ledger.
 */
@Composable
internal fun rememberChatCommandEntries(
    state: ChatUi,
    actions: ChatActions,
    work: ChatWorkModel?,
    serverCommands: List<ComposerCommand>,
    serverGroup: String,
    onFind: () -> Unit,
    onTasks: () -> Unit,
    onChooseModel: () -> Unit,
    onRename: (() -> Unit)?,
    onInsert: (String) -> Unit,
): List<ChatCommandEntry> {
    val navigate = stringResource(Res.string.chatUiNavigate)
    val current = stringResource(Res.string.chatUiCurrentSession)
    val display = stringResource(Res.string.chatUiTranscriptDisplay)
    val modelGroup = stringResource(Res.string.chatUiModelAndAgent)
    val caps = state.capabilities
    val idle = state.live == null
    val newTitle = stringResource(Res.string.workspaceNewSession)
    val newBody = stringResource(Res.string.chatUiStartACleanSessionInThisWorkspace)
    val sessionsTitle = stringResource(Res.string.usageSessions)
    val sessionsBody = stringResource(Res.string.chatUiFindSessionsAcrossEveryOpenCodeProject)
    val filesTitle = stringResource(Res.string.chatUiProjectFiles)
    val filesBody = stringResource(Res.string.chatUiBrowsePreviewDownloadAndAttachProjectFiles)
    val modelTitle = stringResource(Res.string.chatUiModel)
    val modelBody = stringResource(Res.string.chatUiChooseAServerModelByProviderAnd)
    val toolsTitle = stringResource(Res.string.chatUiToolsAndCapabilities)
    val timelineTitle = stringResource(Res.string.chatUiMessageTimeline)
    val timelineBody = stringResource(Res.string.chatUiFindAMessageJumpToItOr)
    val diffTitle = stringResource(Res.string.chatUiSessionChanges)
    val diffBody = stringResource(Res.string.chatUiReviewTheActualDiffForThisSession)
    val renameTitle = stringResource(Res.string.chatUiRenameSession)
    val renameBody = stringResource(Res.string.chatUiChangeTheTitleShownInTheSession)
    val compactTitle = stringResource(Res.string.chatUiCompactContext)
    val compactBody = stringResource(Res.string.chatUiSummarizeTheSessionUsingTheSelectedModel)
    val thinkingTitle =
        stringResource(
            if (state.reasoningExpanded) Res.string.chatUiCollapseReasoning
            else Res.string.chatUiExpandReasoning
        )
    val thinkingBody = stringResource(Res.string.chatUiToggleLongReasoningDetailsAcrossTheTranscript)
    val undoTitle = stringResource(Res.string.chatUiRevertLastPrompt)
    val undoBody = stringResource(Res.string.revertUndoDescription)
    val shellTitle = stringResource(Res.string.chatUiRunShellCommand)
    val shellBody = stringResource(Res.string.commandSheetShellDescription)
    val retryTitle = stringResource(Res.string.chatUiRetryLastPrompt)
    val retryBody = stringResource(Res.string.commandSheetRetryDescription)
    val plansTitle = stringResource(Res.string.chatUiTodos)
    val plansBody = stringResource(Res.string.commandSheetPlanDescription)
    val reloadTitle = stringResource(Res.string.chatUiReloadMessages)
    val reloadBody = stringResource(Res.string.commandSheetReloadDescription)
    val hasPrompt = state.rows.any { it is io.github.eslamasabry.oc.engine.transcript.state.PromptRow }
    return buildList {
        add(
            ChatCommandEntry("new", "new", newTitle, newBody, navigate, KitIcons.add, listOf("clear"), actions::openNewConversation)
        )
        add(
            ChatCommandEntry(
                "sessions", "sessions", sessionsTitle, sessionsBody, navigate, KitIcons.history,
                listOf("resume", "continue"), actions::openConversations,
            )
        )
        if (caps.files)
            add(ChatCommandEntry("files", "files", filesTitle, filesBody, navigate, KitIcons.files, listOf("open"), actions::openFiles))
        add(ChatCommandEntry("models", "models", modelTitle, modelBody, modelGroup, KitIcons.swap, listOf("model", "mo"), onChooseModel))
        add(ChatCommandEntry("tools", "tools", toolsTitle, null, serverGroup, KitIcons.tools, emptyList(), actions::openTools))
        add(ChatCommandEntry("diff", "diff", diffTitle, diffBody, current, KitIcons.review, listOf("changes"), actions::openChanges))
        if (onRename != null)
            add(ChatCommandEntry("rename", "rename", renameTitle, renameBody, current, KitIcons.edit, emptyList(), onRename))
        add(ChatCommandEntry("timeline", "timeline", timelineTitle, timelineBody, current, KitIcons.timeline, emptyList(), actions::openTimeline))
        add(ChatCommandEntry("find", "find", timelineTitle, null, current, KitIcons.search, listOf("search"), onFind))
        if (caps.compact && idle && hasPrompt)
            add(ChatCommandEntry("compact", "compact", compactTitle, compactBody, current, KitIcons.layers, listOf("summarize"), actions::compactRequested))
        add(ChatCommandEntry("thinking", "thinking", thinkingTitle, thinkingBody, display, KitIcons.idea, listOf("toggle-thinking"), actions::toggleReasoning))
        if (caps.undo && hasPrompt && idle)
            add(ChatCommandEntry("undo", "undo", undoTitle, undoBody, current, KitIcons.undo, listOf("revert"), actions::undoLastPrompt))
        if (work != null && caps.shell)
            add(ChatCommandEntry("shell", "shell", shellTitle, shellBody, current, KitIcons.terminal, emptyList()) { work.openShellDialog() })
        if (hasPrompt && idle)
            add(ChatCommandEntry("retry", "retry", retryTitle, retryBody, current, KitIcons.retry, emptyList(), actions::retryLastPrompt))
        add(ChatCommandEntry("plan", "plan", plansTitle, plansBody, current, KitIcons.checklist, emptyList(), onTasks))
        add(ChatCommandEntry("reload", "reload", reloadTitle, reloadBody, current, KitIcons.retry, emptyList(), actions::retryLoad))
        // The server's own commands run by name from the message box, so they arrive as `/name `.
        if (caps.slashCommands)
            serverCommands.forEach { command ->
                add(
                    ChatCommandEntry(
                        key = "server-${command.name}",
                        slash = command.name,
                        title = "/" + command.name,
                        description = command.description,
                        group = serverGroup,
                        icon = KitIcons.terminal,
                        onRun = { onInsert("/" + command.name + " ") },
                    )
                )
            }
    }
}
