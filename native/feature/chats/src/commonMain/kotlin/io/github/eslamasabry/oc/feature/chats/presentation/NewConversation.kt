package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.git.GitLinkRepairReport
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class NewConversationServer(
    val profile: ProfileId,
    val name: String,
    val connected: Boolean,
)

public data class NewConversationProject(
    val directory: String,
    val name: String,
    val separateCopy: Boolean,
)

public data class NewConversationModel(
    val model: ModelRef,
    val name: String,
    val runtime: String? = null,
    /** N-51: the provider's name, the heading its models sit under (as in the chat's model sheet). */
    val providerName: String? = null,
)

public data class NewConversationSetup(
    val directory: String?,
    val projects: List<NewConversationProject>,
    val models: List<NewConversationModel>,
    val agents: List<String>,
    val model: ModelRef? = null,
    val agent: String? = null,
    val importDirectory: String? = null,
    val canChangeProject: Boolean = true,
    val defaultRuntime: String = "OpenCode",
    val modes: List<String> = emptyList(),
    /** N-51: this server's favourite models, as the chat's model sheet shows them. */
    val favorites: List<ModelRef> = emptyList(),
)

/** Keeps successful stages on an explicit retry: a copy or conversation is never created twice. */
public data class NewConversationAttempt(
    val directory: String,
    val ref: ConversationRef? = null,
    val failure: Failure? = null,
    val dispatched: Boolean = false,
    val creationAttempted: Boolean = false,
    val deliveryUnconfirmed: Boolean = false,
    val targetIdentity: String? = null,
)

public interface NewConversationHost {
    public fun canRepair(profile: ProfileId, directory: String): Boolean = false

    public suspend fun repair(profile: ProfileId, directory: String): Outcome<GitLinkRepairReport> =
        Outcome.Failed(Failure.Unavailable(FeatureId("gitLinkRepair")))

    public fun openRepairSettings() {}

    /** Whether [profile] can make a new project folder here (the app's own phone server only, as in Projects). */
    public fun canCreateProject(profile: ProfileId): Boolean = false

    /** Makes a project folder named [name] with Git under the projects root; answers its path. */
    public suspend fun createProject(profile: ProfileId, name: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectFolderCreation")))

    public val servers: StateFlow<List<NewConversationServer>>

    public suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup>

    public suspend fun review(profile: ProfileId, directory: String?): Outcome<ConversationRef?> =
        Outcome.Ok(null)

    /**
     * N-48: the person's Discard on a first message whose arrival was never confirmed: the review record leaves and
     * so does its waiting copy in the device queue, so nothing stays locked behind it.
     */
    public suspend fun dismissReview(profile: ProfileId, directory: String?): Outcome<Unit> = Outcome.Ok(Unit)

    public suspend fun draft(profile: ProfileId, directory: String?): Outcome<String>

    public suspend fun saveDraft(
        profile: ProfileId,
        directory: String?,
        text: String,
    ): Outcome<Unit>

    /**
     * N-51: adds or removes [model] from [profile]'s favourite models (the store the chat's model sheet
     * uses) and answers the favourites as they now are.
     */
    public suspend fun toggleFavorite(profile: ProfileId, model: ModelRef): Outcome<List<ModelRef>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("modelFavorites")))

    /** Saved with the project draft; changing screens never discards the selected model. */
    public suspend fun saveChoices(
        profile: ProfileId,
        directory: String?,
        model: ModelRef?,
        agent: String?,
    ): Outcome<Unit> = Outcome.Ok(Unit)

    public suspend fun start(
        profile: ProfileId,
        directory: String,
        text: String,
        model: ModelRef?,
        agent: String?,
        separateCopy: Boolean,
        previous: NewConversationAttempt?,
        /** The session and local send exist; its composer can open while admission settles. */
        onStarted: (ConversationRef) -> Unit = {},
    ): NewConversationAttempt

    public suspend fun imports(
        profile: ProfileId,
        directory: String,
    ): Outcome<ImportableConversations>

    public suspend fun import(
        profile: ProfileId,
        directory: String,
        conversation: ImportableConversation,
    ): Outcome<ConversationRef>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoNewConversationHost : NewConversationHost {
    override val servers: StateFlow<List<NewConversationServer>> = MutableStateFlow(emptyList())

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId("newConversation")))

    override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> = unavailable()

    override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> =
        unavailable()

    override suspend fun saveDraft(
        profile: ProfileId,
        directory: String?,
        text: String,
    ): Outcome<Unit> = unavailable()

    override suspend fun start(
        profile: ProfileId,
        directory: String,
        text: String,
        model: ModelRef?,
        agent: String?,
        separateCopy: Boolean,
        previous: NewConversationAttempt?,
        onStarted: (ConversationRef) -> Unit,
    ): NewConversationAttempt =
        NewConversationAttempt(
            directory,
            failure = Failure.Unavailable(FeatureId("newConversation")),
        )

    override suspend fun imports(
        profile: ProfileId,
        directory: String,
    ): Outcome<ImportableConversations> = unavailable()

    override suspend fun import(
        profile: ProfileId,
        directory: String,
        conversation: ImportableConversation,
    ): Outcome<ConversationRef> = unavailable()
}

/**
 * [Agents] is who works and how; [Models] is the kit's one model sheet (N-51), the same sheet a
 * conversation's model chip opens.
 */
public enum class NewConversationSheet {
    Projects,
    Models,
    Agents,
    Import,
}

/** Ways to start that fill the prompt and never send; the words live in `:ui:words`. */
public enum class NewConversationStarter {
    ExplainProject,
    FindBug,
    AddTests,
    ListFolder,
}

/**
 * The starters for [directory]. Folder facts are not read on this screen (no listing before the
 * conversation exists), so only starters that hold for any project are offered.
 */
public fun newConversationStarters(directory: String?): List<NewConversationStarter> {
    val name = directory?.trim()?.replace('\\', '/')?.split('/')?.lastOrNull { it.isNotEmpty() }
    val projectsRoot = directory?.trim()?.trimEnd('/')?.endsWith("/root/projects") == true
    return if (name == null || projectsRoot)
        listOf(NewConversationStarter.ListFolder, NewConversationStarter.FindBug)
    else
        listOf(
            NewConversationStarter.ExplainProject,
            NewConversationStarter.FindBug,
            NewConversationStarter.AddTests,
        )
}

/** A label ending in an ellipsis starts a sentence: the field gets it with a trailing space. */
public fun starterInsertText(label: String): String =
    if (label.endsWith('…')) label.dropLast(1).trimEnd() + " " else label

/** Search over what each model row shows (its name and its host app), never hidden ids. */
public fun filterNewConversationModels(
    models: List<NewConversationModel>,
    query: String,
): List<NewConversationModel> {
    val q = query.trim().lowercase()
    val named = models.filter { it.model.modelId != "default" }
    if (q.isEmpty()) return named
    return named.filter { model ->
        model.name.lowercase().contains(q) || model.runtime?.lowercase()?.contains(q) == true
    }
}

public data class NewConversationUi(
    val servers: List<NewConversationServer> = emptyList(),
    val profile: ProfileId? = null,
    val setup: NewConversationSetup? = null,
    val directory: String? = null,
    val text: String = "",
    val textRevision: Long = 0,
    /** Bumped when a starter fills the prompt: the field takes the cursor back. */
    val focusRevision: Long = 0,
    val model: ModelRef? = null,
    /** N-51: this server's favourite models; the model sheet's stars change them. */
    val favorites: List<ModelRef> = emptyList(),
    val agent: String? = null,
    val separateCopy: Boolean = false,
    val busy: Boolean = false,
    val openingChat: Boolean = false,
    val failure: Failure? = null,
    val sheet: NewConversationSheet? = null,
    val imports: ImportableConversations? = null,
    /** When [imports] was read, for each row's relative age. */
    val importsReadAtMs: Long = 0,
    val importBusy: Boolean = false,
    val importFailed: Boolean = false,
    val draftFailed: Boolean = false,
    val attempted: Boolean = false,
    val creationUnconfirmed: Boolean = false,
    val deliveryUnconfirmed: Boolean = false,
    val reviewConversation: ConversationRef? = null,
    val repairAvailable: Boolean = false,
    val repair: GitLinkRepairReport? = null,
    val repairing: Boolean = false,
    /** The project sheet offers New project (the app's own phone server can make a Git folder under its projects). */
    val canCreateProject: Boolean = false,
    /** The New project name dialog is open. */
    val askingProjectName: Boolean = false,
    val creatingProject: Boolean = false,
    val projectFailure: Failure? = null,
) {
    val canCopy: Boolean
        get() = setup?.projects?.any { it.directory == directory && it.separateCopy } == true
}

public interface NewConversationActions {
    public fun openCreatedConversation() {}

    /** N-48: Discard the unconfirmed first message: the form opens again, empty. */
    public fun discardUnconfirmed() {}

    public fun shown() {}

    public fun back()

    public fun agents() {}

    public fun addServer()

    public fun chooseServer(profile: ProfileId)

    public fun draftChanged(text: String)

    public fun chooseDirectory(directory: String)

    public fun chooseModel(model: ModelRef?)

    /** N-51: a model sheet star: adds or removes [model] from this server's favourites. */
    public fun toggleFavorite(model: ModelRef) {}

    public fun chooseAgent(agent: String?)

    public fun chooseMode(mode: String) {
        chooseAgent(mode)
    }

    /** A starter fills an empty prompt; it never sends. */
    public fun chooseStarter(starter: NewConversationStarter, label: String) {}

    /** From a first message that was definitely not sent: back to its words and choices. */
    public fun editPrompt() {}

    public fun separateCopy(on: Boolean)

    public fun sheet(sheet: NewConversationSheet?)

    public fun start()

    public fun import(conversation: ImportableConversation)

    public fun repair() {}

    public fun repairSettings() {}

    public fun retryLoad()

    /** New project in the project sheet: asks for its name. */
    public fun askNewProject() {}

    /** The name dialog's answer: makes the folder with Git and picks it; null closes the dialog. */
    public fun createProject(name: String?) {}
}

@AssistedInject
public class NewConversationViewModel(
    @Assisted private val route: ChatsRoute.New,
    private val host: NewConversationHost,
    private val nav: AppNavigator,
    private val clock: Clock,
) : ViewModel(), NewConversationActions {
    private val mutable = MutableStateFlow(NewConversationUi())
    public val state: StateFlow<NewConversationUi> = mutable.asStateFlow()
    private val edits = Mutex()
    private var generation = 0
    private var attempt: NewConversationAttempt? = null

    init {
        viewModelScope.launch {
            host.servers.collect { servers ->
                mutable.update { it.copy(servers = servers) }
                if (mutable.value.profile == null) {
                    val target =
                        route.profile?.takeIf { wanted -> servers.any { it.profile == wanted } }
                            ?: servers.firstOrNull { it.connected }?.profile
                            ?: servers.singleOrNull()?.profile
                    target?.let(::chooseServer)
                }
            }
        }
    }

    // This entry owns its draft in memory while pickers or other pages cover it. A new entry
    // starts empty; resuming must never import another conversation's old project draft.
    override fun shown() {}

    override fun back() {
        val shown = mutable.value
        val profile = shown.profile ?: return nav.back()
        if (shown.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            val saved = edits.withLock { host.saveDraft(profile, shown.directory, shown.text) }
            mutable.update { it.copy(busy = false, draftFailed = saved is Outcome.Failed) }
            if (saved is Outcome.Ok) nav.back()
        }
    }

    override fun agents(): Unit = nav.open(AgentsRoute.Home)

    override fun addServer(): Unit = nav.open(ServersRoute.Home)

    override fun chooseServer(profile: ProfileId) {
        if (mutable.value.busy || mutable.value.attempted || mutable.value.deliveryUnconfirmed)
            return
        val ticket = ++generation
        mutable.update { it.copy(profile = profile, busy = true, failure = null, setup = null) }
        viewModelScope.launch {
            when (val loaded = host.setup(profile)) {
                is Outcome.Failed ->
                    if (ticket == generation)
                        mutable.update { it.copy(busy = false, failure = loaded.failure) }
                is Outcome.Ok ->
                    if (ticket == generation) {
                        val setup = loaded.value
                        mutable.update {
                            it.copy(
                                setup = setup,
                                directory = setup.directory,
                                model = setup.model,
                                favorites = setup.favorites,
                                agent = setup.agent,
                                busy = false,
                                repairAvailable =
                                    setup.directory?.let { dir -> host.canRepair(profile, dir) } ==
                                        true,
                            )
                        }
                        restore(profile, setup.directory)
                    }
            }
        }
    }

    private suspend fun restore(profile: ProfileId, directory: String?) {
        edits.withLock {
            val before = mutable.value
            if (before.profile != profile || before.directory != directory) return
            when (val review = host.review(profile, directory)) {
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(
                            deliveryUnconfirmed = true,
                            attempted = true,
                            reviewConversation = null,
                            failure = review.failure,
                        )
                    }
                is Outcome.Ok -> {
                    if (review.value != null) {
                        attempt =
                            NewConversationAttempt(
                                directory.orEmpty(),
                                ref = review.value,
                                deliveryUnconfirmed = true,
                                creationAttempted = true,
                            )
                        mutable.update {
                            it.copy(
                                deliveryUnconfirmed = true,
                                attempted = true,
                                reviewConversation = review.value,
                            )
                        }
                    } else
                        mutable.update {
                            it.copy(deliveryUnconfirmed = false, reviewConversation = null)
                        }
                }
            }
            // Only an unresolved first-send receipt may restore persisted words on a fresh
            // entry: these belong to the conversation offered for review, not a new prompt.
            if (before.text.isNotEmpty() || !mutable.value.deliveryUnconfirmed) return
            when (val saved = host.draft(profile, directory)) {
                is Outcome.Failed -> mutable.update { it.copy(draftFailed = true) }
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            text = saved.value,
                            textRevision = it.textRevision + 1,
                            draftFailed = false,
                        )
                    }
            }
        }
    }

    override fun openCreatedConversation() {
        val ref = mutable.value.reviewConversation ?: return
        viewModelScope.launch {
            // Re-read the persisted scope receipt immediately before navigation; profile edits
            // never redirect it.
            val shown = mutable.value
            val profile = shown.profile ?: return@launch
            val current = host.review(profile, shown.directory)
            if (current is Outcome.Ok && current.value == ref) {
                if (nav.top.value == route) nav.back()
                nav.openChat(ref)
            } else mutable.update { it.copy(reviewConversation = null) }
        }
    }

    override fun discardUnconfirmed() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        if (!shown.deliveryUnconfirmed && !shown.creationUnconfirmed) return
        viewModelScope.launch {
            edits.withLock {
                when (val done = host.dismissReview(profile, shown.directory)) {
                    is Outcome.Failed -> mutable.update { it.copy(failure = done.failure) }
                    is Outcome.Ok -> {
                        attempt = null
                        host.saveDraft(profile, shown.directory, "")
                        mutable.update {
                            it.copy(
                                deliveryUnconfirmed = false,
                                creationUnconfirmed = false,
                                attempted = false,
                                reviewConversation = null,
                                failure = null,
                                text = "",
                                textRevision = it.textRevision + 1,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun retryLoad() {
        mutable.value.profile?.let(::chooseServer)
    }

    override fun draftChanged(text: String) {
        if (mutable.value.busy || mutable.value.deliveryUnconfirmed) return
        mutable.update { it.copy(text = text) }
        val current = mutable.value
        val profile = current.profile ?: return
        viewModelScope.launch {
            edits.withLock {
                val saved = host.saveDraft(profile, current.directory, text)
                if (
                    mutable.value.profile == profile &&
                        mutable.value.directory == current.directory &&
                        mutable.value.text == text
                )
                    mutable.update { it.copy(draftFailed = saved is Outcome.Failed) }
            }
        }
    }

    override fun chooseDirectory(directory: String) {
        if (
            mutable.value.busy ||
                mutable.value.attempted ||
                mutable.value.deliveryUnconfirmed ||
                mutable.value.setup?.canChangeProject != true ||
                !directory.trim().startsWith('/') ||
                directory.trim().split('/').any { it == ".." || it == "." } ||
                directory.any { it.code <= 0x1f || it.code == 0x7f }
        )
            return
        val before = mutable.value
        val profile = before.profile ?: return
        mutable.update {
            it.copy(
                directory = directory.trim(),
                sheet = null,
                failure = null,
                separateCopy = false,
                repairAvailable = host.canRepair(profile, directory.trim()),
                repair = null,
            )
        }
        viewModelScope.launch {
            edits.withLock {
                if (before.text.isNotEmpty()) {
                    val saved = host.saveDraft(profile, directory.trim(), before.text)
                    if (saved is Outcome.Ok) host.saveDraft(profile, before.directory, "")
                    mutable.update { it.copy(draftFailed = saved is Outcome.Failed) }
                }
            }
            if (before.text.isEmpty()) restore(profile, directory.trim())
            host.saveChoices(profile, directory.trim(), before.model, before.agent)
        }
    }

    override fun chooseModel(model: ModelRef?) {
        if (mutable.value.busy || mutable.value.deliveryUnconfirmed) return
        // The model is the last choice of the sheet: picking one closes it.
        mutable.update { it.copy(model = model, sheet = null) }
        saveChoices()
    }

    override fun toggleFavorite(model: ModelRef) {
        val profile = mutable.value.profile ?: return
        viewModelScope.launch {
            val now = host.toggleFavorite(profile, model)
            // A star that could not be saved stays as it was; the list never claims what the store refused.
            if (now is Outcome.Ok && mutable.value.profile == profile)
                mutable.update { it.copy(favorites = now.value) }
        }
    }

    override fun chooseStarter(starter: NewConversationStarter, label: String) {
        val shown = mutable.value
        if (
            shown.busy ||
                shown.openingChat ||
                shown.deliveryUnconfirmed ||
                shown.setup == null ||
                shown.text.isNotBlank() ||
                label.isBlank() ||
                starter !in newConversationStarters(shown.directory)
        )
            return
        draftChanged(starterInsertText(label))
        mutable.update {
            it.copy(textRevision = it.textRevision + 1, focusRevision = it.focusRevision + 1)
        }
    }

    override fun editPrompt() {
        val shown = mutable.value
        if (
            !shown.openingChat ||
                shown.busy ||
                shown.creationUnconfirmed ||
                shown.deliveryUnconfirmed
        )
            return
        // Nothing was made yet: the person may also change server or project. A made conversation
        // keeps its project; its words, agent and model stay editable and Retry reuses it.
        val nothingMade = attempt?.ref == null && attempt?.creationAttempted != true
        if (nothingMade) attempt = null
        mutable.update {
            it.copy(
                openingChat = false,
                attempted = if (nothingMade) false else it.attempted,
                textRevision = it.textRevision + 1,
            )
        }
    }

    override fun chooseAgent(agent: String?) {
        val setup = mutable.value.setup ?: return
        if (
            agent != null &&
                agent !in setup.modes &&
                (agent !in setup.agents || !isStartableAgent(agent))
        )
            return
        // The sheet stays open: Build/Plan and the model are the next choices in the same sheet.
        if (!mutable.value.busy && !mutable.value.deliveryUnconfirmed)
            mutable.update { it.copy(agent = agent) }
        saveChoices()
    }

    private fun saveChoices() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        viewModelScope.launch {
            edits.withLock {
                val saved = host.saveChoices(profile, shown.directory, shown.model, shown.agent)
                if (saved is Outcome.Failed) mutable.update { it.copy(draftFailed = true) }
            }
        }
    }

    override fun separateCopy(on: Boolean) {
        if (!mutable.value.busy && !mutable.value.attempted)
            mutable.update { it.copy(separateCopy = on && it.canCopy) }
    }

    override fun sheet(sheet: NewConversationSheet?) {
        val profile = mutable.value.profile
        mutable.update {
            it.copy(
                sheet = sheet,
                canCreateProject =
                    if (sheet == NewConversationSheet.Projects && profile != null) host.canCreateProject(profile)
                    else it.canCreateProject,
                projectFailure = if (sheet == NewConversationSheet.Projects) null else it.projectFailure,
            )
        }
        if (sheet == NewConversationSheet.Import) loadImports()
    }

    override fun askNewProject() {
        val shown = mutable.value
        if (!shown.canCreateProject || shown.creatingProject || shown.setup?.canChangeProject != true) return
        mutable.update { it.copy(askingProjectName = true, projectFailure = null) }
    }

    override fun createProject(name: String?) {
        val shown = mutable.value
        val profile = shown.profile
        if (name == null || profile == null) {
            if (!shown.creatingProject) mutable.update { it.copy(askingProjectName = false) }
            return
        }
        if (shown.creatingProject) return
        val folder = io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName.validated(name) ?: return
        mutable.update { it.copy(askingProjectName = false, creatingProject = true, projectFailure = null) }
        viewModelScope.launch {
            val made = host.createProject(profile, folder)
            if (mutable.value.profile != profile) return@launch
            mutable.update { it.copy(creatingProject = false) }
            when (made) {
                is Outcome.Ok -> chooseDirectory(made.value)
                is Outcome.Failed -> mutable.update { it.copy(projectFailure = made.failure) }
            }
        }
    }

    private fun loadImports() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        mutable.update { it.copy(importBusy = true, importFailed = false, imports = null) }
        viewModelScope.launch {
            when (val result = host.imports(profile, directory)) {
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            importBusy = false,
                            imports = result.value,
                            importsReadAtMs = clock.now().toEpochMilliseconds(),
                        )
                    }
                is Outcome.Failed ->
                    mutable.update { it.copy(importBusy = false, importFailed = true) }
            }
        }
    }

    override fun import(conversation: ImportableConversation) {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        if (shown.importBusy) return
        mutable.update { it.copy(importBusy = true, importFailed = false) }
        viewModelScope.launch {
            when (val result = host.import(profile, directory, conversation)) {
                is Outcome.Ok -> {
                    if (nav.top.value == route) nav.back()
                    nav.openChat(result.value)
                }
                is Outcome.Failed ->
                    mutable.update { it.copy(importBusy = false, importFailed = true) }
            }
        }
    }

    override fun repairSettings(): Unit = host.openRepairSettings()

    override fun repair() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        if (
            shown.busy ||
                !shown.repairAvailable ||
                shown.failure !is Failure.ProjectGit ||
                shown.creationUnconfirmed ||
                shown.deliveryUnconfirmed
        )
            return
        mutable.update { it.copy(busy = true, repairing = true, repair = null) }
        viewModelScope.launch {
            when (val result = host.repair(profile, directory)) {
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(busy = false, repairing = false, failure = result.failure)
                    }
                is Outcome.Ok -> {
                    mutable.update {
                        it.copy(busy = false, repairing = false, repair = result.value)
                    }
                    if (
                        result.value.result == GitLinkRepairResult.Repaired ||
                            result.value.result == GitLinkRepairResult.Healthy
                    ) {
                        // A definite Git rejection created no session. Repair continues the same
                        // explicit Start.
                        if (attempt?.ref == null && attempt?.failure is Failure.ProjectGit)
                            attempt = null
                        start()
                    }
                }
            }
        }
    }

    override fun start() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        if (
            shown.busy ||
                shown.text.isBlank() ||
                shown.setup == null ||
                shown.creationUnconfirmed ||
                shown.deliveryUnconfirmed
        )
            return
        if (shown.separateCopy) {
            // The separate-copy readiness review remains explicit. Save the exact first
            // prompt and choices before handing it to that owner, just as for ordinary Start.
            mutable.update { it.copy(busy = true, failure = null) }
            viewModelScope.launch {
                val saved = edits.withLock {
                    when (val draft = host.saveDraft(profile, directory, shown.text)) {
                        is Outcome.Failed -> draft
                        is Outcome.Ok ->
                            host.saveChoices(profile, directory, shown.model, shown.agent)
                    }
                }
                mutable.update { it.copy(busy = false, draftFailed = saved is Outcome.Failed) }
                if (saved is Outcome.Ok)
                    nav.open(
                        ProjectsRoute.Project(
                            profile,
                            directory,
                            initialTask = shown.text.trim(),
                            autoStartIsolated = true,
                            taskProvider = shown.model?.providerId,
                            taskModel = shown.model?.modelId,
                            taskAgent = shown.agent,
                        )
                    )
            }
            return
        }
        mutable.update {
            it.copy(busy = true, openingChat = true, failure = null, attempted = true)
        }
        viewModelScope.launch {
            var openedConversation: ConversationRef? = null
            val result = edits.withLock {
                val saved = host.saveDraft(profile, directory, shown.text)
                val choices = host.saveChoices(profile, directory, shown.model, shown.agent)
                when {
                    saved is Outcome.Failed ->
                        NewConversationAttempt(directory, failure = saved.failure)
                    choices is Outcome.Failed ->
                        NewConversationAttempt(directory, failure = choices.failure)
                    else ->
                        when (
                            val started =
                                io.github.eslamasabry.oc.core.common.attempt({
                                    Failure.StorageRefused()
                                }) {
                                    host.start(
                                        profile,
                                        directory,
                                        shown.text.trim(),
                                        shown.model,
                                        shown.agent,
                                        shown.separateCopy,
                                        attempt,
                                        onStarted = { ref ->
                                            if (nav.top.value == route) {
                                                // Keep this entry's ViewModel alive until the
                                                // receipt settles, while the real chat owns the
                                                // composer and the engine's pending bubble.
                                                openedConversation = ref
                                                nav.openChat(ref)
                                                leaveWhenChatCloses()
                                            }
                                        },
                                    )
                                }
                        ) {
                            is Outcome.Ok -> started.value
                            is Outcome.Failed ->
                                NewConversationAttempt(
                                    directory,
                                    failure = started.failure,
                                    deliveryUnconfirmed = true,
                                )
                        }
                }
            }
            attempt = result
            if ((result.dispatched || result.deliveryUnconfirmed) && result.ref != null) {
                // Once the session exists, its chat owns the receipt and always has an editor.
                // An uncertain send must not strand the person on the chip-only starting frame.
                if (openedConversation == null && nav.top.value == route) {
                    nav.back()
                    nav.openChat(result.ref)
                }
                // A screen left lower in the stack starts the next conversation afresh.
                attempt = null
                mutable.update {
                    it.copy(
                        // Preserve the saved first prompt until its receipt is confirmed. Never
                        // insert it into the chat editor, where Send could duplicate it.
                        text = if (result.dispatched) "" else it.text,
                        textRevision = it.textRevision + 1,
                        busy = false,
                        attempted = !result.dispatched,
                        openingChat = false,
                        deliveryUnconfirmed = result.deliveryUnconfirmed,
                        reviewConversation = result.ref.takeIf { result.deliveryUnconfirmed },
                    )
                }
            } else
                mutable.update {
                    it.copy(
                        busy = false,
                        failure = result.failure,
                        creationUnconfirmed = result.creationAttempted && result.ref == null,
                        deliveryUnconfirmed = result.deliveryUnconfirmed,
                        reviewConversation = result.ref.takeIf { result.deliveryUnconfirmed },
                    )
                }
        }
    }

    /**
     * N-43: this form stays alive under the new chat until the receipt settles, but Back from that chat goes to where
     * the person came from, never to the emptied form: once the form is on top again it steps off.
     */
    private fun leaveWhenChatCloses() {
        viewModelScope.launch {
            nav.top.dropWhile { it == route }.first { it == route }
            nav.back()
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatsRoute.New): NewConversationViewModel
    }
}

/** Server modes and background helpers are never peer agents in the chooser. */
public fun isStartableAgent(name: String): Boolean =
    name.isNotBlank() &&
        name.trim().lowercase() !in
            setOf(
                "build",
                "plan",
                "compaction",
                "compact",
                "title",
                "summary",
                "summarize",
                "explore",
                "general",
            )
