package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The composer's logic for one conversation, free of Compose and of the ViewModel class so it runs under a test scope.
 * [ComposerViewModel] owns one over `viewModelScope`.
 */
class ComposerPresenter(
    private val scope: CoroutineScope,
    private val port: ComposerPort,
    private val openers: ComposerOpeners,
    private val clock: Clock,
) : ComposerActions {
    private data class Local(
        val hasText: Boolean = false,
        val note: ComposerNote? = null,
        val edit: FieldEdit? = null,
        val sends: Int = 0,
    )

    private val local = MutableStateFlow(Local())
    private var text = ""
    private var failedText: String? = null
    private var lastSendAt: Instant? = null
    private var nextEditId = 1L
    private val insertedIds = mutableSetOf<Long>()

    val state: StateFlow<ComposerUi> =
        combine(port.facts, local) { facts, l -> build(facts, l) }
            .stateIn(scope, SharingStarted.Eagerly, build(port.facts.value, Local()))

    private fun build(facts: ComposerFacts, l: Local): ComposerUi {
        val readOnly = facts.readOnly
        val chips =
            if (readOnly != null) {
                persistentListOf()
            } else {
                buildList {
                    add(ModelChipLabel.chip(facts.model, clock.now()))
                    add(ComposerChip.Approval(facts.approval))
                    // Stop: trailing end, the whole running turn, absent while a request owns the composer.
                    if (facts.busy && !facts.requestWaiting) add(ComposerChip.Stop(stuck = facts.stuck))
                }.toImmutableList()
            }
        return ComposerUi(
            button = FieldButtons.button(l.hasText, facts),
            chips = chips,
            glow = facts.busy && facts.glowSetting && !facts.requestWaiting && readOnly == null,
            note = readOnly?.let { ComposerNote.ReadOnly(it) } ?: l.note,
            sends = l.sends,
            edit = l.edit,
            fieldEnabled = readOnly == null,
        )
    }

    override fun onTextChanged(text: String) {
        if (text == this.text) return
        this.text = text
        local.update { it.copy(hasText = text.isNotBlank()) }
        port.draftChanged(text)
    }

    override fun onSend() {
        send(text)
    }

    override fun onRetry() {
        send(failedText ?: text)
    }

    private fun send(words: String) {
        if (words.isBlank()) return
        val facts = port.facts.value
        if (facts.sending) return
        val how = FieldButtons.how(facts) ?: return
        lastSendAt = clock.now()
        failedText = null
        // Bubble first: the field empties at once; a failed send brings the words back.
        local.update { it.copy(note = null, edit = edit(FieldEdit.Change.Clear), sends = it.sends + 1) }
        scope.launch {
            when (port.send(words, how)) {
                is Outcome.Ok -> port.flushDraft()
                is Outcome.Failed -> {
                    failedText = words
                    local.update { l ->
                        // Words typed since keep their place; an empty field (or one not yet cleared) takes them back.
                        val untouched = text.isBlank() || text == words
                        val back = if (untouched) edit(FieldEdit.Change.Replace(words, words.length)) else l.edit
                        l.copy(note = ComposerNote.SendFailed, edit = back)
                    }
                }
            }
        }
    }

    override fun onStop() {
        val sentAt = lastSendAt
        // A double tap on Send never stops the turn (fix 49d067f2d).
        if (sentAt != null && clock.now() - sentAt < MotionTokens.STOP_IGNORE_AFTER_SEND.milliseconds) return
        scope.launch { port.stop() }
    }

    override fun onHistoryKey(older: Boolean, value: FieldValue, suggestionsOpen: Boolean): Boolean {
        val next = port.history(older, value, suggestionsOpen) ?: return false
        local.update { it.copy(edit = edit(FieldEdit.Change.Replace(next.text, next.selectionEnd))) }
        return true
    }

    /** An agent `choices` tap or a starter (plan 09 §6.3): inserted once per [id], at the selection. */
    fun insert(id: Long, text: String) {
        if (!insertedIds.add(id)) return
        local.update { it.copy(edit = edit(FieldEdit.Change.Insert(text))) }
    }

    override fun onEditApplied(id: Long) {
        local.update { if (it.edit?.id == id) it.copy(edit = null) else it }
    }

    override fun onModelChip(): Unit = openers.openModelPicker()

    override fun onApprovalChip(): Unit = openers.openApprovals()

    override fun onMic(): Unit = openers.startVoice()

    private fun edit(change: FieldEdit.Change) = FieldEdit(nextEditId++, change)
}
