package io.github.eslamasabry.oc.feature.prompts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PromptsRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Saved prompts for the conversation's server (Flutter `_PromptStashSheet`): list newest first, search, write a new
 * one, edit, delete at once with Undo, and put one into the conversation's composer. One operation at a time; a tap
 * while one runs does nothing.
 */
@AssistedInject
public class SavedPromptsViewModel(
    @Assisted private val route: PromptsRoute.Saved,
    private val store: SavedPromptsStore,
    private val inbox: ComposerInbox,
    private val nav: AppNavigator,
) : ViewModel(), SavedPromptsActions {
    private val profile = route.ref.profileId.raw
    private val local = MutableStateFlow(SavedPromptsUi())
    public val state: StateFlow<SavedPromptsUi> = local.asStateFlow()
    private var generation = 0L
    private var restore: SavedPromptRestore? = null

    init {
        reload()
    }

    private fun reload(after: SavedPromptsUi.() -> SavedPromptsUi = { this }) {
        viewModelScope.launch {
            when (val read = store.list(profile)) {
                is Outcome.Ok ->
                    local.update {
                        it.copy(loading = false, prompts = read.value.toImmutableList()).after()
                    }
                is Outcome.Failed ->
                    local.update {
                        it.copy(loading = false, issue = SavedPromptsIssue.ReadFailed, failure = read.failure)
                    }
            }
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, issue = null, failure = null, note = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private fun failed(issue: SavedPromptsIssue, failure: Failure?) {
        local.update { it.copy(issue = issue, failure = failure) }
    }

    override fun back(): Unit = nav.back()

    override fun retry() {
        local.update { it.copy(loading = true, issue = null, failure = null) }
        reload()
    }

    override fun search(query: String) {
        local.update { it.copy(query = query) }
    }

    override fun newPrompt() {
        if (local.value.busy) return
        if (local.value.full) {
            failed(SavedPromptsIssue.Full, null)
            return
        }
        generation += 1
        local.update { it.copy(editor = PromptEditor(null, "", generation), issue = null, note = null) }
    }

    override fun editPrompt(id: String) {
        if (local.value.busy) return
        val prompt = local.value.prompts.firstOrNull { it.id == id } ?: return
        generation += 1
        local.update { it.copy(editor = PromptEditor(id, prompt.text, generation), issue = null, note = null) }
    }

    override fun cancelEdit() {
        if (!local.value.busy) local.update { it.copy(editor = null) }
    }

    override fun submitEdit(text: String) {
        val editor = local.value.editor ?: return
        if (text.isBlank()) return
        operation {
            val id = editor.id
            val result = if (id == null) store.save(profile, text) else store.edit(profile, id, text)
            when (result) {
                is Outcome.Failed ->
                    failed(
                        if (id == null && local.value.full) SavedPromptsIssue.Full else SavedPromptsIssue.SaveFailed,
                        result.failure,
                    )
                is Outcome.Ok -> {
                    val note = if (id == null) SavedPromptsNote.Saved else SavedPromptsNote.Edited
                    local.update { it.copy(editor = null) }
                    reload { copy(note = note) }
                }
            }
        }
    }

    override fun insert(id: String) {
        val current = local.value
        if (current.busy || current.editor != null) return
        val prompt = current.prompts.firstOrNull { it.id == id } ?: return
        if (prompt.attachments > 0 || prompt.references > 0) {
            // The composer takes words only for now: never drop the files silently (Codex prompts #2).
            local.update {
                it.copy(issue = SavedPromptsIssue.FilesStay, failure = null, wordsOnly = id.takeIf { prompt.text.isNotBlank() })
            }
            return
        }
        if (prompt.text.isBlank()) return
        inbox.offer(ComposerInbox.target(profile, route.ref.sessionId.raw), prompt.text)
        nav.returnToChat()
    }

    override fun insertWordsOnly(id: String) {
        val current = local.value
        if (current.busy || current.editor != null) return
        val prompt = current.prompts.firstOrNull { it.id == id } ?: return
        if (prompt.text.isBlank()) return
        local.update { it.copy(issue = null, wordsOnly = null) }
        inbox.offer(ComposerInbox.target(profile, route.ref.sessionId.raw), prompt.text)
        nav.returnToChat()
    }

    override fun editorChanged(text: String) {
        local.update { ui -> ui.editor?.let { ui.copy(editor = it.copy(initial = text)) } ?: ui }
    }

    override fun delete(id: String) {
        operation {
            when (val removed = store.delete(profile, id)) {
                is Outcome.Failed -> failed(SavedPromptsIssue.DeleteFailed, removed.failure)
                is Outcome.Ok -> {
                    restore = removed.value
                    local.update { ui ->
                        ui.copy(
                            prompts = ui.prompts.filterNot { it.id == id }.toImmutableList(),
                            undo = SavedPromptsUndo(id),
                        )
                    }
                }
            }
        }
    }

    override fun undo() {
        val pending = restore ?: return
        operation {
            when (val put = pending.undo()) {
                is Outcome.Failed -> {
                    // B-STATE-085: the Undo stays offered, so the person can try again (Flutter's Try again).
                    failed(SavedPromptsIssue.UndoFailed, put.failure)
                }
                is Outcome.Ok -> {
                    restore = null
                    local.update { it.copy(undo = null) }
                    reload()
                }
            }
        }
    }

    override fun undoTimedOut() {
        restore = null
        local.update { it.copy(undo = null) }
    }

    override fun openCommands(): Unit = nav.open(PromptsRoute.Commands(route.ref))

    override fun openTasks(): Unit = nav.open(PromptsRoute.Tasks(route.ref))

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: PromptsRoute.Saved): SavedPromptsViewModel
    }
}
