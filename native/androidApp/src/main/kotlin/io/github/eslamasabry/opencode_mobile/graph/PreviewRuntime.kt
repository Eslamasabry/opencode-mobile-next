package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.domain.SessionCreationGateway
import io.github.eslamasabry.oc.core.domain.SessionReadStateGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.*
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Saved server -> live graph -> conversations. No production Flutter adoption is performed here.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerConnections>())
@Inject
class PreviewRuntime(
    private val application: android.app.Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val profileScope: ProfileScope,
    private val sessionReads: NativeSessionReads,
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val draftOwners: Set<ConversationDrafts>,
    private val runtimeOwner: RuntimeProfileOwner,
    private val cleaners:
        Set<ProfileResourceCleaner>, // P12-FIX: the saved-prompt shelf and its files (B-SEC-172)
    private val deliveryStores: Set<DeliveryStorage>,
    private val profileClosers: Set<ProfileCloser>,
    private val clock: Clock,
    // 40-port-screens-3: new conversations use the server's saved model/variant defaults.
    private val defaults: NativeServerDefaults,
    // job-47: the root-owned managed upgrade admits a profile before any gateway opens.
    private val managedUpgrade: RootManagedUpgrade,
    // job-47: Flutter-compatible opening labels (also read by Paseo's no-restamp idle guard).
    private val sessionInventory: NativeSessionInventory,
) : ServerConnections, ServerDisconnects {
    val profiles: ProfileStore = stores.profiles
    private val lane = dispatchers.serial("preview-runtime")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val order = Mutex()
    private val ready = CompletableDeferred<Outcome<Unit>>()
    private val initialConnection = CompletableDeferred<Outcome<Unit>>()
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
    private val refreshJobs = mutableMapOf<ProfileId, Job>()
    private val refreshRevision = mutableMapOf<ProfileId, Long>()
    private val requestRevision = mutableMapOf<ProfileId, Long>()
    private val runRevision = mutableMapOf<ProfileId, Long>()
    private val failedRuns = mutableMapOf<ProfileId, MutableSet<SessionId>>()
    private val sessionSignals = mutableMapOf<ProfileId, MutableMap<SessionId, Long>>()
    private val runSignals = mutableMapOf<ProfileId, MutableMap<SessionId, Long>>()
    private val requests = mutableMapOf<ProfileId, MutableMap<Pair<String, String>, SessionId>>()
    // The exact graph each listing follows; a replaced or closed graph detaches its listing.
    private val listingGraphs = mutableMapOf<ProfileId, ConnectionGraph>()
    // Retained only for a saved profile whose deletion reached the draft fence.
    private val draftRecovery = mutableSetOf<ProfileId>()
    private val restoreRequested = AtomicBoolean(false)
    // AGENT-2's session-creation receipts share the process owner of the pending-command journals
    // (DeliveryStorage), so creation, delivery and deletion see one journal per profile.
    private val pendingCommands: PendingCommandJournals =
        deliveryStores.singleOrNull()?.journals
            ?: PendingCommandJournals(
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
        managedUpgrade.reconnect = { connect(it) }
        scope.launch {
            val result = stores.warm()
            ready.complete(result)
            if (result is Outcome.Ok) {
                publishServers()
            }
        }
        scope.launch { profiles.profiles.collect { if (ready.isCompleted) publishServers() } }
        scope.launch {
            if (ready.await() is Outcome.Failed) return@launch
            combine(sessionReads.revision, profiles.activeId) { _, active -> active }
                .collect { active ->
                    order.withLock {
                        if (active == null || registry.live.value[active] == null)
                            sessionReads.clearBrief()
                        for ((id, listing) in listings) publishReadState(id, listing)
                    }
                }
        }
        scope.launch {
            if (ready.await() is Outcome.Failed) return@launch
            registry.live.collect {
                order.withLock {
                    val live = registry.live.value
                    val gone = listings.keys.filter { listingGraphs[it] !== live[it] }
                    gone.forEach(::detachListing)
                    if (gone.isNotEmpty()) rebuildFeed()
                    publishServers()
                }
            }
        }
    }

    /** Drops every per-connection record of [id]'s listing; the saved server stays. */
    private fun detachListing(id: ProfileId) {
        eventJobs.remove(id)?.cancel()
        refreshJobs.remove(id)?.cancel()
        refreshRevision.remove(id)
        requestRevision.remove(id)
        runRevision.remove(id)
        runSignals.remove(id)
        sessionSignals.remove(id)
        failedRuns.remove(id)
        requests.remove(id)
        listings.remove(id)
        listingGraphs.remove(id)
        loadedSources.update { it - id }
    }

    /**
     * Only a visible app launch may restore the ordinary connection. Cold OS actions validate their
     * target first.
     */
    fun restoreSavedConnection() {
        if (!restoreRequested.compareAndSet(false, true)) return
        scope.launch {
            val warmed = ready.await()
            if (warmed is Outcome.Failed) {
                initialConnection.complete(warmed)
                return@launch
            }
            val active = profiles.activeId.value ?: profiles.profiles.value.firstOrNull()?.id
            // PORT-SCREENS-2: the home list records each source's failure, then the other shown
            // sources join.
            // job-46: pinned shortcuts wait for this restore (awaitInitialConnection).
            initialConnection.complete(
                if (active != null) command { connectForHome(active) } else Unit.ok()
            )
            synchronizeListSources()
        }
    }

    /** Launcher shortcuts wait for the existing startup connection; they never start or switch a server. */
    internal suspend fun awaitInitialConnection(): Outcome<Unit> = initialConnection.await()

    private fun publishServers() {
        mutableServers.value =
            profiles.profiles.value
                .map {
                    SavedServer(
                        it.id,
                        it.profile.name,
                        it.profile.baseUrl,
                        registry.live.value[it.id]?.session?.status?.value ==
                            StreamStatus.Connected,
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

    /**
     * Runtime migration retires only local transports; saved rows, secrets and queues stay intact.
     */
    internal suspend fun disconnectForRuntimeSwitch(ids: Set<ProfileId>): Outcome<Unit> = command {
        for (id in ids) {
            registry.close(id)
            detachListing(id)
        }
        rebuildFeed()
        Unit.ok()
    }

    private suspend fun connectNow(
        id: ProfileId,
        recoverDrafts: Boolean = false,
        makeActive: Boolean = true,
    ): Outcome<Unit> {
        when (val admitted = managedUpgrade.prepare(id)) {
            is Outcome.Failed -> return admitted
            is Outcome.Ok -> Unit
        }
        val loaded =
            profiles.profile(id)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted)
                    .failed()
                    .atStage(ConnectionStage.ProfileRead)
        if (loaded.profile.requiresOpenCode2Migration) {
            return Failure.Incompatible(IncompatibleKind.LegacyOpenCode1)
                .failed()
                .atStage(ConnectionStage.ProfileRead)
        }
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
        when (val claimed = runtimeOwner.claim(id, profile.origin)) {
            is Outcome.Failed -> return claimed.atStage(ConnectionStage.ProfileRead)
            is Outcome.Ok -> Unit
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
            is Outcome.Failed -> {
                if (
                    (opened.failure as? Failure.Incompatible)?.kind ==
                        IncompatibleKind.LegacyOpenCode1 &&
                        io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
                            .isManagedLocal(
                                io.github.eslamasabry.oc.engine.runtime.switching.LocalProfile(
                                    id.raw,
                                    loaded.profile.name,
                                    loaded.profile.baseUrl,
                                    if (loaded.profile.usesAgentSocket)
                                        io.github.eslamasabry.oc.engine.runtime.switching
                                            .ServerBackendKind
                                            .Other
                                    else
                                        io.github.eslamasabry.oc.engine.runtime.switching
                                            .ServerBackendKind
                                            .OpenCode,
                                )
                            )
                ) {
                    when (
                        val saved =
                            profiles.upsert(loaded.profile.copy(requiresOpenCode2Migration = true))
                    ) {
                        is Outcome.Failed -> return saved.atStage(ConnectionStage.SaveActiveProfile)
                        is Outcome.Ok -> Unit
                    }
                }
                return opened.atStage(ConnectionStage.OpenGateway)
            }
            is Outcome.Ok -> {
                val saved = if (makeActive) profiles.setActive(id) else Unit.ok()
                if (saved is Outcome.Failed) return saved.atStage(ConnectionStage.SaveActiveProfile)
                val graph = opened.value
                if (listingGraphs[id] !== graph) detachListing(id)
                if (!listings.containsKey(id)) {
                    listingGraphs[id] = graph
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
                        launch {
                            graph.session.reads.missing.collect { missing ->
                                order.withLock {
                                    if (registry.live.value[id] === graph) {
                                        listings[id]?.let { listing ->
                                            listing.value =
                                                listing.value.copy(
                                                    sessions =
                                                        listing.value.sessions
                                                            .filterNot { it.id in missing }
                                                            .toImmutableList()
                                                )
                                        }
                                    }
                                }
                            }
                        }
                        launch { graph.session.status.collect { publishServers() } }
                        graph.session.server.events.all.collect { envelope ->
                            val event = envelope.event
                            if (event is GatewayEvent.SessionDeleted)
                                graph.session.reads.forget(event.sessionId)
                            listings[id]?.let { listing ->
                                val old = listing.value
                                when (event) {
                                    GatewayEvent.Connected ->
                                        listing.value = old.copy(running = emptyMap())
                                    is GatewayEvent.RunStarted ->
                                        listing.value =
                                            old.copy(
                                                running =
                                                    old.running +
                                                        (event.sessionId to
                                                            FeedRunning(
                                                                clock.now().toEpochMilliseconds()
                                                            ))
                                            )
                                    is GatewayEvent.RunEnded ->
                                        listing.value =
                                            old.copy(running = old.running - event.sessionId)
                                    is GatewayEvent.PartUpserted -> {
                                        val part = event.part
                                        val tool = part.tool
                                        if (tool != null) {
                                            val sid = SessionId(part.sessionId)
                                            val previous = old.running[sid]
                                            val active = tool.status == ToolStatus.Running
                                            val command =
                                                if (
                                                    active &&
                                                        part.toolName in setOf("bash", "shell")
                                                )
                                                    (tool.input["command"] as? JsonPrimitive)
                                                        ?.contentOrNull
                                                        ?.lineSequence()
                                                        ?.firstOrNull()
                                                        ?.trim()
                                                        ?.takeIf { it.isNotEmpty() }
                                                else null
                                            if (active || previous != null)
                                                listing.value =
                                                    old.copy(
                                                        running =
                                                            old.running +
                                                                (sid to
                                                                    FeedRunning(
                                                                        tool.startedAtMs
                                                                            ?: previous?.sinceMs
                                                                            ?: clock
                                                                                .now()
                                                                                .toEpochMilliseconds(),
                                                                        command?.let(
                                                                            redactor::redact
                                                                        ),
                                                                    ))
                                                    )
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                            if (registry.live.value[id] !== graph) return@collect
                            if (observeFeedEvent(id, envelope.event)) scheduleRefresh(id)
                        }
                    }
                }
                if (makeActive) rebuildFeed()
                return refreshOne(id)
            }
        }
    }

    /** A first-event deadline: busy streams cannot postpone reconciliation indefinitely. */
    private fun scheduleRefresh(id: ProfileId) {
        refreshRevision[id] = (refreshRevision[id] ?: 0) + 1
        if (refreshJobs[id]?.isActive == true) return
        refreshJobs[id] = scope.launch {
            try {
                do {
                    delay(2_000)
                    val revision = refreshRevision[id]
                    order.withLock { if (listings.containsKey(id)) refreshOne(id) }
                } while (listings.containsKey(id) && refreshRevision[id] != revision)
            } finally {
                refreshJobs.remove(id)
            }
        }
    }

    /**
     * Live evidence outranks reads which left before it, including run ends during slow inventory
     * reads.
     */
    private fun observeFeedEvent(id: ProfileId, event: GatewayEvent): Boolean {
        val listing = listings[id] ?: return false
        val current = listing.value
        fun status(session: SessionId, status: SessionStatus, failed: Boolean? = null) {
            val next = (runRevision[id] ?: 0) + 1
            runRevision[id] = next
            runSignals.getOrPut(id) { mutableMapOf() }[session] = next
            if (failed == true) failedRuns.getOrPut(id) { mutableSetOf() }.add(session)
            if (failed == false) failedRuns[id]?.remove(session)
            listing.value =
                listing.value.copy(
                    statuses = listing.value.statuses + (session to status),
                    sessions =
                        if (failed == null) listing.value.sessions
                        else
                            listing.value.sessions.map {
                                if (it.id == session) it.copy(lastRunFailed = failed) else it
                            },
                )
        }
        fun request(kind: String, request: String, session: SessionId?) {
            requestRevision[id] = (requestRevision[id] ?: 0) + 1
            val pending = requests.getOrPut(id) { mutableMapOf() }
            if (session == null) pending.remove(kind to request)
            else pending[kind to request] = session
            listing.value = listing.value.copy(needsYou = pending.values.toSet())
        }
        val sessionEvent =
            when (event) {
                is GatewayEvent.SessionDeleted -> event.sessionId
                is GatewayEvent.SessionUpserted -> event.session.id
                is GatewayEvent.SessionChanged -> event.sessionId
                else -> null
            }
        sessionEvent?.let {
            sessionSignals.getOrPut(id) { mutableMapOf() }[it] = (refreshRevision[id] ?: 0) + 1
        }
        when (event) {
            is GatewayEvent.RunStarted -> status(event.sessionId, SessionStatus.Busy, false)
            is GatewayEvent.RunRetrying -> status(event.sessionId, SessionStatus.Retry, false)
            is GatewayEvent.RunEnded ->
                status(
                    event.sessionId,
                    SessionStatus.Idle,
                    when (event.outcome) {
                        is RunOutcome.Failed -> true
                        RunOutcome.Idle -> null
                        else -> false
                    },
                )
            is GatewayEvent.PermissionAsked ->
                request("permission", event.request.id.raw, event.request.sessionId)
            is GatewayEvent.PermissionSettled -> request("permission", event.requestId.raw, null)
            is GatewayEvent.FormCreated -> request("form", event.form.id.raw, event.form.sessionId)
            is GatewayEvent.FormSettled -> request("form", event.formId.raw, null)
            is GatewayEvent.SessionDeleted -> {
                runSignals[id]?.remove(event.sessionId)
                failedRuns[id]?.remove(event.sessionId)
                requests[id]?.entries?.removeAll { it.value == event.sessionId }
                listing.value =
                    current.copy(
                        sessions = current.sessions.filterNot { it.id == event.sessionId },
                        statuses = current.statuses - event.sessionId,
                        needsYou = requests[id].orEmpty().values.toSet(),
                    )
            }
            is GatewayEvent.SessionUpserted ->
                listing.value =
                    current.copy(
                        sessions =
                            current.sessions.filterNot { it.id == event.session.id } +
                                event.session.copy(
                                    lastRunFailed =
                                        event.session.lastRunFailed ||
                                            event.session.id in failedRuns[id].orEmpty()
                                )
                    )
            is GatewayEvent.SessionChanged ->
                listing.value =
                    current.copy(
                        sessions =
                            current.sessions.map {
                                if (it.id != event.sessionId) it
                                else
                                    it.copy(
                                        title = event.title ?: it.title,
                                        directory =
                                            if (event.replacesLocation) event.directory
                                            else it.directory,
                                    )
                            }
                    )
            GatewayEvent.Connected,
            is GatewayEvent.SessionViewed,
            is GatewayEvent.RefreshHint -> Unit
            else -> return false
        }
        return true
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
                    locationKey =
                        registry.live.value[id]?.let {
                            sessionReads.locationKey(it.profile, it.session.server.location.value)
                        },
                    refresh = { refreshOne(id) },
                )
            }
        val merged = ChatFeed(sources, scope)
        feed = merged
        snapshot.value = merged.snapshot.value
        feedJob = scope.launch { merged.snapshot.collect { snapshot.value = it } }
    }

    private suspend fun publishReadState(
        id: ProfileId,
        listing: MutableStateFlow<OpenCodeListing>,
    ) {
        val graph = registry.live.value[id] ?: return
        val location = graph.session.server.location.value
        val current = listing.value
        val busy =
            current.statuses
                .filterValues { it == SessionStatus.Busy || it == SessionStatus.Retry }
                .keys
        val known = graph.session.server is SessionReadStateGateway
        val finished =
            if (known)
                current.sessions
                    .filter {
                        sessionReads.unread(graph.profile, location, it, it.id in busy)
                    }
                    .mapTo(mutableSetOf()) { it.id }
            else emptySet()
        listing.value = current.copy(finishedUnseen = finished)
        if (profiles.activeId.value != id) return
        val blockers = mutableMapOf<SessionId, Pair<BriefBlocker, String>>()
        val rank =
            mapOf(
                "permission" to BriefBlocker.Permission,
                "question" to BriefBlocker.Question,
                "form" to BriefBlocker.Form,
            )
        requests[id]
            .orEmpty()
            .entries
            .sortedBy { rank[it.key.first]?.ordinal ?: Int.MAX_VALUE }
            .forEach { (key, session) ->
                rank[key.first]?.let { blockers.putIfAbsent(session, it to key.second) }
            }
        sessionReads.publishBrief(
            graph.profile,
            location,
            current.sessions,
            busy,
            blockers,
            inventoryPartial = !current.complete || current.failed,
            readStateKnown = known,
        )
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
        val readEventRevision = refreshRevision[id] ?: 0
        val readRunRevision = runRevision[id] ?: 0
        val readRequestRevision = requestRevision[id] ?: 0
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
                val stored = profiles.profile(id)?.profile
                if (stored != null)
                    sessionInventory.save(stored, page.value.sessions) {
                        registry.live.value[id] === graph
                    }
                val statuses = graph.session.server.sessionStatuses()
                val caps = graph.session.server.capabilities.value
                val permissions =
                    if (caps.permissionRequests) graph.session.server.pendingPermissions() else null
                val questions =
                    if (caps.legacyQuestionRequests) graph.session.server.pendingQuestions()
                    else null
                val forms = if (caps.forms) graph.session.server.pendingForms() else null
                if (registry.live.value[id] !== graph)
                    return Failure.Refused(RefusalReason.StaleScope).failed()
                val pending = requests.getOrPut(id) { mutableMapOf() }
                if ((requestRevision[id] ?: 0) == readRequestRevision) {
                    fun replace(kind: String, entries: List<Pair<String, SessionId>>?) {
                        if (entries == null) return // Unknown retains the last known asks.
                        pending.keys.removeAll { it.first == kind }
                        entries.forEach { (request, session) -> pending[kind to request] = session }
                    }
                    replace(
                        "permission",
                        (permissions as? Outcome.Ok)?.value?.map { it.id.raw to it.sessionId },
                    )
                    replace(
                        "question",
                        (questions as? Outcome.Ok)?.value?.map { it.id.raw to it.sessionId },
                    )
                    replace(
                        "form",
                        (forms as? Outcome.Ok)?.value?.map { it.id.raw to it.sessionId },
                    )
                }
                val resolvedStatuses =
                    ((statuses as? Outcome.Ok)?.value ?: listing.value.statuses).toMutableMap()
                val newer = runSignals[id].orEmpty().filterValues { it > readRunRevision }.keys
                for (session in newer) listing.value.statuses[session]?.let {
                    resolvedStatuses[session] = it
                }
                val liveSessions = listing.value.sessions.associateBy { it.id }
                val changedSessions =
                    sessionSignals[id].orEmpty().filterValues { it > readEventRevision }.keys
                val inventory =
                    page.value.sessions.filterNot { it.id in changedSessions } +
                        changedSessions.mapNotNull { liveSessions[it] }
                val sessions = inventory.map {
                    if (it.id in failedRuns[id].orEmpty()) it.copy(lastRunFailed = true)
                    else if (it.id in newer)
                        it.copy(
                            lastRunFailed = liveSessions[it.id]?.lastRunFailed ?: it.lastRunFailed
                        )
                    else it
                }
                runSignals[id]?.entries?.removeAll { it.value <= readRunRevision }
                sessionSignals[id]?.entries?.removeAll { it.value <= readEventRevision }
                page.value.sessions.forEach(graph.session.readState::observe)
                listing.value =
                    OpenCodeListing(
                        sessions =
                            sessions
                                .filterNot { it.id in graph.session.reads.missing.value }
                                .toImmutableList(),
                        statuses = resolvedStatuses,
                        running = listing.value.running,
                        permissions =
                            if (permissions is Outcome.Ok)
                                permissions.value
                                    .groupBy { it.sessionId }
                                    .mapNotNull { (sid, asks) ->
                                        asks.firstOrNull()?.let { ask ->
                                            val command =
                                                (ask.metadata["command"] as? JsonPrimitive)
                                                    ?.contentOrNull
                                                    ?: ask.patterns.firstOrNull()
                                                    ?: ask.permission
                                            sid to
                                                FeedPermission(
                                                    ask.id.raw,
                                                    redactor.redact(
                                                        command
                                                            .lineSequence()
                                                            .firstOrNull()
                                                            .orEmpty()
                                                    ),
                                                )
                                        }
                                    }
                                    .toMap()
                            else listing.value.permissions,
                        needsYou = pending.values.toSet(),
                        finishedUnseen =
                            graph.session.readState.state.value
                                .filterValues { it.finishedUnseen }
                                .keys,
                        // B1: more pages on the server is not "couldn't load".
                        complete = page.value.complete,
                        failed =
                            statuses is Outcome.Failed ||
                                permissions is Outcome.Failed ||
                                questions is Outcome.Failed ||
                                forms is Outcome.Failed,
                        acrossProjects =
                            graph.session.server.capabilities.value.globalSessionSearch,
                    )
                loadedSources.update { it + id }
                publishReadState(id, listing)
                // Unknown optional asks affect the feed's completeness, never healthy
                // connect/create admission.
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

    internal fun prefetch(ref: ConversationRef) {
        if (profiles.profile(ref.profileId) == null) return
        registry.live.value[ref.profileId]?.session?.reads?.prefetch(ref.sessionId)
    }

    override suspend fun disconnect(id: ProfileId): Outcome<Unit> = command {
        val graph = registry.live.value[id]
        if (graph != null) {
            // Fence the graph's delivery owner (its app-lane online-marker writes) before closing.
            val delivery =
                (graph as? ChatGraphAccessors)?.chatDelivery?.queue
                    ?: return@command Failure.StorageRefused().failed()
            delivery.closeProfile(id)
        }
        registry.close(id)
        detachListing(id)
        rebuildFeed()
        application.platformHub.onExplicitDisconnect(id)
        // Flutter disconnect(keepActive: true): the server stays the chosen one, just offline.
        Unit.ok()
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
            return@command when (val created = defaults.create(graph)) {
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

    override suspend fun remove(id: ProfileId): Outcome<Unit> =
        removeConfirmed(id, DeletionOptions())

    /**
     * [options] carries the person's confirmed queued-prompt plan (ServerRemovalWiring): a queue
     * that changed since the confirmation refuses; queued prompts without one still block removal.
     */
    internal suspend fun removeConfirmed(id: ProfileId, options: DeletionOptions): Outcome<Unit> {
        fun confirmedQueue(): Outcome<Unit> {
            val queued = OfflineQueueStore(stores.prefs, stores.lanes.app).load()
            if (queued !is QueueRead.Readable) return Failure.StorageRefused().failed()
            val actual =
                QueuedPromptRemovalPlan.fromSnapshot(
                    id,
                    (stores.prefs.get(OfflineQueueStore.KEY)
                            as? io.github.eslamasabry.oc.core.data.kv.PrefValue.Str)
                        ?.value,
                )
            if (actual is Outcome.Failed) return actual
            if (
                options.confirmedQueue != null &&
                    options.confirmedQueue != (actual as Outcome.Ok).value
            )
                return Failure.Refused(RefusalReason.StaleScope).failed()
            if (queued.entries.any { it.profileId == id } && options.confirmedQueue == null)
                return Failure.Refused(RefusalReason.DispatchUncertain).failed()
            return Unit.ok()
        }
        val result = command {
            when (val queue = confirmedQueue()) {
                is Outcome.Failed -> return@command queue
                is Outcome.Ok -> Unit
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
            detachListing(id)
            mutableSourceFailures.update { it - id }
            rebuildFeed()
            // Queue admission is fenced above and close joins connection work. A send may have
            // written
            // its marker
            // after the optimistic check above; inspect again only after that drain, before
            // sweeping either metadata or secrets. A refusal keeps the saved server intact.
            when (val queue = confirmedQueue()) {
                is Outcome.Failed -> return@command queue
                is Outcome.Ok -> Unit
            }
            val journals =
                deliveryStores.singleOrNull()?.journals
                    ?: return@command Failure.StorageRefused().failed()
            journals.closeProfile(id)
            when (val receipts = journals.deletionSnapshot(id)) {
                is Outcome.Failed -> return@command receipts
                is Outcome.Ok ->
                    if (receipts.value.any { !it.state.terminal })
                        return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
            }
            val drafts =
                draftOwners.singleOrNull() ?: return@command Failure.StorageRefused().failed()
            draftRecovery += id
            when (val closed = drafts.closeProfile(id)) {
                is Outcome.Failed -> return@command closed
                is Outcome.Ok -> Unit
            }
            // The shared ProfileScope registers every unit's keys (STATE's among them) and the
            // secrets.
            when (
                val result =
                    ProfileDeletion(
                            profiles,
                            profileScope,
                            stores.lanes,
                            closers = profileClosers,
                            cleaners = cleaners,
                        )
                        .delete(id, options)
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
        if (result is Outcome.Failed && profiles.profile(id) != null) {
            deliveryStores.singleOrNull()?.journals?.cancelDeletion(id)
        }
        return result
    }
}

@ContributesBinding(AppScope::class, replaces = [QuietConversationsFeed::class])
@Inject
class PreviewConversationsFeed(private val runtime: PreviewRuntime) : ConversationsFeed {
    override val snapshot: StateFlow<ChatFeedSnapshot> = runtime.snapshot

    override suspend fun refresh(): Outcome<Unit> = runtime.refresh()

    override fun prefetch(ref: ConversationRef) = runtime.prefetch(ref)
}

@ContributesBinding(AppScope::class, replaces = [ClosedSettingsDoor::class])
@Inject
class PreviewSettingsDoor(private val navigator: AppNavigator) : SettingsDoor {
    override val available: Boolean = true

    override fun open() {
        navigator.open(ServersRoute.Home)
    }
}
