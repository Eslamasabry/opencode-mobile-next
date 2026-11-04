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
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.platform.PromptFilePicker
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.ComposerCommand
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the basic composer shows (plan 09 §6.3 slice 2: field, Send, Stop; the model choice and file
 * attachments of the full-composer slice ride along).
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
    /** Server slash commands matching a `/word` field (B-NAV-049). */
    val commandMatches: List<ComposerCommand> = emptyList(),
    /** Project files matching the `@word` being typed (B-CHAT-087). */
    val fileMatches: List<String> = emptyList(),
    /** The extra ways to attach this platform offers besides the document picker. */
    val attachKinds: Set<AttachKind> = emptySet(),
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
    public suspend fun selectModel(choice: ComposerModel?, variant: String = ""): Boolean = false

    public suspend fun toggleModelFavorite(choice: ComposerModel) {}

    /** True = the world verified the conversation now runs as [agent]. */
    public suspend fun selectAgent(agent: String): Boolean = false

    /** Opens the platform picker; a cancelled pick changes nothing. */
    public fun attachFile() {}

    /** Removes one attached file by its current index. */
    public fun removeAttachment(index: Int) {}

    public fun dismissAttachmentFailure() {}

    /** Stages the project file picked from the `@` list (the field inserts its `@path`). */
    public fun mentionFile(path: String) {}

    /** Photos, camera or clipboard picture, where the platform offers them. */
    public fun attach(kind: AttachKind) {}
}

/** What a typed field asks the suggestion strip for (Flutter `_activeAgentQuery`, slash match). */
public sealed interface ComposerQuery {
    public data class Command(val query: String) : ComposerQuery

    public data class File(val query: String, val start: Int) : ComposerQuery

    public companion object {
        private val SLASH = Regex("^/(\\S*)$")
        private val BEFORE = setOf('(', '[', '{', '"', '\'')

        /** The query the end of [text] asks for, or null. */
        public fun of(text: String): ComposerQuery? {
            SLASH.find(text.trim())?.let {
                return Command(it.groupValues[1])
            }
            val at = text.lastIndexOf('@')
            if (at < 0) return null
            val word = text.substring(at + 1)
            if (word.any { it.isWhitespace() }) return null
            if (at > 0 && !text[at - 1].isWhitespace() && text[at - 1] !in BEFORE) return null
            return File(word, at)
        }

        /**
         * Name prefix first, then contains, then the rest; at most [limit], no folders or spaces.
         */
        public fun rankFiles(query: String, paths: List<String>, limit: Int = 20): List<String> {
            val q = query.lowercase()
            val usable =
                paths.filter { it.isNotBlank() && !it.endsWith("/") && ' ' !in it }.distinct()
            fun name(p: String) = p.substringAfterLast('/').lowercase()
            val prefix = usable.filter { name(it).startsWith(q) }
            val contains = usable.filter { it !in prefix && it.lowercase().contains(q) }
            val rest = usable.filter { it !in prefix && it !in contains }
            return (prefix + contains + rest).take(limit)
        }
    }
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
    private val filePickers: Set<PromptFilePicker> = emptySet(),
    private val attachSources: Set<ComposerAttachSource> = emptySet(),
    private val appCommands: Set<ComposerAppCommands> = emptySet(),
) : ViewModel(), BasicComposerActions {
    private val profileId = ProfileId(target.profileId)
    private val query = MutableStateFlow<ComposerQuery?>(null)
    private val fileMatches = MutableStateFlow<List<String>>(emptyList())
    private var commandsRequested = false

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
                        val suggestions =
                            combine(query, world.commands, fileMatches) { q, commands, files ->
                                when (q) {
                                    is ComposerQuery.Command ->
                                        commands
                                            .filter {
                                                it.name.startsWith(q.query, ignoreCase = true)
                                            }
                                            .plus(
                                                commands.filter {
                                                    !it.name.startsWith(
                                                        q.query,
                                                        ignoreCase = true,
                                                    ) &&
                                                        it.name.contains(q.query, ignoreCase = true)
                                                }
                                            )
                                            .take(MAX_COMMANDS) to emptyList()
                                    is ComposerQuery.File -> emptyList<ComposerCommand>() to files
                                    null -> emptyList<ComposerCommand>() to emptyList()
                                }
                            }
                        combine(
                            combine(gated, suggestions) { ui, s ->
                                ui.copy(commandMatches = s.first, fileMatches = s.second)
                            },
                            world.models,
                            filesBusy,
                            selectingModel,
                            attachmentFailure,
                        ) { ui, models, busy, selecting, failure ->
                            val (picking, committing) = busy
                            ui.copy(
                                attachKinds = attachSources.flatMapTo(HashSet()) { it.kinds },
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

    init {
        // One project-file search once typing settles; a newer `@` word cancels the older search.
        world?.let { w ->
            viewModelScope.launch {
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                query.debounce(SEARCH_DEBOUNCE_MS).collectLatest { q ->
                    if (q is ComposerQuery.File && q.query.isNotEmpty()) {
                        val found = w.findFiles(q.query)
                        if (query.value == q && found is Outcome.Ok)
                            fileMatches.value = ComposerQuery.rankFiles(q.query, found.value)
                    }
                }
            }
        }
    }

    override fun draftChanged(text: String) {
        world?.draftChanged(text)
        suggest(text)
    }

    /** The strip follows the words: `/` lists server commands, `@` searches project files. */
    private fun suggest(text: String) {
        val w = world ?: return
        val next = ComposerQuery.of(text)
        if (next == query.value) return
        query.value = next
        when (next) {
            is ComposerQuery.Command ->
                if (!commandsRequested) {
                    commandsRequested = true
                    viewModelScope.launch {
                        if (w.loadCommands() is Outcome.Failed) commandsRequested = false
                    }
                }
            is ComposerQuery.File -> fileMatches.value = emptyList()
            null -> fileMatches.value = emptyList()
        }
    }

    override fun mentionFile(path: String) {
        val w = world ?: return
        query.value = null
        fileMatches.value = emptyList()
        if (committingFiles.value || pickingFile.value) return
        committingFiles.value = true
        viewModelScope.launch {
            try {
                // Without a project folder the words still name the file; nothing is staged.
                w.mentionFile(path)
            } finally {
                committingFiles.value = false
            }
        }
    }

    override fun attach(kind: AttachKind) {
        val w = world ?: return
        val source = attachSources.firstOrNull { kind in it.kinds } ?: return
        if (pickingFile.value || committingFiles.value) return
        pickingFile.value = true
        val generation = w.draftGeneration
        viewModelScope.launch {
            try {
                when (val picked = source.attach(kind, w.draft.value.attachments)) {
                    is Outcome.Ok -> {
                        if (picked.value.isEmpty()) {
                            // A cancel changes nothing; an empty clipboard says so in plain words.
                            if (kind == AttachKind.Clipboard)
                                attachmentFailure.value =
                                    Failure.Unavailable(FeatureId("clipboardImage"))
                            return@launch
                        }
                        if (w.draftGeneration != generation) {
                            attachmentFailure.value = Failure.Refused(RefusalReason.StaleScope)
                            return@launch
                        }
                        val latest = w.draft.value
                        if (!latest.ready || latest.failure != null || latest.sendBlocked) {
                            attachmentFailure.value =
                                latest.failure
                                    ?: Failure.Unavailable(FeatureId("promptAttachments"))
                            return@launch
                        }
                        val room = MAX_FILES - latest.attachments.size
                        if (room <= 0) {
                            attachmentFailure.value = Failure.Media(MediaProblem.DraftFull)
                            return@launch
                        }
                        committingFiles.value = true
                        commit(w, latest.attachments + picked.value.take(room))
                    }
                    is Outcome.Failed -> attachmentFailure.value = picked.failure
                }
            } finally {
                pickingFile.value = false
            }
        }
    }

    /** Read directly, never through [state]: StateFlow publishes later than a tap can arrive. */
    private fun busy(): Boolean =
        sending.value || pickingFile.value || committingFiles.value || selectingModel.value

    override suspend fun send(text: String): Boolean {
        val w = world ?: return false
        if (busy()) return false
        if (!SendGate.accepts(state.value.copy(sending = sending.value), text)) return false
        // Codex composer #5: the app's own commands run here and leave the box; nothing goes to the
        // server.
        AppCommands.of(text)?.let { command ->
            val runner = appCommands.firstOrNull() ?: return@let
            w.draftChanged("")
            runner.run(command, profileId)
            return true
        }
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
        if (busy() || !state.value.canRepeat) return false
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

    override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean {
        val w = world ?: return false
        if (selectingModel.value || sending.value) return false
        selectingModel.value = true
        return try {
            w.selectModel(choice, variant) is Outcome.Ok
        } finally {
            selectingModel.value = false
        }
    }

    override suspend fun toggleModelFavorite(choice: ComposerModel) {
        world?.toggleModelFavorite(choice)
    }

    override suspend fun selectAgent(agent: String): Boolean {
        val w = world ?: return false
        if (selectingModel.value || sending.value) return false
        selectingModel.value = true
        return try {
            w.selectAgent(agent) is Outcome.Ok
        } finally {
            selectingModel.value = false
        }
    }

    override fun attachFile() {
        val w = world ?: return
        val picker = filePickers.firstOrNull() ?: return
        if (pickingFile.value || committingFiles.value) return
        pickingFile.value = true
        // The draft owner the pick belongs to: a result for a replaced owner writes nothing.
        val generation = w.draftGeneration
        viewModelScope.launch {
            try {
                when (val picked = picker.pickFile(w.draft.value.attachments)) {
                    is Outcome.Ok -> {
                        val file = picked.value ?: return@launch // cancelled: no change
                        if (w.draftGeneration != generation) {
                            attachmentFailure.value = Failure.Refused(RefusalReason.StaleScope)
                            return@launch
                        }
                        // The world may have moved while the picker was up; append to the latest
                        // files and only through a settled draft.
                        val latest = w.draft.value
                        if (!latest.ready || latest.failure != null || latest.sendBlocked) {
                            attachmentFailure.value =
                                latest.failure
                                    ?: Failure.Unavailable(FeatureId("promptAttachments"))
                            return@launch
                        }
                        committingFiles.value = true
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
        // One file operation at a time, latched before any suspension: a second tap during a
        // commit would edit a list that is about to change.
        if (committingFiles.value || pickingFile.value || sending.value) return
        val latest = w.draft.value
        if (index !in latest.attachments.indices) return
        if (!latest.ready || latest.failure != null || latest.sendBlocked) {
            attachmentFailure.value =
                latest.failure ?: Failure.Unavailable(FeatureId("promptAttachments"))
            return
        }
        committingFiles.value = true
        viewModelScope.launch { commit(w, latest.attachments.filterIndexed { i, _ -> i != index }) }
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
        const val SEARCH_DEBOUNCE_MS = 150L
        const val MAX_COMMANDS = 8
        const val MAX_FILES = 5
    }
}
