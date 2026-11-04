package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.*
import io.github.eslamasabry.oc.engine.agents.certification.AgentCertification
import io.github.eslamasabry.oc.engine.agents.gate.PhoneGate
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.engine.agents.host.PaseoConfig
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.agents.hub.AgentHandoffDrafts
import io.github.eslamasabry.oc.feature.handoff.presentation.*
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.feature.chat.header.world.AgentHandoffChoice as ChatAgentHandoffChoice
import io.github.eslamasabry.oc.feature.chat.header.world.ChatAgentHandoff
import io.github.eslamasabry.oc.feature.chat.header.world.ChatAgentHandoffs
import io.github.eslamasabry.oc.protocol.codex.CodexGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.genUiHistoryPage
import io.github.eslamasabry.oc.protocol.paseo.gateway.writeAgentHandoff
import io.github.eslamasabry.oc.protocol.paseo.work.PaseoPayloadLease
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.security.SecureRandom
import java.util.UUID
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Shared navigation metadata, with synchronous admission closure before the generic profile sweep.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class NativeHandoffLinks(
    private val stores: PreviewStores,
    private val redactor: Redactor,
    private val scope: AppCoroutineScope,
) : ProfileCloser, ProfileResourceCleaner {
    val links = HandoffLinks(KvAgentPrefs(stores.prefs, stores.lanes.app), redactor)
    private val closed = HashSet<ProfileId>()
    private val sessions = LinkedHashMap<Any, Pair<Set<ProfileId>, () -> Unit>>()
    override val owner = ":engine:agents/handoff"
    override val order = DeletionStep.DEVICE_SURFACES + 10
    override val guardsSweep = true

    @Synchronized
    fun admitting(id: ProfileId): Boolean =
        id !in closed &&
            stores.profiles.profile(id) != null &&
            stores.lanes.forProfile(id).isAdmitting

    @Synchronized
    fun track(ids: Set<ProfileId>, close: () -> Unit): (() -> Unit)? {
        if (ids.any { !admitting(it) }) return null
        val token = Any()
        sessions[token] = ids to close
        return {
            synchronized(this) {
                sessions.remove(token)
                Unit
            }
        }
    }

    override fun stopAdmission(profile: ProfileId) {
        val callbacks =
            synchronized(this) {
                closed += profile
                links.stopAdmission(profile.raw)
                sessions.values.filter { profile in it.first }.map { it.second }
            }
        callbacks.forEach { it() }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        links.closeProfile(profile.raw)
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) {
        synchronized(this) { closed -= profile }
        scope.launch { links.cancelDeletion(profile.raw) }
    }

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        when (val cleared = links.removeProfile(profileId.raw)) {
            is Outcome.Failed -> cleared
            is Outcome.Ok ->
                if (cleared.value is HandoffLinkSave.Saved) 1.ok()
                else Failure.StorageRefused().failed()
        }
}

/**
 * Only an explicit handoff or opening a persisted continued chat asks this source to start the
 * phone helper.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<TransientConnectionSource>())
class NativeHandoffSource(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    private val saved: NativeHandoffLinks,
    private val payloads: PhonePayloadGuard,
) : TransientConnectionSource {
    private val lane = Mutex()
    private val linux by lazy { BuiltinLinux.get(app) }
    private val secure = SecureRandom()

    fun ownerCurrent(owner: ProfileId): Boolean =
        saved.admitting(owner) &&
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == owner.raw &&
            !SetupRunner.get(app).running

    fun project(directory: String): Boolean =
        directory.length <= 4096 &&
            (directory == "/root/projects" || directory.startsWith("/root/projects/")) &&
            directory.split('/').none { it == "." || it == ".." } &&
            directory.none { it.code < 32 || it.code == 127 }

    suspend fun helper(owner: ProfileId, directory: String): Outcome<TransientConnection> =
        lane.withLock {
            if (!project(directory) || !ownerCurrent(owner)) return@withLock stale()
            val claim =
                when (val claimed = payloads.reserveNativeSignIn(owner)) {
                    is Outcome.Failed -> return@withLock claimed
                    is Outcome.Ok -> claimed.value
                }
            try {
                when (val ready = claim.awaitReady()) {
                    is Outcome.Failed -> return@withLock ready
                    is Outcome.Ok -> Unit
                }
                if (!ownerCurrent(owner)) return@withLock stale()
                val filesystem =
                    withContext(dispatchers.io) { linux.captureHandoffFilesystem(owner.raw) }
                        ?: return@withLock stale()
                if (!ownerCurrent(owner) || !filesystem.isCurrent()) return@withLock stale()
                val key = "oc.agentHostSecret.${owner.raw}"
                val stored =
                    when (val read = stores.secrets.read(key)) {
                        is Outcome.Failed -> return@withLock read
                        is Outcome.Ok -> read.value
                    }
                if (!ownerCurrent(owner) || !filesystem.isCurrent()) return@withLock stale()
                val password =
                    if (stored == null) {
                        val fresh =
                            ByteArray(32).also(secure::nextBytes).joinToString("") {
                                (it.toInt() and 255).toString(16).padStart(2, '0')
                            }
                        redactor.registerSecret(fresh)
                        when (
                            val committed =
                                stores.lanes.forProfile(owner).write {
                                    if (!ownerCurrent(owner)) stale()
                                    else stores.secrets.write(key, fresh)
                                }
                        ) {
                            is Outcome.Failed -> return@withLock committed
                            is Outcome.Ok -> Unit
                        }
                        fresh
                    } else
                        stored.takeIf { Regex("[a-f0-9]{64}").matches(it) }
                            ?: return@withLock Failure.SecretUnavailable().failed()
                redactor.registerSecret(password)
                if (!ownerCurrent(owner) || !filesystem.isCurrent()) return@withLock stale()
                val started =
                    withContext(dispatchers.io) {
                        attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                            linux.startTrustedAgentHelper(
                                owner.raw,
                                password,
                                PaseoConfig.render().json,
                                AgentPins.builtIn.paseo.version,
                            )
                        }
                    }
                if (!ownerCurrent(owner) || !filesystem.isCurrent()) return@withLock stale()
                if (started is Outcome.Failed) return@withLock started
                val identity =
                    withContext(dispatchers.io) {
                        linux.agentHost.captureHandoffIdentity(owner.raw)
                    } ?: return@withLock stale()
                if (!ownerCurrent(owner) || !filesystem.isCurrent() || !identity.isCurrent())
                    return@withLock stale()
                val shared =
                    withContext(dispatchers.io) {
                        attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                            linux.agentHost.checks.workspace(owner.raw)["shared"] == true
                        }
                    }
                if (!ownerCurrent(owner) || !filesystem.isCurrent() || !identity.isCurrent())
                    return@withLock stale()
                if ((shared as? Outcome.Ok)?.value != true)
                    return@withLock Failure.Unavailable(FeatureId("sharedProject")).failed()
                val parent =
                    stores.profiles.profile(owner)?.toServerProfile() ?: return@withLock stale()
                val profile =
                    parent.copy(
                        id = ProfileId("${owner.raw}.agents"),
                        backend = BackendKind.Paseo,
                        origin = ServerOrigin("ws", "127.0.0.1", 4099),
                        managedOnPhone = true,
                        username = null,
                    )
                TransientConnection(
                        profile,
                        ConnectionCredentials(null, password),
                        owner,
                        ServerLocation(directory),
                        { ownerCurrent(owner) && filesystem.isCurrent() && identity.isCurrent() },
                    )
                    .ok()
            } finally {
                claim.release()
            }
        }

    override suspend fun resolve(ref: ConversationRef): Outcome<TransientConnection?> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val raw = ref.profileId.raw.removeSuffix(".agents")
        if (ref.profileId.raw != "$raw.agents" || !Regex("[A-Za-z0-9_-]{1,80}").matches(raw))
            return null.ok()
        val owner = ProfileId(raw)
        if (!ownerCurrent(owner)) return stale()
        val matching =
            saved.links.forSession(owner.raw, ref.sessionId.raw, HandoffOrigin.PhoneAgent)
                ?: return stale()
        val directories = matching.map { it.projectDirectory }.distinct()
        if (directories.size != 1)
            return Failure.Unavailable(FeatureId("agentHandoffLink")).failed()
        return helper(owner, directories.single())
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}

@Inject
@ContributesBinding(AppScope::class, replaces = [NoAgentHandoffHost::class])
class GraphAgentHandoffHost(
    private val app: Application,
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val source: NativeHandoffSource,
    private val saved: NativeHandoffLinks,
    private val payloads: PhonePayloadGuard,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    private val drafts: AgentHandoffDrafts,
) : AgentHandoffHost {
    private val linux by lazy { BuiltinLinux.get(app) }
    private val random = SecureBytes { count -> ByteArray(count).also(SecureRandom()::nextBytes) }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    private fun rawOwner(profile: ProfileId): ProfileId? =
        ((stores.prefs.get("oc.phoneAgentOwner.${profile.raw}") as? PrefValue.Str)?.value
                ?: profile.raw)
            .takeIf { Regex("[A-Za-z0-9_-]{1,80}").matches(it) }
            ?.let(::ProfileId)

    override suspend fun review(source: ConversationRef): Outcome<AgentHandoffReview?> {
        val graph = registry.graphFor(source) ?: return stale()
        // Codex's normal messagePage resumes/subscribes. The source-review service deliberately
        // uses its full thread/read snapshot, including turns, and never calls messagePage.
        val gateway = graph.session.server as? CodexGateway ?: return null.ok()
        val revision = gateway.scopeRevision
        val location = gateway.location.value
        val profile = stores.profiles.profile(source.profileId) ?: return stale()
        fun current(): Boolean =
            registry.graphFor(source) === graph &&
                gateway.scopeRevision == revision &&
                gateway.location.value == location &&
                gateway.events.status.value == StreamStatus.Connected &&
                stores.profiles.profile(source.profileId) == profile &&
                saved.admitting(source.profileId)
        if (!current()) return stale()
        val record =
            when (val built = drafts.prepare(source.profileId, source.sessionId)) {
                is Outcome.Failed -> return built
                is Outcome.Ok -> built.value
            }
        if (!current()) return stale()
        val id = HandoffEngine.uuidV4(random)
        val path = ".handoff/$id/record.md"
        val draft =
            HandoffDraft(
                id,
                HandoffRecord(
                    record.markdown,
                    record.omittedMessages,
                    record.redacted,
                    record.byteLength,
                ),
                HandoffPrompt.initial(path),
                path,
            )
        // Reviewing context is safe without a shared-filesystem attestation. It never grants a
        // destination, creates a file, starts Claude, or offers send/link/retry commands.
        return AgentHandoffReview(redactor.redact(graph.profile.name), draft) { text ->
                text.split(path).joinToString(path) {
                    redactor.opaqueTokens(redactor.redact(it))
                }
            }
            .ok()
    }

    override suspend fun open(source: ConversationRef): Outcome<AgentHandoffSession> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val original = registry.graphFor(source) ?: return stale()
        // A direct Codex endpoint provides read-only history, but no native process attestation
        // tying its filesystem to our phone project. Its source-only draft is handled by review().
        if (original.session.server is CodexGateway)
            return Failure.Unavailable(FeatureId("sharedProject")).failed()
        val trustedOwner = registry.ownerOfTransient(source.profileId)
        val parent = trustedOwner ?: source.profileId
        val owner = trustedOwner ?: rawOwner(parent) ?: return stale()
        val location = original.session.server.location.value
        val directory = location.directory ?: return stale()
        if (
            !this.source.project(directory) ||
                !saved.admitting(parent) ||
                !this.source.ownerCurrent(owner)
        )
            return stale()
        val originalProfile = stores.profiles.profile(parent) ?: return stale()
        fun sourceCurrent(): Boolean =
            registry.graphFor(source) === original &&
                original.session.server.location.value == location &&
                original.scope.coroutineContext[Job]?.isActive == true &&
                stores.profiles.profile(parent) == originalProfile &&
                saved.admitting(parent) &&
                this.source.ownerCurrent(owner)
        val filesystem =
            withContext(dispatchers.io) { linux.captureHandoffFilesystem(owner.raw) }
                ?: return stale()
        if (!sourceCurrent() || !filesystem.isCurrent()) return stale()
        val sourceProof =
            if (trustedOwner != null) {
                withContext(dispatchers.io) { linux.agentHost.captureHandoffIdentity(owner.raw) }
            } else {
                val origin = original.profile.origin
                if (
                    origin.host !in setOf("127.0.0.1", "localhost", "::1") ||
                        origin.scheme !in setOf("http", "https")
                )
                    return stale()
                val password =
                    when (val secret = stores.profiles.secret(parent)) {
                        is Outcome.Failed -> return secret
                        is Outcome.Ok -> secret.value ?: return Failure.SecretUnavailable().failed()
                    }
                if (!sourceCurrent() || !filesystem.isCurrent()) return stale()
                redactor.registerSecret(password)
                withContext(dispatchers.io) {
                    linux.captureManagedHandoffSource(owner.raw, origin.port, password, filesystem)
                }
            } ?: return stale()
        if (!sourceCurrent() || !sourceProof.isCurrent()) return stale()
        val originalSession =
            when (val read = original.session.server.session(source.sessionId)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        if (!sourceCurrent() || !sourceProof.isCurrent() || originalSession.directory != directory)
            return stale()
        val spec =
            when (val target = this.source.helper(owner, directory)) {
                is Outcome.Failed -> return target
                is Outcome.Ok -> target.value
            }
        if (!sourceCurrent() || !sourceProof.isCurrent() || !spec.currentOwner()) return stale()
        val targetGraph =
            when (
                val opened =
                    registry.openTransient(
                        spec.profile,
                        spec.credentials,
                        owner,
                        spec.currentOwner,
                        spec.location,
                    )
            ) {
                is Outcome.Failed -> return opened
                is Outcome.Ok -> opened.value
            }
        val targetLocation = targetGraph.session.server.location.value
        if (
            !sourceCurrent() ||
                !sourceProof.isCurrent() ||
                targetLocation.directory != directory ||
                !spec.currentOwner()
        )
            return stale()
        val claim =
            when (val read = payloads.reserveForProfile(targetGraph.profile.id)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        var transferred = false
        try {
            when (val ready = claim.awaitReady()) {
                is Outcome.Failed -> return ready
                is Outcome.Ok -> Unit
            }
            fun current(): Boolean =
                sourceCurrent() &&
                    filesystem.isCurrent() &&
                    sourceProof.isCurrent() &&
                    spec.currentOwner() &&
                    registry.live.value[targetGraph.profile.id] === targetGraph &&
                    targetGraph.session.server.location.value == targetLocation &&
                    saved.admitting(owner)
            if (!current()) return stale()
            val gateway = targetGraph.session.server
            when (val healthy = gateway.health()) {
                is Outcome.Failed -> return healthy
                is Outcome.Ok -> Unit
            }
            if (!current()) return stale()
            if (gateway !is AgentHandoffGateway || !gateway.handoffFilesReady)
                return Failure.Unavailable(FeatureId("agentHandoffFiles")).failed()
            val inventory =
                when (val read = gateway.providers()) {
                    is Outcome.Failed -> return read
                    is Outcome.Ok -> read.value
                }
            if (!current()) return stale()
            val gatesRaw =
                (stores.prefs.get("oc.agentPhoneGate.${owner.raw}") as? PrefValue.Str)?.value
            val gates =
                PhoneGate.read(
                    KvAgentPrefs(stores.prefs, stores.lanes.forProfile(owner)),
                    OwnerId(owner.raw),
                )
            val arch =
                when {
                    "arm64-v8a" in Build.SUPPORTED_ABIS -> AgentArchitecture.Arm64
                    "x86_64" in Build.SUPPORTED_ABIS -> AgentArchitecture.X64
                    else -> null
                }
            val targets = LinkedHashMap<String, Pair<AgentHandoffChoice, HandoffTarget>>()
            val sourceAdapter = HandoffGatewayAdapter(original.session.server, ::current)
            val targetAdapter =
                if (original === targetGraph) sourceAdapter
                else HandoffGatewayAdapter(gateway, ::current)
            val sourceScope =
                HandoffScope(
                    parent.raw,
                    directory,
                    location.workspace,
                    filesystem.filesystemId,
                    if (trustedOwner == null) HandoffOrigin.Server else HandoffOrigin.PhoneAgent,
                )
            val targetScope =
                HandoffScope(
                    owner.raw,
                    directory,
                    targetLocation.workspace,
                    filesystem.filesystemId,
                    HandoffOrigin.PhoneAgent,
                )
            for (provider in inventory.providers) {
                val agent =
                    AgentCatalog.builtIn.agents.firstOrNull { it.providerId == provider.id }
                        ?: continue
                if (!AgentCertification.bundled.certifiedForChat(agent.id)) continue
                val recipe = agent.recipe ?: continue
                val version =
                    withContext(dispatchers.io) {
                        attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                            linux.agentHost.checks.version(
                                owner.raw,
                                recipe.executable,
                                recipe.version,
                            )
                        }
                    }
                if (!current()) return stale()
                val found = ((version as? Outcome.Ok)?.value?.get("foundVersion") as? String)
                val pin = gates[agent.id.raw]
                val qualified =
                    arch != null &&
                        found != null &&
                        (((pin?.get("fingerprint") as? JsonPrimitive)?.content ==
                            PhoneGate.fingerprint(agent, arch, AgentPins.builtIn) &&
                            found == recipe.version) ||
                            PhoneGate.olderQualifiedVersion(pin, agent, arch, AgentPins.builtIn) ==
                                found)
                for (model in provider.modelIds) {
                    val id = "${provider.id}\u0000$model"
                    val label =
                        redactor.redact(
                            (provider.modelData[model]?.get("name") as? JsonPrimitive)?.content
                                ?: model
                        )
                    val choice =
                        AgentHandoffChoice(
                            id,
                            redactor.redact(agent.name),
                            label,
                            if (qualified) null else HandoffChoiceIssue.PhoneCheck,
                        )
                    targets[id] =
                        choice to
                            HandoffTarget(
                                targetAdapter,
                                targetScope,
                                HandoffModelRef(provider.id, model),
                                redactor.redact(agent.name),
                            )
                }
            }
            if (
                !current() ||
                    gatesRaw !=
                        (stores.prefs.get("oc.agentPhoneGate.${owner.raw}") as? PrefValue.Str)
                            ?.value
            )
                return stale()
            val sourceId = sourceAdapter.durableId(source.sessionId.raw)
            val existing =
                saved.links.forSession(parent.raw, sourceId, sourceScope.origin)
                    ?: return Failure.StorageRefused().failed()
            val session =
                Session(
                    source,
                    redactor.redact(originalSession.title.orEmpty()),
                    parent,
                    owner,
                    sourceId,
                    sourceScope,
                    sourceAdapter,
                    targetGraph,
                    targets,
                    existing,
                    claim,
                    {
                        current() &&
                            gatesRaw ==
                                (stores.prefs.get("oc.agentPhoneGate.${owner.raw}")
                                        as? PrefValue.Str)
                                    ?.value
                    },
                )
            val untrack = saved.track(setOf(parent, owner), session::close) ?: return stale()
            session.untrack = untrack
            transferred = true
            return session.ok()
        } finally {
            if (!transferred) claim.release()
        }
    }

    private inner class Session(
        private val sourceRef: ConversationRef,
        override val title: String,
        private val parent: ProfileId,
        private val nativeOwner: ProfileId,
        private val sourceId: String,
        private val sourceScope: HandoffScope,
        private val sourceGateway: HandoffGatewayAdapter,
        private val targetGraph: ConnectionGraph,
        private val targets: Map<String, Pair<AgentHandoffChoice, HandoffTarget>>,
        private val storedLinks: List<HandoffLink>,
        private val claim: PaseoPayloadLease,
        private val remainsCurrent: () -> Boolean,
    ) : AgentHandoffSession {
        @Volatile private var closed = false
        var untrack: (() -> Unit)? = null
        private var engine: HandoffEngine? = null
        private val mutable = MutableStateFlow(HandoffState())
        override val state = mutable.asStateFlow()
        override val directory = redactor.redact(sourceScope.directory)
        override val choices = targets.values.map { it.first }.toImmutableList()
        override val links =
            storedLinks
                .map {
                    AgentHandoffLink(
                        it.id,
                        redactor.redact(it.sourceTitle),
                        redactor.redact(it.targetAgent),
                    )
                }
                .toImmutableList()

        override fun current(): Boolean = !closed && remainsCurrent()

        override suspend fun prepare(choice: String): Outcome<HandoffPrepared> {
            if (!current() || engine != null) return stale()
            val selected =
                targets[choice]?.takeIf { it.first.issue == null }?.second ?: return stale()
            val attempt =
                HandoffEngine(
                    sourceGateway,
                    sourceScope,
                    sourceId,
                    selected,
                    saved.links,
                    ::current,
                    { HandoffEngine.uuidV4(random) },
                    redactor,
                    onPhase = { mutable.value = engine?.state?.value ?: HandoffState(phase = it) },
                )
            engine = attempt
            val result = attempt.prepare("")
            mutable.value = attempt.state.value
            return result
        }

        override fun sanitizePrompt(text: String): String =
            engine?.state?.value?.draft?.let { engine?.sanitizePrompt(it, text) } ?: ""

        override suspend fun send(prompt: String): Outcome<HandoffSent> {
            val attempt = engine ?: return stale()
            if (!current()) return stale()
            val result = attempt.send(prompt)
            mutable.value = attempt.state.value
            return result
        }

        override suspend fun retryLink(): Outcome<HandoffSent> {
            val attempt = engine ?: return stale()
            if (!current()) return stale()
            val result = attempt.retryLink()
            mutable.value = attempt.state.value
            return result
        }

        override fun createdRef(): ConversationRef? {
            if (!current()) return null
            val state = engine?.state?.value ?: return null
            if (
                state.phase != HandoffPhase.Complete &&
                    state.failure != HandoffFailureKind.LinkFailed
            )
                return null
            return state.createdSessionId?.let {
                ConversationRef(targetGraph.profile.id, SessionId(it))
            }
        }

        override suspend fun openLink(id: String, continued: Boolean): Outcome<ConversationRef> {
            if (!current()) return stale()
            val fresh =
                saved.links.forSession(parent.raw, sourceId, sourceScope.origin)
                    ?: return Failure.StorageRefused().failed()
            val link = fresh.singleOrNull { it.id == id } ?: return stale()
            val profile = ProfileId(if (continued) link.targetProfileId else link.sourceProfileId)
            val session = SessionId(if (continued) link.targetSessionId else link.sourceSessionId)
            val origin = if (continued) link.targetOrigin else link.sourceOrigin
            if (!saved.admitting(profile) || !source.project(link.projectDirectory)) return stale()
            val ref =
                if (origin == HandoffOrigin.PhoneAgent)
                    ConversationRef(ProfileId("${profile.raw}.agents"), session)
                else ConversationRef(profile, session)
            if (origin == HandoffOrigin.PhoneAgent) {
                val resolved =
                    when (val read = source.helper(profile, link.projectDirectory)) {
                        is Outcome.Failed -> return read
                        is Outcome.Ok -> read.value
                    }
                if (!current()) return stale()
                when (
                    val opened =
                        registry.openTransient(
                            resolved.profile,
                            resolved.credentials,
                            resolved.ownerProfileId,
                            resolved.currentOwner,
                            resolved.location,
                        )
                ) {
                    is Outcome.Failed -> return opened
                    is Outcome.Ok -> Unit
                }
            } else if (registry.graphFor(ref) == null) {
                val loaded = stores.profiles.profile(profile) ?: return stale()
                val connectionProfile = loaded.toServerProfile() ?: return stale()
                when (val opened = registry.open(connectionProfile)) {
                    is Outcome.Failed -> return opened
                    is Outcome.Ok -> Unit
                }
            }
            if (!current()) return stale()
            val graph = registry.graphFor(ref) ?: return stale()
            // Reading the canonical daemon id confirms the linked chat exists; this never creates
            // or resumes one.
            when (val read = graph.session.server.session(session)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> Unit
            }
            return if (current()) ref.ok() else stale()
        }

        override fun close() {
            synchronized(this) {
                if (closed) return
                closed = true
            }
            claim.release()
            untrack?.invoke()
            untrack = null
        }
    }
}

/**
 * The current daemon is app-owned evidence of one filesystem. A second saved server, a matching URL
 * or a matching folder name is deliberately insufficient: no cross-server destination is
 * synthesized from those strings.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ChatAgentHandoffs>())
class AppAgentHandoffs(private val registry: ConnectionRegistry, saved: NativeHandoffLinks) :
    ChatAgentHandoffs {
    // One shared link book: NativeHandoffLinks owns admission closure, drain, reopen and the
    // profile-deletion sweep for these links too (the unit's separate closer/cleaner merged there).
    val links: HandoffLinks = saved.links

    override fun open(ref: ConversationRef): ChatAgentHandoff? {
        val graph = registry.graphFor(ref) ?: return null
        val gateway = graph.session.server as? PaseoGateway ?: return null
        return DaemonHandoff(ref, graph, gateway, registry, links)
    }
}

private class DaemonHandoff(
    private val ref: ConversationRef,
    private val graph: ConnectionGraph,
    private val gateway: PaseoGateway,
    private val registry: ConnectionRegistry,
    private val links: HandoffLinks,
) : ChatAgentHandoff {
    private var generation = 0L
    private var engine: HandoffEngine? = null
    private var currentChoices: List<ChatAgentHandoffChoice> = emptyList()

    private fun current(): Boolean = registry.graphFor(ref) === graph && !gateway.isClosed

    private fun refused(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    override suspend fun choices(): Outcome<List<ChatAgentHandoffChoice>> {
        if (!current() || !gateway.capabilities.value.agentHandoffFiles)
            return Outcome.Ok(emptyList())
        val read = gateway.providers()
        if (!current()) return refused()
        return read.map { inventory ->
            inventory.providers
                .flatMap { provider ->
                    provider.modelIds.map { model ->
                        ChatAgentHandoffChoice(provider.id, model, "${provider.name} · $model")
                    }
                }
                .also { currentChoices = it }
        }
    }

    override suspend fun prepare(choice: ChatAgentHandoffChoice): Outcome<HandoffPrepared> {
        if (!current() || choice !in currentChoices) return refused()
        val folder = gateway.location.value.directory ?: return refused()
        val workspace = gateway.location.value.workspace
        // Each engine captures a separate selection generation, never mutable handoff state from a
        // later sheet.
        val token = ++generation
        val port = DaemonHandoffGateway(gateway, { current() })
        val scope =
            HandoffScope(
                ref.profileId.raw,
                folder,
                workspace,
                "connected-daemon",
                HandoffOrigin.Server,
            )
        val target =
            HandoffTarget(
                port,
                scope,
                HandoffModelRef(choice.providerId, choice.modelId),
                choice.label,
            )
        val fresh =
            HandoffEngine(
                port,
                scope,
                ref.sessionId.raw,
                target,
                links,
                isCurrent = { generation == token && current() },
                recordId = { UUID.randomUUID().toString() },
            )
        engine = fresh
        return fresh.prepare("")
    }

    override suspend fun send(prompt: String): Outcome<HandoffSent> =
        engine?.send(prompt) ?: refused()

    override suspend fun retryLink(): Outcome<HandoffSent> = engine?.retryLink() ?: refused()

    override fun close() {
        generation++
    }
}

/** Protocol mapping is kept in the app graph: engines and UI never import a concrete protocol. */
private class DaemonHandoffGateway(
    private val gateway: PaseoGateway,
    private val current: () -> Boolean,
) : HandoffGateway {
    override val directory: String?
        get() = gateway.location.value.directory

    override val workspace: String?
        get() = gateway.location.value.workspace

    override val isClosed: Boolean
        get() = !current() || gateway.isClosed

    override val capabilities: HandoffCapabilities
        get() =
            HandoffCapabilities(
                agentHandoffFiles = gateway.capabilities.value.agentHandoffFiles,
                sessionTodos = gateway.capabilities.value.sessionTodos,
                sessionDiff = gateway.capabilities.value.sessionDiff,
                sessionModelProviderSwitching =
                    gateway.capabilities.value.sessionModelProviderSwitching,
            )

    override val historyReader: HandoffHistoryReader =
        HandoffHistoryReader.Paged { id, cursor, limit ->
            gateway.genUiHistoryPage(SessionId(id), cursor, limit).map { page ->
                HandoffHistoryPage(page.items.map(::message), page.olderCursor, page.hasMore)
            }
        }

    override suspend fun session(id: String): Outcome<HandoffSession> =
        gateway.session(SessionId(id)).map(::session)

    override suspend fun todos(sessionId: String): Outcome<List<HandoffTodo>> =
        gateway.todos(SessionId(sessionId)).map { todos ->
            todos.map { HandoffTodo(it.content, it.status, it.priority) }
        }

    override suspend fun diff(sessionId: String): Outcome<List<HandoffFileDiff>> =
        gateway.diff(SessionId(sessionId)).map { files ->
            files.map { HandoffFileDiff(it.file, it.additions, it.deletions) }
        }

    override suspend fun providers(): Outcome<List<HandoffProvider>> =
        gateway.providers().map { inventory ->
            inventory.providers.map { HandoffProvider(it.id, it.modelIds) }
        }

    override suspend fun createSession(): Outcome<HandoffSession> =
        gateway.createSession(NewSession()).map(::session)

    override val handoffTarget: HandoffTargetPort =
        object : HandoffTargetPort {
            private val aliases = mutableMapOf<String, String>()

            override suspend fun writeRecord(
                directory: String,
                recordId: String,
                markdown: String,
            ): Outcome<String> = gateway.writeAgentHandoff(directory, recordId, markdown)

            override fun durableSessionId(sessionId: String): String {
                val durable = gateway.daemonSessionId(SessionId(sessionId)).raw
                aliases[durable] = sessionId
                return durable
            }

            override fun navigationSessionId(sessionId: String): String =
                aliases[sessionId] ?: sessionId

            override suspend fun promptHandoff(
                sessionId: String,
                text: String,
                model: HandoffModelRef,
                agent: String?,
                variant: String?,
                beforeSend: () -> Boolean,
            ): Outcome<Unit> =
                when (
                    val sent =
                        gateway.promptWithMessageId(
                            SessionId(sessionId),
                            gateway.createPromptMessageId(),
                            OutgoingPrompt(
                                text,
                                model =
                                    ModelSelection(
                                        ModelRef(model.providerId, model.modelId),
                                        variant,
                                    ),
                                agent = agent,
                            ),
                            beforeSend = { current() && beforeSend() },
                        )
                ) {
                    is Outcome.Failed -> sent
                    is Outcome.Ok ->
                        when (sent.value) {
                            is PromptAdmission.Confirmed -> Outcome.Ok(Unit)
                            PromptAdmission.Uncertain -> Outcome.Failed(Failure.TimedOut())
                        }
                }
        }

    private fun session(value: Session): HandoffSession =
        HandoffSession(
            value.id.raw,
            value.title,
            value.directory,
            value.path,
            value.selection?.agent ?: value.agent,
            value.selection?.model?.modelId ?: value.model,
            value.time?.updatedMs,
        )

    private fun message(value: MessageWithParts): HandoffMessage =
        HandoffMessage(
            value.info.id,
            value.info.sessionId,
            value.info.role.name.lowercase(),
            parts =
                value.parts.map { part ->
                    HandoffPart(
                        type = part.rawType,
                        text = part.text,
                        synthetic = part.synthetic,
                        filename = part.filename,
                        url = part.url,
                        toolName = part.toolName,
                        tool =
                            part.tool?.let { tool ->
                                HandoffToolState(
                                    status = tool.status.name.lowercase(),
                                    input = tool.input.mapValues { jsonValue(it.value) },
                                    inputJson = tool.inputRaw,
                                    output = tool.output,
                                    outputValue = tool.outputValue?.let(::jsonValue),
                                    metadata = tool.metadata?.mapValues { jsonValue(it.value) },
                                    pruned = tool.pruned,
                                    executed = tool.ran,
                                )
                            } ?: HandoffToolState("pending"),
                    )
                },
            errorText = value.info.errorText,
        )
}

private fun jsonValue(value: JsonElement): Any? =
    when (value) {
        JsonNull -> null
        is JsonObject -> value.mapValues { jsonValue(it.value) }
        is JsonArray -> value.map(::jsonValue)
        is JsonPrimitive ->
            if (value.isString) value.content
            else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
    }
