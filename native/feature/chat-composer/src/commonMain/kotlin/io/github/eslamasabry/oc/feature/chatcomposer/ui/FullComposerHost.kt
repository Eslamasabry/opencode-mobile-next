package io.github.eslamasabry.oc.feature.chatcomposer.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerViewModel

/**
 * What the chat hands the composer (plan 09 §6.3 `ComposerChrome`, minus what the facts already carry). A feature-local
 * mirror until `:ui:kit-chat` declares `ComposerHost`/`ComposerChrome`/`InsertRequest`; then [FullComposerHost]
 * implements that interface and this class goes (report §6).
 */
class ComposerChromeInputs(
    /** An agent `choices` tap: (id, text); state, not a one-shot flow, so a request made mid-transition waits. */
    val pendingInsert: () -> Pair<Long, String>?,
    val insertDone: (id: Long) -> Unit,
    val onSent: () -> Unit,
    val finePointer: Boolean = false,
    val keepFocusOnSend: Boolean = false,
)

/**
 * The full composer for one conversation (slice 3 replaces the chat's `BasicComposerHost`, plan 09 §6.3). Not yet
 * contributed to Metro: the binding and the per-conversation ViewModel factory follow the kit-chat interface.
 */
object FullComposerHost {
    @Composable
    fun Composer(
        viewModel: ComposerViewModel,
        chrome: ComposerChromeInputs,
        modifier: Modifier = Modifier,
        mic: (@Composable () -> Unit)? = null,
    ) {
        val state by viewModel.state.collectAsState()
        val field = rememberTextFieldState()
        val insert = chrome.pendingInsert()
        LaunchedEffect(insert?.first) {
            val (id, text) = insert ?: return@LaunchedEffect
            viewModel.insert(id, text)
            chrome.insertDone(id)
        }
        LaunchedEffect(state.sends) { if (state.sends > 0) chrome.onSent() }
        ComposerScreen(
            state = state,
            actions = viewModel.actions,
            field = field,
            modifier = modifier,
            finePointer = chrome.finePointer,
            keepFocusOnSend = chrome.keepFocusOnSend,
            mic = mic,
        )
    }
}
