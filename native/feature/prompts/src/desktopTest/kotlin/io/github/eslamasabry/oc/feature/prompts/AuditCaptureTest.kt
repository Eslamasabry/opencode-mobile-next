package io.github.eslamasabry.oc.feature.prompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandItem
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandsActions
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandsUi
import io.github.eslamasabry.oc.feature.prompts.presentation.ListPhase
import io.github.eslamasabry.oc.feature.prompts.presentation.PromptEditor
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptItem
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsActions
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsIssue
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsNote
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsUi
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsUndo
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskRules
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksActions
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksUi
import io.github.eslamasabry.oc.feature.prompts.ui.CommandsScreen
import io.github.eslamasabry.oc.feature.prompts.ui.SavedPromptsScreen
import io.github.eslamasabry.oc.feature.prompts.ui.TasksScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Audit capture only: renders the prompts screens in many states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object Taps : SavedPromptsActions, CommandsActions, TasksActions {
        override fun back() {}
        override fun retry() {}
        override fun search(query: String) {}
        override fun newPrompt() {}
        override fun editPrompt(id: String) {}
        override fun cancelEdit() {}
        override fun submitEdit(text: String) {}
        override fun insert(id: String) {}
        override fun delete(id: String) {}
        override fun undo() {}
        override fun undoTimedOut() {}
        override fun openCommands() {}
        override fun openTasks() {}
        override fun choose(name: String) {}
        override fun toggleUnfinished() {}
        override fun activateSkill(id: String) = Unit
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) { content() }
    }

    private val long = "Review the open pull request and explain, file by file, every change that touches the connection layer, " +
        "then list anything that could break an existing profile after the update and what I should test first"

    private val prompts = persistentListOf(
        SavedPromptItem("b", "Review the open pull request", 2),
        SavedPromptItem("a", "Explain the failing test", 1, attachments = 2),
        SavedPromptItem("c", long, 3, attachments = 1, references = 2),
        SavedPromptItem("d", "Kept from a removed server", 4, kept = true),
        SavedPromptItem("e", "Needs a file that is gone", 5, unavailable = listOf("notes.txt")),
    )

    private fun saved(name: String, ui: SavedPromptsUi) =
        Audit.guard("prompts__saved__${name}__build") {
            Audit.shoot("prompts", "saved-prompts", name) { Frame { SavedPromptsScreen(ui, Taps) } }
        }

    @Test
    fun savedPrompts() {
        saved("populated", SavedPromptsUi(loading = false, prompts = prompts))
        saved("loading", SavedPromptsUi(loading = true))
        saved("empty", SavedPromptsUi(loading = false))
        saved("read-failed", SavedPromptsUi(loading = false, issue = SavedPromptsIssue.ReadFailed))
        saved("save-failed", SavedPromptsUi(loading = false, prompts = prompts, issue = SavedPromptsIssue.SaveFailed))
        saved("delete-failed", SavedPromptsUi(loading = false, prompts = prompts, issue = SavedPromptsIssue.DeleteFailed))
        saved("full", SavedPromptsUi(loading = false, prompts = prompts, issue = SavedPromptsIssue.Full))
        saved("files-stay", SavedPromptsUi(loading = false, prompts = prompts, issue = SavedPromptsIssue.FilesStay, wordsOnly = "Explain the failing test"))
        saved("restore-failed", SavedPromptsUi(loading = false, prompts = prompts, issue = SavedPromptsIssue.RestoreFailed))
        saved("note-saved", SavedPromptsUi(loading = false, prompts = prompts, note = SavedPromptsNote.Saved))
        saved("undo", SavedPromptsUi(loading = false, prompts = prompts, undo = SavedPromptsUndo("b")))
        saved("search-no-match", SavedPromptsUi(loading = false, prompts = prompts, query = "zzzz"))
        saved("search-match", SavedPromptsUi(loading = false, prompts = prompts, query = "review"))
        saved("editor-new", SavedPromptsUi(loading = false, prompts = prompts, editor = PromptEditor(null, "", 1L)))
        saved("editor-long", SavedPromptsUi(loading = false, prompts = prompts, editor = PromptEditor("c", long, 2L)))
        saved("busy", SavedPromptsUi(loading = false, prompts = prompts, busy = true))
        saved("migrating", SavedPromptsUi(loading = false, prompts = prompts, migrationPending = true))
    }

    private fun commands(name: String, ui: CommandsUi) =
        Audit.guard("prompts__commands__${name}__build") {
            Audit.shoot("prompts", "commands", name) { Frame { CommandsScreen(ui, Taps) } }
        }

    @Test
    fun commands() {
        val items = listOf(
            CommandItem("review", "Review changes"),
            CommandItem("init", null),
            CommandItem("a-very-long-command-name-that-keeps-going-and-going", "A long description that explains in some detail what this command does for the person using it on a narrow screen"),
        )
        commands("populated", CommandsUi(ListPhase.Shown(items, "Home")))
        commands("loading", CommandsUi(ListPhase.Loading))
        commands("empty", CommandsUi(ListPhase.Shown(emptyList(), "Home")))
        commands("not-on-server", CommandsUi(ListPhase.NotOnServer("Home")))
        commands("offline", CommandsUi(ListPhase.Failed(Failure.Network(NetworkWhy.Offline))))
    }

    private fun tasks(name: String, ui: TasksUi) =
        Audit.guard("prompts__tasks__${name}__build") {
            Audit.shoot("prompts", "tasks", name) { Frame { TasksScreen(ui, Taps) } }
        }

    @Test
    fun tasks() {
        val items = listOf(
            TaskRules.item("Write tests", "completed", "high"),
            TaskRules.item("Fix build", "in_progress", null),
            TaskRules.item("Old idea", "cancelled", null),
            TaskRules.item("Wire the new connection layer through every screen that still reads the old profile store directly", "pending", "medium"),
        )
        tasks("populated", TasksUi(ListPhase.Shown(items, "Home")))
        tasks("unfinished-only", TasksUi(ListPhase.Shown(items, "Home"), unfinishedOnly = true))
        tasks("loading", TasksUi(ListPhase.Loading))
        tasks("empty", TasksUi(ListPhase.Shown(emptyList(), "Home")))
        tasks("not-on-server", TasksUi(ListPhase.NotOnServer("Home")))
        tasks("offline", TasksUi(ListPhase.Failed(Failure.Network(NetworkWhy.Offline))))
    }
}
