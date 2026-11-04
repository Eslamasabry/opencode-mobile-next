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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    public val servers: StateFlow<List<NewConversationServer>>

    public suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup>

    public suspend fun review(profile: ProfileId, directory: String?): Outcome<ConversationRef?> =
        Outcome.Ok(null)

    public suspend fun draft(profile: ProfileId, directory: String?): Outcome<String>

    public suspend fun saveDraft(
        profile: ProfileId,
        directory: String?,
        text: String,
    ): Outcome<Unit>

    public suspend fun start(
        profile: ProfileId,
        directory: String,
        text: String,
        model: ModelRef?,
        agent: String?,
        separateCopy: Boolean,
        previous: NewConversationAttempt?,
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

public enum class NewConversationSheet {
    Projects,
    Models,
    Agents,
    Import,
}

public data class NewConversationUi(
    val servers: List<NewConversationServer> = emptyList(),
    val profile: ProfileId? = null,
    val setup: NewConversationSetup? = null,
    val directory: String? = null,
    val text: String = "",
    val textRevision: Long = 0,
    val model: ModelRef? = null,
    val agent: String? = null,
    val separateCopy: Boolean = false,
    val busy: Boolean = false,
    val failure: Failure? = null,
    val sheet: NewConversationSheet? = null,
    val imports: ImportableConversations? = null,
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
) {
    val canCopy: Boolean
        get() = setup?.projects?.any { it.directory == directory && it.separateCopy } == true
}

public interface NewConversationActions {
    public fun openCreatedConversation() {}

    public fun shown() {}

    public fun back()

    public fun agents() {}

    public fun addServer()

    public fun chooseServer(profile: ProfileId)

    public fun draftChanged(text: String)

    public fun chooseDirectory(directory: String)

    public fun chooseModel(model: ModelRef?)

    public fun chooseAgent(agent: String?)

    public fun separateCopy(on: Boolean)

    public fun sheet(sheet: NewConversationSheet?)

    public fun start()

    public fun import(conversation: ImportableConversation)

    public fun repair() {}

    public fun repairSettings() {}

    public fun retryLoad()
}

@AssistedInject
public class NewConversationViewModel(
    @Assisted private val route: ChatsRoute.New,
    private val host: NewConversationHost,
    private val nav: AppNavigator,
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

    override fun shown() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        if (shown.setup == null || shown.busy || shown.draftFailed) return
        viewModelScope.launch {
            edits.withLock {
                val now = mutable.value
                if (now.profile != profile || now.directory != shown.directory) return@withLock
                val saved = host.draft(profile, shown.directory)
                if (saved is Outcome.Ok && saved.value != now.text)
                    mutable.update {
                        it.copy(text = saved.value, textRevision = it.textRevision + 1)
                    }
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
            if (before.text.isNotEmpty()) return
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
                directory.isBlank()
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
        }
    }

    override fun chooseModel(model: ModelRef?) {
        if (!mutable.value.busy && !mutable.value.deliveryUnconfirmed)
            mutable.update { it.copy(model = model, sheet = null) }
    }

    override fun chooseAgent(agent: String?) {
        if (!mutable.value.busy && !mutable.value.deliveryUnconfirmed)
            mutable.update { it.copy(agent = agent, sheet = null) }
    }

    override fun separateCopy(on: Boolean) {
        if (!mutable.value.busy && !mutable.value.attempted)
            mutable.update { it.copy(separateCopy = on && it.canCopy) }
    }

    override fun sheet(sheet: NewConversationSheet?) {
        mutable.update { it.copy(sheet = sheet) }
        if (sheet == NewConversationSheet.Import) loadImports()
    }

    private fun loadImports() {
        val shown = mutable.value
        val profile = shown.profile ?: return
        val directory = shown.directory ?: return
        mutable.update { it.copy(importBusy = true, importFailed = false, imports = null) }
        viewModelScope.launch {
            when (val result = host.imports(profile, directory)) {
                is Outcome.Ok ->
                    mutable.update { it.copy(importBusy = false, imports = result.value) }
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
            return
        }
        mutable.update { it.copy(busy = true, failure = null, attempted = true) }
        viewModelScope.launch {
            val result = edits.withLock {
                host.start(
                    profile,
                    directory,
                    shown.text.trim(),
                    shown.model,
                    shown.agent,
                    shown.separateCopy,
                    attempt,
                )
            }
            attempt = result
            if (result.dispatched && result.ref != null) {
                mutable.update {
                    it.copy(
                        text = "",
                        textRevision = it.textRevision + 1,
                        busy = false,
                        attempted = false,
                    )
                }
                if (nav.top.value == route) nav.back()
                nav.openChat(result.ref)
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

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatsRoute.New): NewConversationViewModel
    }
}
