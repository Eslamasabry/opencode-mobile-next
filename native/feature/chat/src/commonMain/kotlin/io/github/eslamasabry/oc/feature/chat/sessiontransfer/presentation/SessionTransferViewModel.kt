package io.github.eslamasabry.oc.feature.chat.sessiontransfer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.engine.agents.sessionimport.*
import io.github.eslamasabry.oc.engine.composer.input.DirectoryReferences
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One review and one serial operation for this exact conversation and connection generation. */
@AssistedInject
public class SessionTransferViewModel(
    @Assisted private val route: ChatRoute,
    private val registry: ConnectionRegistry,
    private val documents: SessionDocuments,
    private val dispatchers: AppDispatchers,
    private val nav: AppNavigator,
    private val worlds: ChatWorlds,
) : ViewModel(), SessionTransferActions {
    private val mutable = MutableStateFlow(SessionTransferUi())
    public val state: StateFlow<SessionTransferUi> = mutable.asStateFlow()
    private val world = worlds.open(route.ref)
    private var graph: ConnectionGraph? = null
    private var capabilityJob: Job? = null
    private var document: SessionImportDocument? = null
    private var operation = 0

    init {
        viewModelScope.launch {
            registry.live.collect {
                val next = registry.graphFor(route.ref)
                if (next !== graph) {
                    operation++
                    document = null
                    graph = next
                    capabilityJob?.cancel()
                    val visible = state.value.visible
                    mutable.value =
                        SessionTransferUi(
                            visible = visible,
                            notice = if (visible) TransferNotice.Changed else null,
                        )
                    if (next != null)
                        capabilityJob = launch {
                            next.session.server.capabilities.collect { caps ->
                                mutable.update { old ->
                                    old.copy(
                                        transferAvailable =
                                            caps.sessionImportExport &&
                                                next.session.server.facet<
                                                    SessionTransferGateway
                                                >() != null,
                                        referencesAvailable =
                                            caps.promptAttachments &&
                                                !caps.promptImagesOnly &&
                                                caps.fileBrowsing,
                                        notesAvailable =
                                            caps.sessionNotes &&
                                                next.session.server.facet<SessionNotesGateway>() !=
                                                    null,
                                    )
                                }
                            }
                        }
                }
            }
        }
    }

    override fun open() {
        mutable.update { it.copy(visible = true, notice = null, failure = null) }
        val owner = graph ?: return
        val run = operation
        viewModelScope.launch {
            when (val result = owner.session.server.session(route.ref.sessionId)) {
                is Outcome.Ok ->
                    if (current(owner, run))
                        mutable.update {
                            if (it.destination.isBlank())
                                it.copy(destination = result.value.directory.orEmpty())
                            else it
                        }
                is Outcome.Failed ->
                    if (current(owner, run)) mutable.update { it.copy(failure = result.failure) }
            }
        }
    }

    override fun close() {
        mutable.update { it.copy(visible = false) }
    }

    override fun sanitize(value: Boolean) {
        if (!state.value.busy) mutable.update { it.copy(sanitize = value) }
    }

    override fun destination(value: String) {
        if (!state.value.busy) mutable.update { it.copy(destination = value) }
    }

    override fun note(value: String) {
        if (!state.value.busy)
            mutable.update { it.copy(note = value, noteLimit = null, notice = null) }
    }

    override fun export() = launchOperation { owner, run ->
        val gateway = owner.session.server.facet<SessionTransferGateway>() ?: return@launchOperation
        val sanitize = state.value.sanitize
        when (val result = gateway.exportSession(route.ref.sessionId, sanitize)) {
            is Outcome.Failed -> if (current(owner, run)) fail(result.failure)
            is Outcome.Ok ->
                if (current(owner, run)) {
                    when (
                        val save =
                            documents.saveJson(
                                "opencode-${route.ref.sessionId.raw.filter { it.isLetterOrDigit() || it in "_-" }}.json",
                                result.value.copyOf(),
                            )
                    ) {
                        is Outcome.Failed -> if (current(owner, run)) fail(save.failure)
                        is Outcome.Ok ->
                            if (current(owner, run) && save.value) notice(TransferNotice.Saved)
                    }
                }
        }
    }

    override fun choose() = launchOperation { owner, run ->
        when (val picked = documents.chooseJson(SessionImportDocument.MAX_BYTES)) {
            is Outcome.Failed -> if (current(owner, run)) fail(picked.failure)
            is Outcome.Ok -> {
                val bytes = picked.value ?: return@launchOperation
                val parsed =
                    withContext(dispatchers.default) {
                        SessionImportDocument.read(sequenceOf(bytes))
                    }
                if (!current(owner, run)) return@launchOperation
                when (parsed) {
                    is SessionImportResult.Refused -> {
                        document = null
                        mutable.update {
                            it.copy(
                                reviewCount = null,
                                reviewTitle = null,
                                imported = false,
                                notice =
                                    if (parsed.problem == SessionImportProblem.TooLarge)
                                        TransferNotice.TooLarge
                                    else TransferNotice.InvalidFile,
                            )
                        }
                    }
                    is SessionImportResult.Ok -> {
                        document = parsed.value
                        mutable.update {
                            it.copy(
                                reviewTitle = parsed.value.title,
                                reviewCount = parsed.value.messageCount,
                                redacted = parsed.value.hasRedactions,
                                imported = false,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun importReviewed() = launchOperation { owner, run ->
        if (state.value.imported) return@launchOperation
        val reviewed = document ?: return@launchOperation
        val gateway = owner.session.server.facet<SessionTransferGateway>() ?: return@launchOperation
        val destination = SessionImportDestination(state.value.destination)
        val body = reviewed.requestBody(destination)
        if (body !is SessionImportResult.Ok) {
            notice(TransferNotice.InvalidFile)
            return@launchOperation
        }
        when (val result = gateway.importSession(body.value)) {
            is Outcome.Failed -> if (current(owner, run)) fail(result.failure)
            is Outcome.Ok ->
                if (current(owner, run)) {
                    if (
                        result.value.id.raw != reviewed.id || result.value.directory.isNullOrBlank()
                    )
                        fail(Failure.UnreadableReply())
                    else {
                        mutable.update {
                            it.copy(imported = true, notice = TransferNotice.Imported)
                        }
                        nav.openChat(route.ref.copy(sessionId = result.value.id))
                    }
                }
        }
    }

    override fun loadNote() = launchOperation { owner, run ->
        val notes = owner.session.server.facet<SessionNotesGateway>() ?: return@launchOperation
        when (val result = notes.loadSessionNote(route.ref.sessionId)) {
            is Outcome.Failed -> if (current(owner, run)) fail(result.failure)
            is Outcome.Ok ->
                if (current(owner, run))
                    mutable.update {
                        it.copy(
                            note = result.value.orEmpty(),
                            noteLoaded = true,
                            noteRevision = it.noteRevision + 1,
                        )
                    }
        }
    }

    override fun saveNote() = launchOperation { owner, run ->
        if (!state.value.noteLoaded) return@launchOperation
        val notes = owner.session.server.facet<SessionNotesGateway>() ?: return@launchOperation
        when (val result = notes.saveSessionNote(route.ref.sessionId, state.value.note)) {
            is Outcome.Failed -> if (current(owner, run)) fail(result.failure)
            is Outcome.Ok ->
                if (current(owner, run))
                    when (val saved = result.value) {
                        SessionNoteSaveResult.Saved -> notice(TransferNotice.NoteSaved)
                        is SessionNoteSaveResult.TooLarge ->
                            mutable.update { it.copy(noteLimit = saved.maxBytes) }
                    }
        }
    }

    override fun removeNote() = launchOperation { owner, run ->
        if (!state.value.noteLoaded) return@launchOperation
        val notes = owner.session.server.facet<SessionNotesGateway>() ?: return@launchOperation
        when (val result = notes.removeSessionNote(route.ref.sessionId)) {
            is Outcome.Failed -> if (current(owner, run)) fail(result.failure)
            is Outcome.Ok ->
                if (current(owner, run))
                    mutable.update {
                        it.copy(
                            note = "",
                            noteRevision = it.noteRevision + 1,
                            notice = TransferNotice.NoteRemoved,
                        )
                    }
        }
    }

    override fun referencePath(value: String) {
        if (!state.value.busy) mutable.update { it.copy(referencePath = value) }
    }

    override fun addReference() = launchOperation { owner, run ->
        if (!state.value.referencesAvailable) {
            fail(Failure.Unavailable(FeatureId("promptAttachments")))
            return@launchOperation
        }
        val reference = DirectoryReferences.reference(state.value.referencePath)
        if (reference is Outcome.Failed) {
            fail(reference.failure)
            return@launchOperation
        }
        val generation = world.draftGeneration
        val attachments = world.draft.value.attachments
        if (!current(owner, run) || generation != world.draftGeneration) return@launchOperation
        when (val saved = world.replaceAttachments(attachments + (reference as Outcome.Ok).value)) {
            is Outcome.Failed -> if (current(owner, run)) fail(saved.failure)
            is Outcome.Ok -> if (current(owner, run)) notice(TransferNotice.ReferenceAdded)
        }
    }

    override fun onCleared() {
        worlds.release(world)
        super.onCleared()
    }

    private fun launchOperation(block: suspend (ConnectionGraph, Int) -> Unit) {
        if (state.value.busy) return
        val owner = graph ?: return
        val run = operation
        mutable.update { it.copy(busy = true, failure = null, notice = null, noteLimit = null) }
        viewModelScope.launch {
            if (!current(owner, run)) return@launch
            try {
                block(owner, run)
            } finally {
                if (current(owner, run)) mutable.update { it.copy(busy = false) }
            }
        }
    }

    private fun current(owner: ConnectionGraph, run: Int): Boolean =
        operation == run && registry.graphFor(route.ref) === owner

    private fun fail(failure: Failure) {
        mutable.update { it.copy(failure = failure) }
    }

    private fun notice(notice: TransferNotice) {
        mutable.update { it.copy(notice = notice) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): SessionTransferViewModel
    }
}

/** Desktop gallery has no system document picker. Android replaces this binding. */
@Inject
@ContributesBinding(AppScope::class)
public class NoSessionDocuments : SessionDocuments {
    override suspend fun chooseJson(maxBytes: Long): Outcome<ByteArray?> =
        Failure.Unavailable(FeatureId("documents")).failed()

    override suspend fun saveJson(name: String, bytes: ByteArray): Outcome<Boolean> =
        Failure.Unavailable(FeatureId("documents")).failed()
}
