@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.schedules

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.engine.schedules.MissedRunPolicy
import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import io.github.eslamasabry.oc.engine.schedules.ScheduleCode
import io.github.eslamasabry.oc.feature.schedules.presentation.EditIssue
import io.github.eslamasabry.oc.feature.schedules.presentation.EditPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.EditorFields
import io.github.eslamasabry.oc.feature.schedules.presentation.LastRunUi
import io.github.eslamasabry.oc.feature.schedules.presentation.ModelChoice
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditActions
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditUi
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleRowUi
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesActions
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesUi
import io.github.eslamasabry.oc.feature.schedules.presentation.Unavailable
import io.github.eslamasabry.oc.feature.schedules.ui.ScheduleEditScreen
import io.github.eslamasabry.oc.feature.schedules.ui.SchedulesScreen
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** Audit capture only: the scheduled prompts list and the schedule editor in their states. */
class AuditCaptureTest {
    private val t0 = 1_793_606_400_000L

    private object ListA : SchedulesActions {
        override fun back() {}

        override fun create() {}

        override fun edit(id: String) {}

        override fun setEnabled(id: String, enabled: Boolean) {}

        override fun runNow(id: String) {}

        override fun askDelete(id: String) {}

        override fun confirmDelete() {}

        override fun cancelDelete() {}

        override fun openConversation(sessionId: String) {}

        override fun setDigest(enabled: Boolean) {}

        override fun retry() {}
    }

    private object EditA : ScheduleEditActions {
        override fun back() {}

        override fun save(input: EditorFields) {}
    }

    private val failedRun = LastRunUi(RunStatus.Failed, "needs_you", t0 - 60_000L, "ses_1", late = false)

    private val rows =
        persistentListOf(
            ScheduleRowUi("a1", "Check the nightly build", Recurrence.daily(9, 0), t0 + 3_600_000L, true, false, failedRun),
            ScheduleRowUi("a2", "Summarize yesterday's commits", Recurrence.weekdays(8, 30), t0 + 7_200_000L, true, true, LastRunUi(RunStatus.Ran, null, t0 - 86_400_000L, "ses_2", late = false)),
            ScheduleRowUi("a3", "Remind me to rotate the keys", Recurrence.once(t0 + 172_800_000L), t0 + 172_800_000L, false, false, null),
            ScheduleRowUi("a4", "Run the test suite", Recurrence.interval(6, t0), t0 + 21_600_000L, true, false, LastRunUi(RunStatus.Running, null, t0 - 30_000L, "ses_3", late = false)),
            ScheduleRowUi("a5", "Skipped one", Recurrence.daily(22, 15), null, true, false, LastRunUi(RunStatus.Skipped, null, t0 - 3_600_000L, null, late = false)),
            ScheduleRowUi("a6", "Late run of a prompt", Recurrence.daily(7, 0), t0 + 1_000_000L, true, false, LastRunUi(RunStatus.Ran, null, t0 - 7_200_000L, "ses_4", late = true)),
            ScheduleRowUi(
                "a7",
                "A very long scheduled prompt that goes on and on and on, describing in detail everything the agent should check, refactor, test and report back about before the day starts",
                Recurrence.daily(6, 45),
                t0 + 9_000_000L,
                true,
                true,
                null,
            ),
        )

    private fun list(state: String, ui: SchedulesUi, setup: ComposeUiTest.() -> Unit = {}) {
        Audit.shoot("schedules", "schedules-list", state, setup = setup) { SchedulesScreen(ui, ListA) }
    }

    private fun openMenu(prompt: String): ComposeUiTest.() -> Unit = {
        runCatching {
            onNodeWithContentDescription("More · $prompt").performClick()
            waitForIdle()
            mainClock.advanceTimeBy(600)
            waitForIdle()
        }
    }

    @Test
    fun listStates() = Audit.guard("schedules__schedules-list__build") {
        val full = SchedulesUi(phase = SchedulesPhase.List, serverName = "Home server", rows = rows, nowMs = t0)
        list("loading", SchedulesUi(phase = SchedulesPhase.Loading))
        list("unavailable-server", SchedulesUi(phase = SchedulesPhase.Unavailable, unavailable = Unavailable.Server, nowMs = t0))
        list("unavailable-phone", SchedulesUi(phase = SchedulesPhase.Unavailable, unavailable = Unavailable.Phone, nowMs = t0))
        list("unavailable-storage", SchedulesUi(phase = SchedulesPhase.Unavailable, unavailable = Unavailable.Storage, nowMs = t0))
        list("empty", SchedulesUi(phase = SchedulesPhase.Empty, serverName = "Home server", nowMs = t0))
        list("populated", full)
        list("populated-digest-on", full.copy(digestOn = true))
        list("populated-highlight", full.copy(highlight = "a2"))
        list("populated-busy", full.copy(busy = true))
        list("populated-started", full.copy(started = "a1"))
        list("populated-error", full.copy(error = ScheduleCode.StorageBusy))
        list("populated-error-limit", full.copy(error = ScheduleCode.ScheduleLimit))
        list("confirm-delete", full.copy(confirmDelete = "a1"))
        list("row-menu", full, setup = openMenu("Check the nightly build"))
        list("long-server-name", full.copy(serverName = "A server with a really long name that keeps going past the edge of the screen"))
    }

    private fun edit(state: String, ui: ScheduleEditUi) {
        Audit.shoot("schedules", "schedule-editor", state) { ScheduleEditScreen(ui, EditA) }
    }

    @Test
    fun editorStates() = Audit.guard("schedules__schedule-editor__build") {
        val ready = ScheduleEditUi(phase = EditPhase.Ready, fields = EditorFields(prompt = "Check the nightly build", project = "/work/app"), seed = 1, nowMs = t0)
        val models = persistentListOf(ModelChoice("anthropic", "claude-sonnet", "Claude Sonnet"), ModelChoice("openai", "gpt", "GPT"))
        edit("loading", ScheduleEditUi(phase = EditPhase.Loading))
        edit("unavailable", ScheduleEditUi(phase = EditPhase.Unavailable))
        edit("new-empty", ScheduleEditUi(phase = EditPhase.Ready, fields = EditorFields(), seed = 1, nowMs = t0))
        edit("new-once", ready.copy(fields = ready.fields.copy(kind = RecurrenceKind.Once, date = "2026-11-10", time = "09:00")))
        edit("new-daily", ready.copy(fields = ready.fields.copy(kind = RecurrenceKind.Daily)))
        edit("new-weekdays", ready.copy(fields = ready.fields.copy(kind = RecurrenceKind.Weekdays)))
        edit("new-interval", ready.copy(fields = ready.fields.copy(kind = RecurrenceKind.Interval, hours = "6")))
        edit("with-models", ready.copy(models = models, fields = ready.fields.copy(model = models.first())))
        edit("skip-missed", ready.copy(fields = ready.fields.copy(missed = MissedRunPolicy.Skip, kind = RecurrenceKind.Daily)))
        edit(
            "conversation",
            ready.copy(continuesConversation = true, conversationTitle = "Fix the login redirect", canChooseConversation = true, fields = ready.fields.copy(thisConversation = true)),
        )
        edit("issue-prompt-required", ready.copy(fields = EditorFields(project = "/work/app"), issue = EditIssue.PromptRequired))
        edit("issue-project-required", ready.copy(fields = EditorFields(prompt = "Summarize"), issue = EditIssue.ProjectRequired))
        edit("issue-time-invalid", ready.copy(fields = ready.fields.copy(kind = RecurrenceKind.Daily, time = "25:99"), issue = EditIssue.TimeInvalid))
        edit("issue-date-invalid", ready.copy(fields = ready.fields.copy(kind = RecurrenceKind.Once, date = "soon"), issue = EditIssue.DateInvalid))
        edit("issue-hours-invalid", ready.copy(fields = ready.fields.copy(kind = RecurrenceKind.Interval, hours = "0"), issue = EditIssue.HoursInvalid))
        edit("issue-future-required", ready.copy(fields = ready.fields.copy(kind = RecurrenceKind.Once, date = "2020-01-01"), issue = EditIssue.FutureRequired))
        edit("saving", ready.copy(saving = true))
        edit("save-error", ready.copy(error = ScheduleCode.StorageBusy))
        edit("save-error-limit", ready.copy(error = ScheduleCode.ScheduleLimit))
        edit("editing-failed-run", ready.copy(editing = true, lastRun = failedRun))
        edit("editing-ran", ready.copy(editing = true, lastRun = LastRunUi(RunStatus.Ran, null, t0 - 60_000L, "ses_1", late = false)))
        edit("editing-running", ready.copy(editing = true, lastRun = LastRunUi(RunStatus.Running, null, t0 - 30_000L, "ses_1", late = false)))
        edit("editing-skipped", ready.copy(editing = true, lastRun = LastRunUi(RunStatus.Skipped, null, t0 - 60_000L, null, late = false)))
        edit("editing-late-run", ready.copy(editing = true, lastRun = LastRunUi(RunStatus.Ran, null, t0 - 60_000L, "ses_1", late = true)))
        edit(
            "long-prompt-and-project",
            ready.copy(
                fields = EditorFields(
                    prompt = "A very long scheduled prompt that goes on and on and on, describing in detail everything the agent should check, refactor, test and report back about before the day starts, and then some more",
                    project = "/home/user/projects/a-very-long-project-folder-name/with/many/nested/directories/that/go/on/app",
                ),
            ),
        )
    }
}
