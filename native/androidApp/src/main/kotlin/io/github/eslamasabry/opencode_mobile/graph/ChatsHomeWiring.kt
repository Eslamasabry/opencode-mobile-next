package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.common.ids.SessionId
import dev.zacsweers.metro.ContributesIntoSet
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.engine.agents.certification.AgentCertification
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.phoneAgentSignedOut
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.chats.presentation.AppStart
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotice
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeSource
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotices
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.HomeFeedCache
import io.github.eslamasabry.oc.engine.agents.feed.OPENCODE_AGENT_ID
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtras
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneStopWatch
import io.github.eslamasabry.oc.feature.chats.presentation.NoChatsHomeExtras
import io.github.eslamasabry.oc.feature.chat.world.LaunchStage
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import io.github.eslamasabry.oc.feature.chats.presentation.OpeningRules
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneStartWatch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How often home quietly tries this phone's server again while it starts (E05-S20). */
private fun optional(key: String, value: String?): Map<String, Any> = if (value == null) emptyMap() else mapOf(key to value)

private val QUIET_RETRY_EVERY = 3.seconds
private val QUIET_RETRY_WHEN_FAILED = 15.seconds
private val STOP_SETTLES = 3.seconds

/** How long after the app's own work on the helper its source is still joining again (never read as failed). */
private val HELPER_SETTLE = 30.seconds

/**
 * One source selector over the actual runtime; hiding a source never closes a graph an open chat
 * may still own.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ChatsHomeExtras>(), replaces = [NoChatsHomeExtras::class])
@Inject
class AppChatsHome(
    private val runtime: PreviewRuntime,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val clock: Clock,
    private val monitor: AppChatsMonitor,
    private val phone: BuiltinRuntime,
    private val phoneServer: AndroidChatPhoneServer,
    /** The automatic agent update: while it installs, the agents' source says "Updating …" (no silent minutes). */
    private val agentUpdate: PhoneAgentUpdateStatus,
    private val sharedStorage: Set<io.github.eslamasabry.oc.core.domain.platform.SharedStorageAccess>,
    /** The one stage source: what this phone is really starting right now (home names it on the Recent line). */
    private val stages: LaunchStages,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) : ChatsHomeExtras, HomeFeedCache {
    private val lane = dispatchers.serial("chats-home")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val mutable = MutableStateFlow(ChatsHomeExtraState(known = false))
    override val snapshot: StateFlow<ChatsHomeExtraState> = mutable.asStateFlow()
    private val cachedRevisions = mutableMapOf<ProfileId, Pair<String, List<ChatFeedItem>>>()
    // What the last saved list was built from (read and written on the home lane only).
    private var savedFrom: InventoryInputs? = null
    // E05-S20: home's wait for this phone's own server, and the quiet retries while it starts.
    private var phoneWatch: Pair<ProfileId, PhoneStartWatch>? = null
    private var quietRetry: Job? = null

    // The person's Stop of this phone's OpenCode closes the connection to it, so the pill and every screen stop
    // reading "Connected".
    private val stopWatch = PhoneStopWatch { profile -> runtime.disconnect(profile) }

    init {
        scope.launch {
            // A restart stops and starts within moments; only a stop that stays is the person's, so wait a few seconds.
            phone.serverFacts.map { it.stoppedByPerson }.distinctUntilChanged().collectLatest { stopped ->
                if (stopped) delay(STOP_SETTLES)
                val owner =
                    runtime.profiles.profiles.value.firstOrNull { phoneServer.runsHere(it.id) }?.id
                stopWatch.observe(stopped, owner)
            }
        }
        // When the agents' update is done their helper is asked again, so the source that was refused during the
        // install joins the list and its kept taps open (the update restarts the helper, nothing re-joined the list).
        scope.launch {
            agentUpdate.decision
                .map { it.step == AgentUpdateStep.Updating }
                .distinctUntilChanged()
                .drop(1)
                .collect { updating -> if (!updating) runtime.synchronizeListSources() }
        }
        // The stage home names changes with the real signals behind it: This phone's own start, and the start of each
        // agents' helper that is saved.
        scope.launch { stages.stageOfPhone().collect { publish() } }
        // A source that becomes LISTED ends the agents' settle: Home reads again at that moment, not at the next event
        // (the feed's own change can come a moment before the source counts as loaded).
        scope.launch { runtime.loadedSourceFlow.collect { publish() } }
        scope.launch {
            runtime.profiles.profiles
                .map { all -> all.map { it.id }.filter(runtime::isPhoneHelper) }
                .distinctUntilChanged()
                .collectLatest { helpers ->
                    if (helpers.isEmpty()) return@collectLatest
                    combine(helpers.map(stages::stageOfHelper)) { Unit }.collect { publish() }
                }
        }
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            combine(
                    runtime.servers,
                    runtime.busy,
                    runtime.snapshot,
                    runtime.profiles.activeId,
                    // A source the app starts that stopped answering no longer says it is starting; an agent update says
                    // it runs.
                    combine(
                        monitor.snapshot,
                        // Only the phone facts home shows: every status read carries a new uptime and disk figure,
                        // and re-publishing home on each of them ran it as often as the facts were read.
                        phone.serverFacts.map { HomePhoneReading.of(it) }.distinctUntilChanged(),
                        runtime.sourceFailureState,
                        agentUpdate.decision,
                        runtime.helperStarting,
                    ) { _, _, _, _, _ ->
                        Unit
                    },
                ) { _, _, _, _, _ ->
                    Unit
                }
                .collect {
                    publish()
                    // The saved list is built only when what it is made of changed (a failed save is tried again
                    // at the next change or new preview line, never on every look of home).
                    val inputs = InventoryInputs(runtime.snapshot.value, runtime.loadedSourceIds(), registry.live.value)
                    if (inputs != savedFrom) {
                        savedFrom = inputs
                        saveCurrentInventory()
                    }
                }
        }
    }

    /** When the app's own work on each helper last showed (its update or start stage), for the grace after it. */
    private val settle = HelperSettle(HELPER_SETTLE.inWholeMilliseconds)
    private var settleLook: kotlinx.coroutines.Job? = null

    /**
     * The helper [id] is being installed or restarted by the app (the update, or its start stage), or did so within
     * [HELPER_SETTLE] (the source joins again after the helper answers). A look is asked for when the grace ends, so a
     * real failure then shows, once, and never clears by itself within seconds.
     */
    private fun helperSettling(id: ProfileId, updating: Boolean): Boolean {
        val settling =
            settle.settling(
                id,
                workNow = updating || stages.stageOfHelper(id).value != null,
                listed = id in runtime.loadedSourceIds(),
                nowMs = clock.now().toEpochMilliseconds(),
            )
        if (settling && settleLook?.isActive != true) {
            settleLook = scope.launch {
                delay(HELPER_SETTLE + 1.seconds)
                publish()
            }
        }
        return settling
    }

    private fun publish() {
        val active = runtime.profiles.activeId.value
        val updating = agentUpdate.decision.value.takeIf { it.step == AgentUpdateStep.Updating }
        val sources =
            runtime.profiles.profiles.value
                .map { loaded ->
                    // F: side_connections.dart:93-99: the phone agents are one switch (named for what they are), part of
                    // This phone: never the server. Not answering only when their helper's start failed (issue 147).
                    val agents = runtime.isPhoneHelper(loaded.id)
                    // The app's OWN install or restart of the helper (an update, or its start stage) and a short while
                    // after it, while the source joins again: waiting, never "didn't start" (and never a flash).
                    val settling = agents && helperSettling(loaded.id, updating != null)
                    // Issue 147: an agents' source with no start, no list and no failure gets a start (never a bare
                    // "Starting" with nothing behind it); during an agent update the update says why it waits.
                    if (agents && updating == null) runtime.keepHelperListed(loaded.id)
                    ChatsListSourceUi(
                        profile = loaded.id,
                        // Issue 147: the agents' switch is the phone's agents, never "Claude Code" for every agent; the
                        // home's words name it "Agents on this phone" (feature/chats, ChatsListSourceUi.agents).
                        name = loaded.profile.name,
                        shown = runtime.listSourceShown(loaded.id),
                        main = !agents && loaded.id == active,
                        onThisPhone = runtime.sourceOnThisPhone(loaded.profile),
                        selfRun = selfRun(loaded),
                        // The person's own Stop on This phone's OpenCode: a state of its own, never "isn't answering".
                        stoppedByPerson =
                            !agents &&
                                phoneServer.runsHere(loaded.id) &&
                                phone.serverFacts.value.let {
                                    it.stoppedByPerson && !it.starting && it.status?.serverRunning != true
                                },
                        // Issue 147: the agents' helper that did not start is named with its Try again (the sheet and
                        // the home's notice), never left reading "Starting"; during an update the update speaks.
                        unreachable =
                            loaded.id in runtime.sourceFailures &&
                                (!agents || (!settling && loaded.id !in runtime.helperStarting.value)),
                        agents = agents,
                        starting =
                            runtime.startingSource(loaded.id, agentsUpdating = agents && updating != null)
                                ?: AppStart.Agents.takeIf {
                                    settling && loaded.id != active && runtime.listSourceShown(loaded.id)
                                },
                        updatingAgent =
                            updating?.agent?.takeIf { agents }?.let { AgentCatalog.builtIn.byId(it)?.name },
                        secret =
                            when {
                                loaded.reentry.password ->
                                    io.github.eslamasabry.oc.feature.chats.presentation.SwitcherSecret.Password
                                loaded.reentry.agentToken ->
                                    io.github.eslamasabry.oc.feature.chats.presentation.SwitcherSecret.Token
                                else -> io.github.eslamasabry.oc.feature.chats.presentation.SwitcherSecret.None
                            },
                    )
                }
                // F: the agents' switch comes after every server's.
                .sortedBy { it.agents }
                .toImmutableList()
        val owner = active?.let(runtime.profiles::profile)
        val opening =
            if (owner != null && !runtime.activeListReady()) {
                val failure = runtime.sourceFailures[active]
                ChatsOpeningUi(
                    phase =
                        OpeningRules.phase(
                            runtime.busy.value,
                            failure,
                            watchPhone(owner),
                            clock.now(),
                        ),
                    serverName = owner.profile.name,
                    rows = cachedRows(owner.id).toImmutableList(),
                    failure = failure,
                    profile = active,
                    onThisPhone = phoneServer.runsHere(active),
                    phoneOff = phoneServer.runsHere(active) && phone.serverFacts.value.stoppedByPerson,
                    address = owner.profile.baseUrl,
                    usesToken = owner.profile.usesAgentSocket,
                    attempts = openingTries(owner.id),
                )
            } else null
        if (opening == null) {
            phoneWatch = null
            openingTries = null
        }
        mutable.value = ChatsHomeExtraState(sources, opening, null, monitor.snapshot.value, stage = homeStage())
        if (opening != null && OpeningRules.worthAskingAgain(opening.phase, phoneWatch?.second)) keepTryingQuietly()
    }

    /**
     * The stage home names: This phone's own start first (Linux, then OpenCode), else the agents' helper's (helper,
     * then the agents) when its conversations are shown in the list. Null when no signal names one.
     */
    private fun homeStage(): HomeStage? =
        HomeStageRules.of(
            phone = stages.stageOfPhone().value,
            helpers =
                runtime.profiles.profiles.value
                    .map { it.id }
                    .filter { runtime.isPhoneHelper(it) && runtime.listSourceShown(it) }
                    .map { stages.stageOfHelper(it).value },
        )

    /**
     * E05-S20: the readings of this phone's own server (the built-in Linux server the app launches) while home waits
     * for its first answer; null for any other server, which keeps the plain connecting/failed rule.
     */
    private fun watchPhone(owner: LoadedProfile): PhoneStartWatch? {
        val facts = phone.serverFacts.value
        if (!builtIn(owner) || facts.status?.installed == false) {
            phoneWatch = null
            return null
        }
        val now = clock.now()
        val prior = phoneWatch?.takeIf { it.first == owner.id }?.second ?: PhoneStartWatch(since = now)
        val alive = facts.status?.serverRunning
        val next =
            prior.copy(
                startingSince = if (facts.starting) prior.startingSince ?: now else null,
                processAlive = alive,
                uptime = facts.status?.serverUptime?.takeIf { alive == true },
                seenAlive = prior.seenAlive || alive == true || facts.starting,
                stoppedByPerson = facts.stoppedByPerson,
                goneSince =
                    if (alive == false && !facts.starting && prior.seenAlive) prior.goneSince ?: now else null,
            )
        phoneWatch = owner.id to next
        return next
    }

    /**
     * A server saved at this phone's own address that is not the app's built-in one and not the agents' helper: the
     * person starts it themselves (the old app's Termux setup was migrated as such a profile).
     */
    private fun selfRun(owner: LoadedProfile): Boolean {
        if (owner.profile.usesAgentSocket || runtime.isPhoneHelper(owner.id) || builtIn(owner)) return false
        val address = owner.profile.baseUrl.substringAfter("://").substringBefore('/')
        val host = address.substringBeforeLast(':').trim('[', ']')
        return host == "127.0.0.1" || host == "localhost" || host == "::1"
    }

    private fun builtIn(owner: LoadedProfile): Boolean =
        !owner.profile.usesAgentSocket &&
            runtime.sourceOnThisPhone(owner.profile) &&
            owner.profile.baseUrl.trimEnd('/') in
                listOf(
                    "http://127.0.0.1:${PhoneServerScripts.PORT}",
                    "http://localhost:${PhoneServerScripts.PORT}",
                )

    /**
     * While this phone starts, home tries its server again every few seconds without showing a failure, and looks at
     * the clock again so the 90 s start window can end in the failure card.
     */
    private fun keepTryingQuietly() {
        if (quietRetry?.isActive == true) return
        quietRetry =
            scope.launch {
                while (true) {
                    // A server that reads failed but whose process runs is asked again, less often, so home
                    // recovers by itself when it answers late.
                    val failed = mutable.value.opening?.phase == ChatsOpeningPhase.Failed
                    delay(if (failed) QUIET_RETRY_WHEN_FAILED else QUIET_RETRY_EVERY)
                    val opening = mutable.value.opening ?: break
                    if (!OpeningRules.worthAskingAgain(opening.phase, phoneWatch?.second)) break
                    val profile = opening.profile
                    if (profile != null && !runtime.busy.value) runtime.retryListSource(profile)
                    publish()
                }
            }
    }

    override suspend fun showSource(profile: ProfileId, shown: Boolean): Outcome<Unit> =
        record(runtime.showListSource(profile, shown))

    override suspend fun retrySource(profile: ProfileId): Outcome<Unit> =
        record(runtime.retryListSource(profile))

    // How many times opening the active server has been tried since it first failed (1 = the first try).
    private var openingTries: Pair<ProfileId, Int>? = null

    private fun openingTries(profile: ProfileId): Int =
        openingTries?.takeIf { it.first == profile }?.second ?: 1

    override suspend fun phoneStorage(): io.github.eslamasabry.oc.feature.chats.presentation.PhoneStorageAccess? {
        val access = sharedStorage.singleOrNull() ?: return null
        val owner = phoneOwnerProfile() ?: return null
        val graph = registry.live.value[owner] ?: return null
        val block = (access.check(owner, "/storage/emulated/0", graph.session.server) as? Outcome.Ok)?.value ?: return null
        return if (block == io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock.None)
            io.github.eslamasabry.oc.feature.chats.presentation.PhoneStorageAccess.On
        else io.github.eslamasabry.oc.feature.chats.presentation.PhoneStorageAccess.Off
    }

    override suspend fun requestPhoneStorage(): Outcome<Unit> {
        val access = sharedStorage.singleOrNull() ?: return Outcome.Failed(Failure.Unavailable(FeatureId("phoneStorage")))
        val owner = phoneOwnerProfile() ?: return Outcome.Failed(Failure.Unavailable(FeatureId("phoneStorage")))
        val graph = registry.live.value[owner] ?: return Outcome.Failed(Failure.Unavailable(FeatureId("phoneStorage")))
        return access.request(
            owner,
            "/storage/emulated/0",
            io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock.AppAccess,
            graph.session.server,
        )
    }

    /** The saved This phone profile, when the app owns one (its own OpenCode). */
    private fun phoneOwnerProfile(): ProfileId? =
        try {
            (stores.prefs.get("oc.builtinServerOwner") as? io.github.eslamasabry.oc.core.data.kv.PrefValue.Str)?.value
                ?.takeIf { it.isNotEmpty() }
                ?.let(::ProfileId)
        } catch (_: Exception) {
            null
        }

    override suspend fun retryOpening(): Outcome<Unit> {
        val active =
            runtime.profiles.activeId.value
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        openingTries = active to (openingTries(active) + 1)
        return record(runtime.retryListSource(active))
    }

    override suspend fun setWatched(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        monitor.setWatched(profile, enabled)

    override suspend fun refreshWatched(): Outcome<Unit> = monitor.refresh()

    override suspend fun openWatched(
        key: String
    ): Outcome<io.github.eslamasabry.oc.core.common.ids.ConversationRef> = monitor.open(key)

    // The home pill's switcher: the same connect-and-make-active the Servers page runs on a tap.
    override suspend fun switchTo(profile: ProfileId): Outcome<Unit> = runtime.connect(profile)

    override suspend fun disconnect(profile: ProfileId): Outcome<Unit> = runtime.disconnect(profile)

    override suspend fun restartPhoneServer(profile: ProfileId): Outcome<Unit> = phoneServer.restart(profile)

    override suspend fun startPhoneServer(profile: ProfileId): Outcome<Unit> {
        val started = phoneServer.start(profile)
        if (started is Outcome.Ok) runtime.retryListSource(profile)
        return record(started)
    }

    private suspend fun record(result: Outcome<Unit>): Outcome<Unit> =
        withContext(lane) {
            publish()
            result
        }

    /**
     * The place the saved rows are kept under: the saved location only, as [NativeSessionInventory] and the opening
     * rows read it before any connection exists. It used to prefer the live connection's place, which moves (a
     * terminal or a schedule sets it) and is Default before it is restored, so a list saved under one place was not
     * found at the next cold start and the home said "No conversations yet" until the server answered.
     */
    private fun location(owner: LoadedProfile): ServerLocation {
        if (owner.profile.usesAgentSocket)
            return ServerLocation(directory = owner.profile.codexDirectory)
        val saved =
            stores.prefs.string("oc.location.${owner.id.raw}")?.let(DartJson::decodeOrNull)
                as? Map<*, *>
        return ServerLocation(
            directory = saved?.get("directory") as? String,
            workspace = saved?.get("workspace") as? String,
        )
    }

    private fun inventoryScope(owner: LoadedProfile): String {
        val p = owner.profile
        val loc = location(owner)
        val backend =
            when (p.backend) {
                BackendKind.OpenCode2 -> "openCode"
                BackendKind.Paseo -> "paseo"
                BackendKind.Codex -> "codex"
            }
        return DartJson.sha256Hex(
            DartJson.encode(
                listOf(backend, "v2", p.baseUrl, p.username, loc.directory, loc.workspace)
            )
        )
    }

    private val previewLines = mutableMapOf<String, String>()

    override fun cachedRows(profile: ProfileId): List<ChatFeedItem> {
        val owner = runtime.profiles.profile(profile) ?: return emptyList()
        return readInventory(owner)
    }

    override fun rememberPreviews(lines: Map<String, String>) {
        var changed = false
        for ((identity, text) in lines) if (previewLines.put(identity, text) != text) changed = true
        if (changed) scope.launch { saveCurrentInventory() }
    }

    private fun readInventory(owner: LoadedProfile): List<ChatFeedItem> {
        if (owner.cleartextUnconfirmed || owner.reentry.password || owner.reentry.agentToken)
            return emptyList()
        val raw = stores.prefs.string("oc.sessionInventory.${owner.id.raw}") ?: return emptyList()
        if (raw.length > 65_536) return emptyList()
        val data = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return emptyList()
        if (data["v"] != 1L && data["v"] != 1) return emptyList()
        if (data["scope"] != inventoryScope(owner)) return emptyList()
        val rows = data["rows"] as? List<*> ?: return emptyList()
        if (rows.size > 100) return emptyList()
        return rows
            .mapNotNull { value ->
                val row = value as? Map<*, *> ?: return@mapNotNull null
                val id = row["id"] as? String ?: return@mapNotNull null
                val title = row["title"] as? String ?: return@mapNotNull null
                if (id.isEmpty() || id.length > 512 || title.length > 1024) return@mapNotNull null
                val dir = (row["dir"] as? String)?.take(1024)
                ChatFeedItem(
                    sourceId = row["src"] as? String ?: "",
                    profileId = owner.id,
                    sessionId = SessionId(id),
                    title = redactor.redact(title),
                    directory = dir,
                    // A saved row always shows its project: from the saved name, else from its folder as a live row does.
                    projectName =
                        (row["project"] as? String)?.takeIf { it.isNotBlank() }
                            ?: io.github.eslamasabry.oc.engine.agents.feed.FeedFolders.rowLabel(dir),
                    status = ChatStatus.Idle,
                    lastActivityMs = (row["updated"] as? Number)?.toLong() ?: 0L,
                    preview = redactor.redact((row["preview"] as? String).orEmpty()).take(256),
                    agentId = row["agent"] as? String ?: OPENCODE_AGENT_ID,
                    agentLabel = row["agentLabel"] as? String,
                    sourceLabel = row["srcLabel"] as? String,
                    locationKey = row["loc"] as? String,
                )
            }
            .distinctBy { it.identity }
    }

    /**
     * Saves the list of every source whose live list is read, not only the server in use: the agents on this phone
     * (and any other source that joined the list) were saved only when All conversations was opened, so their saved
     * rows were days old and without their agent or folder when the app next started.
     */
    private suspend fun saveCurrentInventory() {
        for (id in runtime.loadedSourceIds()) saveInventory(id)
    }

    private suspend fun saveInventory(id: ProfileId) {
        val owner = runtime.profiles.profile(id) ?: return
        val graph = registry.live.value[id] ?: return
        val signature = inventoryScope(owner)
        val items =
            runtime.snapshot.value.items
                // Sub-conversations belong to their parent and are never counted on home: saving them made the saved
                // count (and its project names) disagree with the first live read.
                .filter { it.profileId == id && !it.isSubagent }
                .sortedByDescending { it.lastActivityMs }
                .take(100)
        val withLines = items.map { it.copy(preview = previewLines[it.identity] ?: it.preview) }
        if (cachedRevisions[id] == (signature to withLines)) return
        val rows = mutableListOf<Map<String, Any>>()
        var bytes = 0
        for (item in items) {
            if (item.sessionId.raw.isEmpty() || item.sessionId.raw.length > 512) continue
            val row =
                mapOf(
                    "id" to item.sessionId.raw,
                    "title" to redactor.redact(item.title.orEmpty()).take(256),
                    "updated" to item.lastActivityMs,
                    "src" to item.sourceId,
                    "project" to item.projectName.take(256),
                    "agent" to item.agentId,
                )
                    .plus(optional("dir", item.directory?.take(1024)))
                    .plus(optional("loc", item.locationKey))
                    .plus(optional("agentLabel", item.agentLabel?.take(128)))
                    .plus(optional("srcLabel", item.sourceLabel?.take(128)))
                    .plus(
                        optional(
                            "preview",
                            (previewLines[item.identity] ?: item.preview).takeIf { it.isNotBlank() }?.let { redactor.redact(it).take(256) },
                        )
                    )
            bytes += DartJson.encode(row).encodeToByteArray().size + 1
            if (bytes > 65_024) break
            rows += row
        }
        val encoded =
            DartJson.encode(
                mapOf(
                    "v" to 1,
                    "scope" to signature,
                    "at" to clock.now().toEpochMilliseconds(),
                    "rows" to rows,
                )
            )
        val result =
            stores.prefs.commit(stores.lanes.forProfile(id)) {
                if (
                    runtime.profiles.profile(id) == owner &&
                        registry.live.value[id] === graph &&
                        inventoryScope(owner) == signature
                ) {
                    putString("oc.sessionInventory.${id.raw}", encoded)
                }
            }
        if (result is Outcome.Ok) cachedRevisions[id] = signature to withLines
    }
}

/**
 * G6 (F: lib/ui/screens/agents/agent_notices.dart:84-157): the home's lines about the phone's agents, read from the
 * same state the Agents page shows. A sign-in or plan line only for an agent New conversation offers (certified for
 * chat); a stopped agent is said only when its helper really stopped ([HomeAgentNoticeRules]).
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@Inject
class AppHomeAgentNotices(phoneAgents: PhoneAgents, appScope: AppCoroutineScope) : HomeAgentNoticeSource {
    override val notices: StateFlow<HomeAgentNotices> =
        phoneAgents.state
            .map { state ->
                HomeAgentNoticeRules.notices(state) { AgentCertification.bundled.certifiedForChat(AgentId(it)) }
            }
            .distinctUntilChanged()
            .stateIn(appScope, SharingStarted.Eagerly, HomeAgentNotices())
}

internal object HomeAgentNoticeRules {
    /**
     * The home's agent lines from the Agents page's [state]. A row reads Stopped whenever the agent helper is not
     * running in this app process (PhoneAgentRules.status: `qualified && helperRunning == false`), and the helper only
     * starts when the Agents page opens (AgentsPresenter.connectWhenInstalled). So after every app start an installed
     * agent read "stopped in the background" although nothing had stopped. The stop line is said only when the helper
     * was seen to stop on its own ([PhoneAgentsState.helperStoppedUnexpectedly]: its exit record, not asked for, from
     * its latest run and since boot and the app's update; the "maybe Android" flag only refines that and never says a
     * stop by itself). Its Resume opens the Agents page, which starts the helper again; that launch supersedes the
     * record, so the line does not come back. Flutter starts the helper itself and hides the line meanwhile
     * (phone_agents.dart:610-616, 684-692).
     */
    fun notices(state: PhoneAgentsState, certifiedForChat: (String) -> Boolean): HomeAgentNotices {
        if (!state.available) return HomeAgentNotices()
        val helperStopped = state.helperStoppedUnexpectedly
        return HomeAgentNotices(
            restart = state.needsRestart,
            lines =
                state.agents
                    .mapNotNull { row ->
                        val kind =
                            when (row.status) {
                                // A sign-in check that could not run is not news that it is signed out (Flutter: failed
                                // is "unavailable", which the home does not list).
                                PhoneAgentStatus.SignInNeeded ->
                                    if (phoneAgentSignedOut(row)) HomeAgentNoticeKind.SignedOut
                                    else return@mapNotNull null
                                PhoneAgentStatus.Stopped ->
                                    if (helperStopped) HomeAgentNoticeKind.Stopped else return@mapNotNull null
                                PhoneAgentStatus.Limit -> HomeAgentNoticeKind.LimitReached
                                else -> return@mapNotNull null
                            }
                        if (kind != HomeAgentNoticeKind.Stopped && !certifiedForChat(row.id)) return@mapNotNull null
                        HomeAgentNotice(row.id, row.name, kind)
                    }
                    .toImmutableList(),
        )
    }
}

/** When the helper's source stops being "settling": the moment it is listed, else after the bound. */
internal object HelperSettleRules {
    fun settling(sinceWorkMs: Long, boundMs: Long, listed: Boolean): Boolean = !listed && sinceWorkMs < boundMs
}

/** The settle of each agents' helper: when its work last showed, and whether it still settles now. */
internal class HelperSettle(private val boundMs: Long) {
    private val workAt = java.util.concurrent.ConcurrentHashMap<ProfileId, Long>()

    fun settling(id: ProfileId, workNow: Boolean, listed: Boolean, nowMs: Long): Boolean {
        if (workNow) workAt[id] = nowMs
        val since = workAt[id] ?: return false
        return HelperSettleRules.settling(nowMs - since, boundMs, listed)
    }
}

/** Which stage home names, from the stage source's readings (pure, so it is tested without the app graph). */
internal object HomeStageRules {
    fun of(phone: LaunchStage?, helpers: List<LaunchStage?>): HomeStage? =
        home(phone) ?: helpers.firstNotNullOfOrNull(::home)

    private fun home(stage: LaunchStage?): HomeStage? =
        when (stage) {
            LaunchStage.Linux -> HomeStage.Linux
            LaunchStage.OpenCode -> HomeStage.OpenCode
            LaunchStage.Helper -> HomeStage.Helper
            LaunchStage.Agent -> HomeStage.Agents
            // The conversation's own read is the chat page's stage, not home's.
            LaunchStage.Conversation, null -> null
        }
}

/**
 * The phone server facts home reads ([AppChatsHome]'s phone watch and Stop line). The uptime is read again by the
 * quiet retries while home waits, so it is not a reason to publish home by itself.
 */
private data class HomePhoneReading(
    val installed: Boolean?,
    val running: Boolean?,
    val starting: Boolean,
    val stoppedByPerson: Boolean,
) {
    companion object {
        fun of(facts: io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts): HomePhoneReading =
            HomePhoneReading(
                installed = facts.status?.installed,
                running = facts.status?.serverRunning,
                starting = facts.starting,
                stoppedByPerson = facts.stoppedByPerson,
            )
    }
}

/** What home's saved list is built from: the feed and the live graphs by identity (a new value is a new instance). */
private class InventoryInputs(private val feed: Any, private val sources: Set<ProfileId>, private val graphs: Any) {
    override fun equals(other: Any?): Boolean =
        other is InventoryInputs && other.feed === feed && other.graphs === graphs && other.sources == sources

    override fun hashCode(): Int = System.identityHashCode(feed)
}
