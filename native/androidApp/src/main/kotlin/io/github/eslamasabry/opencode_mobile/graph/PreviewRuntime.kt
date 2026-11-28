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
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionRunOutcome
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.*
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffLinks
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

/** Window after a busy session ends in which a phone-server Stop still claims it as cut. */
private const val RECENT_CUT_MS = 10_000L

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
    private val inventory: NativeSessionInventory,
    private val cleaners:
        Set<ProfileResourceCleaner>, // P12-FIX: the saved-prompt shelf and its files (B-SEC-172)
    private val deliveryStores: Set<DeliveryStorage>,
    private val profileClosers: Set<ProfileCloser>,
    private val clock: Clock,
    private val localGates: LocalServerGates,
    // Lazy: the helper's starter connects through this class, so it cannot be built before it.
    private val phoneHelper: Lazy<AppLocalAgentHostStarter>,
    /** The app coming to the front wakes the helper's start ([wantHelper]): Android admits a start only then. */
    private val visibility: AppVisibility,
    /** The one stage source: this class tells it which helper start loops run and which sources connect. */
    private val stages: LaunchStages,
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
    // Busy is a command holding the lock, or an attempt waiting for this phone's own server outside it.
    private val busyLock = Any()
    private var holding = false
    private val localWaits =
        io.github.eslamasabry.oc.engine.runtime.server.LocalServerWaits(
            scope,
            gate = { id ->
                localGates.gate?.invoke(id) ?: io.github.eslamasabry.oc.engine.runtime.server.LocalServerWait.Ready
            },
            onWaitingChanged = ::publishBusy,
        )
    internal val snapshot = MutableStateFlow(ChatFeedSnapshot())
    private val unverifiedListings = mutableSetOf<ProfileId>()

    /**
     * Sources the app itself starts (This phone's own server, its agents' helper) whose saved rows stand in the list
     * while they start, before any connection exists: unverified (a tap is kept) and replaced in place when the source
     * connects. Only ever touched inside [command].
     */
    private val seededListings = mutableSetOf<ProfileId>()
    private val listings = mutableMapOf<ProfileId, MutableStateFlow<OpenCodeListing>>()
    private val mutableSourceFailures = MutableStateFlow<Map<ProfileId, Failure>>(emptyMap())
    internal val sourceFailureState: StateFlow<Map<ProfileId, Failure>> =
        mutableSourceFailures.asStateFlow()
    internal val sourceFailures: Map<ProfileId, Failure>
        get() = mutableSourceFailures.value

    private val loadedSources = MutableStateFlow<Set<ProfileId>>(emptySet())

    /** True once any server's list has been read in this run of the app; see [publish]. */
    @Volatile private var anySourceLoaded = false
    private var feed: ChatFeed? = null
    private var feedJob: Job? = null
    private val eventJobs = mutableMapOf<ProfileId, Job>()
    private val refreshJobs = mutableMapOf<ProfileId, Job>()
    private val refreshRevision = mutableMapOf<ProfileId, Long>()
    private val requestRevision = mutableMapOf<ProfileId, Long>()
    private val runRevision = mutableMapOf<ProfileId, Long>()
    private val failedRuns = mutableMapOf<ProfileId, MutableSet<SessionId>>()
    private val recentlyEnded = mutableMapOf<ProfileId, MutableMap<SessionId, Long>>()
    private val sessionSignals = mutableMapOf<ProfileId, MutableMap<SessionId, Long>>()
    private val runSignals = mutableMapOf<ProfileId, MutableMap<SessionId, Long>>()
    private val requests = mutableMapOf<ProfileId, MutableMap<Pair<String, String>, SessionId>>()
    // Retained only for a saved profile whose deletion reached the draft fence.
    private val draftRecovery = mutableSetOf<ProfileId>()
    private val restoreRequested = AtomicBoolean(false)
    /** B-486: set once an activity resumed in this run; the phone's Linux admits a helper's start only after that. */
    private val activityResumedOnce = AtomicBoolean(false)
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
        scope.launch {
            val result = stores.warm()
            ready.complete(result)
            if (result is Outcome.Ok) {
                publishServers()
            } else {
                initialConnection.complete(result)
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
    }

    /**
     * Only a visible app launch may restore the ordinary connection. Cold OS actions validate their
     * target first.
     */
    fun restoreSavedConnection() {
        if (!restoreRequested.compareAndSet(false, true)) return
        scope.launch {
            if (ready.await() is Outcome.Failed) return@launch
            // The phone agents' helper is part of This phone, never the server the home is on: a saved choice of it
            // (earlier builds listed it as a server) moves to the phone, and nothing is lost: its conversations stay
            // in the list beside the phone's own.
            val chosen = profiles.activeId.value
            val active =
                PhoneAgentRules.homeServer(
                        chosen?.raw,
                        profiles.profiles.value.map { it.id.raw },
                        isHelper = { isPhoneHelper(ProfileId(it)) },
                        phoneOf = { stores.phoneOfHelper(ProfileId(it))?.raw },
                    )
                    ?.let(::ProfileId)
            if (chosen != null && active != null && active != chosen) profiles.setActive(active)
            // Before any connection opens: the helper carries its phone's name, so every place that names a
            // connection (a chat's header, an alert, All conversations) reads This phone, never "Phone agents".
            stores.nameHelpersAfterPhones()
            // B-486: seed active + shown phone-agent helpers and start the helper alongside OpenCode instead of
            // waiting behind OpenCode's boot and initial connection.
            val phoneHelpers =
                profiles.profiles.value
                    .filter { it.id != active && isPhoneHelper(it) && listSourceShown(it.id) }
                    .map { it.id }
            io.github.eslamasabry.oc.engine.runtime.server.ColdStartLaunch.run(
                io.github.eslamasabry.oc.engine.runtime.server.ColdStartLaunch.Targets(
                    active = active,
                    phoneHelpers = phoneHelpers,
                ),
                io.github.eslamasabry.oc.engine.runtime.server.ColdStartLaunch.HelperHooks(
                    seed = { id ->
                        command {
                            seedWhileStarting(id)
                            Unit.ok()
                        }
                    },
                    start = ::joinPhoneHelper,
                    adopt = ::adoptPhoneAgents,
                ),
                io.github.eslamasabry.oc.engine.runtime.server.ColdStartLaunch.PrimaryHooks(
                    connect = { id ->
                        connectCommand(id, makeActive = true) { turn ->
                            connectForHome(id, makeActive = turn.activate, local = turn.local)
                        }
                    },
                    empty = { Unit.ok() },
                    onComplete = { initialConnection.complete(it) },
                    syncRemaining = { synchronizeListSources() },
                ),
            )
        }
    }

    /** Launcher shortcuts wait for the existing startup connection; they never start or switch a server. */
    internal suspend fun awaitInitialConnection(): Outcome<Unit> = initialConnection.await()

    private fun publishServers() {
        mutableServers.value =
            profiles.profiles.value
                // The phone agents' helper is part of This phone, not a server a person picks, edits or removes.
                .filterNot { isPhoneHelper(it) }
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
                setHolding(true)
                try {
                    block()
                } finally {
                    setHolding(false)
                    publishServers()
                }
            }
        }
    }

    private fun setHolding(held: Boolean) {
        synchronized(busyLock) { holding = held }
        publishBusy()
    }

    private fun publishBusy() {
        synchronized(busyLock) { mutableBusy.value = holding || localWaits.waiting > 0 }
    }

    /**
     * A connection attempt. One to this phone's own server waits for it to answer BEFORE it takes the command lock
     * (LocalServerWaits): the lock guards the connection state, and the wait (up to about 150 s while the phone boots)
     * changes none of it, so a switch, a Try again or a Stop is never queued behind it. A newer command supersedes the
     * waiting attempt, which then ends with StaleScope and changes nothing (no failure is recorded for it).
     */
    private suspend fun connectCommand(
        id: ProfileId,
        makeActive: Boolean,
        connect: suspend (io.github.eslamasabry.oc.engine.runtime.server.LocalServerWaits.Turn) -> Outcome<Unit>,
    ): Outcome<Unit> {
        when (val start = ready.await()) {
            is Outcome.Failed -> return start.atStage(ConnectionStage.StorageReady)
            is Outcome.Ok -> Unit
        }
        return localWaits.run(
            id,
            makeActive,
            waits = profiles.profile(id)?.let(::waitsForPhoneServer) == true,
            locked = { block -> command(block) },
            stale = ::superseded,
        ) { turn ->
            // The person chose another server while this one booted: it still joins the list if it is shown there.
            if (turn.overtaken && !listSourceShown(id)) superseded() else connect(turn)
        }
    }

    private fun superseded(): Outcome<Unit> = Failure.Refused(RefusalReason.StaleScope).failed()

    /** This phone's own server, which may still be booting: the first connection waits for it (phase 0). */
    private fun waitsForPhoneServer(loaded: LoadedProfile): Boolean =
        localGates.gate != null &&
            !loaded.profile.usesAgentSocket &&
            !loaded.profile.requiresOpenCode2Migration &&
            !loaded.cleartextUnconfirmed &&
            io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer.isBuiltin(loaded.profile.baseUrl)

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
        if (
            ProfileOrigins.needsCleartextConsent(origin) &&
                (!allowCleartext || ProfileOrigins.cleartextNeedsHttps(origin)) // B-177
        )
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

    override suspend fun connect(id: ProfileId): Outcome<Unit> =
        // The phone agents' helper never becomes the home's server (a notification, a link or a page that connects
        // it): it joins the list as part of This phone.
        if (ready.await() is Outcome.Ok && isPhoneHelper(id)) connectInBackground(id)
        else
            connectCommand(id, makeActive = true) { turn ->
                connectForHome(id, recoverDrafts = true, makeActive = turn.activate, local = turn.local)
            }

    /** The server on the home stays the one the person chose: opening the Agents page used to switch it to "Phone agents". */
    override suspend fun connectInBackground(id: ProfileId): Outcome<Unit> =
        connectCommand(id, makeActive = id == profiles.activeId.value) { turn ->
            connectForHome(id, makeActive = turn.activate && id == profiles.activeId.value, local = turn.local)
        }

    /**
     * [local]: how the wait for this phone's own server ended, when the attempt waited for it outside the lock
     * (connectCommand); null when it did not.
     */
    private suspend fun connectForHome(
        id: ProfileId,
        recoverDrafts: Boolean = false,
        makeActive: Boolean = true,
        local: io.github.eslamasabry.oc.engine.runtime.server.LocalServerWait? = null,
    ): Outcome<Unit> {
        // The helper's own source connecting is a stage of the start ([LaunchStages]).
        val helper = isPhoneHelper(id)
        if (helper) stages.agentConnecting(id, true)
        // Only a source whose saved rows stood in (one the app starts, or the server in use) gets them back on a
        // failed connection: another computer never shows chats the app did not read.
        val wasSeeded = dropSeed(id)
        synchronized(personStopped) { personStopped.remove(id) }
        val result =
            try {
                connectNow(id, recoverDrafts, makeActive, local)
            } finally {
                if (helper) stages.agentConnecting(id, false)
            }
        if (result is Outcome.Failed && wasSeeded && listSourceShown(id)) seedWhileStarting(id)
        val stopped = result is Outcome.Failed && synchronized(personStopped) { personStopped.remove(id) }
        when {
            // The person's own stop is a state (Home says so, with Start OpenCode); it is never a source failure.
            stopped -> mutableSourceFailures.update { it - id }
            result is Outcome.Failed -> mutableSourceFailures.update { it + (id to result.failure) }
            else -> mutableSourceFailures.update { it - id }
        }
        return result
    }

    /** Sources whose last attempt ended because the person had stopped This phone's OpenCode (not a failure). */
    private val personStopped = mutableSetOf<ProfileId>()

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

    /** Every list source whose list has been read in this run (the server in use and the ones that joined it). */
    internal fun loadedSourceIds(): Set<ProfileId> = loadedSources.value

    /** The same, as a flow: Home reads again the moment a source is listed. */
    internal val loadedSourceFlow: StateFlow<Set<ProfileId>> get() = loadedSources

    internal suspend fun synchronizeListSources(): Outcome<Unit> = command {
        var failed: Outcome.Failed? = null
        for (profile in profiles.profiles.value) {
            if (
                profile.id != profiles.activeId.value &&
                    listSourceShown(profile.id) &&
                    (profile.id !in listings || profile.id in seededListings)
            ) {
                // The phone's agent helper may need a start of up to 30 s: it joins later and never holds the
                // OpenCode list (or any other command) while it starts.
                if (isPhoneHelper(profile)) {
                    joinPhoneHelper(profile.id)
                    continue
                }
                // This phone's own server may be booting: it joins once it answers, outside the lock.
                if (waitsForPhoneServer(profile)) {
                    joinPhoneServer(profile.id)
                    continue
                }
                val result = connectForHome(profile.id, makeActive = false)
                if (result is Outcome.Failed) failed = result
            }
        }
        rebuildFeed()
        failed ?: Unit.ok()
    }

    private fun isPhoneHelper(profile: LoadedProfile): Boolean = stores.isPhoneHelper(profile)

    /**
     * While a source the app itself starts is starting, its rows saved last time stand in the list (unverified, so a
     * tap is kept and opens it when it can). Nothing when it is already listed or nothing was saved.
     */
    private fun seedWhileStarting(id: ProfileId) {
        if (id in listings || !listSourceShown(id)) return
        val loaded = profiles.profile(id) ?: return
        val cached = inventory.read(loaded.profile)
        if (cached.isEmpty()) return
        seededListings += id
        unverifiedListings += id
        listings[id] = MutableStateFlow(OpenCodeListing(sessions = cached, loading = true, complete = false))
        rebuildFeed()
    }

    /**
     * The source is connecting now: its real listing takes the saved rows' place (same rows, same identities).
     * True when its saved rows stood in.
     */
    private fun dropSeed(id: ProfileId): Boolean {
        if (!seededListings.remove(id)) return false
        listings.remove(id)
        return true
    }

    /**
     * What the app is starting for list source [id], when it is one the app itself starts (This phone's own server,
     * its agents' helper), it is shown, it is not the server in use (the opening card speaks for that one), its list
     * has not been read yet and it has not failed. Null otherwise.
     */
    internal fun startingSource(
        id: ProfileId,
        agentsUpdating: Boolean = false,
    ): io.github.eslamasabry.oc.feature.chats.presentation.AppStart? {
        if (id == profiles.activeId.value || !listSourceShown(id)) return null
        val loaded = profiles.profile(id) ?: return null
        val kind = appStart(loaded, agentsUpdating) ?: return null
        // The agents' update keeps their helper down whatever happened before: it says so until it is done (a start
        // refused during the install is not "not answering"; the helper is asked again afterwards).
        if (kind == io.github.eslamasabry.oc.feature.chats.presentation.AppStart.AgentsUpdating) return kind
        if (id in loadedSources.value) return null
        // Issue 147: the helper reads "Starting" exactly while its start loop is in its first window, also over a
        // refused first try (nothing listened yet after an app start or update); never with no start behind it.
        if (kind == io.github.eslamasabry.oc.feature.chats.presentation.AppStart.Agents)
            return kind.takeIf { id in helperJoins.starting.value }
        if (id in mutableSourceFailures.value) return null
        return kind
    }

    /** Whether the app itself starts [loaded]'s server, and which kind; null for a server the person connects. */
    internal fun appStart(
        loaded: LoadedProfile,
        agentsUpdating: Boolean = false,
    ): io.github.eslamasabry.oc.feature.chats.presentation.AppStart? =
        when {
            isPhoneHelper(loaded) && agentsUpdating ->
                io.github.eslamasabry.oc.feature.chats.presentation.AppStart.AgentsUpdating
            isPhoneHelper(loaded) -> io.github.eslamasabry.oc.feature.chats.presentation.AppStart.Agents
            io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer.isBuiltin(loaded.profile.baseUrl) ->
                io.github.eslamasabry.oc.feature.chats.presentation.AppStart.Phone
            else -> null
        }

    /** The phone agents' helper profile: part of This phone, never listed or chosen as a server. */
    internal fun isPhoneHelper(id: ProfileId): Boolean = stores.isPhoneHelper(id)

    /**
     * The helper's starts as a list source ([HelperJoins], issue 147). Owner's phone, build 10045: the chip read
     * "Starting Claude Code…" for minutes with no helper and nothing starting it. Galaxy S22, build 10046, after an
     * update: the first try failed at once, "isn't answering · Try again" showed within 4 s and nothing started the
     * helper until the tap. Now one start loop per source: it reads starting for its first 90 s whatever the tries
     * answer (a refused try is tried again in 5 s), names the failure only after that or when waiting cannot change it,
     * and keeps trying with a growing pause; a shown source that is not listed always has a loop
     * ([keepHelperListed]).
     */
    private val helperJoins = HelperJoins(scope, now = { clock.now().toEpochMilliseconds() })

    init {
        // After [helperJoins] exists. Issue 147 (owner's phone, build 10057): a start of the agents' helper is admitted
        // only while the app is in front; one refused in the background (an update or a boot started the app) is tried
        // again as soon as the app comes to the front.
        scope.launch { helperJoins.starting.collect { stages.helperWanted(it) } }
        scope.launch {
            if (ready.await() is Outcome.Failed) return@launch
            helperJoins.followFront(
                visibility.state.map { it == io.github.eslamasabry.oc.core.common.pace.Visibility.Foreground },
                sources = { profiles.profiles.value.map { it.id } },
                want = { wantHelper(it) },
            )
        }
        // B-486: wake any helper start refused between onActivityStarted and BuiltinLinux.setActivityResumed(true)
        // so cold start never burns a 5 s retry sleep waiting for the next poll.
        scope.launch {
            if (ready.await() is Outcome.Failed) return@launch
            helperJoins.followFront(
                localGates.activityResumed,
                sources = { profiles.profiles.value.map { it.id } },
                want = { wantHelper(it) },
            )
        }
        // B-486: remembers the first resume of this run, for the one retry of the Phone agents adoption.
        scope.launch { localGates.activityResumed.collect { if (it) activityResumedOnce.set(true) } }
    }

    /** The helper sources whose start loop is in its first window (the home reads "Starting" only for these). */
    internal val helperStarting: StateFlow<Set<ProfileId>> get() = helperJoins.starting

    /** Starts the helper outside the command lock, then adds its conversations to the list. */
    private fun joinPhoneHelper(id: ProfileId) {
        helperJoins.ensure(
            id,
            keepTrying = HelperStartRules::keepTrying,
            onFailed = { failure ->
                // The source did not answer: named, with Try again; the loop keeps trying after a pause. A hidden
                // source (wanted by an open conversation) has no row to name it on.
                if (id !in loadedSources.value && listSourceShown(id))
                    mutableSourceFailures.update { it + (id to failure) }
            },
        ) {
            command {
                seedWhileStarting(id)
                Unit.ok()
            }
            val helper = phoneHelper.value.ensureHelper(id)
            when (helper) {
                is Outcome.Ok ->
                    command {
                        // Not listed yet, also when an earlier connection left its listing unread: connect it now
                        // (before, such a source kept "Starting" with no start and no connection behind it).
                        if (
                            (id !in listings || id in seededListings || id !in loadedSources.value) &&
                                listSourceShown(id)
                        ) {
                            dropSeed(id)
                            // A connection that fails keeps the saved rows on show.
                            if (connectForHome(id, makeActive = false) is Outcome.Failed) seedWhileStarting(id)
                            rebuildFeed()
                        }
                        Unit.ok()
                    }
                // Recorded for the loop; hidden while the source still reads starting (ChatsHomeWiring).
                is Outcome.Failed -> if (listSourceShown(id)) mutableSourceFailures.update { it + (id to helper.failure) }
            }
            when {
                // Listed: this loop is done.
                id in loadedSources.value -> null
                // Hidden from the list (an open conversation wants the helper): done once the helper is up; until
                // then the loop tries again like a shown source's.
                !listSourceShown(id) ->
                    (helper as? Outcome.Failed)?.failure
                // Otherwise why it is not listed.
                else -> mutableSourceFailures.value[id] ?: Failure.Runtime(RuntimeProblem.ServerStartUnavailable)
            }
        }
    }

    private val helperWanting =
        HelperWanting(
            joins = helperJoins,
            isHelper = ::isPhoneHelper,
            isActive = { it == profiles.activeId.value },
            listed = { it in loadedSources.value },
            shown = ::listSourceShown,
            failure = { mutableSourceFailures.value[it] },
            startLoop = ::joinPhoneHelper,
        )

    /**
     * A shown helper source that is not listed and has no start loop (nothing asked for it since the app started, its
     * connection was closed, or a failure was recorded by another path before any start) gets one; a failure waiting
     * cannot change (the phone's Linux or the helper must be installed or updated first) waits for Try again.
     */
    internal fun keepHelperListed(id: ProfileId) = helperWanting.keepListed(id)

    /**
     * Issue 147 (owner's phone, build 10057): the helper of [id] is needed now (the app came to the front, or one of its
     * conversations opened from the home, All conversations, a notification, the edge light or search). Not listed: its
     * start loop runs, and one that pauses between tries tries again at once with a new 90 s window. A source hidden
     * from the home's list is wanted too when a conversation of it opens ([evenHidden]); the app coming to the front
     * wants shown sources only.
     */
    internal fun wantHelper(id: ProfileId, evenHidden: Boolean = false) = helperWanting.want(id, evenHidden)

    private val joiningServers = mutableSetOf<ProfileId>()

    /** Connects this phone's own server as a list source once it answers; the wait holds no lock. */
    private fun joinPhoneServer(id: ProfileId) {
        if (!synchronized(joiningServers) { joiningServers.add(id) }) return
        scope.launch {
            try {
                command {
                    seedWhileStarting(id)
                    Unit.ok()
                }
                connectCommand(id, makeActive = false) { turn ->
                    if ((id !in listings || id in seededListings) && listSourceShown(id)) {
                        dropSeed(id)
                        // A connection that fails keeps the saved rows on show.
                        if (connectForHome(id, makeActive = false, local = turn.local) is Outcome.Failed) seedWhileStarting(id)
                        rebuildFeed()
                    }
                    Unit.ok()
                }
            } finally {
                synchronized(joiningServers) { joiningServers.remove(id) }
            }
        }
    }

    /**
     * Cold start: with a phone agent installed, the "Phone agents" source is saved and joins the list on its own,
     * with no visit to the Agents page. Never raises a failure: the OpenCode list shows either way. The phone's
     * Linux admits the helper's start only while the app is in front: an adoption that failed before any activity
     * resumed is tried once more at the first resume; one that failed with the app in front is not repeated.
     */
    private fun adoptPhoneAgents() {
        scope.launch {
            var adopted = phoneHelper.value.adoptSource()
            if (adopted is Outcome.Failed && !activityResumedOnce.get()) {
                localGates.activityResumed.first { it }
                adopted = phoneHelper.value.adoptSource()
            }
            if (adopted is Outcome.Ok && adopted.value != null) synchronizeListSources()
        }
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
        // The choice is saved. A server that does not answer is recorded on its own row (sourceFailures); it is not a
        // failure to change the list, so turning it on or off here never raises the sheet's error.
        if (shown) {
            val loaded = profiles.profile(id)
            if (loaded != null && waitsForPhoneServer(loaded)) joinPhoneServer(id)
            else connectForHome(id, makeActive = false)
        }
        rebuildFeed()
        Unit.ok()
    }

    /** The home's Try again (and its quiet retries): supersedes an attempt for [id] still waiting for the phone. */
    internal suspend fun retryListSource(id: ProfileId): Outcome<Unit> =
        connectCommand(id, makeActive = id == profiles.activeId.value) { turn ->
            if (!listSourceShown(id)) Failure.Refused(RefusalReason.StaleScope).failed()
            else connectForHome(id, makeActive = turn.activate && id == profiles.activeId.value, local = turn.local)
        }

    internal suspend fun connectWatched(id: ProfileId, expected: StoredProfile): Outcome<Unit> =
        connectCommand(id, makeActive = true) { turn ->
            if (profiles.profile(id)?.profile != expected) Failure.Refused(RefusalReason.StaleScope).failed()
            else connectForHome(id, makeActive = turn.activate, local = turn.local)
        }

    /**
     * Runtime migration retires only local transports; saved rows, secrets and queues stay intact.
     */
    internal suspend fun disconnectForRuntimeSwitch(ids: Set<ProfileId>): Outcome<Unit> = command {
        localWaits.cancel(ids)
        for (id in ids) {
            registry.close(id)
            eventJobs.remove(id)?.cancel()
            listings.remove(id)
            seededListings.remove(id)
        }
        rebuildFeed()
        Unit.ok()
    }

    private suspend fun connectNow(
        id: ProfileId,
        recoverDrafts: Boolean = false,
        makeActive: Boolean = true,
        local: io.github.eslamasabry.oc.engine.runtime.server.LocalServerWait? = null,
    ): Outcome<Unit> {
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
            seededListings.remove(id)
            loadedSources.update { it - id }
            mutableSourceFailures.update { it - id }
            rebuildFeed()
        }
        // Phase 0: This phone's own server may still be booting (right after setup or an app start). Wait for it
        // to answer before the first connection instead of being refused. The wait ran before the lock
        // (connectCommand, [local]); waiting here, in the lock, is left only for a profile edited into this phone's
        // server between that wait and the lock.
        if (
            !loaded.profile.usesAgentSocket &&
                io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer.isBuiltin(
                    loaded.profile.baseUrl
                )
        ) {
            val gate = localGates.gate
            val waited = local ?: gate?.invoke(id)
            if (waited == io.github.eslamasabry.oc.engine.runtime.server.LocalServerWait.StoppedByPerson) {
                // The person stopped it: nothing to connect to, and not a failure of the source (connectForHome).
                synchronized(personStopped) { personStopped.add(id) }
                return Failure.Runtime(RuntimeProblem.ServerStartUnavailable)
                    .failed()
                    .atStage(ConnectionStage.OpenGateway)
            }
            if (waited == io.github.eslamasabry.oc.engine.runtime.server.LocalServerWait.NotAnswering) {
                return Failure.Runtime(RuntimeProblem.ServerStartUnavailable)
                    .failed()
                    .atStage(ConnectionStage.OpenGateway)
            }
        }
        // The saved "Phone agents" server needs its helper running; the app starts it, not only the Agents page.
        if (loaded.profile.backend == BackendKind.Paseo) {
            when (val helper = phoneHelper.value.ensureHelper(id)) {
                is Outcome.Failed -> return helper.atStage(ConnectionStage.OpenGateway)
                is Outcome.Ok -> Unit
            }
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
                if (!listings.containsKey(id)) {
                    val cached = inventory.read(loaded.profile)
                    if (cached.isNotEmpty()) unverifiedListings += id
                    listings[id] =
                        MutableStateFlow(
                            OpenCodeListing(sessions = cached, loading = true, complete = false)
                        )
                    rebuildFeed()
                    eventJobs[id] = scope.launch {
                        // H6: the Stops the person pressed from this app (the row says "Stopped", not "Done").
                        launch {
                            ListRowFacts.stopped(id, graph).collect { stopped ->
                                listings[id]?.let { it.value = it.value.copy(stoppedByPerson = stopped) }
                            }
                        }
                        launch {
                            // One truth for "Done": publishReadState reads the device's saved marks. The engine's own
                            // in-memory read state does not know them yet at launch, so it used to say "Done" for a
                            // moment and the saved marks then took it back (a word that appears and vanishes).
                            graph.session.readState.state.collect {
                                if (registry.live.value[id] === graph) {
                                    order.withLock { listings[id]?.let { publishReadState(id, it) } }
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
        fun status(
            session: SessionId,
            status: SessionStatus,
            failed: Boolean? = null,
            outcome: SessionRunOutcome? = null,
        ) {
            val wasBusy =
                listing.value.statuses[session].let { it == SessionStatus.Busy || it == SessionStatus.Retry }
            if (status == SessionStatus.Busy || status == SessionStatus.Retry) recentlyEnded[id]?.remove(session)
            else if (wasBusy && status == SessionStatus.Idle)
                recentlyEnded.getOrPut(id) { mutableMapOf() }[session] = clock.now().toEpochMilliseconds()
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
                                if (it.id == session)
                                    it.copy(
                                        lastRunFailed = failed,
                                        // A new run clears the server's word for the last one; an end sets it.
                                        lastRunOutcome = outcome ?: if (failed == false) null else it.lastRunOutcome,
                                    )
                                else it
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
                    when (event.outcome) {
                        is RunOutcome.Failed -> SessionRunOutcome.Failed
                        is RunOutcome.Interrupted -> SessionRunOutcome.Interrupted
                        RunOutcome.Succeeded -> SessionRunOutcome.Succeeded
                        else -> null
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
                                    lastRunOutcome =
                                        event.session.lastRunOutcome
                                            ?: current.sessions
                                                .firstOrNull { it.id == event.session.id }
                                                ?.lastRunOutcome,
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
                    // An agents server's rows say which agent runs them (Claude Code, Codex, ...); its saved rows,
                    // shown before it answers, say the agent each row kept, never OpenCode.
                    agentOf =
                        profiles.profile(id)?.profile?.takeIf { it.backend == BackendKind.Paseo }?.let { stored ->
                            val saved = if (id in unverifiedListings) inventory.agents(stored) else emptyMap()
                            ({ session: Session -> PhoneConversationRules.agentOf(session) ?: saved[session.id.raw] })
                        } ?: ({ _: Session -> null }),
                    // H4: an agent conversation the helper can no longer reopen offers a new one on tap.
                    needsNewChat = { ListRowFacts.needsNewChat(registry.live.value[id], it.id) },
                    refresh = { refreshOne(id) },
                )
            }
        val merged = ChatFeed(sources, scope)
        feed = merged
        publish(merged.snapshot.value)
        feedJob = scope.launch { merged.snapshot.collect(::publish) }
    }

    /**
     * Shows [next], except that an empty feed never replaces rows the home already shows before any server has
     * answered in this run of the app. Until then "no rows" means "not known yet", not "no conversations": the rows
     * read from the last run stay, marked not verified (owner 2026-11-10, a cold start while the phone's server was
     * still starting: the saved list, then "No conversations yet", then skeleton rows, then the list again).
     */
    private fun publish(next: ChatFeedSnapshot) {
        val held = snapshot.value
        snapshot.value =
            if (!anySourceLoaded && next.items.isEmpty() && held.items.isNotEmpty()) held.copy(loading = false, complete = false)
            else openingFlags(next)
    }

    private fun openingFlags(value: ChatFeedSnapshot): ChatFeedSnapshot =
        value.copy(
            items =
                value.items
                    .map {
                        if (it.profileId in unverifiedListings) it.copy(canReopen = false) else it
                    }
                    .toImmutableList()
        )

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
        when (val page = readFeedSessionPages(graph.session.server, graph.operations())) {
            is Outcome.Failed -> {
                listing.value = listing.value.copy(loading = false, failed = true)
                return page.atStage(ConnectionStage.LoadConversations)
            }
            is Outcome.Ok -> {
                if (registry.live.value[id] !== graph)
                    return Failure.Refused(RefusalReason.StaleScope)
                        .failed()
                        .atStage(ConnectionStage.LoadConversations)
                unverifiedListings.remove(id)
                val stored = profiles.profile(id)?.profile
                if (stored != null)
                    // Own project first, then the others: the saved list holds 100 rows, so keep the newest of all.
                    inventory.save(
                        stored,
                        page.value.sessions.sortedByDescending {
                            it.time?.updatedMs ?: it.time?.createdMs ?: 0L
                        },
                    ) {
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
                // Across projects the ask lists above cover the connection's own folder only.
                val others =
                    if (caps.globalSessionSearch)
                        readOtherProjectAsks(
                            graph.session.server,
                            page.value.sessions,
                            (statuses as? Outcome.Ok)?.value,
                            graph.session.server.location.value.directory,
                        )
                    else OtherProjectAsks.None
                val allPermissions = (permissions as? Outcome.Ok)?.value?.plus(others.permissions)
                val allForms = (forms as? Outcome.Ok)?.value?.plus(others.forms)
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
                        allPermissions?.map { it.id.raw to it.sessionId },
                    )
                    replace(
                        "question",
                        (questions as? Outcome.Ok)?.value?.map { it.id.raw to it.sessionId },
                    )
                    replace(
                        "form",
                        allForms?.map { it.id.raw to it.sessionId },
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
                        stoppedByPerson = listing.value.stoppedByPerson,
                        permissions =
                            if (allPermissions != null)
                                allPermissions
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
                                                    ask.permission,
                                                )
                                        }
                                    }
                                    .toMap()
                            else listing.value.permissions,
                        // H1: the first waiting question / form of each conversation, so its row can be answered.
                        questions =
                            if (questions is Outcome.Ok)
                                questions.value
                                    .groupBy { it.sessionId }
                                    .mapNotNull { (sid, asks) ->
                                        asks.firstOrNull()?.let { ask ->
                                            val first = ask.prompts.firstOrNull()
                                            sid to
                                                FeedQuestion(
                                                    requestId = ask.id.raw,
                                                    title = redactor.redact(first?.title.orEmpty()),
                                                    question = redactor.redact(first?.question.orEmpty()),
                                                    choices =
                                                        first?.choices.orEmpty().map { redactor.redact(it.label) },
                                                    prompts = ask.prompts.size,
                                                    custom = first?.custom == true,
                                                    inPlace =
                                                        ask.prompts.size == 1 &&
                                                            first != null &&
                                                            !first.multiple &&
                                                            !first.optional &&
                                                            first.choices.none { it.description.length > 140 },
                                                )
                                        }
                                    }
                                    .toMap()
                            else if (questions == null) emptyMap() else listing.value.questions,
                        forms =
                            if (forms is Outcome.Ok)
                                forms.value
                                    .groupBy { it.sessionId }
                                    .mapNotNull { (sid, asks) ->
                                        asks.firstOrNull()?.let { ask ->
                                            sid to
                                                FeedForm(
                                                    ask.id.raw,
                                                    ask.title?.let(redactor::redact),
                                                    ask.fields.size,
                                                )
                                        }
                                    }
                                    .toMap()
                            else if (forms == null) emptyMap() else listing.value.forms,
                        needsYou = pending.values.toSet(),
                        // The listing's "Done" marks are published once, by publishReadState below.
                        finishedUnseen = listing.value.finishedUnseen,
                        // B1: more pages on the server is not "couldn't load".
                        complete = page.value.complete,
                        failed =
                            statuses is Outcome.Failed ||
                                permissions is Outcome.Failed ||
                                questions is Outcome.Failed ||
                                forms is Outcome.Failed,
                        // The phone agents' folder-by-folder list never makes the whole list look single-project.
                        acrossProjects =
                            PhoneAgentRules.listsAcrossProjects(
                                graph.session.server.capabilities.value.globalSessionSearch,
                                isPhoneHelper(id),
                            ),
                    )
anySourceLoaded = true
                loadedSources.update { it + id }
                publishReadState(id, listing)
                // Unknown optional asks affect the feed's completeness, never healthy
                // connect/create admission.
                return statuses.map { Unit }.atStage(ConnectionStage.LoadActivity)
            }
        }
    }

    internal suspend fun refresh(): Outcome<Unit> = command {
        // A person's refresh is the way forward for the agents' helper that did not start: it is started again.
        profiles.profiles.value
            .filter {
                isPhoneHelper(it.id) &&
                    it.id != profiles.activeId.value &&
                    listSourceShown(it.id) &&
                    it.id !in loadedSources.value
            }
            .forEach {
                joinPhoneHelper(it.id)
                helperJoins.wake(it.id)
            }
        var failed: Outcome.Failed? = null
        // A source still starting has no connection to read yet; its saved rows wait for it.
        listings.keys.filterNot { it in seededListings }.forEach {
            val result = refreshOne(it)
            if (result is Outcome.Failed) failed = result
        }
        failed ?: Unit.ok()
    }

    internal fun prefetch(ref: ConversationRef) {
        if (profiles.profile(ref.profileId) == null) return
        val session = registry.live.value[ref.profileId]?.session ?: return
        // A pressed row is about to open: its history may be read in full (issue 147, HistoryOpenGateway).
        session.server.facet<io.github.eslamasabry.oc.core.domain.HistoryOpenGateway>()?.historyOpened(ref.sessionId)
        session.reads.prefetch(ref.sessionId)
    }

    /**
     * The person stopped this phone's server: every conversation it was running is cut by that stop, so Home, the
     * light and the turn's foot say "Stopped" (the person's own), never "Done". The server's own word is gone with it.
     */
    internal suspend fun markRunsStopped(id: ProfileId) {
        val busy =
            listings[id]?.value?.statuses?.filterValues { it == SessionStatus.Busy || it == SessionStatus.Retry }?.keys
                .orEmpty()
        val cutoff = clock.now().toEpochMilliseconds() - RECENT_CUT_MS
        val recent = recentlyEnded[id].orEmpty().filterValues { it >= cutoff }.keys
        val cut = busy + recent
        if (cut.isEmpty()) return
        val delivery = (registry.live.value[id] as? ChatGraphAccessors)?.chatDelivery ?: return
        delivery.markCutByStop(cut.map { ConversationRef(id, it) })
    }

    override suspend fun disconnect(id: ProfileId): Outcome<Unit> {
        // A waiting attempt for this server ends at once, and so does its wait (Disconnect, the person's Stop).
        localWaits.cancel(listOf(id))
        return disconnectNow(id)
    }

    private suspend fun disconnectNow(id: ProfileId): Outcome<Unit> = command {
        registry.close(id)
        eventJobs.remove(id)?.cancel()
        refreshJobs.remove(id)?.cancel()
        refreshRevision.remove(id)
        requestRevision.remove(id)
        runRevision.remove(id)
        runSignals.remove(id)
        sessionSignals.remove(id)
        failedRuns.remove(id)
        recentlyEnded.remove(id)
        requests.remove(id)
        listings.remove(id)
        seededListings.remove(id)
        loadedSources.update { it - id }
        unverifiedListings.remove(id)
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

    override suspend fun remove(id: ProfileId): Outcome<Unit> = remove(id, discardHeld = false)

    /**
     * N-48: with [discardHeld] (the person confirmed it on the removal page) this server's waiting prompts go with it,
     * a send that may already have arrived included, instead of keeping the server forever.
     */
    suspend fun remove(id: ProfileId, discardHeld: Boolean): Outcome<Unit> {
        // The phone agents' helper is part of This phone and has no row of its own to remove it from: it goes with
        // the phone (its prompts too: the person removed the phone). Read before the phone's keys are swept.
        val helpers =
            PhoneAgentRules.removedWith(id.raw, profiles.profiles.value.map { it.id.raw }) {
                    stores.phoneOfHelper(ProfileId(it))?.raw
                }
                .map(::ProfileId)
        val removed = removeOne(id, discardHeld)
        if (removed is Outcome.Failed) return removed
        for (helper in helpers) {
            val gone = removeOne(helper, discardHeld = true)
            if (gone is Outcome.Failed) return gone
        }
        return removed
    }

    private suspend fun removeOne(id: ProfileId, discardHeld: Boolean): Outcome<Unit> {
        val result = command {
            when (val queued = OfflineQueueStore(stores.prefs, stores.lanes.app).load()) {
                QueueRead.Unreadable -> return@command Failure.StorageRefused().failed()
                is QueueRead.Readable ->
                    if (!discardHeld && queued.entries.any { it.profileId == id })
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
                if (discardHeld) {
                    val swept = delivery.sweepProfile(id)
                    if (swept is Outcome.Failed) return@command swept
                }
            }
            localWaits.cancel(listOf(id))
            registry.close(id)
            eventJobs.remove(id)?.cancel()
            listings.remove(id)
            seededListings.remove(id)
            loadedSources.update { it - id }
            mutableSourceFailures.update { it - id }
            rebuildFeed()
            // Queue admission is fenced above and close joins connection work. A send may have
            // written
            // its marker
            // after the optimistic check above; inspect again only after that drain, before
            // sweeping either metadata or secrets. A refusal keeps the saved server intact.
            when (val queued = OfflineQueueStore(stores.prefs, stores.lanes.app).load()) {
                QueueRead.Unreadable -> return@command Failure.StorageRefused().failed()
                is QueueRead.Readable ->
                    if (queued.entries.any { it.profileId == id }) {
                        if (!discardHeld)
                            return@command Failure.Refused(RefusalReason.DispatchUncertain).failed()
                        // No live owner held them: the stored queue drops this server's entries itself.
                        val kept = queued.entries.filter { it.profileId != id }
                        if (!OfflineQueueStore(stores.prefs, stores.lanes.app).save(kept))
                            return@command Failure.StorageRefused().failed()
                    }
            }
            val journals =
                deliveryStores.singleOrNull()?.journals
                    ?: return@command Failure.StorageRefused().failed()
            journals.closeProfile(id)
            when (val receipts = journals.deletionSnapshot(id)) {
                is Outcome.Failed -> return@command receipts
                is Outcome.Ok ->
                    if (!discardHeld && receipts.value.any { !it.state.terminal })
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
