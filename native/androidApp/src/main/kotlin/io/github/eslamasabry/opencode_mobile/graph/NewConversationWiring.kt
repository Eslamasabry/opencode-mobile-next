package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.WireJson
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.domain.ProviderConversationImportGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.DirectoryPaths
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.delivery.LocalSendId
import io.github.eslamasabry.oc.engine.delivery.PromptDelivery
import io.github.eslamasabry.oc.engine.delivery.ReceiptLook
import io.github.eslamasabry.oc.engine.delivery.SendOutcome
import io.github.eslamasabry.oc.engine.delivery.SendOutcomeId
import io.github.eslamasabry.oc.engine.delivery.SendPhase
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import io.github.eslamasabry.oc.engine.delivery.port.GatewaySendLink
import io.github.eslamasabry.oc.engine.delivery.port.nothingSent
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceipt
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptController
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptState
import io.github.eslamasabry.oc.engine.git.GitLinkRepairReport
import io.github.eslamasabry.oc.engine.git.GitLinkRepairService
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chats.presentation.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString

/** The first message uses the same durable delivery owner as the conversation composer. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoNewConversationHost::class])
@Inject
class AndroidNewConversationHost(
    private val registry: ConnectionRegistry,
    private val connections: ServerConnections,
    private val stores: PreviewStores,
    private val gitRepair: GitLinkRepairService,
    private val deliveryStores: Set<DeliveryStorage>,
    private val projectServers: io.github.eslamasabry.oc.feature.projects.presentation.ProjectServers,
    private val projectCreation: io.github.eslamasabry.oc.feature.projects.presentation.ProjectCreation,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) : NewConversationHost {
    private val serial = dispatchers.serial("new-conversation")
    private val lane = Mutex()
    override val servers: StateFlow<List<NewConversationServer>> =
        connections.servers
            .map { rows -> rows.map { NewConversationServer(it.id, it.name, it.connected) } }
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    override fun canRepair(profile: ProfileId, directory: String): Boolean =
        gitRepair.availableProject(profile, directory)

    override suspend fun repair(
        profile: ProfileId,
        directory: String,
    ): Outcome<GitLinkRepairReport> = gitRepair.repairProject(profile, directory)

    override fun openRepairSettings(): Unit = gitRepair.openTermuxSettings()

    private fun projectServer(profile: ProfileId) =
        projectServers.servers.value.firstOrNull { it.profile == profile }

    // New project in the picker: the same phone-server folder creator as Projects (a Git folder under /root/projects).
    override fun canCreateProject(profile: ProfileId): Boolean =
        projectServer(profile)?.let(projectCreation::available) == true

    override suspend fun createProject(profile: ProfileId, name: String): Outcome<String> {
        val server = projectServer(profile) ?: return refused()
        return projectCreation.create(server, name)
    }

    private fun refused(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    private fun draftKey(profile: ProfileId, directory: String?): String =
        "oc.draft.new-chat.${directory?.let { Base64.UrlSafe.encode(it.encodeToByteArray()).trimEnd('=') } ?: "none"}.${profile.raw}"

    override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> =
        withContext(serial) {
            if (stores.profiles.profile(profile) == null) return@withContext refused()
            attempt({ Failure.StorageRefused() }) {
                    val raw = stores.prefs.get(draftKey(profile, directory))
                    if (raw != null && raw !is PrefValue.Str) return@attempt refused()
                    Outcome.Ok((raw as? PrefValue.Str)?.value.orEmpty())
                }
                .let {
                    when (it) {
                        is Outcome.Ok -> it.value
                        is Outcome.Failed -> it
                    }
                }
        }

    override suspend fun saveDraft(
        profile: ProfileId,
        directory: String?,
        text: String,
    ): Outcome<Unit> =
        withContext(serial) {
            if (stores.profiles.profile(profile) == null) return@withContext refused()
            stores.prefs.commit(stores.lanes.forProfile(profile)) {
                if (text.isEmpty()) remove(draftKey(profile, directory))
                else putString(draftKey(profile, directory), text)
            }
        }

    private data class DraftChoices(
        val provider: String? = null,
        val model: String? = null,
        val agent: String? = null,
    ) {
        fun encode(): String =
            DartJson.encode(linkedMapOf("provider" to provider, "model" to model, "agent" to agent))

        companion object {
            fun decode(raw: String): DraftChoices? {
                val fields = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
                if (
                    listOf("provider", "model", "agent").any {
                        fields[it] != null && fields[it] !is String
                    }
                )
                    return null
                return DraftChoices(
                    fields["provider"] as? String,
                    fields["model"] as? String,
                    fields["agent"] as? String,
                )
            }
        }
    }

    private fun choicesKey(profile: ProfileId, directory: String?): String =
        draftKey(profile, directory).replace("oc.draft.new-chat.", "oc.draft.new-chat-choices.")

    override suspend fun saveChoices(
        profile: ProfileId,
        directory: String?,
        model: ModelRef?,
        agent: String?,
    ): Outcome<Unit> =
        withContext(serial) {
            if (stores.profiles.profile(profile) == null) return@withContext refused()
            stores.prefs.commit(stores.lanes.forProfile(profile)) {
                putString(
                    choicesKey(profile, directory),
                    DraftChoices(model?.providerId, model?.modelId, agent).encode(),
                )
                if (directory != null) putString("oc.newChatProject.${profile.raw}", directory)
            }
        }

    private fun reviewKey(profile: ProfileId, directory: String?): String =
        draftKey(profile, directory).replace("oc.draft.new-chat.", "oc.draft.new-chat-review.")

    private fun creationKey(profile: ProfileId, directory: String?): String =
        draftKey(profile, directory).replace("oc.draft.new-chat.", "oc.draft.new-chat-creation.")

    // androidApp has no serialization compiler plugin. Keep this tiny persisted envelope
    // explicit: @Serializable here compiles but encodeToString fails at runtime before Send.
    private data class FirstMessageReview(
        val session: String,
        val directory: String,
        val target: String,
        val reviewId: String? = null,
        val draftDigest: String? = null,
    ) {
        fun encode(): String =
            DartJson.encode(
                linkedMapOf(
                    "session" to session,
                    "directory" to directory,
                    "target" to target,
                    "reviewId" to reviewId,
                    "draftDigest" to draftDigest,
                )
            )

        companion object {
            fun decode(raw: String): FirstMessageReview? {
                val fields = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
                val reviewId = fields["reviewId"]
                if (
                    reviewId != null &&
                        (reviewId !is String || !Regex("^[a-zA-Z0-9_:-]{1,256}$").matches(reviewId))
                )
                    return null
                val draftDigest = fields["draftDigest"]
                if (
                    draftDigest != null &&
                        (draftDigest !is String || !Regex("^[a-f0-9]{64}$").matches(draftDigest))
                )
                    return null
                return FirstMessageReview(
                    fields["session"] as? String ?: return null,
                    fields["directory"] as? String ?: return null,
                    fields["target"] as? String ?: return null,
                    reviewId as? String,
                    draftDigest as? String,
                )
            }
        }
    }

    private fun target(graph: ConnectionGraph, directory: String): String =
        target(graph.profile, directory)

    private fun target(profile: ServerProfile, directory: String): String =
        DartJson.sha256Hex(
            WireJson.encodeToString(
                listOf(
                    profile.id.raw,
                    profile.backend.name,
                    profile.origin.toString(),
                    profile.username.orEmpty(),
                    stores.profiles.profile(profile.id)?.profile?.codexDirectory.orEmpty(),
                    directory,
                )
            )
        )

    private fun currentTarget(graph: ConnectionGraph, directory: String): Boolean =
        registry.live.value[graph.profile.id] === graph &&
            stores.profiles.profile(graph.profile.id)?.toServerProfile()?.let {
                target(it, directory) == target(graph, directory)
            } == true

    override suspend fun review(profile: ProfileId, directory: String?): Outcome<ConversationRef?> =
        withContext(serial) {
            if (stores.profiles.profile(profile) == null) return@withContext refused()
            val expected =
                linkedMapOf(
                    reviewKey(profile, directory) to
                        stores.prefs.get(reviewKey(profile, directory)),
                    creationKey(profile, directory) to
                        stores.prefs.get(creationKey(profile, directory)),
                    draftKey(profile, directory) to stores.prefs.get(draftKey(profile, directory)),
                )
            val raw =
                expected[reviewKey(profile, directory)]
                    ?: expected[creationKey(profile, directory)]
                    ?: return@withContext Outcome.Ok(null)
            val graph = registry.live.value[profile] ?: return@withContext refused()
            if (raw !is PrefValue.Str || raw.value.length > 8192 || directory == null)
                return@withContext Outcome.Failed(Failure.StorageRefused())
            val receipt =
                try {
                    FirstMessageReview.decode(raw.value)
                        ?: return@withContext Outcome.Failed(Failure.StorageRefused())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withContext Outcome.Failed(Failure.StorageRefused())
                }
            if (
                receipt.session.isBlank() ||
                    receipt.session.length > 256 ||
                    receipt.directory != directory ||
                    receipt.target != target(graph, directory) ||
                    !currentTarget(graph, directory)
            )
                return@withContext refused()
            if (firstMessageConfirmed(graph, receipt)) {
                if (!currentTarget(graph, directory) || target(graph, directory) != receipt.target)
                    return@withContext refused()
                val cleared =
                    stores.prefs.compareAndCommit(stores.lanes.forProfile(profile), expected) {
                        if (
                            currentTarget(graph, directory) &&
                                target(graph, directory) == receipt.target
                        ) {
                            remove(reviewKey(profile, directory))
                            remove(creationKey(profile, directory))
                            // A newer draft may already occupy this project slot. Only a digest
                            // bound before the original send can authorize deleting its words.
                            val draft =
                                (expected[draftKey(profile, directory)] as? PrefValue.Str)?.value
                            if (
                                receipt.draftDigest != null &&
                                    draft != null &&
                                    DartJson.sha256Hex(draft) == receipt.draftDigest
                            )
                                remove(draftKey(profile, directory))
                        }
                    }
                if (cleared is Outcome.Failed) return@withContext cleared
                if (!currentTarget(graph, directory) || target(graph, directory) != receipt.target)
                    return@withContext refused()
                if (
                    (cleared as Outcome.Ok).value &&
                        stores.prefs.get(reviewKey(profile, directory)) == null &&
                        stores.prefs.get(creationKey(profile, directory)) == null
                )
                    return@withContext Outcome.Ok(null)
            }
            Outcome.Ok(ConversationRef(profile, SessionId(receipt.session)))
        }

    /** A removed queue row alone proves nothing; only the original scoped admission does. */
    private suspend fun firstMessageConfirmed(
        graph: ConnectionGraph,
        binding: FirstMessageReview,
    ): Boolean {
        val journals = deliveryStores.singleOrNull()?.journals ?: return false
        val journal = (journals.forProfile(graph.profile.id) as? Outcome.Ok)?.value ?: return false
        val rows = (journal.read() as? Outcome.Ok)?.value ?: return false
        val link =
            GatewaySendLink(graph.profile.id, graph.profile.origin.toString(), graph.session.server)
        val origin = link.scope
        val candidates = rows.filter {
            it.scope == origin.receiptScope &&
                it.sessionId == binding.session &&
                it.tabId == binding.session &&
                it.commandId.startsWith("queue:") &&
                (binding.reviewId == null ||
                    it.commandId == CommandReceipt.queueCommandId(binding.reviewId))
        }
        // Old envelopes had no marker id. Only a unique exact-session journal binding can
        // recover those safely; several historical sends remain ambiguous.
        val original = candidates.singleOrNull() ?: return false
        fun current(): Boolean =
            currentTarget(graph, binding.directory) &&
                target(graph, binding.directory) == binding.target &&
                link.scope == origin &&
                journal.isOpen
        if (!current()) return false
        if (original.state == CommandReceiptState.Confirmed) return true
        if (binding.reviewId == null) return false
        val checked =
            CommandReceiptController(journal).retry(original.commandId) { receipt ->
                val found =
                    link.lookupAdmission(
                        SessionId(receipt.sessionId),
                        MessageId(receipt.receiptId),
                    )
                current() && found is Outcome.Ok && found.value
            }
        val confirmed = (checked as? Outcome.Ok)?.value ?: return false
        return current() &&
            confirmed.sameIdentity(original) &&
            confirmed.state == CommandReceiptState.Confirmed
    }

    private suspend fun bindFirstMessageReview(
        delivery: PromptDelivery,
        localId: LocalSendId,
        ref: ConversationRef,
        directory: String,
        expectedTarget: String,
    ): Outcome<Unit> {
        val reviewId =
            delivery.pending.value.firstOrNull { it.localId == localId && it.ref == ref }?.reviewId
                ?: delivery.outcomes.value
                    .filterIsInstance<SendOutcome.Unconfirmed>()
                    .firstOrNull { it.localId == localId && it.ref == ref }
                    ?.reviewId
                ?: return Outcome.Ok(Unit)
        val key = reviewKey(ref.profileId, directory)
        val before = stores.prefs.get(key)
        val original =
            (before as? PrefValue.Str)?.value?.let(FirstMessageReview::decode)
                ?: return Outcome.Failed(Failure.StorageRefused())
        if (
            original.session != ref.sessionId.raw ||
                original.directory != directory ||
                original.target != expectedTarget
        )
            return refused()
        return when (
            val bound =
                stores.prefs.compareAndCommit(
                    stores.lanes.forProfile(ref.profileId),
                    mapOf(key to before),
                ) {
                    putString(key, original.copy(reviewId = reviewId).encode())
                }
        ) {
            is Outcome.Failed -> bound
            is Outcome.Ok ->
                if (bound.value) Outcome.Ok(Unit) else Outcome.Failed(Failure.StorageRefused())
        }
    }

    override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
        withContext(serial) {
            lane.withLock {
                if (stores.profiles.profile(profile) == null) return@withLock refused()
                when (val connected = connections.connect(profile)) {
                    is Outcome.Failed -> return@withLock connected
                    is Outcome.Ok -> Unit
                }
                val graph = registry.live.value[profile] ?: return@withLock refused()
                val gateway = graph.session.server
                val ops = gateway.facet<ServerOperationsGateway>()
                val caps = gateway.capabilities.value
                val projects =
                    if (caps.projectManagement && ops != null)
                        when (val r = ops.listProjects()) {
                            is Outcome.Ok -> r.value
                            is Outcome.Failed -> return@withLock r
                        }
                    else emptyList()
                val providers =
                    when (val r = gateway.providers()) {
                        is Outcome.Ok -> r.value
                        is Outcome.Failed -> return@withLock r
                    }
                val catalog =
                    if (caps.serverCatalog && ops != null) (ops.catalog() as? Outcome.Ok)?.value
                    else null
                val defaults =
                    if (caps.serverCatalog && ops != null)
                        (ops.chatDefaults() as? Outcome.Ok)?.value
                    else null
                // Primary-only modes (Build/Plan) belong to the runtime; hidden helpers
                // (Compaction/Title/Summary) are never user-startable agent choices.
                // Unlike the generic agent list, the catalog retains authoritative visibility.
                // A supported catalog that failed is unknown, not permission to expose hidden
                // agents.
                val agentCatalog =
                    if (caps.serverCatalog) emptyList()
                    else (gateway.agents() as? Outcome.Ok)?.value.orEmpty()
                val agents =
                    (catalog
                            ?.agents
                            ?.filter {
                                !it.hidden &&
                                    (it.mode == null || it.mode == "primary" || it.mode == "all")
                            }
                            ?.map { it.id }
                            ?: agentCatalog
                                .filter {
                                    it.mode == null || it.mode == "primary" || it.mode == "all"
                                }
                                .map { it.name })
                        .filter(::isStartableAgent)
                        .distinct()
                val modes =
                    (catalog?.agents?.filter { !it.hidden && it.mode != "subagent" }?.map { it.id }
                            ?: agentCatalog.filter { it.mode != "subagent" }.map { it.name })
                        .filter { it.lowercase() == "build" || it.lowercase() == "plan" }
                if (registry.live.value[profile] !== graph) return@withLock refused()
                val previous = stores.prefs.string("oc.newChatProject.${profile.raw}")
                val current = gateway.location.value.directory
                val directory =
                    (listOf(previous, current) + projects.map { it.directory }).firstOrNull {
                        candidate ->
                        !candidate.isNullOrBlank() &&
                            candidate != "/" &&
                            candidate != "/root" &&
                            !candidate.startsWith("/tmp/") &&
                            candidate.startsWith('/') &&
                            candidate.none { it.code <= 0x1f || it.code == 0x7f }
                    }
                val importer = gateway.facet<ProviderConversationImportGateway>()
                val draftChoices =
                    stores.prefs.string(choicesKey(profile, directory))?.let { raw ->
                        try {
                            DraftChoices.decode(raw)
                                ?: return@withLock Outcome.Failed(Failure.StorageRefused())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            return@withLock Outcome.Failed(Failure.StorageRefused())
                        }
                    }
                val draftModel = draftChoices?.let { choice ->
                    choice.provider?.let { provider ->
                        choice.model?.let { ModelRef(provider, it) }
                    }
                }
                val savedModel =
                    stores.prefs.string("oc.model.${profile.raw}")?.let { raw ->
                        val fields = raw.split('|', limit = 2)
                        if (
                            fields.size == 2 &&
                                providers.providers.any {
                                    it.id == fields[0] && fields[1] in it.modelIds
                                }
                        )
                            ModelRef(fields[0], fields[1])
                        else null
                    }
                Outcome.Ok(
                    NewConversationSetup(
                        directory = directory,
                        projects =
                            projects
                                .filter {
                                    it.directory != "/" &&
                                        it.directory != "/root" &&
                                        !it.directory.startsWith("/tmp/")
                                }
                                .map {
                                    NewConversationProject(
                                        it.directory,
                                        it.name.ifBlank { it.directory.substringAfterLast('/') },
                                        it.isGit == true && caps.worktreeCreate,
                                    )
                                },
                        models =
                            providers.providers.flatMap { p ->
                                p.modelIds.map { model ->
                                    NewConversationModel(
                                        ModelRef(p.id, model),
                                        catalog
                                            ?.models
                                            ?.firstOrNull {
                                                it.providerId == p.id && it.id == model
                                            }
                                            ?.name ?: model,
                                        if (caps.hostAgentProviders) p.name else null,
                                    )
                                }
                            },
                        agents = agents,
                        model =
                            draftModel
                                ?: savedModel
                                ?: defaults?.model?.takeIf { model ->
                                    providers.providers.any {
                                        it.id == model.providerId && model.modelId in it.modelIds
                                    }
                                }
                                ?: providers.defaultProviderId?.let { p ->
                                    providers.defaultModelId?.let { ModelRef(p, it) }
                                }
                                ?: providers.providers.firstNotNullOfOrNull { p ->
                                    p.modelIds.firstOrNull()?.let { ModelRef(p.id, it) }
                                },
                        agent = draftChoices?.agent?.takeIf { it in modes || it in agents },
                        modes = modes,
                        importDirectory =
                            importer
                                ?.takeIf { caps.hostAgentProviders && it.providerImportSupported }
                                ?.providerImportDirectory,
                        canChangeProject = caps.projectManagement,
                        defaultRuntime =
                            if (caps.hostAgentProviders)
                                providers.providers
                                    .firstOrNull { it.id == providers.defaultProviderId }
                                    ?.name ?: "OpenCode"
                            else "OpenCode",
                    )
                )
            }
        }

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
        withContext(serial) {
            lane.withLock {
                val graph =
                    registry.live.value[profile]
                        ?: return@withLock NewConversationAttempt(
                            directory,
                            failure = refused().failure,
                        )
                val expectedTarget = target(graph, directory)
                if (!currentTarget(graph, directory))
                    return@withLock NewConversationAttempt(directory, failure = refused().failure)
                when (val held = review(profile, directory)) {
                    is Outcome.Failed ->
                        return@withLock NewConversationAttempt(
                            directory,
                            deliveryUnconfirmed = true,
                            failure = held.failure,
                            targetIdentity = expectedTarget,
                        )
                    is Outcome.Ok ->
                        held.value
                            ?.takeUnless {
                                previous?.ref == it && previous?.deliveryUnconfirmed == false
                            }
                            ?.let { ref ->
                                return@withLock NewConversationAttempt(
                                    directory,
                                    ref = ref,
                                    deliveryUnconfirmed = true,
                                    failure = Failure.Refused(RefusalReason.DispatchUncertain),
                                    targetIdentity = expectedTarget,
                                )
                            }
                }
                var attempt =
                    previous ?: NewConversationAttempt(directory, targetIdentity = expectedTarget)
                if (attempt.targetIdentity != null && attempt.targetIdentity != expectedTarget)
                    return@withLock attempt.copy(
                        ref = null,
                        deliveryUnconfirmed = true,
                        failure = refused().failure,
                    )
                if (
                    stores.profiles.profile(profile) == null ||
                        previous?.dispatched == true ||
                        previous?.deliveryUnconfirmed == true
                )
                    return@withLock attempt.copy(failure = refused().failure)
                val gateway = graph.session.server
                when (val ready = gateway.awaitActionTransport()) {
                    is Outcome.Failed -> return@withLock attempt.copy(failure = ready.failure)
                    is Outcome.Ok -> Unit
                }
                if (
                    registry.live.value[profile] !== graph ||
                        target(graph, directory) != expectedTarget
                )
                    return@withLock attempt.copy(failure = refused().failure)
                // Separate-copy creation belongs to the readiness-checked isolated-task owner.
                if (separateCopy) return@withLock attempt.copy(failure = refused().failure)
                if (attempt.ref == null) {
                    if (attempt.creationAttempted)
                        return@withLock attempt.copy(
                            failure = Failure.Refused(RefusalReason.DispatchUncertain)
                        )
                    // Journal before the create call: a killed process must never retry an
                    // uncertain creation and silently make a second conversation.
                    when (
                        val journal =
                            stores.prefs.commit(stores.lanes.forProfile(profile)) {
                                putString(
                                    creationKey(profile, directory),
                                    FirstMessageReview("", directory, expectedTarget).encode(),
                                )
                            }
                    ) {
                        is Outcome.Failed -> return@withLock attempt.copy(failure = journal.failure)
                        is Outcome.Ok -> Unit
                    }
                    attempt = attempt.copy(creationAttempted = true)
                    when (
                        val created =
                            gateway.createSession(
                                NewSession(
                                    title = text.lineSequence().firstOrNull()?.take(80),
                                    location = ServerLocation(directory = attempt.directory),
                                )
                            )
                    ) {
                        is Outcome.Failed -> {
                            if (created.failure is Failure.ProjectGit)
                                stores.prefs.commit(stores.lanes.forProfile(profile)) {
                                    remove(creationKey(profile, directory))
                                }
                            return@withLock attempt.copy(
                                failure = created.failure,
                                creationAttempted = created.failure !is Failure.ProjectGit,
                            )
                        }
                        is Outcome.Ok -> {
                            attempt = attempt.copy(ref = ConversationRef(profile, created.value.id))
                            val journal =
                                stores.prefs.commit(stores.lanes.forProfile(profile)) {
                                    putString(
                                        creationKey(profile, directory),
                                        FirstMessageReview(
                                                created.value.id.raw,
                                                directory,
                                                expectedTarget,
                                            )
                                            .encode(),
                                    )
                                }
                            if (journal is Outcome.Failed)
                                return@withLock attempt.copy(
                                    failure = journal.failure,
                                    deliveryUnconfirmed = true,
                                )
                        }
                    }
                }
                if (
                    registry.live.value[profile] !== graph ||
                        target(graph, directory) != expectedTarget ||
                        stores.profiles.profile(profile) == null
                )
                    return@withLock attempt.copy(failure = refused().failure)
                val ref = attempt.ref ?: return@withLock attempt.copy(failure = refused().failure)
                val located =
                    when (val read = gateway.session(ref.sessionId)) {
                        is Outcome.Failed -> return@withLock attempt.copy(failure = read.failure)
                        is Outcome.Ok -> read.value
                    }
                if (
                    registry.live.value[profile] !== graph ||
                        target(graph, directory) != expectedTarget ||
                        located.directory == null ||
                        !DirectoryPaths.same(located.directory, attempt.directory)
                )
                    return@withLock attempt.copy(failure = refused().failure)
                // V2 model/agent choices live on the session, never in the prompt body.
                // Keep the created ref on failure, and read the chosen state back before send.
                if (gateway.capabilities.value.sessionModelProviderSwitching) {
                    val selection =
                        gateway.facet<SessionSelectionGateway>()
                            ?: return@withLock attempt.copy(
                                failure = Failure.Refused(RefusalReason.StaleScope)
                            )
                    if (model != null && located.selection?.model != model) {
                        when (val chosen = selection.setSessionModel(ref.sessionId, model, "")) {
                            is Outcome.Failed ->
                                return@withLock attempt.copy(failure = chosen.failure)
                            is Outcome.Ok -> Unit
                        }
                        if (!currentTarget(graph, directory))
                            return@withLock attempt.copy(failure = refused().failure)
                    }
                    if (agent != null && located.selection?.agent != agent) {
                        when (val chosen = selection.setSessionAgent(ref.sessionId, agent)) {
                            is Outcome.Failed ->
                                return@withLock attempt.copy(failure = chosen.failure)
                            is Outcome.Ok -> Unit
                        }
                        if (!currentTarget(graph, directory))
                            return@withLock attempt.copy(failure = refused().failure)
                    }
                    val confirmed =
                        when (val read = gateway.session(ref.sessionId)) {
                            is Outcome.Failed ->
                                return@withLock attempt.copy(failure = read.failure)
                            is Outcome.Ok -> read.value
                        }
                    if (
                        !currentTarget(graph, directory) ||
                            confirmed.directory == null ||
                            !DirectoryPaths.same(confirmed.directory, attempt.directory) ||
                            (model != null && confirmed.selection?.model != model) ||
                            (agent != null && confirmed.selection?.agent != agent)
                    )
                        return@withLock attempt.copy(
                            failure = Failure.Refused(RefusalReason.DispatchUncertain)
                        )
                }
                val delivery =
                    (graph as? ChatGraphAccessors)?.chatDelivery?.engine
                        ?: return@withLock attempt.copy(failure = Failure.StorageRefused())
                // The binding survives process death between durable admission and the exact
                // dispatch outcome.
                // It contains only the created session id; the person's words remain in the
                // existing project draft.
                val previousReview = stores.prefs.get(reviewKey(profile, directory))
                if (previousReview != null)
                    return@withLock attempt.copy(
                        deliveryUnconfirmed = true,
                        failure = Failure.Refused(RefusalReason.DispatchUncertain),
                    )
                when (
                    val held =
                        stores.prefs.commit(stores.lanes.forProfile(profile)) {
                            putString(draftKey(profile, directory), text)
                            putString(
                                reviewKey(profile, directory),
                                FirstMessageReview(
                                        ref.sessionId.raw,
                                        directory,
                                        expectedTarget,
                                        draftDigest = DartJson.sha256Hex(text),
                                    )
                                    .encode(),
                            )
                            // Keep the explicit choice even if delivery needs review. Waiting
                            // until acceptance lost it whenever the first send failed.
                            putString("oc.newChatProject.${profile.raw}", directory)
                            if (model == null) remove("oc.model.${profile.raw}")
                            else
                                putString(
                                    "oc.model.${profile.raw}",
                                    "${model.providerId}|${model.modelId}",
                                )
                        }
                ) {
                    is Outcome.Failed -> return@withLock attempt.copy(failure = held.failure)
                    is Outcome.Ok -> Unit
                }
                val request =
                    SendRequest(
                        ref = ref,
                        text = text,
                        model = model?.let(::ModelSelection),
                        agent = agent,
                    )
                val receipt =
                    awaitFirstMessage(
                        delivery,
                        graph,
                        ref,
                        directory,
                        expectedTarget,
                        request,
                        onStarted,
                    )
                if (!currentTarget(graph, directory) || target(graph, directory) != expectedTarget)
                    return@withLock attempt.copy(
                        ref = null,
                        deliveryUnconfirmed = true,
                        failure = refused().failure,
                    )
                when (receipt) {
                    is FirstMessage.Accepted -> {
                        val expected =
                            linkedMapOf(
                                reviewKey(profile, directory) to
                                    stores.prefs.get(reviewKey(profile, directory)),
                                creationKey(profile, directory) to
                                    stores.prefs.get(creationKey(profile, directory)),
                                draftKey(profile, directory) to
                                    stores.prefs.get(draftKey(profile, directory)),
                            )
                        fun binding(key: String): FirstMessageReview? =
                            (expected[key] as? PrefValue.Str)
                                ?.value
                                ?.let(FirstMessageReview::decode)
                        fun original(binding: FirstMessageReview?): Boolean =
                            binding != null &&
                                binding.session == ref.sessionId.raw &&
                                binding.directory == directory &&
                                binding.target == expectedTarget
                        val review = binding(reviewKey(profile, directory))
                        val creation = binding(creationKey(profile, directory))
                        val draft =
                            (expected[draftKey(profile, directory)] as? PrefValue.Str)?.value
                        // Confirmation belongs to this send even if another New screen has
                        // already replaced its project draft or metadata. Cleanup is best effort;
                        // a refused CAS must never turn that confirmed send into a failed send.
                        stores.prefs.compareAndCommit(stores.lanes.forProfile(profile), expected) {
                            if (
                                currentTarget(graph, directory) &&
                                    target(graph, directory) == expectedTarget
                            ) {
                                if (original(review)) remove(reviewKey(profile, directory))
                                if (original(creation)) remove(creationKey(profile, directory))
                                if (
                                    review != null &&
                                        original(review) &&
                                        review.draftDigest == DartJson.sha256Hex(text) &&
                                        draft != null &&
                                        DartJson.sha256Hex(draft) == review.draftDigest
                                )
                                    remove(draftKey(profile, directory))
                            }
                        }
                        receipt.outcome?.let(delivery::acknowledge)
                        attempt.copy(dispatched = true, failure = null)
                    }
                    is FirstMessage.PutBack -> {
                        val cleared =
                            stores.prefs.commit(stores.lanes.forProfile(profile)) {
                                remove(reviewKey(profile, directory))
                            }
                        receipt.outcome?.let(delivery::acknowledge)
                        if (cleared is Outcome.Failed)
                            attempt.copy(deliveryUnconfirmed = true, failure = cleared.failure)
                        else attempt.copy(failure = receipt.failure)
                    }
                    is FirstMessage.Unknown ->
                        attempt.copy(
                            deliveryUnconfirmed = true,
                            failure =
                                receipt.failure ?: Failure.Refused(RefusalReason.DispatchUncertain),
                        )
                }
            }
        }

    private sealed interface FirstMessage {
        data class Accepted(val outcome: SendOutcomeId? = null) : FirstMessage

        data class PutBack(
            val failure: Failure,
            val outcome: SendOutcomeId? = null,
        ) : FirstMessage

        data class Unknown(
            val failure: Failure? = null,
            val outcome: SendOutcomeId? = null,
        ) : FirstMessage
    }

    private data class FirstMessageObservations(
        val current: Boolean = true,
        val receipts: Map<LocalSendId, FirstMessage> = emptyMap(),
    )

    /**
     * Subscribe before admission; retain exact-id outcomes even if another display acknowledges
     * their engine row.
     */
    private suspend fun awaitFirstMessage(
        delivery: PromptDelivery,
        owner: ConnectionGraph,
        ref: ConversationRef,
        directory: String,
        expectedTarget: String,
        request: SendRequest,
        onStarted: (ConversationRef) -> Unit,
    ): FirstMessage = coroutineScope {
        val observed = MutableStateFlow(FirstMessageObservations())
        val observer =
            launch(start = CoroutineStart.UNDISPATCHED) {
                combine(
                        delivery.outcomes,
                        delivery.pending,
                        registry.live,
                        stores.profiles.profiles,
                    ) { outcomes, pending, live, saved ->
                        val receipts = observed.value.receipts.toMutableMap()
                        pending
                            .filter { it.ref == ref && it.phase == SendPhase.Accepted }
                            .forEach { receipts[it.localId] = FirstMessage.Accepted() }
                        outcomes
                            .filter { it.ref == ref }
                            .forEach { outcome ->
                                when (outcome) {
                                    is SendOutcome.Accepted ->
                                        receipts[outcome.localId] =
                                            FirstMessage.Accepted(outcome.id)
                                    is SendOutcome.PutBack ->
                                        outcome.localId?.let {
                                            receipts[it] =
                                                FirstMessage.PutBack(outcome.reason, outcome.id)
                                        }
                                    is SendOutcome.Unconfirmed ->
                                        outcome.localId?.let {
                                            if (receipts[it] !is FirstMessage.Accepted)
                                                receipts[it] =
                                                    FirstMessage.Unknown(outcome.reason, outcome.id)
                                        }
                                    is SendOutcome.Queued ->
                                        receipts[LocalSendId(outcome.entryId)] =
                                            FirstMessage.Unknown()
                                }
                            }
                        FirstMessageObservations(
                            live[ref.profileId] === owner &&
                                target(owner, directory) == expectedTarget &&
                                saved
                                    .firstOrNull { it.id == ref.profileId }
                                    ?.toServerProfile()
                                    ?.let { target(it, directory) == target(owner, directory) } ==
                                    true,
                            receipts,
                        )
                    }
                    .collect { observed.value = it }
            }
        try {
            when (val sent = delivery.send(request)) {
                is Outcome.Failed -> {
                    val uncertain =
                        (sent.failure as? Failure.Refused)?.reason ==
                            RefusalReason.DispatchUncertain
                    if (sent.failure.nothingSent && !uncertain) FirstMessage.PutBack(sent.failure)
                    else FirstMessage.Unknown(sent.failure)
                }
                is Outcome.Ok -> {
                    when (
                        val bound =
                            bindFirstMessageReview(
                                delivery,
                                sent.value,
                                ref,
                                directory,
                                expectedTarget,
                            )
                    ) {
                        is Outcome.Failed ->
                            return@coroutineScope FirstMessage.Unknown(bound.failure)
                        is Outcome.Ok -> Unit
                    }
                    // The engine owns the pending send now. Open its real conversation with
                    // the composer immediately while this owner reconciles its first receipt.
                    if (
                        observed.value.current &&
                            currentTarget(owner, directory) &&
                            target(owner, directory) == expectedTarget
                    )
                        onStarted(ref)
                    // Commands return only after their synchronous gateway receipt, with no
                    // bubble/outcome row.
                    if (
                        sent.value.raw.startsWith("cmd-") &&
                            observed.value.current &&
                            currentTarget(owner, directory)
                    )
                        FirstMessage.Accepted()
                    else {
                        // An Unconfirmed transport outcome is provisional. The receipt can
                        // arrive after the POST response (especially when this phone hosts the
                        // server too). Keep observing the exact send and check its journalled
                        // message id; both paths read only, and neither resends the words.
                        withTimeoutOrNull(240_000) {
                            while (
                                observed.value.current &&
                                    currentTarget(owner, directory) &&
                                    target(owner, directory) == expectedTarget
                            ) {
                                val state = observed.value
                                when (val receipt = state.receipts[sent.value]) {
                                    is FirstMessage.Accepted -> return@withTimeoutOrNull receipt
                                    is FirstMessage.PutBack -> return@withTimeoutOrNull receipt
                                    is FirstMessage.Unknown -> {
                                        val checked = delivery.checkAgain(sent.value)
                                        if (
                                            checked is Outcome.Ok &&
                                                checked.value == ReceiptLook.Confirmed &&
                                                currentTarget(owner, directory) &&
                                                target(owner, directory) == expectedTarget
                                        )
                                            return@withTimeoutOrNull FirstMessage.Accepted(
                                                receipt.outcome
                                            )
                                    }
                                    null -> Unit
                                }
                                delay(2_000)
                            }
                            FirstMessage.Unknown()
                        }
                            ?: observed.value.receipts[sent.value]?.let {
                                if (it is FirstMessage.Unknown) it else FirstMessage.Unknown()
                            }
                            ?: FirstMessage.Unknown()
                    }
                }
            }
        } finally {
            observer.cancel()
        }
    }

    private fun importer(
        profile: ProfileId,
        directory: String,
    ): Pair<ConnectionGraph, ProviderConversationImportGateway>? {
        val graph = registry.live.value[profile] ?: return null
        if (
            stores.profiles.profile(profile) == null ||
                !graph.session.capabilities.value.hostAgentProviders
        )
            return null
        val gateway = graph.session.server.facet<ProviderConversationImportGateway>() ?: return null
        if (!gateway.providerImportSupported || gateway.providerImportDirectory != directory)
            return null
        return graph to gateway
    }

    override suspend fun imports(
        profile: ProfileId,
        directory: String,
    ): Outcome<ImportableConversations> =
        withContext(serial) {
            val (graph, importer) = importer(profile, directory) ?: return@withContext refused()
            val result = importer.importableConversations()
            if (registry.live.value[profile] !== graph) refused() else result
        }

    override suspend fun import(
        profile: ProfileId,
        directory: String,
        conversation: ImportableConversation,
    ): Outcome<ConversationRef> =
        withContext(serial) {
            lane.withLock {
                val (graph, importer) = importer(profile, directory) ?: return@withLock refused()
                if (conversation.directory != directory) return@withLock refused()
                when (val imported = importer.importConversation(conversation)) {
                    is Outcome.Failed -> imported
                    is Outcome.Ok ->
                        if (registry.live.value[profile] !== graph) refused()
                        else Outcome.Ok(ConversationRef(profile, imported.value.id))
                }
            }
        }
}
