package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.domain.SessionCreationGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.*
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.engine.delivery.queue.*
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournals
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileEndpoints
import io.github.eslamasabry.oc.engine.delivery.receipts.SessionCreationReceipts
import io.github.eslamasabry.oc.engine.delivery.receipts.SessionCreationTransport
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.ui.routes.*
import java.util.UUID
import kotlin.time.Clock
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
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val draftOwners: Set<ConversationDrafts>,
    private val profileClosers: Set<ProfileCloser>,
    private val cleaners:
        Set<ProfileResourceCleaner>, // P12-FIX: the saved-prompt shelf and its files (B-SEC-172)
    private val clock: Clock,
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
    private var feed: ChatFeed? = null
    private var feedJob: Job? = null
    private val eventJobs = mutableMapOf<ProfileId, Job>()
    // Retained only for a saved profile whose deletion reached the draft fence.
    private val draftRecovery = mutableSetOf<ProfileId>()
    private val pendingCommands =
        PendingCommandJournals(
            stores.prefs,
            stores.lanes,
            ProfileDirectory { profiles.profile(it) != null },
        )

    private class CreationOwner(
        val profile: ProfileId,
        val tab: String,
        val receipts: SessionCreationReceipts,
        val gateway: SessionCreationGateway,
        val current: () -> Boolean,
    )

    // Never evict a pending owner and silently attach its command to a replacement connection.
    private val creationOwners = LinkedHashMap<String, CreationOwner>()

    init {
        scope.launch {
            val result = stores.warm()
            ready.complete(result)
            if (result is Outcome.Ok) {
                publishServers()
                val active = profiles.activeId.value ?: profiles.profiles.value.firstOrNull()?.id
                if (active != null) command { connectNow(active) }
            }
        }
        scope.launch { profiles.profiles.collect { if (ready.isCompleted) publishServers() } }
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
        connectNow(id, recoverDrafts = true)
    }

    private suspend fun connectNow(id: ProfileId, recoverDrafts: Boolean = false): Outcome<Unit> {
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
        when (val opened = registry.open(profile)) {
            is Outcome.Failed -> return opened.atStage(ConnectionStage.OpenGateway)
            is Outcome.Ok -> {
                val saved = profiles.setActive(id)
                if (saved is Outcome.Failed) return saved.atStage(ConnectionStage.SaveActiveProfile)
                val graph = opened.value
                if (!listings.containsKey(id)) {
                    listings[id] = MutableStateFlow(OpenCodeListing(loading = true))
                    rebuildFeed()
                    eventJobs[id] = scope.launch {
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
                                else -> Unit
                            }
                        }
                    }
                }
                return refreshOne(id)
            }
        }
    }

    private fun rebuildFeed() {
        feedJob?.cancel()
        feed?.close()
        val sources = listings.map { (id, listing) ->
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
                listing.value =
                    OpenCodeListing(
                        sessions = page.value.sessions,
                        statuses = (statuses as? Outcome.Ok)?.value ?: listing.value.statuses,
                        // B1: more pages on the server is not "couldn't load".
                        complete = page.value.complete,
                        failed = statuses is Outcome.Failed,
                        acrossProjects =
                            graph.session.server.capabilities.value.globalSessionSearch,
                    )
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
        createConversation(profile, UUID.randomUUID().toString(), UUID.randomUUID().toString())

    override suspend fun createConversation(
        profile: ProfileId?,
        commandId: String,
        tabId: String,
    ): Outcome<ConversationRef> = command {
        if (
            !Regex("^[A-Za-z0-9_:-]{1,128}$").matches(commandId) ||
                !Regex("^[A-Za-z0-9_:-]{1,256}$").matches(tabId)
        )
            return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
        val id =
            profile
                ?: profiles.activeId.value
                ?: return@command Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val previousOwner = creationOwners[commandId]
        if (
            previousOwner != null &&
                (previousOwner.profile != id ||
                    previousOwner.tab != tabId ||
                    !previousOwner.current())
        )
            return@command Failure.Refused(RefusalReason.StaleScope).failed()
        when (val connected = connectNow(id)) {
            is Outcome.Failed -> return@command connected
            is Outcome.Ok -> Unit
        }
        val graph =
            registry.live.value[id]
                ?: return@command Failure.Refused(RefusalReason.StaleScope).failed()
        val gateway = graph.session.server
        val target = gateway.facet<SessionCreationGateway>()
        if (target == null) {
            // An ordinary backend without client-selected ids has no receipt owner.
            if (previousOwner != null)
                return@command Failure.Refused(RefusalReason.StaleScope).failed()
            return@command when (val created = gateway.createSession(NewSession())) {
                is Outcome.Failed -> created
                is Outcome.Ok -> {
                    if (
                        registry.live.value[id] !== graph ||
                            graph.scope.coroutineContext[Job]?.isActive != true
                    )
                        Failure.Refused(RefusalReason.StaleScope).failed()
                    else {
                        refreshOne(id)
                        if (
                            registry.live.value[id] === graph &&
                                graph.scope.coroutineContext[Job]?.isActive == true
                        )
                            ConversationRef(id, created.value.id).ok()
                        else Failure.Refused(RefusalReason.StaleScope).failed()
                    }
                }
            }
        }
        val owner =
            previousOwner
                ?: run {
                    if (creationOwners.size >= 256)
                        return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
                    val at = gateway.location.value
                    val expectedEndpoint = graph.profile.origin.toString()
                    fun current(): Boolean =
                        registry.live.value[id] === graph &&
                            graph.scope.coroutineContext[Job]?.isActive == true &&
                            gateway.location.value == at &&
                            profiles.profile(id)?.profile?.baseUrl?.let {
                                SessionCreationReceipts.normalizeRoot(it) ==
                                    SessionCreationReceipts.normalizeRoot(expectedEndpoint)
                            } == true
                    val transport =
                        object : SessionCreationTransport {
                            override val endpoint: String = expectedEndpoint
                            override val directory: String?
                                get() = gateway.location.value.directory

                            override val workspace: String?
                                get() = gateway.location.value.workspace

                            override val closed: Boolean
                                get() = !current()

                            override suspend fun createSession(id: SessionId): Outcome<SessionId> {
                                if (!current())
                                    return Failure.Refused(RefusalReason.StaleScope).failed()
                                val answer = target.createSessionWithId(id, at)
                                return if (current()) answer
                                else Failure.Refused(RefusalReason.StaleScope).failed()
                            }

                            override suspend fun lookupSession(id: SessionId): Outcome<Boolean> {
                                if (!current())
                                    return Failure.Refused(RefusalReason.StaleScope).failed()
                                val answer = target.lookupCreatedSession(id)
                                return if (current()) answer
                                else Failure.Refused(RefusalReason.StaleScope).failed()
                            }
                        }
                    val receipts =
                        when (
                            val opened =
                                SessionCreationReceipts.open(
                                    id,
                                    ProfileEndpoints { profiles.profile(it)?.profile?.baseUrl },
                                    transport,
                                    pendingCommands,
                                    clock,
                                    { target.newSessionCreationId().raw },
                                )
                        ) {
                            is Outcome.Failed -> return@command opened
                            is Outcome.Ok -> opened.value
                        }
                    CreationOwner(id, tabId, receipts, target, ::current).also {
                        creationOwners[commandId] = it
                    }
                }
        if (!owner.current()) return@command Failure.Refused(RefusalReason.StaleScope).failed()
        // send() claims once durably. Every later invocation returns that receipt without POST.
        when (val sent = owner.receipts.send(commandId, tabId)) {
            is Outcome.Failed -> return@command sent
            is Outcome.Ok -> Unit
        }
        val session =
            owner.receipts.check(commandId, tabId)
                ?: return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
        if (!owner.current()) return@command Failure.Refused(RefusalReason.StaleScope).failed()
        // A confirmed tombstone still cannot navigate to a session that has since disappeared.
        val exists = owner.gateway.lookupCreatedSession(session)
        if (!owner.current()) return@command Failure.Refused(RefusalReason.StaleScope).failed()
        if (exists !is Outcome.Ok || !exists.value)
            return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
        refreshOne(id)
        if (!owner.current()) Failure.Refused(RefusalReason.StaleScope).failed()
        else {
            // Completed identities remain in the journal; only pending commands retain a graph.
            creationOwners.remove(commandId)
            ConversationRef(id, session).ok()
        }
    }

    override fun currentLocation(
        profile: ProfileId
    ): io.github.eslamasabry.oc.core.model.ServerLocation? =
        registry.live.value[profile]?.session?.server?.location?.value

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
        when (val journal = pendingCommands.forProfile(id)) {
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
        val scoped =
            DefaultProfileScope(stores.prefs, stores.secrets, stores.lanes.app).apply {
                registerSecret("pw.", "profiles")
                registerSecret("oc.codexToken.", "profiles")
                registerSecret("oc.agentHostSecret.", ":engine:agents")
                io.github.eslamasabry.oc.engine.integrations.quota.CollectorRules.register(this)
                io.github.eslamasabry.oc.engine.integrations.quota.InAppQuotaCollector.register(
                    this
                )
                for (prefix in
                    listOf(
                        "oc.notifyRules.",
                        "oc.monitorRoutes.",
                        "oc.monitorBusy.",
                        "oc.widgetTarget.",
                    )) {
                    register(
                        io.github.eslamasabry.oc.core.data.scope.KeyShape(
                            prefix,
                            io.github.eslamasabry.oc.core.data.scope.KeyScope.Profile,
                            io.github.eslamasabry.oc.core.data.kv.PrefType.Str,
                            ":engine:attention",
                            "monitor and private widget routing metadata",
                        )
                    )
                }
                register(
                    io.github.eslamasabry.oc.core.data.scope.KeyShape(
                        "oc.monitorAlerts.",
                        io.github.eslamasabry.oc.core.data.scope.KeyScope.Profile,
                        io.github.eslamasabry.oc.core.data.kv.PrefType.StrList,
                        ":engine:attention",
                        "recent per-server monitor alert ids",
                    )
                )
                register(
                    io.github.eslamasabry.oc.core.data.scope.KeyShape(
                        "oc.monitorConsent.",
                        io.github.eslamasabry.oc.core.data.scope.KeyScope.Profile,
                        io.github.eslamasabry.oc.core.data.kv.PrefType.Bool,
                        ":engine:attention",
                        "explicit per-server foreground monitor consent",
                    )
                )
                register(
                    io.github.eslamasabry.oc.core.data.scope.KeyShape(
                        "oc.pendingCommands.",
                        io.github.eslamasabry.oc.core.data.scope.KeyScope.Profile,
                        io.github.eslamasabry.oc.core.data.kv.PrefType.Str,
                        ":engine:delivery",
                        "durable session creation and prompt admission receipts",
                    )
                )
                register(
                    io.github.eslamasabry.oc.core.data.scope.KeyShape(
                        "oc.agentHandoffs.",
                        io.github.eslamasabry.oc.core.data.scope.KeyScope.Profile,
                        io.github.eslamasabry.oc.core.data.kv.PrefType.Str,
                        ":engine:agents",
                        "durable native agent handoff links",
                    )
                )
                register(
                    io.github.eslamasabry.oc.core.data.scope.KeyShape(
                        "oc.setupRegistry.",
                        io.github.eslamasabry.oc.core.data.scope.KeyScope.Profile,
                        io.github.eslamasabry.oc.core.data.kv.PrefType.Str,
                        ":engine:integrations-catalog",
                        "public connector catalogue consent and sanitized listings",
                    )
                )
            }
        when (
            val result =
                ProfileDeletion(
                        profiles,
                        scoped,
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
