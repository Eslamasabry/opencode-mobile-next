package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.rememberTranscriptActs
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPlace
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerCommand
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiRestoreRevertedPrompt
import io.github.eslamasabry.oc.ui.words.res.chatUiRestoreTheCurrentlyRevertedSessionState
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
import io.github.eslamasabry.oc.ui.words.res.chatUiProjectsAndWorkspaces
import io.github.eslamasabry.oc.ui.words.res.chatUiSwitchProjectDirectoryOrWorktree
import io.github.eslamasabry.oc.ui.words.res.chatUiPromptEditor
import io.github.eslamasabry.oc.ui.words.res.chatUiEditTheCurrentPromptInAFocused
import io.github.eslamasabry.oc.ui.words.res.chatUiProjectHealth
import io.github.eslamasabry.oc.ui.words.res.chatUiInspectGitLanguageServicesAndFormattersFor
import io.github.eslamasabry.oc.ui.words.res.libraryTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenPersistentWorkspaceTerminals
import io.github.eslamasabry.oc.ui.words.res.chatUiAgent
import io.github.eslamasabry.oc.ui.words.res.chatUiChooseTheActiveOpenCodeAgent
import io.github.eslamasabry.oc.ui.words.res.modelThinkingMode
import io.github.eslamasabry.oc.ui.words.res.chatUiChooseTheCurrentModelVariantOrReasoning
import io.github.eslamasabry.oc.ui.words.res.chatUiMCPServers
import io.github.eslamasabry.oc.ui.words.res.chatUiInspectMCPStatusAuthenticationAndResources
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectProvider
import io.github.eslamasabry.oc.ui.words.res.chatUiManageProviderAndIntegrationAuthentication
import io.github.eslamasabry.oc.ui.words.res.chatUiSkills
import io.github.eslamasabry.oc.ui.words.res.chatUiBrowseProjectAndGlobalSkills
import io.github.eslamasabry.oc.ui.words.res.chatUiProjectReferences
import io.github.eslamasabry.oc.ui.words.res.chatUiAddAnOpenCodeProjectReferenceToThis
import io.github.eslamasabry.oc.ui.words.res.chatUiServerStatus
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectionHealthServerVersionAndLiveMode
import io.github.eslamasabry.oc.ui.words.res.chatUiAppDiagnostics
import io.github.eslamasabry.oc.ui.words.res.chatUiReviewHandledAppErrorsAndSendA
import io.github.eslamasabry.oc.ui.words.res.chatUiAppearance
import io.github.eslamasabry.oc.ui.words.res.chatUiFollowAndroidOrChooseTheNativeLight
import io.github.eslamasabry.oc.ui.words.res.chatUiSessionContext
import io.github.eslamasabry.oc.ui.words.res.chatUiInspectCurrentTokensCacheCostAndContext
import io.github.eslamasabry.oc.ui.words.res.chatUiShareSession
import io.github.eslamasabry.oc.ui.words.res.chatUiCopyShareLink
import io.github.eslamasabry.oc.ui.words.res.chatUiCreateOrCopyAPublicSessionLink
import io.github.eslamasabry.oc.ui.words.res.chatUiStopSharing
import io.github.eslamasabry.oc.ui.words.res.chatUiDisableTheCurrentPublicSessionLink
import io.github.eslamasabry.oc.ui.words.res.chatUiForkSession
import io.github.eslamasabry.oc.ui.words.res.sessionMenuForkHint
import io.github.eslamasabry.oc.ui.words.res.chatUiCopyTranscript
import io.github.eslamasabry.oc.ui.words.res.chatUiCopyTheRenderedConversationAsMarkdown
import io.github.eslamasabry.oc.ui.words.res.chatUiExportTranscript
import io.github.eslamasabry.oc.ui.words.res.chatUiSaveTheConversationAsAMarkdownFile
import io.github.eslamasabry.oc.ui.words.res.sessionNoteTitle
import io.github.eslamasabry.oc.ui.words.res.commandSheetNoteDescription
import io.github.eslamasabry.oc.ui.words.res.approvalsUiMenu
import io.github.eslamasabry.oc.ui.words.res.commandSheetApprovalsDescription
import io.github.eslamasabry.oc.ui.words.res.chatUiCompose
import io.github.eslamasabry.oc.ui.words.res.chatUiHideTimestamps
import io.github.eslamasabry.oc.ui.words.res.chatUiShowTimestamps
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
    header: ChatHeaderSlot? = null,
    onContext: (() -> Unit)? = null,
    onEditor: (() -> Unit)? = null,
    /** C10: null where the device cannot save the choice; true = timestamps shown now. */
    timestamps: Pair<Boolean, () -> Unit>? = null,
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
    val timestampsTitle =
        stringResource(
            if (timestamps?.first == true) Res.string.chatUiHideTimestamps
            else Res.string.chatUiShowTimestamps
        )
    val undoTitle = stringResource(Res.string.chatUiRevertLastPrompt)
    val undoBody = stringResource(Res.string.revertUndoDescription)
    val redoTitle = stringResource(Res.string.chatUiRestoreRevertedPrompt)
    val redoBody = stringResource(Res.string.chatUiRestoreTheCurrentlyRevertedSessionState)
    val shellTitle = stringResource(Res.string.chatUiRunShellCommand)
    val shellBody = stringResource(Res.string.commandSheetShellDescription)
    val retryTitle = stringResource(Res.string.chatUiRetryLastPrompt)
    val retryBody = stringResource(Res.string.commandSheetRetryDescription)
    val plansTitle = stringResource(Res.string.chatUiTodos)
    val plansBody = stringResource(Res.string.commandSheetPlanDescription)
    val reloadTitle = stringResource(Res.string.chatUiReloadMessages)
    val reloadBody = stringResource(Res.string.commandSheetReloadDescription)
    val compose = stringResource(Res.string.chatUiCompose)
    val more =
        MoreWords(
            workspaces = stringResource(Res.string.chatUiProjectsAndWorkspaces) to stringResource(Res.string.chatUiSwitchProjectDirectoryOrWorktree),
            editor = stringResource(Res.string.chatUiPromptEditor) to stringResource(Res.string.chatUiEditTheCurrentPromptInAFocused),
            health = stringResource(Res.string.chatUiProjectHealth) to stringResource(Res.string.chatUiInspectGitLanguageServicesAndFormattersFor),
            terminal = stringResource(Res.string.libraryTerminalTitle) to stringResource(Res.string.chatUiOpenPersistentWorkspaceTerminals),
            agents = stringResource(Res.string.chatUiAgent) to stringResource(Res.string.chatUiChooseTheActiveOpenCodeAgent),
            variants = stringResource(Res.string.modelThinkingMode) to stringResource(Res.string.chatUiChooseTheCurrentModelVariantOrReasoning),
            mcps = stringResource(Res.string.chatUiMCPServers) to stringResource(Res.string.chatUiInspectMCPStatusAuthenticationAndResources),
            connect = stringResource(Res.string.chatUiConnectProvider) to stringResource(Res.string.chatUiManageProviderAndIntegrationAuthentication),
            skills = stringResource(Res.string.chatUiSkills) to stringResource(Res.string.chatUiBrowseProjectAndGlobalSkills),
            references = stringResource(Res.string.chatUiProjectReferences) to stringResource(Res.string.chatUiAddAnOpenCodeProjectReferenceToThis),
            status = stringResource(Res.string.chatUiServerStatus) to stringResource(Res.string.chatUiConnectionHealthServerVersionAndLiveMode),
            debug = stringResource(Res.string.chatUiAppDiagnostics) to stringResource(Res.string.chatUiReviewHandledAppErrorsAndSendA),
            themes = stringResource(Res.string.chatUiAppearance) to stringResource(Res.string.chatUiFollowAndroidOrChooseTheNativeLight),
            context = stringResource(Res.string.chatUiSessionContext) to stringResource(Res.string.chatUiInspectCurrentTokensCacheCostAndContext),
            share = stringResource(Res.string.chatUiShareSession) to stringResource(Res.string.chatUiCreateOrCopyAPublicSessionLink),
            shareLink = stringResource(Res.string.chatUiCopyShareLink) to stringResource(Res.string.chatUiCreateOrCopyAPublicSessionLink),
            unshare = stringResource(Res.string.chatUiStopSharing) to stringResource(Res.string.chatUiDisableTheCurrentPublicSessionLink),
            fork = stringResource(Res.string.chatUiForkSession) to stringResource(Res.string.sessionMenuForkHint),
            copy = stringResource(Res.string.chatUiCopyTranscript) to stringResource(Res.string.chatUiCopyTheRenderedConversationAsMarkdown),
            export = stringResource(Res.string.chatUiExportTranscript) to stringResource(Res.string.chatUiSaveTheConversationAsAMarkdownFile),
            note = stringResource(Res.string.sessionNoteTitle) to stringResource(Res.string.commandSheetNoteDescription),
            approvals = stringResource(Res.string.approvalsUiMenu) to stringResource(Res.string.commandSheetApprovalsDescription),
        )
    val transcript = header?.let { rememberTranscriptActs(it.actions) }
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
        add(more.entry("workspaces", more.workspaces, navigate, KitIcons.folders, listOf("workspace")) { actions.openPlace(ChatPlace.Workspaces) })
        if (onEditor != null)
            add(more.entry("editor", more.editor, compose, KitIcons.edit, emptyList(), onEditor))
        if (caps.health)
            add(more.entry("health", more.health, navigate, KitIcons.checklist, emptyList()) { actions.openPlace(ChatPlace.Health) })
        if (caps.terminal)
            add(more.entry("terminal", more.terminal, navigate, KitIcons.terminal, emptyList()) { actions.openPlace(ChatPlace.Terminal) })
        add(ChatCommandEntry("models", "models", modelTitle, modelBody, modelGroup, KitIcons.swap, listOf("model", "mo"), onChooseModel))
        if (caps.agents)
            add(more.entry("agents", more.agents, modelGroup, KitIcons.swap, listOf("agent"), onChooseModel))
        if (caps.serverCatalog)
            add(more.entry("variants", more.variants, modelGroup, KitIcons.idea, emptyList(), onChooseModel))
        add(more.entry("mcps", more.mcps, serverGroup, KitIcons.tools, listOf("mcp")) { actions.openPlace(ChatPlace.McpServers) })
        add(more.entry("connect", more.connect, serverGroup, KitIcons.tools, emptyList()) { actions.openPlace(ChatPlace.Providers) })
        add(more.entry("skills", more.skills, serverGroup, KitIcons.tools, emptyList()) { actions.openPlace(ChatPlace.Skills) })
        add(more.entry("references", more.references, serverGroup, KitIcons.files, listOf("reference", "refs")) { actions.openPlace(ChatPlace.References) })
        add(more.entry("status", more.status, serverGroup, KitIcons.idea, emptyList()) { actions.openPlace(ChatPlace.ServerStatus) })
        add(more.entry("debug", more.debug, serverGroup, KitIcons.review, emptyList()) { actions.openPlace(ChatPlace.Diagnostics) })
        add(more.entry("themes", more.themes, display, KitIcons.idea, listOf("theme")) { actions.openPlace(ChatPlace.Appearance) })
        add(ChatCommandEntry("tools", "tools", toolsTitle, null, serverGroup, KitIcons.tools, emptyList(), actions::openTools))
        add(ChatCommandEntry("diff", "diff", diffTitle, diffBody, current, KitIcons.review, listOf("changes"), actions::openChanges))
        if (onRename != null)
            add(ChatCommandEntry("rename", "rename", renameTitle, renameBody, current, KitIcons.edit, emptyList(), onRename))
        if (onContext != null)
            add(more.entry("context", more.context, current, KitIcons.layers, listOf("usage"), onContext))
        // The header's ⋯ menu is the truth for what this conversation can share, fork and copy.
        val menu = header?.state?.menu.orEmpty()
        val choose: (HeaderAction) -> () -> Unit = { a -> { header?.actions?.choose(a) } }
        if (HeaderAction.Share in menu)
            add(more.entry("share", more.share, current, KitIcons.link, emptyList(), choose(HeaderAction.Share)))
        if (HeaderAction.CopyShareLink in menu)
            add(more.entry("share", more.shareLink, current, KitIcons.link, emptyList(), choose(HeaderAction.CopyShareLink)))
        if (HeaderAction.Unshare in menu)
            add(more.entry("unshare", more.unshare, current, KitIcons.unlink, emptyList(), choose(HeaderAction.Unshare)))
        if (HeaderAction.Fork in menu && hasPrompt)
            add(more.entry("fork", more.fork, current, KitIcons.swap, emptyList(), choose(HeaderAction.Fork)))
        if (HeaderAction.CopyTranscript in menu && transcript != null)
            add(more.entry("copy", more.copy, current, KitIcons.copy, emptyList(), transcript.copy))
        if (HeaderAction.ExportTranscript in menu && transcript != null)
            add(more.entry("export", more.export, current, KitIcons.download, emptyList(), transcript.export))
        header?.noteActions?.let { note ->
            add(more.entry("note", more.note, current, KitIcons.edit, emptyList(), note::openNote))
        }
        header?.approval?.takeIf { caps.approvals && it.state.offered }?.let { slot ->
            add(more.entry("approvals", more.approvals, current, KitIcons.checklist, emptyList(), slot.actions::openApprovalMenu))
        }
        add(ChatCommandEntry("timeline", "timeline", timelineTitle, timelineBody, current, KitIcons.timeline, emptyList(), actions::openTimeline))
        add(ChatCommandEntry("find", "find", timelineTitle, null, current, KitIcons.search, listOf("search"), onFind))
        if (caps.compact && idle && hasPrompt)
            add(ChatCommandEntry("compact", "compact", compactTitle, compactBody, current, KitIcons.layers, listOf("summarize"), actions::compactRequested))
        add(ChatCommandEntry("thinking", "thinking", thinkingTitle, thinkingBody, display, KitIcons.idea, listOf("toggle-thinking"), actions::toggleReasoning))
        // C10 (Flutter `timestamps` command): the same choice as Settings, one tap from the chat.
        if (timestamps != null)
            add(ChatCommandEntry("timestamps", "timestamps", timestampsTitle, null, display, KitIcons.clock, emptyList(), timestamps.second))
        if (caps.undo && hasPrompt && idle)
            add(ChatCommandEntry("undo", "undo", undoTitle, undoBody, current, KitIcons.undo, listOf("revert"), actions::undoLastPrompt))
        // S28 story 3 (Flutter `/redo`): undone turns come back (a staged undo opens its review page).
        if (caps.undo && state.canPutBack)
            add(ChatCommandEntry("redo", "redo", redoTitle, redoBody, current, KitIcons.restore, listOf("restore"), actions::putBack))
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
                        key = SERVER_COMMAND_KEY + command.name,
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

/** A server command's entry key starts with this; the "/" menu puts its `/name ` in the box instead of running it. */
internal const val SERVER_COMMAND_KEY: String = "server-"

/** S24-CHAT: the added commands' words (title to description), read once per composition. */
private class MoreWords(
    val workspaces: Pair<String, String>,
    val editor: Pair<String, String>,
    val health: Pair<String, String>,
    val terminal: Pair<String, String>,
    val agents: Pair<String, String>,
    val variants: Pair<String, String>,
    val mcps: Pair<String, String>,
    val connect: Pair<String, String>,
    val skills: Pair<String, String>,
    val references: Pair<String, String>,
    val status: Pair<String, String>,
    val debug: Pair<String, String>,
    val themes: Pair<String, String>,
    val context: Pair<String, String>,
    val share: Pair<String, String>,
    val shareLink: Pair<String, String>,
    val unshare: Pair<String, String>,
    val fork: Pair<String, String>,
    val copy: Pair<String, String>,
    val export: Pair<String, String>,
    val note: Pair<String, String>,
    val approvals: Pair<String, String>,
) {
    fun entry(
        slash: String,
        words: Pair<String, String>,
        group: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector?,
        aliases: List<String>,
        onRun: () -> Unit,
    ): ChatCommandEntry = ChatCommandEntry(slash, slash, words.first, words.second, group, icon, aliases, onRun)
}
