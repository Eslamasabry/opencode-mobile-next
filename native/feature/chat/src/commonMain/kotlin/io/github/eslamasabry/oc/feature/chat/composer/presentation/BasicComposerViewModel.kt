package io.github.eslamasabry.oc.feature.chat.composer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the basic composer shows (plan 09 §6.3 slice 2: field, Send, Stop; model chip and drafts come in S3). */
public data class BasicComposerUi(
    /** There is a conversation to send to (slice 2 has no new-conversation composer here). */
    val available: Boolean,
    /** A dispatch is in flight: Send waits, so one tap is one send. */
    val sending: Boolean,
    /** A reply runs and this server steers: a send joins the running turn (plan 09 §4.3). */
    val steers: Boolean,
)

public interface BasicComposerActions {
    /** Every change of the field (ADR §9.2); the chat's Back guard reads it. */
    public fun draftChanged(text: String)

    /** True = accepted: the bubble shows at once and the field clears. False = nothing was sent. */
    public fun send(text: String): Boolean
}

/** Pure send gate, so the never-twice rule is table-tested without a ViewModel. */
public object SendGate {
    public fun accepts(ui: BasicComposerUi, text: String): Boolean = ui.available && !ui.sending && text.isNotBlank()
}

/** The per-conversation composer ViewModel (plan 09 §6.3), keyed by its target inside the chat entry. */
@AssistedInject
public class BasicComposerViewModel(@Assisted target: ComposerTarget, private val worlds: ChatWorlds) :
    ViewModel(), BasicComposerActions {
    private val world: ChatWorld? =
        target.sessionId?.let { worlds.open(ConversationRef(ProfileId(target.profileId), SessionId(it))) }
    private val sending = MutableStateFlow(false)

    public val state: StateFlow<BasicComposerUi> =
        if (world == null) {
            flowOf(BasicComposerUi(available = false, sending = false, steers = false))
        } else {
            combine(world.transcript, world.capabilities, sending) { t, caps, inFlight ->
                BasicComposerUi(available = true, sending = inFlight, steers = t.run.busy && caps.promptSteering)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), BasicComposerUi(world != null, false, false))

    override fun draftChanged(text: String) {
        world?.draftChanged(text)
    }

    override fun send(text: String): Boolean {
        val w = world ?: return false
        if (!SendGate.accepts(state.value.copy(sending = sending.value), text)) return false
        sending.value = true
        viewModelScope.launch {
            w.send(text.trim())
            sending.value = false
        }
        return true
    }

    override fun onCleared() {
        world?.let(worlds::release)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted target: ComposerTarget): BasicComposerViewModel
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
