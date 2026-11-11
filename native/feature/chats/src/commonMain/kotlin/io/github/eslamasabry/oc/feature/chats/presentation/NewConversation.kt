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
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.selection.AgentSelection
import io.github.eslamasabry.oc.engine.git.GitLinkRepairReport
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.NewChatOutcome
import io.github.eslamasabry.oc.ui.routes.NewChatReports
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** How long the page waits for a server's first answer before it says so and offers Try again. */
internal val SETUP_WAIT = 90.seconds

public data class NewConversationServer(
    val profile: ProfileId,
    val name: String,
    val connected: Boolean,
    /** This phone's own OpenCode is off because the person stopped it. */
    val phoneOff: Boolean = false,
    /**
     * Connections that are part of this server and never listed on their own (This phone's agents): a page opened
     * for one of them (Start new from an agent's conversation) starts here, with that agent chosen.
     */
    val standsFor: Set<ProfileId> = emptySet(),
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

/** One agent this phone can run, as New conversation offers it (Flutter `ChatAgentChoice` / a set-up row). */
public data class NewConversationPhoneAgent(
    val id: String,
    val name: String,
    val iconKey: String,
)

/**
 * The agents on this phone beside the connected server's own: [choices] can start a conversation now,
 * [needsSetup] open their setup. [helper] is the profile their conversations live on, and [selected] the agent
 * remembered for the shown server (null = the server's own agent).
 */
public data class NewConversationPhone(
    val helper: ProfileId,
    val choices: List<NewConversationPhoneAgent>,
    val needsSetup: List<NewConversationPhoneAgent>,
    val selected: String? = null,
)

/** A phone agent's models, and the one remembered for it (else its first). */
public data class NewConversationPhoneModels(
    val models: List<NewConversationModel>,
    val model: ModelRef?,
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
    /** The agents on this phone; null where the phone has no agent host. */
    val phone: NewConversationPhone? = null,
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

    /** Whether [profile] can make a new project folder here (the app's own phone server only, as in Projects). */
    public fun canCreateProject(profile: ProfileId): Boolean = false

    /** Makes a project folder named [name] with Git under the projects root; answers its path. */
    public suspend fun createProject(profile: ProfileId, name: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectFolderCreation")))

    /**
     * B-063: whether [directory] is really there on [profile]'s server, read where the app can look
     * (its own phone server); null when it can't tell. The server keeps listing a project whose
     * folder is gone, and a message sent there is refused.
     */
    public suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = null

    /** B-063: makes [directory] again as an empty Git project; answers its path. */
    public suspend fun remakeFolder(profile: ProfileId, directory: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectFolderCreation")))

    public val servers: StateFlow<List<NewConversationServer>>

    /** Starts this phone's own OpenCode after the person stopped it. */
    public suspend fun startPhoneServer(profile: ProfileId): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("startPhoneServer")))

    public suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup>

    /**
     * Remembers the agent for [profile] (`oc.chatAgent.<profile>`); null is the server's own agent. A phone agent that
     * cannot start a conversation now is refused and nothing is written.
     */
    public suspend fun choosePhoneAgent(profile: ProfileId, agentId: String?): Outcome<Unit> = Outcome.Ok(Unit)

    /** [agentId]'s models and the remembered one; the phone's helper starts when it has to (the first read is slow). */
    public suspend fun phoneAgentModels(profile: ProfileId, agentId: String): Outcome<NewConversationPhoneModels> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phoneAgentModels")))

    /** Remembers [model] for [agentId] (`oc.agentModel.<profile>`). */
    public suspend fun savePhoneAgentModel(profile: ProfileId, agentId: String, model: ModelRef?): Outcome<Unit> =
        Outcome.Ok(Unit)

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
    /** B-063: the chosen project's folder is not on its server any more; Start waits for a choice. */
    val folderMissing: Boolean = false,
    val remakingFolder: Boolean = false,
    val remakeFailure: Failure? = null,
    /** The chosen server is this phone's own OpenCode and the person stopped it: no list can be read until it starts. */
    val phoneOff: Boolean = false,
    val startingPhone: Boolean = false,
    /** The agent on this phone chosen for this conversation; null = the server's own agent. */
    val phoneAgent: String? = null,
    /** [phoneAgent]'s models and the chosen one; empty while they are read. */
    val phoneModels: List<NewConversationModel> = emptyList(),
    val phoneModel: ModelRef? = null,
    val phoneModelsBusy: Boolean = false,
) {
    /** A separate copy is OpenCode only: a phone agent hides it (Flutter `_start`). */
    val canCopy: Boolean
        get() = phoneAgent == null && setup?.projects?.any { it.directory == directory && it.separateCopy } == true

    /** The chosen phone agent's row, while it is still offered. */
    val phoneAgentChoice: NewConversationPhoneAgent?
        get() = setup?.phone?.choices?.firstOrNull { it.id == phoneAgent }

    /** The models the Model row and sheet list: the phone agent's own, else the server's. */
    val shownModels: List<NewConversationModel>
        get() = if (phoneAgent != null) phoneModels else setup?.models.orEmpty()

    /**
     * Whether the Model row is a control: a person can name at least two models, or the phone agent's list is
     * still being read. With one model or none there is nothing to choose, so the row only states the model.
     */
    val modelChoosable: Boolean
        get() = (phoneAgent != null && phoneModelsBusy) || filterNewConversationModels(shownModels, "").size >= 2

    /** The model the Model row shows. */
    val shownModel: ModelRef?
        get() = if (phoneAgent != null) phoneModel else model

    /** Why Start cannot run for a phone agent: it works only in the projects on this phone (`/root/projects/`). */
    val phoneNeedsProjectsFolder: Boolean
        get() =
            phoneAgent != null && directory != null &&
                !AgentSelection.folderAllowed(AgentId(phoneAgent), directory)

    /**
     * E20-S08: the project sheet's rows, one per folder. A server can list the same folder twice (a worktree, a
     * trailing "/"); the first listing wins, and it says Separate copy when any listing of that folder does.
     */
    val projectChoices: List<NewConversationProject>
        get() =
            setup?.projects.orEmpty().groupBy { projectPathKey(it.directory) }.values.map { same ->
                same.first().copy(separateCopy = same.any { it.separateCopy })
            }

    /** Whether [project] is the chosen folder, however its path is spelled. */
    public fun isChosen(project: NewConversationProject): Boolean =
        directory != null && projectPathKey(project.directory) == projectPathKey(directory)
}

/** One spelling per folder: no trailing "/" (the root stays "/"). */
internal fun projectPathKey(path: String): String = path.trim().trimEnd('/').ifEmpty { if (path.isBlank()) "" else "/" }

public interface NewConversationActions {
    public fun openCreatedConversation() {}

    /** N-48: Discard the unconfirmed first message: the form opens again, empty. */
    public fun discardUnconfirmed() {}

    /** B-096/B-103: read the unconfirmed first message's record again (the server may be back); frees the form when it is settled. */
    public fun recheckUnconfirmed() {}

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

    /** An agent on this phone, or null for the server's own agent; remembered per server. */
    public fun choosePhoneAgent(agent: String?) {}

    /** A dimmed agent: opens its setup (the Agents page); coming back rereads the offer. */
    public fun setUpPhoneAgent(agent: String) {
        agents()
    }

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

    public fun retryLoad()

    /** Starts this phone's OpenCode when [NewConversationUi.phoneOff]. */
    public fun startPhoneServer() {}

    /** New project in the project sheet: asks for its name. */
    public fun askNewProject() {}

    /** The name dialog's answer: makes the folder with Git and picks it; null closes the dialog. */
    public fun createProject(name: String?) {}

    /** B-063: makes the missing project folder again (empty, with Git). */
    public fun remakeFolder() {}
}

@AssistedInject
public class NewConversationViewModel(
    @Assisted private val route: ChatsRoute.New,
    private val host: NewConversationHost,
    private val nav: AppNavigator,
    private val clock: Clock,
    private val reports: NewChatReports,
    kept: Set<ImportsLastKnown> = emptySet(),
) : ViewModel(), NewConversationActions {
    private val importShelf = kept.firstOrNull()
    private val mutable =
        MutableStateFlow(
            if (route.initialText.isEmpty()) NewConversationUi()
            else NewConversationUi(text = route.initialText, textRevision = 1)
        )
    /** Whether [ChatsRoute.New.reportKey] has had its one report. */
    private var reported = false
    public val state: StateFlow<NewConversationUi> = mutable.asStateFlow()
    private val edits = Mutex()
    private var generation = 0
    private var attempt: NewConversationAttempt? = null

    init {
        viewModelScope.launch {
            host.servers.collect { servers ->
                mutable.update { it.copy(servers = servers) }
                // The person's Start (here or on This phone) brought the server back: read it again.
                val shown = mutable.value
                if (shown.phoneOff && !shown.startingPhone && servers.none { it.profile == shown.profile && it.phoneOff })
                    shown.profile?.let(::chooseServer)
                if (mutable.value.profile == null) {
                    val target =
                        route.profile?.let { wanted ->
                            servers.firstOrNull { it.profile == wanted || wanted in it.standsFor }?.profile
                        }
                            ?: servers.firstOrNull { it.connected }?.profile
                            ?: servers.singleOrNull()?.profile
                    target?.let(::chooseServer)
                }
            }
        }
    }

    // This entry owns its draft in memory while pickers or other pages cover it. A new entry
    // starts empty; resuming must never import another conversation's old project draft.
    /** Agent setup was opened from here: the agents may have changed when this screen is shown again. */
    private var refreshOnShown = false

    /** [ChatsRoute.New.phoneAgent] is applied once, on the first setup that offers it. */
    private var wantedApplied = false

    override fun shown() {
        if (!refreshOnShown) return
        val profile = mutable.value.profile ?: return
        refreshOnShown = false
        viewModelScope.launch {
            // Only the offered agents are reread: the draft, project, model and agent stay as they are.
            val loaded = host.setup(profile) as? Outcome.Ok ?: return@launch
            var reread: String? = null
            mutable.update {
                if (it.profile == profile && it.setup != null) {
                    // A phone agent that is no longer offered (removed, signed out) goes back to OpenCode.
                    val kept =
                        it.phoneAgent?.takeIf { id -> loaded.value.phone?.choices?.any { c -> c.id == id } == true }
                    reread = kept
                    it.copy(
                        setup = loaded.value,
                        phoneAgent = kept,
                        phoneModels = if (kept == null) emptyList() else it.phoneModels,
                        phoneModel = if (kept == null) null else it.phoneModel,
                    )
                } else it
            }
        }
    }

    override fun back() {
        val shown = mutable.value
        val profile = shown.profile ?: return nav.back()
        if (shown.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            val saved = edits.withLock { host.saveDraft(profile, shown.directory, shown.text) }
            mutable.update { it.copy(busy = false, draftFailed = saved is Outcome.Failed) }
            if (saved is Outcome.Ok) {
                report(NewChatOutcome.Left)
                nav.back()
            }
        }
    }

    /** The one report a keyed new conversation makes (F: `showNewChat(onSent:)`, then cancelled when not sent). */
    private fun report(outcome: NewChatOutcome) {
        val key = route.reportKey ?: return
        if (reported) return
        reported = true
        reports.report(key, outcome)
    }

    override fun onCleared() {
        report(NewChatOutcome.Left)
    }

    override fun agents() {
        refreshOnShown = true
        nav.open(AgentsRoute.Home)
    }

    /** A dimmed agent's "Set up" opens that agent's own sheet (not the whole page) and comes back here when it is ready. */
    override fun setUpPhoneAgent(agent: String) {
        refreshOnShown = true
        nav.open(AgentsRoute.Agent(agent, forNewChat = true))
    }

    override fun addServer(): Unit = nav.open(ServersRoute.Home)

    override fun chooseServer(profile: ProfileId) {
        if (mutable.value.busy || mutable.value.attempted || mutable.value.deliveryUnconfirmed)
            return
        val ticket = ++generation
        // A server the person turned off cannot answer: say so now instead of waiting on it.
        if (mutable.value.servers.any { it.profile == profile && it.phoneOff }) {
            mutable.update {
                it.copy(profile = profile, busy = false, failure = null, setup = null, phoneOff = true)
            }
            return
        }
        mutable.update {
            it.copy(profile = profile, busy = true, failure = null, setup = null, phoneOff = false)
        }
        viewModelScope.launch {
            // Never wait for ever: a server that does not answer in time is a failure with a Try again.
            val answered =
                withTimeoutOrNull(SETUP_WAIT) { host.setup(profile) }
                    ?: Outcome.Failed(Failure.Network(NetworkWhy.Unknown))
            when (val loaded = answered) {
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
                                phoneAgent = setup.phone?.selected,
                                phoneModels = emptyList(),
                                phoneModel = null,
                                busy = false,
                                repairAvailable =
                                    setup.directory?.let { dir -> host.canRepair(profile, dir) } ==
                                        true,
                            )
                        }
                        val wanted = route.phoneAgent
                        if (
                            wanted != null && !wantedApplied &&
                                setup.phone?.choices?.any { c -> c.id == wanted } == true
                        ) {
                            // The Agents page chose this agent: it is the one chosen here, remembered like a tap.
                            wantedApplied = true
                            if (setup.phone?.selected != wanted) choosePhoneAgent(wanted)
                            else loadPhoneModels(profile, wanted)
                        } else setup.phone?.selected?.let { loadPhoneModels(profile, it) }
                        restore(profile, setup.directory)
                        setup.directory?.let { checkFolder(profile, it) }
                        startInWantedFolder(profile, setup.directory)
                    }
            }
        }
    }

    /**
     * [ChatsRoute.New.directory] is applied once; a folder the server no longer has leaves the usual one. Where the app
     * cannot check (a helper on another computer, or the phone's Linux not running) the old folder is used, as Flutter's
     * start-new-chat uses the old conversation's folder unchecked.
     */
    private var wantedFolderApplied = false

    private suspend fun startInWantedFolder(profile: ProfileId, current: String?) {
        val wanted = route.directory?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (wantedFolderApplied) return
        wantedFolderApplied = true
        if (wanted == current) return
        if (host.folderExists(profile, wanted) != false) chooseDirectory(wanted)
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
                            // attempted too: a record that settled (or is gone) leaves no frame to be stuck in.
                            it.copy(
                                deliveryUnconfirmed = false,
                                attempted = if (before.deliveryUnconfirmed) false else it.attempted,
                                failure = if (before.deliveryUnconfirmed) null else it.failure,
                                reviewConversation = null,
                            )
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

    override fun recheckUnconfirmed() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        if (!shown.deliveryUnconfirmed) return
        viewModelScope.launch { restore(profile, shown.directory) }
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

    override fun startPhoneServer() {
        val profile = mutable.value.profile ?: return
        if (!mutable.value.phoneOff || mutable.value.startingPhone) return
        mutable.update { it.copy(startingPhone = true, failure = null) }
        viewModelScope.launch {
            val started = host.startPhoneServer(profile)
            mutable.update { it.copy(startingPhone = false) }
            when (started) {
                is Outcome.Failed -> mutable.update { it.copy(failure = started.failure) }
                is Outcome.Ok -> chooseServer(profile)
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
                folderMissing = false,
                remakeFailure = null,
            )
        }
        checkFolder(profile, directory.trim())
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
        val shown = mutable.value
        val phone = shown.phoneAgent
        val profile = shown.profile
        if (phone != null && profile != null) {
            mutable.update { it.copy(phoneModel = model, sheet = null) }
            viewModelScope.launch { host.savePhoneAgentModel(profile, phone, model) }
            return
        }
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
        if (!mutable.value.busy && !mutable.value.deliveryUnconfirmed) {
            // The server's own agent is the choice now: a phone agent is no longer the one remembered.
            if (mutable.value.phoneAgent != null) choosePhoneAgent(null)
            mutable.update { it.copy(agent = agent) }
        }
        saveChoices()
    }

    override fun choosePhoneAgent(agent: String?) {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val phone = shown.setup?.phone ?: return
        if (shown.busy || shown.deliveryUnconfirmed || shown.attempted || shown.phoneAgent == agent) return
        if (agent != null && phone.choices.none { it.id == agent }) return
        mutable.update {
            it.copy(
                phoneAgent = agent,
                // Build/Plan and a separate copy belong to the server's own agent.
                agent = if (agent != null) null else it.agent,
                separateCopy = if (agent != null) false else it.separateCopy,
                phoneModels = emptyList(),
                phoneModel = null,
            )
        }
        viewModelScope.launch {
            if (host.choosePhoneAgent(profile, agent) is Outcome.Failed)
                mutable.update { it.copy(draftFailed = true) }
        }
        if (agent != null) loadPhoneModels(profile, agent)
        else saveChoices()
    }

    /** The phone agent's own models; read while the person types, so Start does not wait for them. */
    private fun loadPhoneModels(profile: ProfileId, agent: String) {
        mutable.update { it.copy(phoneModelsBusy = true) }
        viewModelScope.launch {
            val read = host.phoneAgentModels(profile, agent)
            mutable.update {
                if (it.phoneAgent != agent || it.profile != profile) it
                else
                    when (read) {
                        is Outcome.Ok ->
                            it.copy(
                                phoneModels = read.value.models,
                                phoneModel = read.value.model,
                                phoneModelsBusy = false,
                            )
                        is Outcome.Failed -> it.copy(phoneModelsBusy = false)
                    }
            }
        }
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
        // The model sheet never opens with nothing to choose.
        if (sheet == NewConversationSheet.Models && !mutable.value.modelChoosable) return
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

    /** B-063: a project whose folder is gone says so before Start, never after a refused send. */
    private fun checkFolder(profile: ProfileId, directory: String) {
        viewModelScope.launch {
            val there = host.folderExists(profile, directory)
            mutable.update {
                if (it.profile == profile && it.directory == directory)
                    it.copy(folderMissing = there == false)
                else it
            }
        }
    }

    override fun remakeFolder() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        if (!shown.folderMissing || shown.remakingFolder) return
        mutable.update { it.copy(remakingFolder = true, remakeFailure = null) }
        viewModelScope.launch {
            val made = host.remakeFolder(profile, directory)
            mutable.update {
                if (it.profile != profile || it.directory != directory) it.copy(remakingFolder = false)
                else
                    when (made) {
                        is Outcome.Ok -> it.copy(remakingFolder = false, folderMissing = false)
                        is Outcome.Failed -> it.copy(remakingFolder = false, remakeFailure = made.failure)
                    }
            }
        }
    }

    private fun loadImports() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        // Cache-first: the list last read for this folder shows at once; the live list replaces it in place.
        val first = importShelf?.peek(profile, directory)
        mutable.update {
            it.copy(
                importBusy = first == null,
                importFailed = false,
                imports = first?.toImports(),
                importsReadAtMs = first?.readAtMs ?: it.importsReadAtMs,
            )
        }
        viewModelScope.launch {
            if (first == null) {
                importShelf?.read(profile, directory)?.let { stored ->
                    mutable.update {
                        if (it.imports != null) it
                        else it.copy(importBusy = false, imports = stored.toImports(), importsReadAtMs = stored.readAtMs)
                    }
                }
            }
            when (val result = host.imports(profile, directory)) {
                is Outcome.Ok -> {
                    val at = clock.now().toEpochMilliseconds()
                    mutable.update { it.copy(importBusy = false, imports = result.value, importsReadAtMs = at) }
                    importShelf?.keep(profile, directory, KeptImports.of(result.value, at))
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
                shown.deliveryUnconfirmed ||
                shown.folderMissing ||
                shown.phoneNeedsProjectsFolder ||
                shown.phoneModelsBusy
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
            // A phone agent's conversation lives on the phone helper's profile, with the agent's own model.
            val phone = shown.setup?.phone?.takeIf { shown.phoneAgent != null }
            val target = phone?.helper ?: profile
            val model = if (phone != null) shown.phoneModel else shown.model
            val agent = if (phone != null) null else shown.agent
            val result = edits.withLock {
                val saved = host.saveDraft(target, directory, shown.text)
                val choices = host.saveChoices(target, directory, model, agent)
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
                                        target,
                                        directory,
                                        shown.text.trim(),
                                        model,
                                        agent,
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
            // The words are the phone agent's conversation now: the server's own draft for this folder is spent.
            if (target != profile && result.ref != null)
                edits.withLock { host.saveDraft(profile, directory, "") }
            if (result.dispatched) report(NewChatOutcome.FirstMessageSent)
            else if (result.deliveryUnconfirmed) report(NewChatOutcome.Unknown)
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
