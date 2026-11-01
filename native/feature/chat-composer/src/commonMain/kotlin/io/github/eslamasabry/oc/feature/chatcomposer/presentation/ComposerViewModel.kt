package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

/**
 * One conversation's composer ViewModel (plan 09 §6.3: keyed by the target, its engines resolved per conversation).
 * Not yet contributed to Metro: the per-conversation factory needs `ConnectionRegistry.graphFor` and the port adapter,
 * both follow-ups once `:engine:composer` compiles and `:engine:delivery` exists (report §6).
 */
class ComposerViewModel(
    port: ComposerPort,
    openers: ComposerOpeners,
    clock: Clock,
) : ViewModel() {
    private val presenter = ComposerPresenter(viewModelScope, port, openers, clock)

    val state: StateFlow<ComposerUi> = presenter.state
    val actions: ComposerActions = presenter

    /** Called with `ComposerChrome.pendingInsert()`; the presenter inserts each id once. */
    fun insert(id: Long, text: String): Unit = presenter.insert(id, text)
}
