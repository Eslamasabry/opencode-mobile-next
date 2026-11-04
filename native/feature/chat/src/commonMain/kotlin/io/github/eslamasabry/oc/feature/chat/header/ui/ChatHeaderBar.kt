package io.github.eslamasabry.oc.feature.chat.header.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeUnavailable
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ContextLevel
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.DetailsUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderConfirm
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderRules
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderSheet
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.notes.SessionNoteActions
import io.github.eslamasabry.oc.feature.chat.notes.SessionNoteScreen
import io.github.eslamasabry.oc.feature.chat.notes.SessionNoteUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarScope
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuGroup
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusContribution
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitChatMoreMenu
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentToolsTitle
import io.github.eslamasabry.oc.ui.words.res.cardsFromAgentsTitle
import io.github.eslamasabry.oc.ui.words.res.chatFreeModelNote
import io.github.eslamasabry.oc.ui.words.res.chatHeaderArchiveFailed
import io.github.eslamasabry.oc.ui.words.res.chatHeaderContext
import io.github.eslamasabry.oc.ui.words.res.chatHeaderDeleteFailed
import io.github.eslamasabry.oc.ui.words.res.chatHeaderFailureDetails
import io.github.eslamasabry.oc.ui.words.res.chatHeaderForkFailed
import io.github.eslamasabry.oc.ui.words.res.chatHeaderInUse
import io.github.eslamasabry.oc.ui.words.res.chatHeaderNotReported
import io.github.eslamasabry.oc.ui.words.res.chatHeaderRenameFailed
import io.github.eslamasabry.oc.ui.words.res.chatHeaderServer
import io.github.eslamasabry.oc.ui.words.res.chatHeaderShareFailed
import io.github.eslamasabry.oc.ui.words.res.chatHeaderTokens
import io.github.eslamasabry.oc.ui.words.res.chatHeaderUnshareFailed
import io.github.eslamasabry.oc.ui.words.res.chatProjectChipFilesSemantics
import io.github.eslamasabry.oc.ui.words.res.chatProjectServerChipFilesSemantics
import io.github.eslamasabry.oc.ui.words.res.chatRenameEmpty
import io.github.eslamasabry.oc.ui.words.res.chatUiAgent
import io.github.eslamasabry.oc.ui.words.res.chatUiAnyoneWithTheLinkCanViewThis
import io.github.eslamasabry.oc.ui.words.res.chatUiAssistant
import io.github.eslamasabry.oc.ui.words.res.chatUiAttachment
import io.github.eslamasabry.oc.ui.words.res.chatUiCopyTranscript
import io.github.eslamasabry.oc.ui.words.res.chatUiError
import io.github.eslamasabry.oc.ui.words.res.chatUiFile
import io.github.eslamasabry.oc.ui.words.res.chatUiForkSession
import io.github.eslamasabry.oc.ui.words.res.chatUiModel
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenCodeSession
import io.github.eslamasabry.oc.ui.words.res.chatUiRename
import io.github.eslamasabry.oc.ui.words.res.chatUiRenameSession
import io.github.eslamasabry.oc.ui.words.res.chatUiShareSession
import io.github.eslamasabry.oc.ui.words.res.chatUiShareThisSession
import io.github.eslamasabry.oc.ui.words.res.chatUiStopSharing
import io.github.eslamasabry.oc.ui.words.res.chatUiTitle
import io.github.eslamasabry.oc.ui.words.res.chatUiTool
import io.github.eslamasabry.oc.ui.words.res.chatUiUser
import io.github.eslamasabry.oc.ui.words.res.commandUntitledChat
import io.github.eslamasabry.oc.ui.words.res.continueWithFactAgent
import io.github.eslamasabry.oc.ui.words.res.continueWithFactProject
import io.github.eslamasabry.oc.ui.words.res.continueWithLoadFailed
import io.github.eslamasabry.oc.ui.words.res.continueWithMenu
import io.github.eslamasabry.oc.ui.words.res.continueWithModelFailed
import io.github.eslamasabry.oc.ui.words.res.continueWithModelsHere
import io.github.eslamasabry.oc.ui.words.res.continueWithNativeNote
import io.github.eslamasabry.oc.ui.words.res.continueWithNewNote
import io.github.eslamasabry.oc.ui.words.res.continueWithNothingReady
import io.github.eslamasabry.oc.ui.words.res.continueWithPreparing
import io.github.eslamasabry.oc.ui.words.res.continueWithSubtitle
import io.github.eslamasabry.oc.ui.words.res.continueWithTitle
import io.github.eslamasabry.oc.ui.words.res.continueWithTryAgain
import io.github.eslamasabry.oc.ui.words.res.handoffNativeTitle
import io.github.eslamasabry.oc.ui.words.res.handoffSheetCopyCommand
import io.github.eslamasabry.oc.ui.words.res.handoffUiComputerCommandLabel
import io.github.eslamasabry.oc.ui.words.res.handoffUiComputerDirectoryNote
import io.github.eslamasabry.oc.ui.words.res.handoffUiComputerIntro
import io.github.eslamasabry.oc.ui.words.res.handoffUiComputerTitle
import io.github.eslamasabry.oc.ui.words.res.handoffUiComputerVerify
import io.github.eslamasabry.oc.ui.words.res.handoffUiUnavailableDirectory
import io.github.eslamasabry.oc.ui.words.res.handoffUiUnavailableReference
import io.github.eslamasabry.oc.ui.words.res.handoffUiUnavailableWorkspace
import io.github.eslamasabry.oc.ui.words.res.historyLoadedOnly
import io.github.eslamasabry.oc.ui.words.res.importAction
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTopBarMore
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveWorking
import io.github.eslamasabry.oc.ui.words.res.nativeHandoffTitle
import io.github.eslamasabry.oc.ui.words.res.safetyStopSharingBody
import io.github.eslamasabry.oc.ui.words.res.safetyStopSharingKeep
import io.github.eslamasabry.oc.ui.words.res.safetyStopSharingTitle
import io.github.eslamasabry.oc.ui.words.res.sessionArchiveBody
import io.github.eslamasabry.oc.ui.words.res.sessionArchiveTitle
import io.github.eslamasabry.oc.ui.words.res.sessionContextLoading
import io.github.eslamasabry.oc.ui.words.res.sessionContextVerdictFull
import io.github.eslamasabry.oc.ui.words.res.sessionContextVerdictNear
import io.github.eslamasabry.oc.ui.words.res.sessionContextVerdictPlenty
import io.github.eslamasabry.oc.ui.words.res.sessionDeleteBody
import io.github.eslamasabry.oc.ui.words.res.sessionDeleteTitle
import io.github.eslamasabry.oc.ui.words.res.sessionMenuArchive
import io.github.eslamasabry.oc.ui.words.res.sessionMenuDelete
import io.github.eslamasabry.oc.ui.words.res.sessionMenuDetails
import io.github.eslamasabry.oc.ui.words.res.sessionMenuDo
import io.github.eslamasabry.oc.ui.words.res.sessionMenuFind
import io.github.eslamasabry.oc.ui.words.res.sessionMenuGoTo
import io.github.eslamasabry.oc.ui.words.res.sessionMenuNeedsPrompt
import io.github.eslamasabry.oc.ui.words.res.stateNoteTitle
import io.github.eslamasabry.oc.ui.words.res.transcriptFindReasoning
import org.jetbrains.compose.resources.stringResource

/**
 * What the chat screen hands its bar: the header's state and intents (plain values and an
 * interface).
 */
@Immutable
public class ChatHeaderSlot(
    public val state: ChatHeaderUi,
    public val actions: ChatHeaderActions,
    public val approval: ApprovalChipSlot? = null,
    public val note: SessionNoteUi? = null,
    public val noteActions: SessionNoteActions? = null,
)

/**
 * The conversation's bar (Flutter chat_top_bar.dart): Back, the title (tap to rename), "server ·
 * project" under it, then Find and the ⋯ conversation menu. No new bar level: it replaces the
 * chat's plain bar in place.
 */
@Composable
public fun ChatHeaderTopBar(
    slot: ChatHeaderSlot,
    onBack: () -> Unit,
    onFind: () -> Unit,
    // Bar actions other units add (git, saved prompts, preview, the context reading): inline up to
    // the window's
    // limit, the rest at the end of the same ⋯ menu.
    extraActions: List<KitLineAction> = emptyList(),
    onProject: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
) {
    val state = slot.state
    val actions = slot.actions
    val subtitle =
        when {
            state.serverName != null && state.projectName != null ->
                stringResource(Res.string.continueWithSubtitle, state.serverName, state.projectName)
            else -> state.serverName ?: state.projectName
        }
    val transcriptWords = transcriptWords()
    val items =
        menuItems(state, actions, onFind, transcriptWords) +
            listOfNotNull(
                slot.noteActions
                    ?.takeIf { slot.note?.available == true }
                    ?.let { noteActions ->
                        KitMenuItem(
                            stringResource(Res.string.stateNoteTitle),
                            {
                                actions.closeMenu()
                                noteActions.openNote()
                            },
                        )
                    }
            )
    KitTopBar(
        title = state.title ?: stringResource(Res.string.commandUntitledChat),
        subtitle = subtitle,
        scope =
            state.projectName?.let { project ->
                {
                    KitTopBarScope(
                        project = project,
                        server = state.serverName,
                        projectLabel =
                            state.serverName?.let {
                                stringResource(
                                    Res.string.chatProjectServerChipFilesSemantics,
                                    project,
                                    it,
                                )
                            } ?: stringResource(Res.string.chatProjectChipFilesSemantics, project),
                        onProject = onProject,
                        branch = state.branch,
                        onBranch = onBranch,
                    )
                }
            },
        exit = KitTopBarExit.Back,
        exitLabel = stringResource(Res.string.kitTopBarBack),
        onExit = onBack,
        onTitleTap = actions::renameRequested,
        titleTapLabel = stringResource(Res.string.chatUiRenameSession),
        actions = extraActions,
        more = { hidden ->
            KitChatMoreMenu(
                label = stringResource(Res.string.kitTopBarMore),
                expanded = state.menuOpen,
                items =
                    items +
                        hidden.map { extra ->
                            KitMenuItem(
                                label = extra.label,
                                onSelect = {
                                    actions.closeMenu()
                                    extra.onClick()
                                },
                                enabled =
                                    if (extra.enabled) KitEnabled.Enabled
                                    else KitEnabled.Disabled(extra.disabledReason.orEmpty()),
                            )
                        },
                onOpen = actions::openMenu,
                onDismissRequest = actions::closeMenu,
            )
        },
    )
    // A failed act is the one status line under the bar: plain words, Details on demand,
    // dismissible.
    var detailsOpen by remember { mutableStateOf<Long?>(null) }
    val failure = state.failure
    KitStatusContribution(
        failure?.let {
            KitStatus(
                KitStatusKind.Info,
                failureWords(it.action),
                id = "chat-header-failure-${it.id}",
                action =
                    KitLineAction(
                        stringResource(Res.string.chatHeaderFailureDetails),
                        { detailsOpen = it.id },
                    ),
                onDismiss = actions::dismissFailure,
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
            )
        }
    )
    // "Free model, slower": one quiet line, below every real status (it never outranks a failure).
    KitStatusContribution(
        if (!state.freeModelNote) null
        else
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.chatFreeModelNote),
                id = "chat-free-model",
                onDismiss = actions::dismissFreeNote,
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
            )
    )
    KitSheet(
        visible = failure != null && detailsOpen == failure.id,
        onDismissRequest = { detailsOpen = null },
        title = stringResource(Res.string.chatHeaderFailureDetails),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        failure?.let { KitErrorNotice(message = failureWords(it.action), failure = it.failure) }
    }
}

/**
 * The header's dialogs, sheets and clipboard effect; drawn once, over the chat (same modal layer).
 */
@Composable
public fun ChatHeaderOverlays(slot: ChatHeaderSlot) {
    val note = slot.note
    val noteActions = slot.noteActions
    if (note != null && noteActions != null) SessionNoteScreen(note, noteActions)
    val state = slot.state
    val actions = slot.actions
    val empty = stringResource(Res.string.chatRenameEmpty)
    KitInputDialog(
        visible = state.renaming != null,
        title = stringResource(Res.string.chatUiRenameSession),
        fieldLabel = stringResource(Res.string.chatUiTitle),
        confirmLabel = stringResource(Res.string.chatUiRename),
        initial = state.renaming.orEmpty(),
        validate = { if (HeaderRules.renameValid(it)) null else empty },
        onResult = actions::renameDone,
    )
    val untitled = stringResource(Res.string.commandUntitledChat)
    KitConfirm(
        spec =
            when (val c = state.confirm) {
                null -> null
                HeaderConfirm.Share ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.chatUiShareThisSession),
                        confirmLabel = stringResource(Res.string.chatUiShareSession),
                        kind = KitConfirmKind.Neutral,
                        body = stringResource(Res.string.chatUiAnyoneWithTheLinkCanViewThis),
                    )
                HeaderConfirm.Unshare ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.safetyStopSharingTitle),
                        confirmLabel = stringResource(Res.string.chatUiStopSharing),
                        kind = KitConfirmKind.Stop,
                        body = stringResource(Res.string.safetyStopSharingBody),
                        cancelLabel = stringResource(Res.string.safetyStopSharingKeep),
                    )
                is HeaderConfirm.Archive ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.sessionArchiveTitle, c.title ?: untitled),
                        confirmLabel = stringResource(Res.string.sessionMenuArchive),
                        kind = KitConfirmKind.Neutral,
                        body = stringResource(Res.string.sessionArchiveBody),
                    )
                is HeaderConfirm.Delete ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.sessionDeleteTitle, c.title ?: untitled),
                        confirmLabel = stringResource(Res.string.sessionMenuDelete),
                        kind = KitConfirmKind.Destructive,
                        body = stringResource(Res.string.sessionDeleteBody),
                    )
            },
        onConfirm = actions::confirm,
        onCancel = actions::dismissConfirm,
    )
    val sheet = state.sheet
    KitSheet(
        visible = sheet is HeaderSheet.Details,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.sessionMenuDetails),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        (sheet as? HeaderSheet.Details)?.let { DetailsBody(it.facts) }
    }
    KitSheet(
        visible = sheet is HeaderSheet.ContinueWith,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.continueWithTitle),
        subtitle =
            listOfNotNull(state.title, state.projectName)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" · "),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        (sheet as? HeaderSheet.ContinueWith)?.let {
            ContinueBody(it, state.working == HeaderAction.ContinueWith, actions)
        }
    }
    KitSheet(
        visible = sheet is HeaderSheet.ContinueOnComputer,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.handoffUiComputerTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        (sheet as? HeaderSheet.ContinueOnComputer)?.let { ComputerBody(it, actions) }
    }
    KitSheet(
        visible = sheet is HeaderSheet.ImportConversation,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.importAction),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        (sheet as? HeaderSheet.ImportConversation)?.let { SessionImportBody(it.state, actions) }
    }
    KitSheet(
        visible = sheet is HeaderSheet.Handoff,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.nativeHandoffTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        (sheet as? HeaderSheet.Handoff)?.let { HandoffBody(it, actions) }
    }
    val clip = state.clip
    KitClipboardEffect(clip?.id, clip?.text, actions::clipDone)
}

@Composable
private fun DetailsBody(facts: DetailsUi) {
    val none = stringResource(Res.string.chatHeaderNotReported)
    Column(Modifier.fillMaxWidth().testTag("chat-details-sheet")) {
        KitRowGroup {
            KitRow(
                stringResource(Res.string.chatHeaderServer),
                trailing = KitRowTrailing.Value(facts.serverName ?: none, chevron = false),
            )
            KitRow(
                stringResource(Res.string.continueWithFactProject),
                trailing = KitRowTrailing.Value(facts.projectName ?: none, chevron = false),
            )
            KitRow(
                stringResource(Res.string.chatUiModel),
                trailing = KitRowTrailing.Value(facts.model ?: none, chevron = false),
            )
            KitRow(
                stringResource(Res.string.chatUiAgent),
                trailing = KitRowTrailing.Value(facts.agent ?: none, chevron = false),
            )
            val percent = facts.contextPercent
            val context =
                if (percent == null) none
                else
                    when (facts.contextLevel) {
                        ContextLevel.Full ->
                            stringResource(
                                Res.string.sessionContextVerdictFull,
                                Numbers.format(percent),
                            )
                        ContextLevel.Warning ->
                            stringResource(
                                Res.string.sessionContextVerdictNear,
                                Numbers.format(percent),
                            )
                        else ->
                            stringResource(
                                Res.string.sessionContextVerdictPlenty,
                                Numbers.format(percent),
                            )
                    }
            KitRow(
                stringResource(Res.string.chatHeaderContext),
                trailing = KitRowTrailing.Value(context, chevron = false),
            )
            facts.totalTokens?.let {
                KitRow(
                    stringResource(Res.string.chatHeaderTokens),
                    trailing = KitRowTrailing.Value(Numbers.format(it), chevron = false),
                )
            }
        }
    }
}

/**
 * Flutter `_ContinueOnComputerBody`: the whole command, wrapped, with its own Copy; or why there is
 * none.
 */
@Composable
private fun ComputerBody(sheet: HeaderSheet.ContinueOnComputer, actions: ChatHeaderActions) {
    val command = sheet.command
    Column(Modifier.fillMaxWidth().testTag("continue-on-computer-sheet")) {
        KitNotice(message = stringResource(Res.string.handoffUiComputerIntro, command.binary))
        val text = command.command
        if (text != null) {
            KitSectionLabel(stringResource(Res.string.handoffUiComputerCommandLabel))
            KitCodeBlock(
                source = text,
                kind = KitCodeKind.Command,
                modifier = Modifier.testTag("continue-on-computer-command"),
                wrap = true,
                copyLabel = stringResource(Res.string.handoffSheetCopyCommand),
                copiedLabel = stringResource(Res.string.kitCopied),
                onCopy = { actions.copyResumeCommand() },
            )
            KitNotice(message = stringResource(Res.string.handoffUiComputerDirectoryNote))
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                notes =
                    listOf(
                        stringResource(
                            Res.string.handoffUiComputerVerify,
                            RESUME_VERIFIED_VERSIONS,
                            command.binary,
                        )
                    ),
            )
        } else {
            KitNotice(
                modifier = Modifier.testTag("continue-on-computer-unavailable"),
                message =
                    when (command.unavailable) {
                        SessionResumeUnavailable.ManagedWorkspace ->
                            stringResource(Res.string.handoffUiUnavailableWorkspace)
                        SessionResumeUnavailable.InvalidSessionId ->
                            stringResource(Res.string.handoffUiUnavailableReference)
                        else -> stringResource(Res.string.handoffUiUnavailableDirectory)
                    },
            )
        }
    }
}

/**
 * The CLI versions the resume syntax was verified against (Flutter
 * `sessionResumeVerifiedVersions`).
 */
private const val RESUME_VERIFIED_VERSIONS = "opencode 1.18.25, opencode2 beta-19242"

@Composable
private fun ContinueBody(
    sheet: HeaderSheet.ContinueWith,
    working: Boolean,
    actions: ChatHeaderActions,
) {
    val handoffActions =
        actions
            as? io.github.eslamasabry.oc.feature.chat.header.presentation.ChatAgentHandoffActions
    if (sheet.draft != null && handoffActions != null) {
        AgentHandoffReview(sheet, working, handoffActions)
        return
    }
    Column(Modifier.fillMaxWidth().testTag("continue-with-sheet")) {
        KitNotice(message = stringResource(Res.string.continueWithNativeNote))
        if (working) KitNotice(message = stringResource(Res.string.continueWithPreparing))
        when {
            sheet.failure != null && sheet.choices.isEmpty() ->
                KitErrorNotice(
                    message = stringResource(Res.string.continueWithLoadFailed),
                    failure = sheet.failure,
                    retry =
                        KitLineAction(
                            stringResource(Res.string.continueWithTryAgain),
                            actions::retryContinueWith,
                        ),
                )
            sheet.loading -> KitNotice(message = stringResource(Res.string.sessionContextLoading))
            sheet.agents.isEmpty() && sheet.choices.size <= 1 && sheet.choices.all { it.current } ->
                KitNotice(message = stringResource(Res.string.continueWithNothingReady))
            else -> {
                if (sheet.failure != null)
                    KitErrorNotice(
                        message = stringResource(Res.string.continueWithModelFailed),
                        failure = sheet.failure,
                    )
                KitSectionLabel(stringResource(Res.string.continueWithModelsHere))
                KitRowGroup {
                    sheet.choices.forEach { choice ->
                        KitRow(
                            title = choice.label,
                            supporting = choice.providerId,
                            trailing =
                                if (choice.current)
                                    KitRowTrailing.Value(
                                        stringResource(Res.string.chatHeaderInUse),
                                        chevron = false,
                                    )
                                else KitRowTrailing.Chevron,
                            enabled = !working,
                            onClick = { actions.continueWith(choice) },
                        )
                    }
                }
                if (sheet.agents.isNotEmpty() && handoffActions != null) {
                    KitSectionLabel(stringResource(Res.string.continueWithFactAgent))
                    KitNotice(message = stringResource(Res.string.continueWithNewNote))
                    KitRowGroup {
                        sheet.agents.forEach { choice ->
                            KitRow(
                                title = choice.label,
                                trailing = KitRowTrailing.Chevron,
                                enabled = !working,
                                onClick = { handoffActions.prepareHandoff(choice) },
                            )
                        }
                    }
                }
                sheet.handoffFailure?.let { AgentHandoffFailure(it) }
            }
        }
    }
}

@Composable
private fun menuItems(
    state: ChatHeaderUi,
    actions: ChatHeaderActions,
    onFind: () -> Unit,
    words: TranscriptWords,
): List<KitMenuItem> {
    val goTo = KitMenuGroup("go-to", stringResource(Res.string.sessionMenuGoTo))
    val act = KitMenuGroup("do", stringResource(Res.string.sessionMenuDo))
    val needsPrompt = stringResource(Res.string.sessionMenuNeedsPrompt)
    val mask = rememberKitMask()
    val working = stringResource(Res.string.kitTurnLiveWorking)
    return state.menu.map { action ->
        val enabled =
            when {
                state.working == action -> KitEnabled.Disabled(working)
                HeaderRules.needsPrompt(action) && !state.hasPrompt ->
                    KitEnabled.Disabled(needsPrompt)
                else -> KitEnabled.Enabled
            }
        KitMenuItem(
            label = menuWords(action),
            onSelect = {
                when (action) {
                    HeaderAction.Find -> {
                        actions.closeMenu()
                        onFind()
                    }
                    HeaderAction.CopyTranscript -> actions.copyTranscript(words, mask)
                    else -> actions.choose(action)
                }
            },
            group =
                when (action) {
                    HeaderAction.Find,
                    HeaderAction.Details -> goTo
                    HeaderAction.Delete -> null
                    else -> act
                },
            destructive = action == HeaderAction.Delete,
            enabled = enabled,
        )
    }
}

@Composable
private fun menuWords(action: HeaderAction): String =
    when (action) {
        HeaderAction.Find -> stringResource(Res.string.sessionMenuFind)
        HeaderAction.Details -> stringResource(Res.string.sessionMenuDetails)
        HeaderAction.Share -> stringResource(Res.string.chatUiShareSession)
        HeaderAction.Unshare -> stringResource(Res.string.chatUiStopSharing)
        HeaderAction.Fork -> stringResource(Res.string.chatUiForkSession)
        HeaderAction.Rename -> stringResource(Res.string.chatUiRenameSession)
        HeaderAction.ContinueWith -> stringResource(Res.string.continueWithMenu)
        HeaderAction.ContinueOnComputer -> stringResource(Res.string.handoffUiComputerTitle)
        HeaderAction.ImportConversation -> stringResource(Res.string.importAction)
        HeaderAction.Handoff -> stringResource(Res.string.nativeHandoffTitle)
        HeaderAction.AgentHandoff -> stringResource(Res.string.handoffNativeTitle)
        HeaderAction.AgentOptions -> stringResource(Res.string.agentToolsTitle)
        HeaderAction.CardsSettings -> stringResource(Res.string.cardsFromAgentsTitle)
        HeaderAction.CopyTranscript -> stringResource(Res.string.chatUiCopyTranscript)
        HeaderAction.Archive -> stringResource(Res.string.sessionMenuArchive)
        HeaderAction.Delete -> stringResource(Res.string.sessionMenuDelete)
    }

@Composable
private fun failureWords(action: HeaderAction): String =
    when (action) {
        HeaderAction.Rename -> stringResource(Res.string.chatHeaderRenameFailed)
        HeaderAction.Share -> stringResource(Res.string.chatHeaderShareFailed)
        HeaderAction.Unshare -> stringResource(Res.string.chatHeaderUnshareFailed)
        HeaderAction.Fork -> stringResource(Res.string.chatHeaderForkFailed)
        HeaderAction.Archive -> stringResource(Res.string.chatHeaderArchiveFailed)
        HeaderAction.Delete -> stringResource(Res.string.chatHeaderDeleteFailed)
        else -> stringResource(Res.string.continueWithModelFailed)
    }

@Composable
private fun transcriptWords(): TranscriptWords {
    val untitled = stringResource(Res.string.chatUiOpenCodeSession)
    val user = stringResource(Res.string.chatUiUser)
    val assistant = stringResource(Res.string.chatUiAssistant)
    val reasoning = stringResource(Res.string.transcriptFindReasoning)
    val partial = stringResource(Res.string.historyLoadedOnly)
    val attachment = stringResource(Res.string.chatUiAttachment)
    val file = stringResource(Res.string.chatUiFile)
    val tool = stringResource(Res.string.chatUiTool)
    val error = stringResource(Res.string.chatUiError)
    return remember(untitled, user, assistant, reasoning, partial, attachment, file, tool, error) {
        TranscriptWords(
            untitled,
            user,
            assistant,
            reasoning,
            partial,
            attachment,
            file,
            tool,
            error,
        )
    }
}
