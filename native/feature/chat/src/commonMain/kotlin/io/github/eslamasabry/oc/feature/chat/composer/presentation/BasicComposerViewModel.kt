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
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.platform.PromptFilePicker
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the basic composer shows (plan 09 §6.3 slice 2: field, Send, Stop; the model choice and
 * file attachments of the full-composer slice ride along).
 */
public data class BasicComposerUi(
    /** There is a conversation to send to (slice 2 has no new-conversation composer here). */
    val available: Boolean,
    /** A dispatch is in flight: Send waits, so one tap is one send. */
    val sending: Boolean,
    /** A reply runs and this server steers: a send joins the running turn (plan 09 §4.3). */
    val steers: Boolean,
    val draft: DraftFacts = DraftFacts(),
    val targetKey: String = "",
    val canRepeat: Boolean = false,
    /** Safe catalog projection only; no raw provider config reaches the screen. */
    val models: ComposerModels = ComposerModels(),
    /** A file pick or its commit is running: Send waits. */
    val pickingFile: Boolean = false,
    /** The last file failure in plain words territory; never raw copy. */
    val attachmentFailure: Failure? = null,
    /** This platform contributed a file picker. */
    val filesAvailable: Boolean = false,
) {
    override fun toString(): String =
        "BasicComposerUi(available=$available, sending=$sending, steers=$steers, draft=$draft, " +
            "pickingFile=$pickingFile, modelSelected=${models.selected != null}, " +
            "attachmentFailure=${attachmentFailure != null}, filesAvailable=$filesAvailable)"
}

public interface BasicComposerActions {
    /** Every change of the field (ADR §9.2); the chat's Back guard reads it. */
    public fun draftChanged(text: String)

    /** True = accepted: the bubble shows at once and the field clears. False = nothing was sent. */
    public suspend fun send(text: String): Boolean

    public fun retrySave() {}

    public suspend fun sendAgain(text: String, expectedRevision: Long): Boolean = false

    /** Loads the conversation's model choices again (first load, retry). */
    public fun loadModels() {}

    /** True = the world verified the new choice and published it. */
    public suspend fun selectModel(choice: ComposerModel?): Boolean = false

    /** Opens the platform picker; a cancelled pick changes nothing. */
    public fun attachFile() {}

    /** Removes one attached file by its current index. */
    public fun removeAttachment(index: Int) {}

    public fun dismissAttachmentFailure() {}
}

/** Pure send gate, so the never-twice rule is table-tested without a ViewModel. */
public object SendGate {
    public fun accepts(ui: BasicComposerUi, text: String): Boolean =
        ui.available &&
            !ui.sending &&
            ui.draft.ready &&
            ui.draft.failure == null &&
            !ui.draft.sendBlocked &&
            (text.isNotBlank() || ui.draft.attachments.isNotEmpty())
}

/**
 * The per-conversation composer ViewModel (plan 09 §6.3), keyed by its target inside the chat
 * entry.
 */
@AssistedInject
public class BasicComposerViewModel(
    @Assisted target: ComposerTarget,
    private val worlds: ChatWorlds,
    private val filePickers: Set<PromptFilePicker>,
) : ViewModel(), BasicComposerActions {
    private val world: ChatWorld? =
        target.sessionId?.let {
            worlds.open(ConversationRef(ProfileId(target.profileId), SessionId(it)))
        }
    private val sending = MutableStateFlow(false)
    private val pickingFile = MutableStateFlow(false)
    private val committingFiles = MutableStateFlow(false)
    private val selectingModel = MutableStateFlow(false)
    private val attachmentFailure = MutableStateFlow<Failure?>(null)
    private val targetKey = "${target.profileId}/${target.sessionId}"

    public val state: StateFlow<BasicComposerUi> =
        if (world == null) {
                flowOf(BasicComposerUi(available = false, sending = false, steers = false))
            } else {
                val facts =
                    combine(
                        world.transcript,
                        world.capabilities,
                        sending,
                        world.load,
                        world.draft,
                    ) { t, caps, inFlight, load, draft ->
                        BasicComposerUi(
                            available =
                                load is io.github.eslamasabry.oc.feature.chat.world.ChatLoad.Live &&
                                    draft.ready &&
                                    draft.failure == null &&
                                    !draft.sendBlocked,
                            sending = inFlight,
                            steers = t.run.busy && caps.promptSteering,
                            draft = draft,
                            targetKey = targetKey,
                            canRepeat =
                                draft.repeatAllowed &&
                                    load is
                                        io.github.eslamasabry.oc.feature.chat.world.ChatLoad.Live,
                        )
                    }
                combine(facts, world.pending) { ui, pending ->
                    val noUnknown = pending.none { it.state == PendingState.Unconfirmed }
                    ui.copy(
                        available = ui.available && noUnknown,
                        canRepeat = ui.canRepeat && noUnknown && !ui.sending,
                    )
                }
                // One gate for every in-flight composer operation: Send waits while a pick, its
                // commit or a model change runs.
                .let { gated ->
                    val filesBusy =
                        combine(pickingFile, committingFiles) { picking, committing ->
                            picking to committing
                        }
                    combine(
                        gated,
                        world.models,
                        filesBusy,
                        selectingModel,
                        attachmentFailure,
                    ) { ui, models, busy, selecting, failure ->
                        val (picking, committing) = busy
                        ui.copy(
                            available = ui.available && !picking && !committing && !selecting,
                            canRepeat = ui.canRepeat && !picking && !committing && !selecting,
                            models = models,
                            pickingFile = picking,
                            attachmentFailure = failure,
                            filesAvailable = filePickers.isNotEmpty(),
                        )
                    }
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MS),
                BasicComposerUi(
                    false,
                    false,
                    false,
                    draft = world?.draft?.value ?: DraftFacts(),
                    targetKey = targetKey,
                ),
            )

    override fun draftChanged(text: String) {
        world?.draftChanged(text)
    }

    override suspend fun send(text: String): Boolean {
        val w = world ?: return false
        if (!SendGate.accepts(state.value.copy(sending = sending.value), text)) return false
        w.draftChanged(
            text
        ) // capture exact editor whitespace before the dispatch text is normalized
        sending.value = true
        return try {
            w.send(text.trim()) is Outcome.Ok
        } finally {
            sending.value = false
        }
    }

    override suspend fun sendAgain(text: String, expectedRevision: Long): Boolean {
        val w = world ?: return false
        if (sending.value || !state.value.canRepeat) return false
        sending.value = true
        return try {
            w.sendRepeated(text, expectedRevision) is Outcome.Ok
        } finally {
            sending.value = false
        }
    }

    override fun retrySave() {
        viewModelScope.launch { world?.saveDraft() }
    }

    override fun loadModels() {
        viewModelScope.launch { world?.loadModels() }
    }

    override suspend fun selectModel(choice: ComposerModel?): Boolean {
        val w = world ?: return false
        if (selectingModel.value) return false
        selectingModel.value = true
        return try {
            w.selectModel(choice) is Outcome.Ok
        } finally {
            selectingModel.value = false
        }
    }

    override fun attachFile() {
        val w = world ?: return
        val picker = filePickers.firstOrNull() ?: return
        if (pickingFile.value) return
        pickingFile.value = true
        viewModelScope.launch {
            try {
                when (val picked = picker.pickFile(w.draft.value.attachments)) {
                    is Outcome.Ok -> {
                        val file = picked.value ?: return@launch // cancelled: no change
                        // The world may have moved while the picker was up; append to the latest
                        // files and only through a settled draft.
                        val latest = w.draft.value
                        if (!latest.ready || latest.failure != null || latest.sendBlocked) {
                            attachmentFailure.value =
                                latest.failure ?: Failure.Unavailable(FeatureId("promptAttachments"))
                            return@launch
                        }
                        commit(w, latest.attachments + file)
                    }
                    is Outcome.Failed -> attachmentFailure.value = picked.failure
                }
            } finally {
                pickingFile.value = false
            }
        }
    }

    override fun removeAttachment(index: Int) {
        val w = world ?: return
        viewModelScope.launch {
            val latest = w.draft.value
            if (index !in latest.attachments.indices) return@launch
            if (!latest.ready || latest.failure != null || latest.sendBlocked) {
                attachmentFailure.value =
                    latest.failure ?: Failure.Unavailable(FeatureId("promptAttachments"))
                return@launch
            }
            commit(w, latest.attachments.filterIndexed { i, _ -> i != index })
        }
    }

    override fun dismissAttachmentFailure() {
        attachmentFailure.value = null
    }

    private suspend fun commit(w: ChatWorld, files: List<PromptAttachment>) {
        committingFiles.value = true
        try {
            when (val committed = w.replaceAttachments(files)) {
                is Outcome.Ok -> Unit
                is Outcome.Failed -> attachmentFailure.value = committed.failure
            }
        } finally {
            committingFiles.value = false
        }
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
