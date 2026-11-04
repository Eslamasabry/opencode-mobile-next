package io.github.eslamasabry.oc.feature.chat.notes

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
import io.github.eslamasabry.oc.engine.agents.notes.SessionNoteReview
import io.github.eslamasabry.oc.engine.agents.notes.SessionNotes
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

public data class SessionNoteUi(
    val visible: Boolean = false,
    val available: Boolean = false,
    val loading: Boolean = false,
    val ready: Boolean = false,
    val draft: String = "",
    val saved: String? = null,
    val failure: Failure? = null,
    val discard: Boolean = false,
    val savedReceipt: Boolean? = null,
    val editRevision: Long = 0,
) {
    public val dirty: Boolean
        get() = ready && draft != saved.orEmpty()

    public val tooLarge: Boolean
        get() = JsonPrimitive(draft).toString().encodeToByteArray().size > 8192
}

public interface SessionNoteActions {
    public fun openNote()

    public fun closeNote()

    public fun discardNote()

    public fun keepEditing()

    public fun changeNote(value: String)

    public fun saveNote()

    public fun removeNote()

    public fun reloadNote()
}

/**
 * One note editor per chat entry. Draft text stays local until the explicit Save action succeeds.
 */
@AssistedInject
public class SessionNoteViewModel(
    @Assisted private val route: ChatRoute,
    private val notes: SessionNotes,
) : ViewModel(), SessionNoteActions {
    private val mutableState = MutableStateFlow(SessionNoteUi())
    public val state: StateFlow<SessionNoteUi> = mutableState.asStateFlow()
    private var review: SessionNoteReview? = null
    private var closed = false

    init {
        viewModelScope.launch {
            notes.availability(route.ref).collect { available ->
                mutableState.update { it.copy(available = available) }
            }
        }
    }

    override fun openNote() {
        if (mutableState.value.loading) return
        mutableState.update { it.copy(visible = true) }
        reloadNote()
    }

    override fun reloadNote() {
        if (mutableState.value.loading) return
        mutableState.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            val loaded = notes.load(route.ref)
            if (closed) return@launch
            when (loaded) {
                is Outcome.Failed ->
                    mutableState.update { it.copy(loading = false, failure = loaded.failure) }
                is Outcome.Ok -> {
                    review = loaded.value
                    mutableState.update {
                        it.copy(
                            loading = false,
                            ready = true,
                            saved = loaded.value.value,
                            draft = loaded.value.value.orEmpty(),
                            failure = null,
                            savedReceipt = notes.receipt(loaded.value),
                            editRevision = it.editRevision + 1,
                        )
                    }
                }
            }
        }
    }

    override fun closeNote() {
        if (mutableState.value.loading) return
        mutableState.update { if (it.dirty) it.copy(discard = true) else it.copy(visible = false) }
    }

    override fun discardNote() {
        mutableState.update { it.copy(visible = false, discard = false) }
    }

    override fun keepEditing() {
        mutableState.update { it.copy(discard = false) }
    }

    override fun changeNote(value: String) {
        mutableState.update { if (it.loading) it else it.copy(draft = value, savedReceipt = null) }
    }

    override fun saveNote() {
        save(mutableState.value.draft)
    }

    override fun removeNote() {
        save(null)
    }

    private fun save(value: String?) {
        val reviewed = review ?: return
        val state = mutableState.value
        if (state.loading || (value != null && (value.isBlank() || state.tooLarge))) return
        mutableState.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            val written = notes.save(reviewed, value)
            if (closed) return@launch
            when (written) {
                is Outcome.Failed ->
                    mutableState.update { it.copy(loading = false, failure = written.failure) }
                is Outcome.Ok -> {
                    mutableState.update { it.copy(loading = false, savedReceipt = value != null) }
                    reloadNote()
                }
            }
        }
    }

    override fun onCleared() {
        closed = true
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): SessionNoteViewModel
    }
}
