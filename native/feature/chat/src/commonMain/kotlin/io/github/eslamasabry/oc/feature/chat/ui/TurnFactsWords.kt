package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkCounts
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTask
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTiming
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolDuration
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolPhrase
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallNotice
import io.github.eslamasabry.oc.engine.transcript.turnfacts.formatRetryCountdown
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolDuration
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnResultUi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.activeContextShell
import io.github.eslamasabry.oc.ui.words.res.activeContextSkill
import io.github.eslamasabry.oc.ui.words.res.aiteamComponentStageTook
import io.github.eslamasabry.oc.ui.words.res.chatStallConnectionLost
import io.github.eslamasabry.oc.ui.words.res.chatStallConnectionUnchecked
import io.github.eslamasabry.oc.ui.words.res.chatStallHelperDown
import io.github.eslamasabry.oc.ui.words.res.chatStallModelSlow
import io.github.eslamasabry.oc.ui.words.res.chatUiAgent
import io.github.eslamasabry.oc.ui.words.res.chatUiApplyPatch
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactingConversation
import io.github.eslamasabry.oc.ui.words.res.chatUiDurationMinutesSeconds
import io.github.eslamasabry.oc.ui.words.res.chatUiDurationSeconds
import io.github.eslamasabry.oc.ui.words.res.chatUiEdit
import io.github.eslamasabry.oc.ui.words.res.chatUiFetchPage
import io.github.eslamasabry.oc.ui.words.res.chatUiFindFiles
import io.github.eslamasabry.oc.ui.words.res.chatUiLanguageServer
import io.github.eslamasabry.oc.ui.words.res.chatUiList
import io.github.eslamasabry.oc.ui.words.res.chatUiLoadTools
import io.github.eslamasabry.oc.ui.words.res.chatUiProviderSearch
import io.github.eslamasabry.oc.ui.words.res.chatUiQuestions
import io.github.eslamasabry.oc.ui.words.res.chatUiRateLimitCountdown
import io.github.eslamasabry.oc.ui.words.res.chatUiRateLimitRetry
import io.github.eslamasabry.oc.ui.words.res.chatUiRead
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
import io.github.eslamasabry.oc.ui.words.res.kitWorkStopped
import io.github.eslamasabry.oc.ui.words.res.kitWorkWaitingForYou
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
    fun arg(i: Int): String = a.getOrNull(i) ?: ""
    return when (f.phrase) {
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
        ToolPhrase.Generic -> arg(0).ifBlank { f.idWords.replaceFirstChar { it.uppercase() } }
    }
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
    }

/** B-CHAT-109: "4.2s" under a minute, "1m 15s" past it, in the phone's language. */
@Composable
internal fun durationWords(ms: Long): String =
    when (val d = toolDuration(ms)) {
        is ToolDuration.Seconds -> stringResource(Res.string.chatUiDurationSeconds, d.tenths)
        is ToolDuration.MinutesSeconds -> stringResource(Res.string.chatUiDurationMinutesSeconds, Numbers.format(d.minutes), d.seconds)
    }

/** The work line's summary (Dart `KitWorkLine.summaryOf`): "Read 3 files · edited 1 file", first letter upper. */
@Composable
internal fun workSummary(c: WorkCounts): String {
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
    if (segments.isEmpty()) return pluralStringResource(Res.plurals.kitWorkSteps, c.steps, c.steps)
    segments[0] = segments[0].replaceFirstChar { it.uppercase() }
    return segments.joinToString(stringResource(Res.string.kitWorkSeparator))
}

/** The state words beside the summary (null = the summary says it all). */
@Composable
internal fun workStateWords(state: WorkLineState): String? =
    when (state) {
        WorkLineState.Running -> stringResource(Res.string.kitWorkWorking)
        WorkLineState.WaitingForYou -> stringResource(Res.string.kitWorkWaitingForYou)
        WorkLineState.Done -> null
        WorkLineState.EndedFailed -> stringResource(Res.string.kitWorkDidntFinish)
        WorkLineState.Stopped -> stringResource(Res.string.kitWorkStopped)
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
                TurnStallNotice.ModelSlow -> stringResource(Res.string.chatStallModelSlow)
                TurnStallNotice.HelperStopped,
                TurnStallNotice.HelperStoppedUnexpectedly,
                TurnStallNotice.HelperStoppedResourceKill -> stringResource(Res.string.chatStallHelperDown)
                TurnStallNotice.ConnectionLost -> stringResource(Res.string.chatStallConnectionLost)
                TurnStallNotice.ConnectionUnchecked -> stringResource(Res.string.chatStallConnectionUnchecked)
            }
        LiveNoticeUi.Compacting -> stringResource(Res.string.chatUiCompactingConversation)
    }

/** B-CHAT-007/008: "build.sh · 3 min" per task, or "2 tasks still running in the background". */
@Composable
internal fun backgroundWords(tasks: List<BackgroundTask>, nowMs: Long): String {
    if (tasks.size == 1) {
        val t = tasks[0]
        val minutes = ((nowMs - t.sinceMs).coerceAtLeast(0) / 60_000).toInt()
        return stringResource(
            Res.string.kitBackgroundWorkOne,
            t.name,
            pluralStringResource(Res.plurals.kitBackgroundWorkMinutes, minutes, minutes),
        )
    }
    return pluralStringResource(Res.plurals.kitBackgroundWorkMany, tasks.size, tasks.size)
}
