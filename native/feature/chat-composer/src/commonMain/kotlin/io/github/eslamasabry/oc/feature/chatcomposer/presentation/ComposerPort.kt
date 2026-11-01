package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

/** The field's value as the UI holds it (UTF-16 offsets; composing -1 = none), like the engine's `ComposerValue`. */
data class FieldValue(
    val text: String,
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
    val composingStart: Int = -1,
    val composingEnd: Int = -1,
) {
    override fun toString(): String = "FieldValue(${text.length} chars, $selectionStart..$selectionEnd)"
}

/**
 * What the composer needs from the engines of one conversation (plan 09 §6.3: resolved per conversation through the
 * connection graph). A port, not an engine: `:engine:composer` does not compile on this tip, and `:engine:delivery`
 * (S2-DLV) does not exist yet. The adapter maps one call to one engine call:
 *
 * | Port | Engine |
 * |---|---|
 * | [facts] | capabilities (`promptSteering`), `MidTurnPromptGateway`, delivery's running state, requests, settings |
 * | [draftChanged] | `:engine:composer` `DraftAutosave.changed` (600 ms debounce) |
 * | [flushDraft] | `DraftAutosave.flush` |
 * | [history] | `PromptHistoryKeys.isHistoryKey` + `PromptHistoryNavigation.move` over `PromptShelf` history |
 * | [send] | `:engine:delivery` `PromptDelivery.send` (bubble first, never twice; steer per §4.3) |
 * | [stop] | `PromptDelivery.stop` (calls off a waiting send, else stops the turn; records `stoppedByPerson`) |
 */
interface ComposerPort {
    val facts: StateFlow<ComposerFacts>

    /** Words committed to the field (never voice partials). */
    fun draftChanged(text: String)

    suspend fun flushDraft(): Outcome<Unit>

    /** Arrow-key history: the new value, or null when the key is the field's own (multi-line moves, IME input). */
    fun history(older: Boolean, current: FieldValue, suggestionsOpen: Boolean): FieldValue?

    suspend fun send(text: String, how: SendHow): Outcome<Unit>

    suspend fun stop(): Outcome<Unit>
}
