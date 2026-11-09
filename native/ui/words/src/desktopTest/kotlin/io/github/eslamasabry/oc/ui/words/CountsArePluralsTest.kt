package io.github.eslamasabry.oc.ui.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A number that a noun or verb agrees with is a `<plurals>`, never a `<string>`: "%1$d waiting prompts will be
 * deleted" read "1 waiting prompts" in English and had no dual or few/many form in Arabic. Every `<string>` in
 * every values folder whose text holds `%d` or `%<n>$d` must be named in [notCounts] with the reason it is not a
 * count. `__eqN` strings are exempt: each renders the one quantity its name fixes. The list only shrinks: an entry
 * whose string no longer holds a number fails too.
 */
class CountsArePluralsTest {
    private val strings = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val count = Regex("""%(\d+\$)?d""")
    private val exact = Regex("""__eq\d+$""")

    @Test
    fun `B-L10N-012 counts are plurals`() {
        val root = File("src/commonMain/composeResources")
        if (!root.isDirectory) fail("composeResources not found from ${File(".").absolutePath}")
        val folders = root.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        assertTrue(folders.isNotEmpty(), "no values folders under $root")
        val counted = HashSet<String>()
        val offenders = folders.sortedBy { it.name }.flatMap { folder ->
            folder.listFiles { f -> f.extension == "xml" }.orEmpty().sortedBy { it.name }.flatMap { file ->
                strings.findAll(file.readText())
                    .map { it.groupValues[1] to it.groupValues[2] }
                    .filter { (name, text) -> count.containsMatchIn(text) && !exact.containsMatchIn(name) }
                    .map { (name, _) -> name }
                    .onEach { counted += it }
                    .filter { it !in notCounts }
                    .map { "${folder.name}/${file.name}: $it" }
                    .toList()
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "a <string> holds a count; make it <plurals>, or add it to notCounts with a reason:\n" +
                offenders.joinToString("\n"),
        )
        val stale = notCounts.keys.filter { it !in counted }.sorted()
        assertTrue(stale.isEmpty(), "notCounts names strings with no number left:\n" + stale.joinToString("\n"))
    }

    private companion object {
        const val PERCENT = "percent"
        const val COMPOUND = "compound adjective (5-minute window)"
        const val UNIT = "unit abbreviation (min, h, s, MB)"
        const val POSITION = "position or range (step of total, A–B)"
        const val NAME = "number as a name (port, exit code, line, ordinal)"
        const val LABEL = "label: value"
        const val NO_NOUN = "no noun or verb agrees with it"
        const val FROZEN_ARB = "frozen ARB copy (B-L10N-010); needs a plural in lib/l10n first"

        val notCounts: Map<String, String> = mapOf(
        // percent (20)
        "account40PercentUsed" to PERCENT, "agentAccountPercentUsed" to PERCENT, "agentCardProgressValue" to PERCENT,
        "e7VoiceUiDownloadPercent" to PERCENT, "languagePickerPartlyTranslated" to PERCENT,
        "voiceDownloadProgressPercent" to PERCENT,
        "m40PlansAlertAt" to PERCENT, "m40PlansLeft" to PERCENT, "m40UsageSameAsOthers" to PERCENT,
        "phoneSetupStartProgressHeadline" to PERCENT,
        "setupProgressViewPercent" to PERCENT, "stateContextUsage" to PERCENT, "statePlansThreshold" to PERCENT,
        "teamUiAgentContextSemantics" to PERCENT, "teamUiAgentContextShort" to PERCENT,
        "usage_widget_column" to PERCENT, "usage_widget_left" to PERCENT, "welcomePhoneSettingUp" to PERCENT,
        "welcomePhoneStopped" to PERCENT, "widget_status_left" to PERCENT,
        // compound adjective (8)
        "account40WindowDays" to COMPOUND, "account40WindowHours" to COMPOUND, "account40WindowMinutes" to COMPOUND,
        "agentAccountWindowDays" to COMPOUND, "agentAccountWindowHours" to COMPOUND,
        "agentAccountWindowMinutes" to COMPOUND, "usage_widget_window_days" to COMPOUND,
        "usage_widget_window_hours" to COMPOUND,
        // unit abbreviation (74)
        "chatStuckHours" to UNIT, "chatStuckMinutes" to UNIT,
        "composerLimitResetsInHours" to UNIT, "composerLimitResetsInHoursMinutes" to UNIT, "composerLimitResetsInMinutes" to UNIT, "connCheckSpeedOk" to UNIT,
        "connectionTryingAgainIn" to UNIT, "continueWithRecordSize" to UNIT, "e7SetupElapsedMinutes" to UNIT,
        "e7SetupElapsedSeconds" to UNIT, "e7VoiceUiMemory" to UNIT, "e7WorkspaceDaysAgo" to UNIT,
        "e7WorkspaceHoursAgo" to UNIT, "e7WorkspaceMinutesAgo" to UNIT, "keptSvcLogRetry" to UNIT,
        "keptSvcRetrying" to UNIT, "keptSvcRunningFor" to UNIT, "kitBackgroundWorkHours" to UNIT,
        "kitDurationDays" to UNIT, "kitDurationDaysHours" to UNIT, "kitDurationHours" to UNIT,
        "kitDurationHoursMinutes" to UNIT, "kitFieldStillChecking" to UNIT, "kitLogQuietSeconds" to UNIT,
        "kitTurnLiveMinutes" to UNIT, "kitTurnLiveSeconds" to UNIT, "kitTurnStillStarting" to UNIT,
        "linuxLifetimeLastUptime" to UNIT, "linuxLifetimeUptime" to UNIT, "linuxSvcLogRetry" to UNIT,
        "linuxSvcRetrying" to UNIT, "m40PlansDurationDays" to UNIT, "m40PlansDurationDaysHours" to UNIT,
        "m40PlansDurationHours" to UNIT, "m40PlansDurationHoursMinutes" to UNIT, "m40PlansDurationMinutes" to UNIT,
        "m40PlansResetHours" to UNIT, "m40PlansResetMinutes" to UNIT, "native_widget_days" to UNIT,
        "native_widget_hours" to UNIT, "native_widget_minutes" to UNIT, "notifStatusHours" to UNIT,
        "notifStatusMinutes" to UNIT, "perfTraceStatLine" to UNIT, "runningDetailElapsed" to UNIT,
        "runningDetailMemory" to UNIT, "teamPhoneRemoveFrees" to UNIT, "teamProjectElapsedHours" to UNIT,
        "teamProjectElapsedMinutes" to UNIT, "teamProjectElapsedSeconds" to UNIT,
        "teamProjectPlanningElapsed" to UNIT, "teamProjectPlanningSlow" to UNIT, "teamUiRunElapsedDays" to UNIT,
        "teamUiRunElapsedHours" to UNIT, "teamUiRunElapsedMinutes" to UNIT, "termuxProcsDurationHours" to UNIT,
        "termuxProcsDurationMinutes" to UNIT, "termuxProcsDurationSeconds" to UNIT, "termuxProcsMemoryMb" to UNIT,
        "termuxStorageScannedHoursAgo" to UNIT, "termuxStorageScannedMinutesAgo" to UNIT,
        "thisPhoneAboutMinutes" to UNIT, "thisPhoneAboutSeconds" to UNIT, "usageDurationDays" to UNIT,
        "usageDurationDaysHours" to UNIT, "usageDurationHours" to UNIT, "usageDurationHoursMinutes" to UNIT,
        "usageDurationMinutes" to UNIT, "usage_widget_short_days" to UNIT, "usage_widget_short_hours" to UNIT,
        "voiceAutoSetupDetailMemoryValue" to UNIT, "voiceNativeSizeMb" to UNIT, "widget_status_span_day" to UNIT,
        "widget_v2_ago_hour" to UNIT, "widget_v2_ago_min" to UNIT, "widget_v2_span_hour" to UNIT,
        "widget_v2_span_min" to UNIT,
        // position or range (47)
        "activityQuestionProgress" to POSITION, "agentCardPhotoCount" to POSITION,
        "appearanceGuideCountAt" to POSITION, "appearanceGuideCountSeen" to POSITION,
        "appearanceGuidePlace" to POSITION, "chatsSourcesSome" to POSITION, "e7SharedDetail307" to POSITION,
        "e7SharedDetail753" to POSITION,  "gitRepairRestoring" to POSITION,
        "isolatedTaskProgress" to POSITION, "kitCarouselPlace" to POSITION, "kitDiffFilePosition" to POSITION,
        "kitDiffFilePositionSpoken" to POSITION, "kitDiffLinesAdded" to POSITION, "kitDiffLinesRemoved" to POSITION,
        "kitGalleryPictureLabel" to POSITION, "kitGalleryPosition" to POSITION, "kitQueuedItemLabel" to POSITION,
        "kitTaskChipAdded" to POSITION, "kitTaskChipCount" to POSITION, "kitTaskChipDone" to POSITION,
        "kitTaskChipSpoken" to POSITION, "kitTaskChipSpokenNow" to POSITION, "kitTourStep" to POSITION,
        "managedRecoveryAttempts" to POSITION, "mobileTasksProgress" to POSITION, "notifStatusSetupTitle" to POSITION,
        "notifStatusTasksProgress" to POSITION, "openCode2MoveRetrying" to POSITION, "phoneTeamStripStep" to POSITION,
        "queuedMoveDonePartial" to POSITION, "readerUiLineRange" to POSITION, "runningPhoneLimitBody" to POSITION,
        "runningPhoneRowNearLimit" to POSITION, "settingsV3SharingCount" to POSITION,
        "setupMigrationProgress" to POSITION, "teamBoardEpicProgress" to POSITION, "teamProgressStep" to POSITION,
        "teamProjectLaneCount" to POSITION, "teamProjectLanesTitle" to POSITION,
        "teamProjectMilestoneTasks" to POSITION, "teamProjectPromoteMerged" to POSITION,
        "teamUiHomeRunProgress" to POSITION, "teamUiRunStageSemantics" to POSITION, "termuxProcsBudget" to POSITION,
        "thisPhoneHistoryMovingCount" to POSITION,
        // number as a name (41)
        "agentCardPhotoName" to NAME, "agentCardPhotoRemove" to NAME, "aiSetupSourceEmpty" to NAME,
        "aiSetupSourceSets" to NAME, "credentialUnnamed" to NAME, "e7ConnectionFailure32" to NAME,
        "e7SetupConnectingAttempt" to NAME, "e7SetupTerminalNumber" to NAME, "fbWhenMonthlyOn" to NAME,
        "fileLineOutsidePreview" to NAME, "filesTextMatchLine" to NAME, "keptSvcExited" to NAME,
        "keptSvcLogExit" to NAME, "keptSvcPortBusy" to NAME, "keptSvcPortNumber" to NAME, "kitDiffLine" to NAME,
        "kitDiffLineAdded" to NAME, "kitDiffLineRemoved" to NAME, "linuxDevProgram" to NAME,
        "linuxDevStopBody" to NAME, "linuxMgrPortPreviewNamed" to NAME, "linuxMgrPortRow" to NAME,
        "linuxSvcExited" to NAME, "linuxSvcLogCrashed" to NAME, "linuxSvcLogEnded" to NAME,
        "localTerminalEndedBody" to NAME, "localTerminalShellName" to NAME, "navProjectDevServer" to NAME,
        "productErrorServer" to NAME, "readerUiLine" to NAME, "readerUiPathLine" to NAME, "runningDetailPid" to NAME,
        "secHistoryEntry" to NAME, "setupNativeSourceOrder" to NAME, "teamChatWorkerNumbered" to NAME,
        "teamProjectPlanFor" to NAME, "teamProjectPlanPhase" to NAME, "teamProjectPromoteAccepted" to NAME,
        "teamProjectPromoteMilestone" to NAME, "toolCardExitFailed" to NAME, "workExitCode" to NAME,
        // label: value (15)
        "activeContextAllCount" to LABEL, "activeContextTypeCount" to LABEL, "agentToolsAlreadyImported" to LABEL,
        "agentToolsMonitorCount" to LABEL, "kitQueuedTitle" to LABEL, "linuxLifetimeExit" to LABEL,
        "linuxLifetimeRestarts" to LABEL, "linuxPortOwnerResult" to LABEL, "modelPickerAllModels" to LABEL,
        "monitor40QuestionCount" to LABEL, "monitorNativePending" to LABEL, "monitorNativeRunning" to LABEL,
        "navProjectChangesCount" to LABEL, "servicesExit" to LABEL, "terminal40Details" to LABEL,
        // no noun to agree with (48)
        "agentElsewhereCounts" to NO_NOUN, "agentProjectLineChanges" to NO_NOUN, "chatsFilterRunningCount" to NO_NOUN,
        // The Running chip's tally ("Running · 2"), as chatsFilterRunningCount.
        "chatsHomeRunningCount" to NO_NOUN,
        "chatsReturnBriefMore" to NO_NOUN, "e7SharedDetail726" to NO_NOUN, "e7SharedDetail754" to NO_NOUN,
        "e7SharedDetail755" to NO_NOUN, "e7SharedDetail756" to NO_NOUN, "fbMemoryFromFactsBook" to NO_NOUN,
        "fbNeedsYouWaiting" to NO_NOUN, "fbRuleWeek" to NO_NOUN, "folderBrowserHintMore" to NO_NOUN,
        "gitNewCount" to NO_NOUN, "kitCodeChanges" to NO_NOUN, "kitDiffCounts" to NO_NOUN,
        "kitRequestChangeSize" to NO_NOUN, "kitSearchPartial" to NO_NOUN, "linuxMgrStopForced" to NO_NOUN,
        "linuxMgrStopRemaining" to NO_NOUN, "native_schedule_digest_body" to NO_NOUN, "notifStatusMore" to NO_NOUN,
        "notifStatusPublicWorking" to NO_NOUN, "notifStatusStillWorking" to NO_NOUN,
        "notifStatusWaitingCount" to NO_NOUN, "phoneScanDoneIn" to NO_NOUN, "phoneScanFirstShown" to NO_NOUN,
        "pluginsBuiltinActive" to NO_NOUN, "pluginsBuiltinFailed" to NO_NOUN, "readerUiOnPrompt" to NO_NOUN,
        "reviewRevertFileLines" to NO_NOUN, "rows40Working" to NO_NOUN, "shellServerPlusOthers" to NO_NOUN,
        "teamBoardFlagBlockedByMore" to NO_NOUN, "teamProjectFindingsCritical" to NO_NOUN,
        "teamProjectFindingsMajor" to NO_NOUN, "teamProjectFindingsMinor" to NO_NOUN,
        "teamProjectMilestoneWaits" to NO_NOUN, "teamProjectPlanAfter" to NO_NOUN, "termuxProcsBudgetNote" to NO_NOUN,
        "termuxProcsBudgetOver" to NO_NOUN, "termuxProcsRefused" to NO_NOUN, "termuxProcsRemaining" to NO_NOUN,
        "termuxProcsStopKind" to NO_NOUN, "termuxProcsStopKindTitle" to NO_NOUN, "termuxProcsStopped" to NO_NOUN,
        "termuxProcsStoppedForced" to NO_NOUN, "widget_status_tasks_running" to NO_NOUN, "workCount" to NO_NOUN,
        // frozen ARB copy (B-L10N-010), needs an ARB plural (35)
        "agentCardTableLabel" to FROZEN_ARB, "appearanceGuideCountIntro" to FROZEN_ARB,
        "appearanceSeeAllLooks" to FROZEN_ARB, "appearanceSeeAllThemes" to FROZEN_ARB,
        "builtinServerTimedOut" to FROZEN_ARB, "e7ConnectionFailure1" to FROZEN_ARB,
        "e7SettingsDiagnosticOccurrences" to FROZEN_ARB, "e7SettingsStorageSummary" to FROZEN_ARB,
        "e7SetupPairedChoice" to FROZEN_ARB, "e7SharedDetail714" to FROZEN_ARB, "e7SharedDetail715" to FROZEN_ARB,
        "fbErrorSlowAfter" to FROZEN_ARB, "filesTextLimited" to FROZEN_ARB,
        "inAppServerStartTimedOutBody" to FROZEN_ARB, "linuxMgrPkgShowAll" to FROZEN_ARB,
        "linuxMgrStopChanged" to FROZEN_ARB, "localTerminalCost" to FROZEN_ARB, "localTerminalCostNow" to FROZEN_ARB,
        "mobileTasksShowAll" to FROZEN_ARB, "notifStatusChatsWorking" to FROZEN_ARB, "phoneScanStopped" to FROZEN_ARB,
        "processBudgetCount" to FROZEN_ARB, "processBudgetWaitRoom" to FROZEN_ARB, "projectExportDone" to FROZEN_ARB,
        "projectHealthLineCounts" to FROZEN_ARB, "readerUiReferenceFull" to FROZEN_ARB,
        "sessionNoteBytes" to FROZEN_ARB, "sessionNoteTooLong" to FROZEN_ARB, "teamProgressMany" to FROZEN_ARB,
        "teamProjectAllTasksDone" to FROZEN_ARB, "teamProjectHeadlineDone" to FROZEN_ARB,
        "teamProjectSettingsSummaryParallel" to FROZEN_ARB, "teamProjectTimelineRepeated" to FROZEN_ARB,
        "teamUiCardAgentsSummary" to FROZEN_ARB, "termuxStorageRefusedCount" to FROZEN_ARB,
        "phoneTeamProofSignal" to NAME, "phoneTeamProofCheckSignal" to NAME,
        )
    }
}
