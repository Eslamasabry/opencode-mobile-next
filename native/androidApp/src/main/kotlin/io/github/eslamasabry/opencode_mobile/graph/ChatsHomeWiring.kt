package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
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
import io.github.eslamasabry.oc.engine.agents.certification.AgentCertification
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How often home quietly tries this phone's server again while it starts (E05-S20). */
private fun optional(key: String, value: String?): Map<String, Any> = if (value == null) emptyMap() else mapOf(key to value)

private val QUIET_RETRY_EVERY = 3.seconds
private val STOP_SETTLES = 3.seconds

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
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) : ChatsHomeExtras, HomeFeedCache {
    private val lane = dispatchers.serial("chats-home")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val mutable = MutableStateFlow(ChatsHomeExtraState(known = false))
    override val snapshot: StateFlow<ChatsHomeExtraState> = mutable.asStateFlow()
    private val cachedRevisions = mutableMapOf<ProfileId, Pair<String, List<ChatFeedItem>>>()
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
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            combine(
                    runtime.servers,
                    runtime.busy,
                    runtime.snapshot,
                    runtime.profiles.activeId,
                    combine(monitor.snapshot, phone.serverFacts) { _, _ -> Unit },
                ) { _, _, _, _, _ ->
                    Unit
                }
                .collect {
                    publish()
                    saveCurrentInventory()
                }
        }
    }

    private fun publish() {
        val active = runtime.profiles.activeId.value
        val sources =
            runtime.profiles.profiles.value
                .map { loaded ->
                    ChatsListSourceUi(
                        profile = loaded.id,
                        name = loaded.profile.name,
                        shown = runtime.listSourceShown(loaded.id),
                        main = loaded.id == active,
                        onThisPhone = runtime.sourceOnThisPhone(loaded.profile),
                        unreachable = loaded.id in runtime.sourceFailures,
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
        mutable.value = ChatsHomeExtraState(sources, opening, null, monitor.snapshot.value)
        if (opening?.phase == ChatsOpeningPhase.Starting) keepTryingQuietly()
    }

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
            )
        phoneWatch = owner.id to next
        return next
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
                    delay(QUIET_RETRY_EVERY)
                    val opening = mutable.value.opening ?: break
                    if (opening.phase != ChatsOpeningPhase.Starting) break
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

    private fun location(owner: LoadedProfile): ServerLocation {
        registry.live.value[owner.id]?.session?.server?.location?.value?.let {
            return it
        }
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
                    projectName = row["project"] as? String ?: "",
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

    private suspend fun saveCurrentInventory() {
        val id = runtime.profiles.activeId.value ?: return
        val owner = runtime.profiles.profile(id) ?: return
        if (!runtime.activeListReady()) return
        val graph = registry.live.value[id] ?: return
        val signature = inventoryScope(owner)
        val items =
            runtime.snapshot.value.items
                .filter { it.profileId == id }
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
 * chat); a stopped agent is always worth saying, so Resume is one tap away.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@Inject
class AppHomeAgentNotices(phoneAgents: PhoneAgents, appScope: AppCoroutineScope) : HomeAgentNoticeSource {
    override val notices: StateFlow<HomeAgentNotices> =
        phoneAgents.state
            .map { state ->
                if (!state.available) HomeAgentNotices()
                else
                    HomeAgentNotices(
                        restart = state.needsRestart,
                        lines =
                            state.agents
                                .mapNotNull { row ->
                                    val kind =
                                        when (row.status) {
                                            PhoneAgentStatus.SignInNeeded -> HomeAgentNoticeKind.SignedOut
                                            PhoneAgentStatus.Stopped -> HomeAgentNoticeKind.Stopped
                                            PhoneAgentStatus.Limit -> HomeAgentNoticeKind.LimitReached
                                            else -> return@mapNotNull null
                                        }
                                    if (
                                        kind != HomeAgentNoticeKind.Stopped &&
                                            !AgentCertification.bundled.certifiedForChat(AgentId(row.id))
                                    )
                                        return@mapNotNull null
                                    HomeAgentNotice(row.id, row.name, kind)
                                }
                                .toImmutableList(),
                    )
            }
            .distinctUntilChanged()
            .stateIn(appScope, SharingStarted.Eagerly, HomeAgentNotices())
}
