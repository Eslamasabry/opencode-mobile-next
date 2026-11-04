package io.github.eslamasabry.oc.feature.prompts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public data class KeptPromptsUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val prompts: ImmutableList<SavedPromptItem> = persistentListOf(),
    val query: String = "",
    val destinations: ImmutableList<KeptPromptDestination> = persistentListOf(),
    val choosing: String? = null,
    val wordsOnly: Boolean = false,
    val failure: Failure? = null,
    val undo: String? = null,
) {
    val visible: List<SavedPromptItem>
        get() = SavedPromptsRules.filter(prompts, query)

    override fun toString(): String =
        "KeptPromptsUi(rows=${prompts.size},loading=$loading,busy=$busy)"
}

public interface KeptPromptsActions {
    public fun back()

    public fun retry()

    public fun search(query: String)

    public fun restore(id: String)

    public fun restoreWordsOnly(id: String)

    public fun choose(destination: ConversationRef)

    public fun closePicker()

    public fun delete(id: String)

    public fun undo()

    public fun undoTimedOut()

    public fun openServers()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class KeptPromptsViewModel(
    private val kept: KeptQueuedPrompts,
    private val inbox: ComposerInbox,
    private val nav: AppNavigator,
) : ViewModel(), KeptPromptsActions {
    private val local = MutableStateFlow(KeptPromptsUi())
    public val state: StateFlow<KeptPromptsUi> = local.asStateFlow()
    private var undoReceipt: SavedPromptRestore? = null

    init {
        retry()
    }

    override fun back() = nav.back()

    override fun search(query: String) {
        local.update { it.copy(query = query) }
    }

    override fun openServers() = nav.open(ServersRoute.Home)

    override fun closePicker() {
        if (!local.value.busy)
            local.update { it.copy(choosing = null, destinations = persistentListOf()) }
    }

    private fun work(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busy = false, loading = false) }
            }
        }
    }

    override fun retry() = work {
        when (val rows = kept.list()) {
            is Outcome.Failed -> local.update { it.copy(failure = rows.failure) }
            is Outcome.Ok -> local.update { it.copy(prompts = rows.value.toImmutableList()) }
        }
    }

    override fun restore(id: String) = selectDestination(id, false)

    override fun restoreWordsOnly(id: String) = selectDestination(id, true)

    private fun selectDestination(id: String, wordsOnly: Boolean) = work {
        if (local.value.prompts.none { it.id == id }) return@work
        when (val targets = kept.destinations()) {
            is Outcome.Failed -> local.update { it.copy(failure = targets.failure) }
            is Outcome.Ok ->
                local.update {
                    it.copy(
                        choosing = id,
                        wordsOnly = wordsOnly,
                        destinations = targets.value.toImmutableList(),
                    )
                }
        }
    }

    override fun choose(destination: ConversationRef) = work {
        val id = local.value.choosing ?: return@work
        if (local.value.destinations.none { it.ref == destination }) return@work
        when (val result = kept.restore(id, destination)) {
            is Outcome.Failed -> local.update { it.copy(failure = result.failure) }
            is Outcome.Ok -> {
                val draft = result.value
                if (!draft.stillCurrent()) {
                    local.update { it.copy(failure = Failure.Refused(RefusalReason.StaleScope)) }
                    return@work
                }
                inbox.offer(
                    ComposerInbox.target(destination.profileId.raw, destination.sessionId.raw),
                    draft.text,
                    attachments = if (local.value.wordsOnly) emptyList() else draft.attachments,
                    missingAttachments =
                        if (local.value.wordsOnly) emptyList() else draft.unavailable,
                    stillCurrent = draft.stillCurrent,
                )
                local.update { it.copy(choosing = null) }
                nav.openChat(destination, focusComposer = true)
            }
        }
    }

    override fun delete(id: String) = work {
        when (val result = kept.delete(id)) {
            is Outcome.Failed -> local.update { it.copy(failure = result.failure) }
            is Outcome.Ok -> {
                undoReceipt = result.value
                local.update {
                    it.copy(
                        prompts = it.prompts.filterNot { p -> p.id == id }.toImmutableList(),
                        undo = id,
                    )
                }
            }
        }
    }

    override fun undo() = work {
        val receipt = undoReceipt ?: return@work
        when (val result = receipt.undo()) {
            is Outcome.Failed -> local.update { it.copy(failure = result.failure) }
            is Outcome.Ok -> {
                undoReceipt = null
                local.update { it.copy(undo = null) }
                when (val rows = kept.list()) {
                    is Outcome.Failed -> local.update { it.copy(failure = rows.failure) }
                    is Outcome.Ok ->
                        local.update { it.copy(prompts = rows.value.toImmutableList()) }
                }
            }
        }
    }

    override fun undoTimedOut() {
        if (!local.value.busy) {
            undoReceipt = null
            local.update { it.copy(undo = null) }
        }
    }
}
