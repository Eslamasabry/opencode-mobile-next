package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.*
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.engine.delivery.queue.*
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournals
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.ui.routes.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Saved server -> live graph -> conversations. No production Flutter adoption is performed here.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerConnections>())
@Inject
class PreviewRuntime(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val profileScope: ProfileScope,
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val draftOwners: Set<ConversationDrafts>,
    private val profileClosers: Set<ProfileCloser>,
    private val cleaners:
        Set<ProfileResourceCleaner>, // P12-FIX: the saved-prompt shelf and its files (B-SEC-172)
) : ServerConnections {
    val profiles: ProfileStore = stores.profiles
    private val lane = dispatchers.serial("preview-runtime")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val order = Mutex()
    private val ready = CompletableDeferred<Outcome<Unit>>()
    private val mutableServers = MutableStateFlow<ImmutableList<SavedServer>>(persistentListOf())
    override val servers: StateFlow<ImmutableList<SavedServer>> = mutableServers.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    override val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()
    internal val snapshot = MutableStateFlow(ChatFeedSnapshot())
    private val listings = mutableMapOf<ProfileId, MutableStateFlow<OpenCodeListing>>()
    private val mutableSourceFailures = MutableStateFlow<Map<ProfileId, Failure>>(emptyMap())
    internal val sourceFailures: Map<ProfileId, Failure>
        get() = mutableSourceFailures.value

    private val loadedSources = MutableStateFlow<Set<ProfileId>>(emptySet())
    private var feed: ChatFeed? = null
    private var feedJob: Job? = null
    private val eventJobs = mutableMapOf<ProfileId, Job>()
    // Retained only for a saved profile whose deletion reached the draft fence.
    private val draftRecovery = mutableSetOf<ProfileId>()
    private val restoreRequested = AtomicBoolean(false)

    init {
        scope.launch {
            val result = stores.warm()
            ready.complete(result)
            if (result is Outcome.Ok) {
                publishServers()
            }
        }
        scope.launch { profiles.profiles.collect { if (ready.isCompleted) publishServers() } }
    }

    /**
     * Only a visible app launch may restore the ordinary connection. Cold OS actions validate their
     * target first.
     */
    fun restoreSavedConnection() {
        if (!restoreRequested.compareAndSet(false, true)) return
        scope.launch {
            if (ready.await() is Outcome.Failed) return@launch
            val active = profiles.activeId.value ?: profiles.profiles.value.firstOrNull()?.id
            // PORT-SCREENS-2: the home list records each source's failure, then the other shown sources join.
            if (active != null) command { connectForHome(active) }
            synchronizeListSources()
        }
    }

    private fun publishServers() {
        mutableServers.value =
            profiles.profiles.value
                .map {
                    SavedServer(
                        it.id,
                        it.profile.name,
                        it.profile.baseUrl,
                        registry.live.value.containsKey(it.id),
                    )
                }
                .toImmutableList()
    }

    /**
     * Ok passes through unchanged; a failure gains only generated stage diagnostics (never raw
     * details).
     */
    private fun <T> Outcome<T>.atStage(stage: ConnectionStage): Outcome<T> =
        when (this) {
            is Outcome.Ok -> this
            is Outcome.Failed ->
                Outcome.Failed(connectionFailure(failure, stage, redactor::details))
        }

    private suspend fun <T> command(block: suspend () -> Outcome<T>): Outcome<T> {
        when (val start = ready.await()) {
            is Outcome.Failed -> return start.atStage(ConnectionStage.StorageReady)
            is Outcome.Ok -> Unit
        }
        return withContext(lane) {
            order.withLock {
                mutableBusy.value = true
                try {
                    block()
                } finally {
                    mutableBusy.value = false
                    publishServers()
                }
            }
        }
    }

    override suspend fun save(
        name: String,
        address: String,
        username: String,
        password: String,
        allowCleartext: Boolean,
    ): Outcome<ProfileId> = command {
        val normalized = AddressRules.normalize(address)
        if (normalized is Outcome.Failed) return@command normalized
        val origin = (normalized as Outcome.Ok).value.toString()
        if (ProfileOrigins.needsCleartextConsent(origin) && !allowCleartext)
            return@command Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val id = ProfileId(UUID.randomUUID().toString())
        profiles
            .upsert(
                StoredProfile(
                    id,
                    name.trim().ifEmpty { "OpenCode" },
                    origin,
                    username = username.trim().ifEmpty { "opencode" },
                ),
                if (password.isEmpty()) SecretEdit.Clear else SecretEdit.Set(password),
                TrustInput(cleartextOrigin = if (allowCleartext) origin else null),
            )
            .map { id }
    }

    override suspend fun connect(id: ProfileId): Outcome<Unit> = command {
        connectForHome(id, recoverDrafts = true)
    }

    private suspend fun connectForHome(
        id: ProfileId,
        recoverDrafts: Boolean = false,
        makeActive: Boolean = true,
    ): Outcome<Unit> {
        val result = connectNow(id, recoverDrafts, makeActive)
        when (result) {
            is Outcome.Failed -> mutableSourceFailures.update { it + (id to result.failure) }
            is Outcome.Ok -> mutableSourceFailures.update { it - id }
        }
        return result
    }

    internal fun listSourceShown(id: ProfileId): Boolean =
        id == profiles.activeId.value ||
            (stores.prefs.bool("oc.chatListShown.${id.raw}")
                ?: profiles.profile(id)?.profile?.let(::sourceOnThisPhone)
                ?: false)

    internal fun sourceOnThisPhone(profile: StoredProfile): Boolean {
        val host = ProfileOrigins.serverOrigin(profile.baseUrl)?.host?.lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }

    internal fun activeListReady(): Boolean = profiles.activeId.value in loadedSources.value

    internal suspend fun synchronizeListSources(): Outcome<Unit> = command {
        var failed: Outcome.Failed? = null
        for (profile in profiles.profiles.value) {
            if (
                profile.id != profiles.activeId.value &&
                    listSourceShown(profile.id) &&
                    profile.id !in listings
            ) {
                val result = connectForHome(profile.id, makeActive = false)
                if (result is Outcome.Failed) failed = result
            }
        }
        rebuildFeed()
        failed ?: Unit.ok()
    }

    internal suspend fun showListSource(id: ProfileId, shown: Boolean): Outcome<Unit> = command {
        if (id == profiles.activeId.value) return@command Unit.ok()
        if (profiles.profile(id) == null)
            return@command Failure.Refused(RefusalReason.ProfileDeleted).failed()
        when (
            val saved =
                stores.prefs.commit(stores.lanes.forProfile(id)) {
                    putBool("oc.chatListShown.${id.raw}", shown)
                }
        ) {
            is Outcome.Failed -> return@command saved
            is Outcome.Ok -> Unit
        }
        val result = if (shown) connectForHome(id, makeActive = false) else Unit.ok()
        rebuildFeed()
        result
    }

    internal suspend fun retryListSource(id: ProfileId): Outcome<Unit> = command {
        if (!listSourceShown(id)) return@command Failure.Refused(RefusalReason.StaleScope).failed()
        connectForHome(id, makeActive = id == profiles.activeId.value)
    }

    internal suspend fun connectWatched(id: ProfileId, expected: StoredProfile): Outcome<Unit> =
        command {
            if (profiles.profile(id)?.profile != expected)
                return@command Failure.Refused(RefusalReason.StaleScope).failed()
            connectForHome(id)
        }

    private suspend fun connectNow(
        id: ProfileId,
        recoverDrafts: Boolean = false,
        makeActive: Boolean = true,
    ): Outcome<Unit> {
        val loaded =
            profiles.profile(id)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted)
                    .failed()
                    .atStage(ConnectionStage.ProfileRead)
        if (loaded.cleartextUnconfirmed)
            return Failure.Refused(RefusalReason.CleartextNotConfirmed)
                .failed()
                .atStage(ConnectionStage.ProfileRead)
        val profile =
            loaded.toServerProfile()
                ?: return Failure.Refused(RefusalReason.InvalidAddress)
                    .failed()
                    .atStage(ConnectionStage.ProfileRead)
        if (id in draftRecovery) {
            if (!recoverDrafts)
                return Failure.Refused(RefusalReason.ProfileClosing)
                    .failed()
                    .atStage(ConnectionStage.DraftRecovery)
            val drafts =
                draftOwners.singleOrNull()
                    ?: return Failure.StorageRefused()
                        .failed()
                        .atStage(ConnectionStage.DraftRecovery)
            when (val reopened = drafts.reopenProfile(id)) {
                is Outcome.Failed -> return reopened.atStage(ConnectionStage.DraftRecovery)
                is Outcome.Ok -> draftRecovery.remove(id)
            }
        }
        val previous = registry.live.value[id]
        if (previous != null && previous.session.profile != profile) {
            if (!registry.closeIfCurrent(id, previous)) {
                return Failure.Refused(RefusalReason.StaleScope)
                    .failed()
                    .atStage(ConnectionStage.OpenGateway)
            }
            eventJobs.remove(id)?.cancelAndJoin()
            listings.remove(id)
            loadedSources.update { it - id }
            mutableSourceFailures.update { it - id }
            rebuildFeed()
        }
        when (val opened = registry.open(profile)) {
            is Outcome.Failed -> return opened.atStage(ConnectionStage.OpenGateway)
            is Outcome.Ok -> {
                val saved = if (makeActive) profiles.setActive(id) else Unit.ok()
                if (saved is Outcome.Failed) return saved.atStage(ConnectionStage.SaveActiveProfile)
                val graph = opened.value
                if (!listings.containsKey(id)) {
                    listings[id] = MutableStateFlow(OpenCodeListing(loading = true))
                    rebuildFeed()
                    eventJobs[id] = scope.launch {
                        launch {
                            graph.session.readState.state.collect { reads ->
                                if (registry.live.value[id] === graph) {
                                    listings[id]?.let { listing ->
                                        listing.value =
                                            listing.value.copy(
                                                finishedUnseen =
                                                    reads.filterValues { it.finishedUnseen }.keys
                                            )
                                    }
                                }
                            }
                        }
                        graph.session.server.events.all.collect { envelope ->
                            when (envelope.event) {
                                GatewayEvent.Connected,
                                is GatewayEvent.SessionUpserted,
                                is GatewayEvent.SessionChanged,
                                is GatewayEvent.SessionDeleted,
                                is GatewayEvent.RunStarted,
                                is GatewayEvent.RunEnded ->
                                    order.withLock {
                                        if (registry.live.value[id] === graph) refreshOne(id)
                                    }
                                is GatewayEvent.PermissionAsked,
                                is GatewayEvent.PermissionSettled,
                                is GatewayEvent.FormCreated,
                                is GatewayEvent.FormSettled ->
                                    order.withLock {
                                        if (registry.live.value[id] === graph) refreshOne(id)
                                    }
                                else -> Unit
                            }
                        }
                    }
                }
                if (makeActive) rebuildFeed()
                return refreshOne(id)
            }
        }
    }

    private fun rebuildFeed() {
        feedJob?.cancel()
        feed?.close()
        val sources =
            listings.filterKeys(::listSourceShown).map { (id, listing) ->
                OpenCodeChatFeedSource(
                    id,
                    profiles.profile(id)?.profile?.name,
                    listing,
                    refresh = { refreshOne(id) },
                )
            }
        val merged = ChatFeed(sources, scope)
        feed = merged
        snapshot.value = merged.snapshot.value
        feedJob = scope.launch { merged.snapshot.collect { snapshot.value = it } }
    }

    private suspend fun refreshOne(id: ProfileId): Outcome<Unit> {
        val graph =
            registry.live.value[id]
                ?: return Failure.Refused(RefusalReason.StaleScope)
                    .failed()
                    .atStage(ConnectionStage.LoadConversations)
        val listing =
            listings[id]
                ?: return Failure.Refused(RefusalReason.StaleScope)
                    .failed()
                    .atStage(ConnectionStage.LoadConversations)
        when (val page = readSessionPages(graph.session.server)) {
            is Outcome.Failed -> {
                listing.value = listing.value.copy(loading = false, failed = true)
                return page.atStage(ConnectionStage.LoadConversations)
            }
            is Outcome.Ok -> {
                if (registry.live.value[id] !== graph)
                    return Failure.Refused(RefusalReason.StaleScope)
                        .failed()
                        .atStage(ConnectionStage.LoadConversations)
                val statuses = graph.session.server.sessionStatuses()
                val gateway = graph.session.server
                val permissions =
                    if (gateway.capabilities.value.permissionRequests) gateway.pendingPermissions()
                    else null
                val questions =
                    if (gateway.capabilities.value.legacyQuestionRequests)
                        gateway.pendingQuestions()
                    else null
                val forms = if (gateway.capabilities.value.forms) gateway.pendingForms() else null
                if (registry.live.value[id] !== graph)
                    return Failure.Refused(RefusalReason.StaleScope).failed()
                val needs = buildSet {
                    (permissions as? Outcome.Ok)?.value?.forEach { add(it.sessionId) }
                    (questions as? Outcome.Ok)?.value?.forEach { add(it.sessionId) }
                    (forms as? Outcome.Ok)?.value?.forEach { add(it.sessionId) }
                }
                page.value.sessions.forEach(graph.session.readState::observe)
                listing.value =
                    OpenCodeListing(
                        sessions = page.value.sessions,
                        statuses = (statuses as? Outcome.Ok)?.value ?: listing.value.statuses,
                        needsYou =
                            if (
                                permissions is Outcome.Failed ||
                                    questions is Outcome.Failed ||
                                    forms is Outcome.Failed
                            )
                                listing.value.needsYou + needs
                            else needs,
                        finishedUnseen =
                            graph.session.readState.state.value
                                .filterValues { it.finishedUnseen }
                                .keys,
                        // B1: more pages on the server is not "couldn't load".
                        complete = page.value.complete,
                        failed = statuses is Outcome.Failed,
                        acrossProjects =
                            graph.session.server.capabilities.value.globalSessionSearch,
                    )
                loadedSources.update { it + id }
                return statuses.map { Unit }.atStage(ConnectionStage.LoadActivity)
            }
        }
    }

    internal suspend fun refresh(): Outcome<Unit> = command {
        var failed: Outcome.Failed? = null
        listings.keys.toList().forEach {
            val result = refreshOne(it)
            if (result is Outcome.Failed) failed = result
        }
        failed ?: Unit.ok()
    }

    override suspend fun createConversation(profile: ProfileId?): Outcome<ConversationRef> =
        command {
            val id =
                profile
                    ?: profiles.activeId.value
                    ?: return@command Failure.Refused(RefusalReason.ProfileDeleted).failed()
            when (val connected = connectNow(id)) {
                is Outcome.Failed -> return@command connected
                is Outcome.Ok -> Unit
            }
            val graph =
                registry.live.value[id]
                    ?: return@command Failure.Refused(RefusalReason.StaleScope).failed()
            when (val created = graph.session.server.createSession(NewSession())) {
                is Outcome.Failed -> created
                is Outcome.Ok -> {
                    refreshOne(id)
                    ConversationRef(id, created.value.id).ok()
                }
            }
        }

    override suspend fun remove(id: ProfileId): Outcome<Unit> = command {
        when (val queued = OfflineQueueStore(stores.prefs, stores.lanes.app).load()) {
            QueueRead.Unreadable -> return@command Failure.StorageRefused().failed()
            is QueueRead.Readable ->
                if (queued.entries.any { it.profileId == id })
                    return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
        }
        val graph = registry.live.value[id]
        if (graph != null) {
            // Composer callers can outlive graph.scope. Fence the actual delivery owner,
            // including its app-lane online-marker writes, before cancelling the graph.
            val delivery =
                (graph as? ChatGraphAccessors)?.chatDelivery?.queue
                    ?: return@command Failure.StorageRefused().failed()
            delivery.closeProfile(id)
        }
        registry.close(id)
        eventJobs.remove(id)?.cancel()
        listings.remove(id)
        loadedSources.update { it - id }
        mutableSourceFailures.update { it - id }
        rebuildFeed()
        // Queue admission is fenced above and close joins connection work. A send may have written
        // its marker
        // after the optimistic check above; inspect again only after that drain, before
        // sweeping either metadata or secrets. A refusal keeps the saved server intact.
        when (val queued = OfflineQueueStore(stores.prefs, stores.lanes.app).load()) {
            QueueRead.Unreadable -> return@command Failure.StorageRefused().failed()
            is QueueRead.Readable ->
                if (queued.entries.any { it.profileId == id })
                    return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
        }
        val journals =
            PendingCommandJournals(
                stores.prefs,
                stores.lanes,
                ProfileDirectory { profiles.profile(it) != null },
            )
        when (val journal = journals.forProfile(id)) {
            is Outcome.Failed -> return@command journal
            is Outcome.Ok ->
                when (val receipts = journal.value.read()) {
                    is Outcome.Failed -> return@command receipts
                    is Outcome.Ok ->
                        if (receipts.value.any { !it.state.terminal })
                            return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
                }
        }
        val drafts = draftOwners.singleOrNull() ?: return@command Failure.StorageRefused().failed()
        draftRecovery += id
        when (val closed = drafts.closeProfile(id)) {
            is Outcome.Failed -> return@command closed
            is Outcome.Ok -> Unit
        }
        when (
            val result =
                ProfileDeletion(
                        profiles,
                        profileScope,
                        stores.lanes,
                        closers = profileClosers,
                        cleaners = cleaners,
                    )
                    .delete(id)
        ) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                if (result.value.complete) {
                    draftRecovery.remove(id)
                    Unit.ok()
                } else
                    (result.value.failures.firstOrNull()?.failure ?: Failure.StorageRefused())
                        .failed()
        }
    }
}

@ContributesBinding(AppScope::class, replaces = [QuietConversationsFeed::class])
@Inject
class PreviewConversationsFeed(private val runtime: PreviewRuntime) : ConversationsFeed {
    override val snapshot: StateFlow<ChatFeedSnapshot> = runtime.snapshot

    override suspend fun refresh(): Outcome<Unit> = runtime.refresh()
}

@ContributesBinding(AppScope::class, replaces = [ClosedSettingsDoor::class])
@Inject
class PreviewSettingsDoor(private val navigator: AppNavigator) : SettingsDoor {
    override val available: Boolean = true

    override fun open() {
        navigator.open(ServersRoute.Home)
    }
}
