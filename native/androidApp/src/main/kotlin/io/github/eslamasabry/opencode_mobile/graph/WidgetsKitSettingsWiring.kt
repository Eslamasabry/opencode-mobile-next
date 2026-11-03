package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetOtherConversation
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetRowDetails
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetServerLine
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSession
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotInput
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotWords
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetText
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/*
 * BUILD-WIDGETS-KIT-SETTINGS wiring. Each worker of the unit adds its own section below; never rewrite another's.
 */

// ---- A1: home-screen widget snapshot (B-ANDR-111..117, B-ANDR-120) ----------------------------------------------------

/**
 * Maps the live conversation feed to the `:android:widgets` snapshot builder's input (Flutter
 * `ConnectionController._widgetSessionDetails` / `_widgetOtherConversations` / `_widgetServers`). The active server's
 * conversations keep their details (project, reply preview, the permission ask with its request id, or a question);
 * other servers' conversations are plain rows that name their server and carry no request identity.
 */
internal object WidgetSnapshotWiring {
    private val fileKeys = listOf("filePath", "filepath", "file_path", "path", "file")

    fun input(
        feed: ChatFeedSnapshot,
        waiting: List<Waiting>,
        active: ProfileId,
        names: Map<ProfileId, String>,
        online: Set<ProfileId>,
        offlineSince: MutableMap<ProfileId, Long>,
        nowMs: Long,
        text: WidgetText,
        words: WidgetSnapshotWords,
    ): WidgetSnapshotInput {
        val items = feed.items.filter { !it.isSubagent }
        val own = items.filter { it.profileId == active }
        val details =
            own.associate { item ->
                val asks = waiting.filter { it.profile == active && it.request.sessionId == item.sessionId }
                val permission = asks.firstNotNullOfOrNull { it.request as? PendingRequest.Permission }
                val question = asks.firstNotNullOfOrNull { it.request as? PendingRequest.Question }
                val base = WidgetRowDetails(project = item.directory ?: item.projectName, preview = item.preview)
                item.sessionId.raw to
                    when {
                        permission != null -> base.copy(requestId = permission.id.raw, ask = ask(permission, text, words))
                        question != null -> {
                            val prompt = question.question.prompts.firstOrNull()
                            val q = prompt?.question?.takeIf { it.isNotBlank() } ?: prompt?.title.orEmpty()
                            base.copy(needsYou = true, ask = if (q.isBlank()) null else mapOf("kind" to "other", "q" to q))
                        }
                        else -> base
                    }
            }
        val others =
            items
                .filter { it.profileId != active }
                .take(12)
                .map { item ->
                    WidgetOtherConversation(
                        id = item.sessionId.raw,
                        title = item.title,
                        project = item.projectName,
                        updatedAt = item.lastActivityMs,
                        busy = item.status == ChatStatus.Running,
                        needsYou =
                            item.status == ChatStatus.NeedsYou ||
                                waiting.any { it.profile == item.profileId && it.request.sessionId == item.sessionId },
                        server = names[item.profileId].orEmpty(),
                        profileId = item.profileId.raw,
                    )
                }
        offlineSince.keys.retainAll(names.keys)
        val servers =
            names.map { (id, name) ->
                if (id in online) {
                    offlineSince.remove(id)
                    WidgetServerLine(name, "online")
                } else {
                    WidgetServerLine(name, "offline", since = offlineSince.getOrPut(id) { nowMs })
                }
            }
        return WidgetSnapshotInput(
            sessions = own.map { WidgetSession(it.sessionId.raw, it.title, it.lastActivityMs) },
            busy = own.filter { it.status == ChatStatus.Running }.map { it.sessionId.raw }.toSet(),
            connected = active in online,
            profileId = active.raw,
            pendingCount = waiting.count { it.profile == active },
            details = details,
            others = others,
            server = names[active].orEmpty(),
            servers = servers,
            projects = own.mapNotNull { it.directory },
        )
    }

    private fun ask(r: PendingRequest.Permission, text: WidgetText, words: WidgetSnapshotWords): Map<String, String> {
        fun meta(key: String) = (r.request.metadata[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val command = meta("command")
        val file = fileKeys.firstNotNullOfOrNull(::meta)
        return text.askFor(
            permission = r.request.permission,
            patterns = r.request.patterns,
            words = words,
            tool = if (command != null) "bash" else null,
            target = command ?: file,
        )
    }
}

// ---- A2: widget action and status runners (B-ANDR-103..107, B-ANDR-118/119/121, B-SEC-180/183, B-AGENT-317) -----------

/** Started from OcApplication right after the platform hub (which binds WidgetFeed.persistence). */
@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
interface WidgetRunnerAccessors {
    val widgetRunners: WidgetRunnerWiring
}

/**
 * Binds `WidgetFeed.backgroundRunner` (a cold tap or a scheduled status check opens its own private OpenCode 2
 * connection through the protocol-neutral gateway) and `WidgetFeed.liveDispatcher` (a tap while the tapped server is
 * live is answered by that live connection), and forwards whether the active server is live to `WidgetFeed`.
 */
@dev.zacsweers.metro.SingleIn(dev.zacsweers.metro.AppScope::class)
@dev.zacsweers.metro.Inject
class WidgetRunnerWiring(
    private val application: android.app.Application,
    private val stores: PreviewStores,
    private val registry: io.github.eslamasabry.oc.engine.connection.ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val factories: Set<io.github.eslamasabry.oc.core.domain.GatewayFactory>,
    private val credentialSources: Set<io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource>,
    private val appScope: io.github.eslamasabry.oc.core.common.AppCoroutineScope,
    private val dispatchers: io.github.eslamasabry.oc.core.common.AppDispatchers,
    private val clock: kotlin.time.Clock,
    private val redactor: io.github.eslamasabry.oc.core.security.Redactor,
) {
    private val log = io.github.eslamasabry.oc.core.common.AppLog.tagged("WidgetRunners")
    @Volatile private var started = false

    /** Where the service shell runs one admitted tap. */
    val scope: kotlinx.coroutines.CoroutineScope get() = appScope

    /** One admission (one process lease) for every widget tap; the service shell holds the foreground. */
    val admission: io.github.eslamasabry.oc.android.widgets.WidgetActionAdmission by lazy {
        io.github.eslamasabry.oc.android.widgets.WidgetActionAdmission(
            application,
            io.github.eslamasabry.opencode_mobile.WidgetServiceForeground,
        )
    }

    fun start() {
        if (started) return
        val persistence = io.github.eslamasabry.oc.android.widgets.WidgetFeed.persistence ?: return
        started = true
        val now = { clock.now().toEpochMilliseconds() }
        io.github.eslamasabry.oc.android.widgets.WidgetFeed.backgroundRunner =
            io.github.eslamasabry.oc.android.widgets.runner.WidgetRunnerHost(
                appScope,
                persistence,
                ::profile,
                ::open,
                io.github.eslamasabry.oc.android.widgets.runner.WidgetRunPreview(redactor::redact)::of,
                now,
            )
        io.github.eslamasabry.oc.android.widgets.WidgetFeed.liveDispatcher =
            io.github.eslamasabry.oc.android.widgets.runner.WidgetLiveHost(
                appScope,
                io.github.eslamasabry.oc.android.widgets.runner.WidgetLiveActions(
                    AppWidgetLiveWorld(stores, registry, runtime)
                ),
            )
        appScope.launch {
            kotlinx.coroutines.flow
                .combine(registry.live, stores.profiles.activeId) { live, active -> active != null && active in live }
                .distinctUntilChanged()
                .collect { live ->
                    io.github.eslamasabry.oc.android.widgets.WidgetFeed.onConnection(application, live)
                }
        }
    }

    /** Metadata fingerprint of a saved profile; never a secret. */
    private suspend fun profile(id: String): io.github.eslamasabry.oc.android.widgets.runner.WidgetRunProfile? {
        if (stores.warm() is Outcome.Failed) return null
        val loaded = stores.profiles.profile(ProfileId(id)) ?: return null
        return io.github.eslamasabry.oc.android.widgets.runner.WidgetRunProfile(
            id,
            io.github.eslamasabry.oc.android.widgets.runner.widgetProfileFingerprint(loaded.profile),
            loaded.profile.transientTransport,
        )
    }

    /**
     * A private cold connection pinned to the target's location (Flutter `createWidgetActionTransport`). OpenCode 2
     * only; a profile that needs migration, a password re-entry or cleartext consent is unsupported. The connection
     * opens no live event stream (its scope is already closed, so the stream never starts): a tap reads and answers,
     * it never subscribes. The person's own live connection is never touched.
     */
    private suspend fun open(
        id: String,
        directory: String?,
        workspace: String?,
    ): io.github.eslamasabry.oc.android.widgets.runner.WidgetOpen {
        val unsupported =
            io.github.eslamasabry.oc.android.widgets.runner.WidgetOpen.Refused(
                io.github.eslamasabry.oc.android.widgets.runner.WidgetOpenRefusal.Unsupported
            )
        val unavailable =
            io.github.eslamasabry.oc.android.widgets.runner.WidgetOpen.Refused(
                io.github.eslamasabry.oc.android.widgets.runner.WidgetOpenRefusal.Unavailable
            )
        if (stores.warm() is Outcome.Failed) return unavailable
        val loaded = stores.profiles.profile(ProfileId(id)) ?: return unavailable
        val stored = loaded.profile
        if (
            stored.backend != io.github.eslamasabry.oc.core.model.BackendKind.OpenCode2 ||
                stored.requiresOpenCode2Migration ||
                stored.transientTransport ||
                loaded.reentry.password ||
                loaded.cleartextUnconfirmed
        )
            return unsupported
        val server = loaded.toServerProfile() ?: return unsupported
        val factory = factories.firstOrNull { it.backend == server.backend } ?: return unsupported
        val credentials =
            when (val read = credentialSources.singleOrNull()?.resolve(server)) {
                null -> io.github.eslamasabry.oc.core.domain.ConnectionCredentials.None
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return unavailable
            }
        val location = io.github.eslamasabry.oc.core.model.ServerLocation(directory, workspace)
        val opened = widgetColdOpen(factory, server, credentials, location, dispatchers)
        if (opened is io.github.eslamasabry.oc.android.widgets.runner.WidgetOpen.Refused) log.i { "widget run: no connection" }
        return opened
    }
}

/**
 * One private, cold OpenCode 2 connection for a widget run (shared with its HTTP test). The scope handed to the
 * gateway is already closed, so the live event stream never starts; reconnecting is off. Closing the transport
 * closes the gateway.
 */
internal suspend fun widgetColdOpen(
    factory: io.github.eslamasabry.oc.core.domain.GatewayFactory,
    server: io.github.eslamasabry.oc.core.model.ServerProfile,
    credentials: io.github.eslamasabry.oc.core.domain.ConnectionCredentials,
    location: io.github.eslamasabry.oc.core.model.ServerLocation,
    dispatchers: io.github.eslamasabry.oc.core.common.AppDispatchers,
): io.github.eslamasabry.oc.android.widgets.runner.WidgetOpen {
    val closed = kotlinx.coroutines.Job().apply { cancel() }
    val scope = io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope(closed + dispatchers.default)
    val spec =
        io.github.eslamasabry.oc.core.domain.GatewaySpec(
            server,
            credentials,
            scope,
            location,
            reconnectAllowed = kotlinx.coroutines.flow.MutableStateFlow(false),
        )
    return when (val pair = factory.connect(spec)) {
        is Outcome.Failed ->
            io.github.eslamasabry.oc.android.widgets.runner.WidgetOpen.Refused(
                io.github.eslamasabry.oc.android.widgets.runner.WidgetOpenRefusal.Unavailable
            )
        is Outcome.Ok -> {
            val gateway = pair.value.live
            if (gateway.location.value != location) gateway.setLocation(location)
            io.github.eslamasabry.oc.android.widgets.runner.WidgetOpen.Opened(
                io.github.eslamasabry.oc.android.widgets.runner.GatewayRunTransport(gateway)
            )
        }
    }
}

/**
 * The live connection's view for widget taps: the active server's live graph (its request engine and gateway), the
 * conversation feed for running state and the published widget snapshot for the scope and run start the tap carries.
 */
internal class AppWidgetLiveWorld(
    private val stores: PreviewStores,
    private val registry: io.github.eslamasabry.oc.engine.connection.ConnectionRegistry,
    private val runtime: PreviewRuntime,
) : io.github.eslamasabry.oc.android.widgets.runner.WidgetLiveWorld {
    private fun active(): ProfileId? = stores.profiles.activeId.value

    private fun graph() = active()?.let { registry.live.value[it] }

    private fun gateway(): io.github.eslamasabry.oc.core.domain.ServerGateway? = graph()?.session?.server

    private fun engine() =
        (graph() as? io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors)?.chatRequests?.engine

    private fun snapshot(): Map<*, *>? =
        try {
            (stores.prefs.get("oc.widgetSessions") as? io.github.eslamasabry.oc.core.data.kv.PrefValue.Str)
                ?.value
                ?.let { io.github.eslamasabry.oc.core.common.dart.DartJson.decodeOrNull(it) as? Map<*, *> }
        } catch (_: IllegalStateException) {
            null // preferences not warm yet: nothing is current
        }

    override fun isCurrent(tap: io.github.eslamasabry.oc.android.widgets.runner.WidgetTap): Boolean {
        val snapshot = snapshot() ?: return false
        return active()?.raw == tap.profileId &&
            graph() != null &&
            snapshot["profileID"] == tap.profileId &&
            snapshot["scopeToken"] == tap.scopeToken
    }

    override fun pendingPermission(requestId: String): io.github.eslamasabry.oc.android.widgets.runner.WidgetPendingAsk? {
        val entry =
            engine()?.pending?.value?.firstOrNull {
                it is PendingRequest.Permission &&
                    it.id.raw == requestId &&
                    (it.phase is io.github.eslamasabry.oc.engine.requests.AnswerPhase.Waiting ||
                        it.phase is io.github.eslamasabry.oc.engine.requests.AnswerPhase.NotAccepted)
            } ?: return null
        return io.github.eslamasabry.oc.android.widgets.runner.WidgetPendingAsk(entry.id.raw, entry.sessionId.raw)
    }

    override fun startedAt(session: String): Long {
        val profile = active() ?: return 0
        val running =
            runtime.snapshot.value.items.any {
                it.profileId == profile && it.sessionId.raw == session && it.status == ChatStatus.Running
            }
        if (!running) return 0
        val rows = (snapshot()?.get("sessions") as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        return (rows.firstOrNull { it["id"] == session }?.get("startedAt") as? Number)?.toLong() ?: 0
    }

    override fun steerMode(session: String): String = gateway()?.let { widgetSteerMode(it, session) } ?: "unknown"

    override val promptSteering: Boolean
        get() = gateway()?.capabilities?.value?.promptSteering == true

    override suspend fun answer(session: String, request: String, allowOnce: Boolean): Outcome<Unit> {
        val engine = engine() ?: return Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unexpected())
        val reply =
            if (allowOnce) io.github.eslamasabry.oc.core.model.PermissionReply.Once
            else io.github.eslamasabry.oc.core.model.PermissionReply.Reject
        return engine.answerNow(
            io.github.eslamasabry.oc.core.common.ids.RequestId(request),
            io.github.eslamasabry.oc.engine.requests.RequestAnswer.Permission(reply),
        )
    }

    override suspend fun stop(session: String): Outcome<Unit> {
        val gateway = gateway() ?: return Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unexpected())
        return when (val stopped = gateway.stop(io.github.eslamasabry.oc.core.common.ids.SessionId(session))) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Failed -> stopped
        }
    }

    override suspend fun send(
        session: String,
        text: String,
        delivery: io.github.eslamasabry.oc.core.model.PromptDelivery?,
    ): Outcome<Unit> {
        val gateway = gateway() ?: return Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unexpected())
        val sent =
            gateway.prompt(
                io.github.eslamasabry.oc.core.common.ids.SessionId(session),
                io.github.eslamasabry.oc.core.model.OutgoingPrompt(text, delivery = delivery),
            )
        return when {
            sent is Outcome.Ok && sent.value is io.github.eslamasabry.oc.core.model.PromptAdmission.Confirmed ->
                Outcome.Ok(Unit)
            // A lost response is uncertain: the caller reports it and never resends.
            sent is Outcome.Failed -> sent
            else -> Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unexpected())
        }
    }
}

/**
 * Adds the on-demand identity the runners check to each published widget snapshot (Flutter `_currentWidgetScope`,
 * `_widgetOnDemandActions`, `_widgetRunMarkers`, `_widgetStartedAt`, `_widgetSteerMode` and the private target): a
 * scope token that changes with the connection or its location, the profile's metadata fingerprint, per row the
 * buttons (allow once / reject for a pending permission, stop for a running turn whose user message was seen), the
 * run marker, a stable run start and the steer mode. The directory and workspace go only to the private target.
 * Without a live, supported connection the snapshot carries no buttons.
 */
internal class WidgetOnDemandWiring(
    private val stores: PreviewStores,
    private val registry: io.github.eslamasabry.oc.engine.connection.ConnectionRegistry,
    private val now: () -> Long,
) {
    private val scope =
        io.github.eslamasabry.oc.android.widgets.runner.WidgetActionScope { size ->
            ByteArray(size).also { java.security.SecureRandom().nextBytes(it) }
        }
    private val watched = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>())
    private val markers = java.util.concurrent.ConcurrentHashMap<String, String>() // "profile|session" -> message id
    private val started = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** The snapshot input with on-demand identity, and the private target to publish with it (null: none). */
    fun decorate(
        input: io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotInput,
        profile: ProfileId,
    ): Pair<io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotInput, String?> {
        val graph = registry.live.value[profile] ?: return input to null
        watch(profile, graph)
        val loaded = stores.profiles.profile(profile) ?: return input to null
        val stored = loaded.profile
        val supported =
            stored.backend == io.github.eslamasabry.oc.core.model.BackendKind.OpenCode2 &&
                !stored.requiresOpenCode2Migration &&
                !stored.transientTransport &&
                !loaded.reentry.password &&
                !loaded.cleartextUnconfirmed
        val gateway = graph.session.server
        val location = gateway.location.value
        val token = scope.token(listOf(profile.raw, System.identityHashCode(graph), location.directory, location.workspace))
        val fingerprint = io.github.eslamasabry.oc.android.widgets.runner.widgetProfileFingerprint(stored)
        val time = now()
        val details =
            input.sessions.associate { session ->
                val id = session.id
                val key = "${profile.raw}|$id"
                val busy = id in input.busy
                val startedAt = if (busy) started.getOrPut(key) { time } else 0L.also { started.remove(key) }
                val base = input.details[id] ?: io.github.eslamasabry.oc.android.widgets.snapshot.WidgetRowDetails()
                val marker = markers[key]
                id to
                    base.copy(
                        startedAt = if (busy) startedAt else base.startedAt,
                        runMarker = marker,
                        steerMode = widgetSteerMode(gateway, id),
                        onDemandActions =
                            io.github.eslamasabry.oc.android.widgets.runner.WidgetActionScope.actions(
                                supported,
                                permission = !base.requestId.isNullOrEmpty(),
                                busy = busy,
                                runMarker = marker,
                            ),
                    )
            }
        val target =
            io.github.eslamasabry.oc.android.widgets.runner.WidgetActionScope.target(
                profile.raw,
                token,
                fingerprint,
                location.directory,
                location.workspace,
            )
        return input.copy(scopeToken = token, onDemandFingerprint = fingerprint, details = input.details + details) to
            target
    }

    /** The server user-message identity of each conversation, from the live stream (never its text). */
    private fun watch(profile: ProfileId, graph: io.github.eslamasabry.oc.engine.connection.ConnectionGraph) {
        if (!synchronized(watched) { watched.add(graph) }) return
        graph.scope.launch {
            graph.session.server.events.all.collect { envelope ->
                val event = envelope.event
                if (
                    event is io.github.eslamasabry.oc.core.domain.events.GatewayEvent.MessageUpserted &&
                        event.message.role == io.github.eslamasabry.oc.core.model.transcript.Role.User &&
                        event.message.id.isNotEmpty()
                ) {
                    markers["${profile.raw}|${event.message.sessionId}"] = event.message.id
                }
            }
        }
    }
}

/** Flutter `_widgetSteerMode`: steer where the server steers, else after-reply; unknown / restart never send. */
internal fun widgetSteerMode(gateway: io.github.eslamasabry.oc.core.domain.ServerGateway, session: String): String {
    if (gateway.capabilities.value.promptSteering) return "steer"
    val midTurn = gateway as? io.github.eslamasabry.oc.core.domain.MidTurnPromptGateway ?: return "afterReply"
    return when (midTurn.midTurnPrompt(io.github.eslamasabry.oc.core.common.ids.SessionId(session))) {
        io.github.eslamasabry.oc.core.model.MidTurnPrompt.JoinsTurn -> "steer"
        io.github.eslamasabry.oc.core.model.MidTurnPrompt.RestartsTurn -> "restart"
        null -> "unknown"
    }
}
