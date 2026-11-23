package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkCounts
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTask
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTiming
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolDuration
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolPhrase
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ShellIntent
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ShellKind
import io.github.eslamasabry.oc.feature.chat.presentation.StepFamily
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.text.KitDurationStyle
import io.github.eslamasabry.oc.ui.kit.text.kitElapsedSecondsMinutes
import io.github.eslamasabry.oc.ui.kit.text.kitMinutesDurationWords
import io.github.eslamasabry.oc.ui.kit.text.kitTenthsSecondsWords
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallNotice
import io.github.eslamasabry.oc.engine.transcript.turnfacts.formatRetryCountdown
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolDuration
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnResultUi
import io.github.eslamasabry.oc.feature.chat.presentation.WorkKind
import io.github.eslamasabry.oc.feature.chat.presentation.WorkLineRules
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatStalledOnTool
import io.github.eslamasabry.oc.ui.words.res.chatStuckCommand
import io.github.eslamasabry.oc.ui.words.res.chatStuckUnnamed
import io.github.eslamasabry.oc.ui.words.res.activeContextShell
import io.github.eslamasabry.oc.ui.words.res.activeContextSkill
import io.github.eslamasabry.oc.ui.words.res.aiteamComponentStageTook
import io.github.eslamasabry.oc.ui.words.res.chatStallConnectionLost
import io.github.eslamasabry.oc.ui.words.res.chatStallConnectionUnchecked
import io.github.eslamasabry.oc.ui.words.res.chatStallHelperDown
import io.github.eslamasabry.oc.ui.words.res.chatQuietThinkingHint
import io.github.eslamasabry.oc.ui.words.res.chatWorkWaitingEdit
import io.github.eslamasabry.oc.ui.words.res.chatWorkWaitingOther
import io.github.eslamasabry.oc.ui.words.res.chatWorkWaitingRun
import io.github.eslamasabry.oc.ui.words.res.chatUiAgent
import io.github.eslamasabry.oc.ui.words.res.chatUiApplyPatch
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactingConversation
import io.github.eslamasabry.oc.ui.words.res.chatUiEdit
import io.github.eslamasabry.oc.ui.words.res.chatUiFetchPage
import io.github.eslamasabry.oc.ui.words.res.chatUiFindFiles
import io.github.eslamasabry.oc.ui.words.res.chatUiLanguageServer
import io.github.eslamasabry.oc.ui.words.res.chatUiList
import io.github.eslamasabry.oc.ui.words.res.chatUiLoadTools
import io.github.eslamasabry.oc.ui.words.res.chatUiStopBackgroundTask
import io.github.eslamasabry.oc.ui.words.res.chatUiReadTaskOutput
import io.github.eslamasabry.oc.ui.words.res.chatLiveReading
import io.github.eslamasabry.oc.ui.words.res.chatLiveReadingAny
import io.github.eslamasabry.oc.ui.words.res.liveStepRunning
import io.github.eslamasabry.oc.ui.words.res.liveStepRunningCommand
import io.github.eslamasabry.oc.ui.words.res.teamAgentStepCommand
import io.github.eslamasabry.oc.ui.words.res.teamAgentStepRead
import io.github.eslamasabry.oc.ui.words.res.stepShellAnalyze
import io.github.eslamasabry.oc.ui.words.res.stepShellAnalyzeNow
import io.github.eslamasabry.oc.ui.words.res.stepShellBuild
import io.github.eslamasabry.oc.ui.words.res.stepShellBuildNow
import io.github.eslamasabry.oc.ui.words.res.stepShellChangeFiles
import io.github.eslamasabry.oc.ui.words.res.stepShellChangeFilesNow
import io.github.eslamasabry.oc.ui.words.res.stepShellChanges
import io.github.eslamasabry.oc.ui.words.res.stepShellChangesNow
import io.github.eslamasabry.oc.ui.words.res.stepShellDelete
import io.github.eslamasabry.oc.ui.words.res.stepShellDeleteNow
import io.github.eslamasabry.oc.ui.words.res.stepShellFetch
import io.github.eslamasabry.oc.ui.words.res.stepShellFetchNow
import io.github.eslamasabry.oc.ui.words.res.stepShellInstall
import io.github.eslamasabry.oc.ui.words.res.stepShellInstallNow
import io.github.eslamasabry.oc.ui.words.res.stepShellList
import io.github.eslamasabry.oc.ui.words.res.stepShellListNow
import io.github.eslamasabry.oc.ui.words.res.stepShellProcesses
import io.github.eslamasabry.oc.ui.words.res.stepShellProcessesNow
import io.github.eslamasabry.oc.ui.words.res.stepShellProgram
import io.github.eslamasabry.oc.ui.words.res.stepShellReadMany
import io.github.eslamasabry.oc.ui.words.res.stepShellReadManyNow
import io.github.eslamasabry.oc.ui.words.res.stepShellReadOne
import io.github.eslamasabry.oc.ui.words.res.stepShellSearch
import io.github.eslamasabry.oc.ui.words.res.stepShellSearchNow
import io.github.eslamasabry.oc.ui.words.res.stepShellStop
import io.github.eslamasabry.oc.ui.words.res.stepShellStopNow
import io.github.eslamasabry.oc.ui.words.res.stepShellTests
import io.github.eslamasabry.oc.ui.words.res.stepShellTestsNow
import io.github.eslamasabry.oc.ui.words.res.stepShellWait
import io.github.eslamasabry.oc.ui.words.res.stepShellWaitNow
import io.github.eslamasabry.oc.ui.words.res.stepShellTestsAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellSearchAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellListAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellProcessesAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellStopAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellChangesAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellAnalyzeAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellBuildAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellInstallAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellFetchAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellDeleteAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellChangeFilesAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellWaitAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellProgramAsk
import io.github.eslamasabry.oc.ui.words.res.stepShellUnknownAsk
import io.github.eslamasabry.oc.ui.words.res.chatUiProviderSearch
import io.github.eslamasabry.oc.ui.words.res.chatUiQuestions
import io.github.eslamasabry.oc.ui.words.res.chatUiRateLimitCountdown
import io.github.eslamasabry.oc.ui.words.res.chatUiRateLimitRetry
import io.github.eslamasabry.oc.ui.words.res.chatUiRead
import io.github.eslamasabry.oc.ui.words.res.chatUiRetryWhyBusy
import io.github.eslamasabry.oc.ui.words.res.chatUiRetryWhyUnreachable
import io.github.eslamasabry.oc.ui.words.res.chatUiRetryingCountdown
import io.github.eslamasabry.oc.ui.words.res.chatUiRetryingSoon
import io.github.eslamasabry.oc.ui.words.res.chatUiSearchText
import io.github.eslamasabry.oc.ui.words.res.chatUiTool
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundNamedCutOff
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundNamedEnded
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundNamedFailed
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundNamedFinished
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundNamedStopped
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundTaskCutOff
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundTaskEnded
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundTaskFailed
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundTaskFinished
import io.github.eslamasabry.oc.ui.words.res.chatUiToolBackgroundTaskStopped
import io.github.eslamasabry.oc.ui.words.res.chatUiToolFrom
import io.github.eslamasabry.oc.ui.words.res.chatUiToolMessageTo
import io.github.eslamasabry.oc.ui.words.res.chatUiToolMessageToAgent
import io.github.eslamasabry.oc.ui.words.res.chatUiToolMessageToEveryone
import io.github.eslamasabry.oc.ui.words.res.chatUiToolMessageToMain
import io.github.eslamasabry.oc.ui.words.res.chatUiToolSearchedConnectors
import io.github.eslamasabry.oc.ui.words.res.chatUiToolShowCard
import io.github.eslamasabry.oc.ui.words.res.chatUiWebSearch
import io.github.eslamasabry.oc.ui.words.res.chatUiWrite
import io.github.eslamasabry.oc.ui.words.res.kitBackgroundWorkMany
import io.github.eslamasabry.oc.ui.words.res.kitBackgroundWorkMinutes
import io.github.eslamasabry.oc.ui.words.res.kitBackgroundWorkOne
import io.github.eslamasabry.oc.ui.words.res.kitToolDone
import io.github.eslamasabry.oc.ui.words.res.kitToolDeclined
import io.github.eslamasabry.oc.ui.words.res.kitToolFailed
import io.github.eslamasabry.oc.ui.words.res.kitToolNotRun
import io.github.eslamasabry.oc.ui.words.res.kitToolRunning
import io.github.eslamasabry.oc.ui.words.res.kitToolStopped
import io.github.eslamasabry.oc.ui.words.res.kitToolWaiting
import io.github.eslamasabry.oc.ui.words.res.kitWorkDelegated
import io.github.eslamasabry.oc.ui.words.res.kitWorkDidntFinish
import io.github.eslamasabry.oc.ui.words.res.kitWorkEdited
import io.github.eslamasabry.oc.ui.words.res.kitWorkFetched
import io.github.eslamasabry.oc.ui.words.res.kitWorkListed
import io.github.eslamasabry.oc.ui.words.res.kitWorkNotRun
import io.github.eslamasabry.oc.ui.words.res.kitWorkOther
import io.github.eslamasabry.oc.ui.words.res.kitWorkRan
import io.github.eslamasabry.oc.ui.words.res.kitWorkRead
import io.github.eslamasabry.oc.ui.words.res.kitWorkSearched
import io.github.eslamasabry.oc.ui.words.res.kitWorkSeparator
import io.github.eslamasabry.oc.ui.words.res.kitWorkSteps
import io.github.eslamasabry.oc.ui.words.res.stepRunCommands
import io.github.eslamasabry.oc.ui.words.res.stepRunFiles
import io.github.eslamasabry.oc.ui.words.res.stepRunSearches
import io.github.eslamasabry.oc.ui.words.res.stepRunFolders
import io.github.eslamasabry.oc.ui.words.res.kitWorkWorking
import io.github.eslamasabry.oc.ui.words.res.replySpeedLast
import io.github.eslamasabry.oc.ui.words.res.toolCardExitFailed
import io.github.eslamasabry.oc.ui.words.res.toolCardExitPassed
import io.github.eslamasabry.oc.ui.words.res.toolCardPlan
import io.github.eslamasabry.oc.ui.words.res.toolCardUpdatedTasks
import io.github.eslamasabry.oc.ui.words.res.toolCardWorktreeSetup
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * CHAT-TRANSCRIPT words: every turn fact the presenter hands over as an enum or number becomes words here, from the
 * strings converted from the Flutter app (English and Arabic in :ui:words). No raw tool id ever reaches the screen
 * (B-CHAT-111): an id the app has no words for is the server's own title, the MCP tool's words, or the id as words.
 */

/** A step's plain title (Dart `toolLabel`). */
@Composable
internal fun stepTitle(f: ToolFacts): String {
    val a = f.titleArgs
    val words = phraseWords(f.phrase, a.getOrNull(0) ?: "", a.getOrNull(1) ?: "")
    return if (f.phrase in serverWordPhrases) words.ifBlank { f.idWords.replaceFirstChar { it.uppercase() } }
    else words
}

/** Phrases whose title is server words (a sub-agent type, an LSP operation, a call's own title): shown as they are. */
private val serverWordPhrases =
    setOf(
        ToolPhrase.AgentType,
        ToolPhrase.LspOperation,
        ToolPhrase.MessageSummary,
        ToolPhrase.ServerTitle,
        ToolPhrase.Generic,
    )

private const val ARG0 = "\u0000"
private const val ARG1 = "\u0001"

/**
 * S28 story 13 (Flutter `transcriptToolHeading`): every step title as words resolved once, for work that runs outside
 * composition (Copy transcript names each tool as its step row does, never by its raw id).
 */
@Composable
internal fun rememberStepTitles(): (ToolFacts) -> String {
    val templates = ToolPhrase.entries.associateWith { phraseWords(it, ARG0, ARG1) }
    return remember(templates) {
        { f ->
            val a = f.titleArgs
            val arg0 = a.getOrNull(0) ?: ""
            if (f.phrase in serverWordPhrases) arg0.ifBlank { f.idWords.replaceFirstChar { it.uppercase() } }
            else templates.getValue(f.phrase).replace(ARG0, arg0).replace(ARG1, a.getOrNull(1) ?: "")
        }
    }
}

/** One phrase's words with its arguments [arg0] and [arg1] (server-word phrases give [arg0] itself). */
@Composable
private fun phraseWords(phrase: ToolPhrase, arg0: String, arg1: String): String {
    fun arg(i: Int): String = if (i == 0) arg0 else arg1
    return when (phrase) {
        ToolPhrase.Read -> stringResource(Res.string.chatUiRead)
        ToolPhrase.List -> stringResource(Res.string.chatUiList)
        ToolPhrase.FindFiles -> stringResource(Res.string.chatUiFindFiles)
        ToolPhrase.SearchText -> stringResource(Res.string.chatUiSearchText)
        ToolPhrase.Shell -> stringResource(Res.string.activeContextShell)
        ToolPhrase.Edit -> stringResource(Res.string.chatUiEdit)
        ToolPhrase.Write -> stringResource(Res.string.chatUiWrite)
        ToolPhrase.ApplyPatch -> stringResource(Res.string.chatUiApplyPatch)
        ToolPhrase.FetchPage -> stringResource(Res.string.chatUiFetchPage)
        ToolPhrase.WebSearch -> stringResource(Res.string.chatUiWebSearch)
        ToolPhrase.ProviderSearch -> stringResource(Res.string.chatUiProviderSearch, arg(0))
        ToolPhrase.Agent -> stringResource(Res.string.chatUiAgent)
        ToolPhrase.UpdatedTasks -> stringResource(Res.string.toolCardUpdatedTasks)
        ToolPhrase.Questions -> stringResource(Res.string.chatUiQuestions)
        ToolPhrase.LanguageServer -> stringResource(Res.string.chatUiLanguageServer)
        ToolPhrase.Plan -> stringResource(Res.string.toolCardPlan)
        ToolPhrase.WorktreeSetup -> stringResource(Res.string.toolCardWorktreeSetup)
        ToolPhrase.Skill -> stringResource(Res.string.activeContextSkill)
        ToolPhrase.BackgroundTaskCutOff -> stringResource(Res.string.chatUiToolBackgroundTaskCutOff)
        ToolPhrase.BackgroundNamedCutOff -> stringResource(Res.string.chatUiToolBackgroundNamedCutOff, arg(0))
        ToolPhrase.BackgroundNamedFinished -> stringResource(Res.string.chatUiToolBackgroundNamedFinished, arg(0))
        ToolPhrase.BackgroundNamedFailed -> stringResource(Res.string.chatUiToolBackgroundNamedFailed, arg(0))
        ToolPhrase.BackgroundNamedStopped -> stringResource(Res.string.chatUiToolBackgroundNamedStopped, arg(0))
        ToolPhrase.BackgroundNamedEnded -> stringResource(Res.string.chatUiToolBackgroundNamedEnded, arg(0))
        ToolPhrase.BackgroundTaskFinished -> stringResource(Res.string.chatUiToolBackgroundTaskFinished)
        ToolPhrase.BackgroundTaskFailed -> stringResource(Res.string.chatUiToolBackgroundTaskFailed)
        ToolPhrase.BackgroundTaskStopped -> stringResource(Res.string.chatUiToolBackgroundTaskStopped)
        ToolPhrase.BackgroundTaskEnded -> stringResource(Res.string.chatUiToolBackgroundTaskEnded)
        ToolPhrase.MessageToEveryone -> stringResource(Res.string.chatUiToolMessageToEveryone)
        ToolPhrase.MessageToMain -> stringResource(Res.string.chatUiToolMessageToMain)
        ToolPhrase.MessageTo -> stringResource(Res.string.chatUiToolMessageTo, arg(0))
        ToolPhrase.MessageToAgent -> stringResource(Res.string.chatUiToolMessageToAgent)
        ToolPhrase.LoadTools -> stringResource(Res.string.chatUiLoadTools)
        ToolPhrase.StopBackgroundTask -> stringResource(Res.string.chatUiStopBackgroundTask)
        ToolPhrase.ReadTaskOutput -> stringResource(Res.string.chatUiReadTaskOutput)
        ToolPhrase.SearchedConnectors -> stringResource(Res.string.chatUiToolSearchedConnectors)
        ToolPhrase.ShowCard -> stringResource(Res.string.chatUiToolShowCard)
        ToolPhrase.McpTool -> stringResource(Res.string.chatUiToolFrom, arg(0), arg(1))
        ToolPhrase.Tool -> stringResource(Res.string.chatUiTool)
        // Server words (a sub-agent type, an LSP operation, a message summary, a call's own title) and the id as
        // sentence-cased words: shown as they are.
        ToolPhrase.AgentType,
        ToolPhrase.LspOperation,
        ToolPhrase.MessageSummary,
        ToolPhrase.ServerTitle,
        ToolPhrase.Generic -> arg(0)
    }
}

/**
 * The glyph a step's row draws when its words know more than its tool (owner 2026-11-14: every command row wore the
 * same terminal, so the list could not be scanned): a command that ran tests draws the test flask, a search the
 * magnifier, a read the file, git the branch, a build the hammer; the terminal stays only for a command of no known
 * kind. Null: the tool's own glyph (an edit's pencil, a read's file).
 */
internal fun stepGlyph(f: ToolFacts) =
    if (f.kind == ToolKind.Shell) shellGlyph(f.shell?.kind ?: ShellKind.Unknown)
    else
        when (f.phrase) {
            ToolPhrase.StopBackgroundTask -> KitIcons.stopCircle
            ToolPhrase.ReadTaskOutput -> KitIcons.fileText
            ToolPhrase.LoadTools -> KitIcons.extensions
            else -> null
        }

/** The glyph for what a command did (see [stepGlyph]). */
internal fun shellGlyph(kind: ShellKind) =
    when (kind) {
        ShellKind.Tests -> KitIcons.experiments
        ShellKind.Search -> KitIcons.search
        ShellKind.ReadOne,
        ShellKind.ReadFile,
        ShellKind.ReadMany -> KitIcons.fileText
        ShellKind.List -> KitIcons.folderOpen
        ShellKind.Processes -> KitIcons.processor
        ShellKind.StopProcess -> KitIcons.stopCircle
        ShellKind.CheckChanges -> KitIcons.branch
        ShellKind.Analyze -> KitIcons.checks
        ShellKind.Build -> KitIcons.build
        ShellKind.Install -> KitIcons.download
        ShellKind.Fetch -> KitIcons.globe
        ShellKind.Delete -> KitIcons.delete
        ShellKind.ChangeFiles -> KitIcons.copy
        ShellKind.Wait -> KitIcons.waiting
        ShellKind.Program,
        ShellKind.Unknown -> KitIcons.terminal
    }

/** Which tense a step's plain words take: it happened, it happens now, or it is asked for (not run, or stopped). */
internal enum class StepTense { Done, Now, Ask }

/** The tense of a step in [state]; a step that waits for the person ([waiting]) is asked for, not running. */
internal fun stepTense(state: StepState, waiting: Boolean = false): StepTense =
    when {
        waiting -> StepTense.Ask
        state == StepState.Done || state == StepState.Failed -> StepTense.Done
        state == StepState.Running -> StepTense.Now
        else -> StepTense.Ask
    }

/**
 * A step row's title (owner 2026-11-14): a shell step says what it did in plain words, judged from its command after
 * the set-up it opens with ("Ran tests", "Searched files", "Read notes.md"), "Running tests" while it runs and "Run
 * tests" while it is asked for, refused or stopped (never a claim that it happened); every other step keeps its title.
 * The command itself is in the opened step, in mono.
 */
@Composable
internal fun stepRowTitle(f: ToolFacts, tense: StepTense): String {
    val intent = f.shell
    return if (f.kind == ToolKind.Shell && intent != null) shellWords(intent, tense) else stepTitle(f)
}

/** [intent] in words in [tense]. A name it carries (a file, a program) reads redacted. */
@Composable
internal fun shellWords(intent: ShellIntent, tense: StepTense): String {
    val mask = rememberKitMask()
    val arg = intent.arg?.let(mask) ?: ""
    fun <T> pick(done: T, now: T, ask: T): T =
        when (tense) {
            StepTense.Done -> done
            StepTense.Now -> now
            StepTense.Ask -> ask
        }
    return when (intent.kind) {
        ShellKind.Tests -> stringResource(pick(Res.string.stepShellTests, Res.string.stepShellTestsNow, Res.string.stepShellTestsAsk))
        ShellKind.Search -> stringResource(pick(Res.string.stepShellSearch, Res.string.stepShellSearchNow, Res.string.stepShellSearchAsk))
        ShellKind.ReadOne ->
            if (tense == StepTense.Now) stringResource(Res.string.chatLiveReading, arg) else stringResource(Res.string.stepShellReadOne, arg)
        ShellKind.ReadFile -> stringResource(pick(Res.string.teamAgentStepRead, Res.string.chatLiveReadingAny, Res.string.teamAgentStepRead))
        ShellKind.ReadMany -> stringResource(pick(Res.string.stepShellReadMany, Res.string.stepShellReadManyNow, Res.string.stepShellReadMany))
        ShellKind.List -> stringResource(pick(Res.string.stepShellList, Res.string.stepShellListNow, Res.string.stepShellListAsk))
        ShellKind.Processes ->
            stringResource(pick(Res.string.stepShellProcesses, Res.string.stepShellProcessesNow, Res.string.stepShellProcessesAsk))
        ShellKind.StopProcess -> stringResource(pick(Res.string.stepShellStop, Res.string.stepShellStopNow, Res.string.stepShellStopAsk))
        ShellKind.CheckChanges ->
            stringResource(pick(Res.string.stepShellChanges, Res.string.stepShellChangesNow, Res.string.stepShellChangesAsk))
        ShellKind.Analyze -> stringResource(pick(Res.string.stepShellAnalyze, Res.string.stepShellAnalyzeNow, Res.string.stepShellAnalyzeAsk))
        ShellKind.Build -> stringResource(pick(Res.string.stepShellBuild, Res.string.stepShellBuildNow, Res.string.stepShellBuildAsk))
        ShellKind.Install -> stringResource(pick(Res.string.stepShellInstall, Res.string.stepShellInstallNow, Res.string.stepShellInstallAsk))
        ShellKind.Fetch -> stringResource(pick(Res.string.stepShellFetch, Res.string.stepShellFetchNow, Res.string.stepShellFetchAsk))
        ShellKind.Delete -> stringResource(pick(Res.string.stepShellDelete, Res.string.stepShellDeleteNow, Res.string.stepShellDeleteAsk))
        ShellKind.ChangeFiles ->
            stringResource(pick(Res.string.stepShellChangeFiles, Res.string.stepShellChangeFilesNow, Res.string.stepShellChangeFilesAsk))
        ShellKind.Wait -> stringResource(pick(Res.string.stepShellWait, Res.string.stepShellWaitNow, Res.string.stepShellWaitAsk))
        ShellKind.Program ->
            stringResource(pick(Res.string.stepShellProgram, Res.string.liveStepRunning, Res.string.stepShellProgramAsk), arg)
        ShellKind.Unknown ->
            stringResource(pick(Res.string.teamAgentStepCommand, Res.string.liveStepRunningCommand, Res.string.stepShellUnknownAsk))
    }
}

/** A run's title: how many steps of its family ran, "Ran 6 commands", "Read 3 files" (first letter upper). */
@Composable
internal fun runTitle(family: StepFamily, count: Int): String {
    val words =
        when (family) {
            StepFamily.Commands -> pluralStringResource(Res.plurals.kitWorkRan, count, count)
            StepFamily.Reads -> pluralStringResource(Res.plurals.kitWorkRead, count, count)
            StepFamily.Searches -> pluralStringResource(Res.plurals.kitWorkSearched, count, count)
            StepFamily.Lists -> pluralStringResource(Res.plurals.kitWorkListed, count, count)
        }
    return words.replaceFirstChar { it.uppercase() }
}

/** A live run's count, after what runs now: "6 commands", "3 files", "2 searches", "2 folders". */
@Composable
internal fun runCount(family: StepFamily, count: Int): String =
    when (family) {
        StepFamily.Commands -> pluralStringResource(Res.plurals.stepRunCommands, count, count)
        StepFamily.Reads -> pluralStringResource(Res.plurals.stepRunFiles, count, count)
        StepFamily.Searches -> pluralStringResource(Res.plurals.stepRunSearches, count, count)
        StepFamily.Lists -> pluralStringResource(Res.plurals.stepRunFolders, count, count)
    }

/**
 * A finished stretch's work in one line (its summary row): each kind in the order it first appeared, with "not run"
 * last, "Ran 11 commands · 2 other steps"; first letter upper.
 */
@Composable
internal fun turnWorkWords(work: TurnWork): String {
    // Only work that happened is said in the past tense: a refused write is no "edited 1 file" (review #2).
    val c = WorkLineRules.split(work).done
    val order = WorkLineRules.firstSeen(work).withIndex().associate { (i, kind) -> kind to i }
    val counted =
        listOf(
                Triple(WorkKind.Ran, c.ran, Res.plurals.kitWorkRan),
                Triple(WorkKind.Read, c.read, Res.plurals.kitWorkRead),
                Triple(WorkKind.Edited, c.edited, Res.plurals.kitWorkEdited),
                Triple(WorkKind.Searched, c.searched, Res.plurals.kitWorkSearched),
                Triple(WorkKind.Listed, c.listed, Res.plurals.kitWorkListed),
                Triple(WorkKind.Fetched, c.fetched, Res.plurals.kitWorkFetched),
                Triple(WorkKind.Delegated, c.delegated, Res.plurals.kitWorkDelegated),
                Triple(WorkKind.Other, c.other, Res.plurals.kitWorkOther),
                Triple(WorkKind.NotRun, c.notRun, Res.plurals.kitWorkNotRun),
            )
            .filter { it.second > 0 }
            .sortedBy { (kind, _, _) ->
                if (kind == WorkKind.NotRun) Int.MAX_VALUE else order[kind] ?: (Int.MAX_VALUE - 1)
            }
    val segments = counted.map { (_, n, res) -> pluralStringResource(res, n, n) }.toMutableList()
    if (segments.isEmpty()) return pluralStringResource(Res.plurals.kitWorkSteps, work.steps.size, work.steps.size)
    segments[0] = segments[0].replaceFirstChar { it.uppercase() }
    return segments.joinToString(stringResource(Res.string.kitWorkSeparator))
}

/** A step's status in words (the screen reader hears it; the row draws it). */
@Composable
internal fun stepStatus(state: StepState, exitCode: Int?): String =
    when (state) {
        StepState.Pending -> stringResource(Res.string.kitToolWaiting)
        StepState.Running -> stringResource(Res.string.kitToolRunning)
        StepState.Done ->
            if (exitCode != null && exitCode == 0) stringResource(Res.string.toolCardExitPassed)
            else stringResource(Res.string.kitToolDone)
        StepState.Failed ->
            if (exitCode != null && exitCode != 0) stringResource(Res.string.toolCardExitFailed, exitCode)
            else stringResource(Res.string.kitToolFailed)
        StepState.Stopped -> stringResource(Res.string.kitToolStopped)
        StepState.NotRun -> stringResource(Res.string.kitToolNotRun)
        StepState.Declined -> stringResource(Res.string.kitToolDeclined)
    }

/** B-CHAT-109: "4.2s" under a minute, "1m 15s" past it, in the phone's language. */
@Composable
internal fun durationWords(ms: Long): String =
    when (val d = toolDuration(ms)) {
        is ToolDuration.Seconds -> kitTenthsSecondsWords(d.tenths)
        is ToolDuration.MinutesSeconds ->
            kitElapsedSecondsMinutes(
                totalSeconds = d.minutes * 60L + ((ms / 1_000L) % 60L),
                style = KitDurationStyle.Compact,
                padSeconds = true,
            )
    }

/**
 * The work line's summary (Dart `KitWorkLine.summaryOf`), first letter upper. P-012: finished steps in the past
 * tense, then the ones still waiting: "Read 3 files · edited 1 file · waiting to run 1 command".
 */
@Composable
internal fun workSummary(work: TurnWork): String {
    val split = WorkLineRules.split(work)
    val c = split.done
    val segments = ArrayList<String>()
    if (c.read > 0) segments += pluralStringResource(Res.plurals.kitWorkRead, c.read, c.read)
    if (c.searched > 0) segments += pluralStringResource(Res.plurals.kitWorkSearched, c.searched, c.searched)
    if (c.listed > 0) segments += pluralStringResource(Res.plurals.kitWorkListed, c.listed, c.listed)
    if (c.edited > 0) segments += pluralStringResource(Res.plurals.kitWorkEdited, c.edited, c.edited)
    if (c.ran > 0) segments += pluralStringResource(Res.plurals.kitWorkRan, c.ran, c.ran)
    if (c.fetched > 0) segments += pluralStringResource(Res.plurals.kitWorkFetched, c.fetched, c.fetched)
    if (c.delegated > 0) segments += pluralStringResource(Res.plurals.kitWorkDelegated, c.delegated, c.delegated)
    if (c.other > 0) segments += pluralStringResource(Res.plurals.kitWorkOther, c.other, c.other)
    if (c.notRun > 0) segments += pluralStringResource(Res.plurals.kitWorkNotRun, c.notRun, c.notRun)
    // Review #12: while a request card waits for the person, the card says what is asked; the line says only how much
    // work there is ("1 step", "Read 2 files"), never a second waiting phrase.
    val asking = work.state == WorkLineState.WaitingForYou
    if (!asking && split.waitingEdit > 0)
        segments += pluralStringResource(Res.plurals.chatWorkWaitingEdit, split.waitingEdit, split.waitingEdit)
    if (!asking && split.waitingRun > 0)
        segments += pluralStringResource(Res.plurals.chatWorkWaitingRun, split.waitingRun, split.waitingRun)
    if (!asking && split.waitingOther > 0)
        segments += pluralStringResource(Res.plurals.chatWorkWaitingOther, split.waitingOther, split.waitingOther)
    if (segments.isEmpty()) return pluralStringResource(Res.plurals.kitWorkSteps, c.steps, c.steps)
    segments[0] = segments[0].replaceFirstChar { it.uppercase() }
    return segments.joinToString(stringResource(Res.string.kitWorkSeparator))
}

/** The state words beside the summary (null = the summary says it all). */
@Composable
internal fun workStateWords(state: WorkLineState): String? =
    when (state) {
        WorkLineState.Running -> stringResource(Res.string.kitWorkWorking)
        // Review #12: the request card says the run waits for the person; the work line does not say it again.
        WorkLineState.WaitingForYou -> null
        WorkLineState.Done -> null
        WorkLineState.EndedFailed -> stringResource(Res.string.kitWorkDidntFinish)
        // A turn the person stopped says so once, in its footer; the work line says how much work happened.
        WorkLineState.Stopped -> null
    }

/** B-CHAT-068/100/101: "Edited 2 files · ran 3 commands" for a finished turn; null when it changed and ran nothing. */
@Composable
internal fun resultSummary(r: TurnResultUi): String? {
    val segments = ArrayList<String>()
    if (r.files.isNotEmpty()) segments += pluralStringResource(Res.plurals.kitWorkEdited, r.files.size, r.files.size)
    if (r.commands.isNotEmpty()) segments += pluralStringResource(Res.plurals.kitWorkRan, r.commands.size, r.commands.size)
    if (segments.isEmpty()) return null
    segments[0] = segments[0].replaceFirstChar { it.uppercase() }
    return segments.joinToString(stringResource(Res.string.kitWorkSeparator))
}

/** B-CHAT-092: "Last reply: first words after 2.1s, finished after 12.4s", or "Took 12.4s" without first words. */
@Composable
internal fun timingWords(t: ReplyTiming): String {
    val total = durationWords(t.totalMs)
    val first = t.firstTokenMs
    return if (first != null) stringResource(Res.string.replySpeedLast, durationWords(first), total)
    else stringResource(Res.string.aiteamComponentStageTook, total)
}

/** One changed file: "src/a.kt +3 −1" (counts omitted when no tool reported them, B-CHAT-068). */
internal fun fileLine(path: String, added: Int?, removed: Int?): String =
    buildString {
        append(path)
        if (added != null || removed != null) {
            append("  ")
            append("+").append(Numbers.format(added ?: 0))
            append(" −").append(Numbers.format(removed ?: 0))
        }
    }

/** The plain reason under a retry line when the server's retry says why; null when it does not. */
@Composable
internal fun retryWhyWords(n: LiveNoticeUi): String? =
    (n as? LiveNoticeUi.Retry)?.facts?.why?.let {
        when (it) {
            io.github.eslamasabry.oc.engine.transcript.turnfacts.RetryWhy.ProviderUnreachable ->
                stringResource(Res.string.chatUiRetryWhyUnreachable)
            io.github.eslamasabry.oc.engine.transcript.turnfacts.RetryWhy.ProviderBusy ->
                stringResource(Res.string.chatUiRetryWhyBusy)
        }
    }

/** What the live line says instead of "Working…" (B-CHAT-016/116/118). */
@Composable
internal fun liveNoticeWords(n: LiveNoticeUi): String =
    when (n) {
        is LiveNoticeUi.Retry -> {
            val f = n.facts
            val attempt = f.attempt?.let { " " + Numbers.format(it) } ?: ""
            val left = f.secondsLeft
            when {
                left == null && f.rateLimited -> stringResource(Res.string.chatUiRateLimitRetry, attempt)
                left == null -> stringResource(Res.string.chatUiRetryingSoon, attempt)
                f.rateLimited -> stringResource(Res.string.chatUiRateLimitCountdown, attempt, formatRetryCountdown(left))
                else -> stringResource(Res.string.chatUiRetryingCountdown, attempt, formatRetryCountdown(left))
            }
        }
        is LiveNoticeUi.Stall ->
            when (n.notice) {
                TurnStallNotice.ModelSlow -> stringResource(Res.string.chatQuietThinkingHint)
                TurnStallNotice.HelperStopped,
                TurnStallNotice.HelperStoppedUnexpectedly,
                TurnStallNotice.HelperStoppedResourceKill -> stringResource(Res.string.chatStallHelperDown)
                TurnStallNotice.ConnectionLost -> stringResource(Res.string.chatStallConnectionLost)
                TurnStallNotice.ConnectionUnchecked -> stringResource(Res.string.chatStallConnectionUnchecked)
            }
        LiveNoticeUi.Compacting -> stringResource(Res.string.chatUiCompactingConversation)
        // S28 story 14 (Flutter `_stuckWords`): "2 h 29 min · may be stuck".
        // Phase 0: "Still waiting for read." (the tool as the server names it).
        is LiveNoticeUi.ToolWait -> stringResource(Res.string.chatStalledOnTool, n.toolName)
        is LiveNoticeUi.StuckCommand -> {
            val time = kitMinutesDurationWords(n.runningMs)
            if (n.command == null) stringResource(Res.string.chatStuckUnnamed, time)
            else stringResource(Res.string.chatStuckCommand, time)
        }
    }

/** B-CHAT-007/008: "build.sh · 3 min" per task, or "2 tasks still running in the background". */
@Composable
internal fun backgroundWords(tasks: List<BackgroundTask>, nowMs: Long): String {
    if (tasks.size == 1) {
        val t = tasks[0]
        val elapsedMs = (nowMs - t.sinceMs).coerceAtLeast(0L)
        return stringResource(
            Res.string.kitBackgroundWorkOne,
            t.name,
            kitMinutesDurationWords(elapsedMs),
        )
    }
    return pluralStringResource(Res.plurals.kitBackgroundWorkMany, tasks.size, tasks.size)
}
