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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.domain.ProviderConversationImportGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
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
import io.github.eslamasabry.oc.engine.delivery.LocalSendId
import io.github.eslamasabry.oc.engine.delivery.PromptDelivery
import io.github.eslamasabry.oc.engine.delivery.SendOutcome
import io.github.eslamasabry.oc.engine.delivery.SendPhase
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import io.github.eslamasabry.oc.engine.delivery.port.nothingSent
import io.github.eslamasabry.oc.engine.git.GitLinkRepairReport
import io.github.eslamasabry.oc.engine.git.GitLinkRepairService
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chats.presentation.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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

    private fun reviewKey(profile: ProfileId, directory: String?): String =
        draftKey(profile, directory).replace("oc.draft.new-chat.", "oc.draft.new-chat-review.")

    @Serializable
    private data class FirstMessageReview(
        val session: String,
        val directory: String,
        val target: String,
    )

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
            val raw =
                stores.prefs.get(reviewKey(profile, directory))
                    ?: return@withContext Outcome.Ok(null)
            val graph = registry.live.value[profile] ?: return@withContext refused()
            if (raw !is PrefValue.Str || raw.value.length > 8192 || directory == null)
                return@withContext Outcome.Failed(Failure.StorageRefused())
            val receipt =
                try {
                    WireJson.decodeFromString<FirstMessageReview>(raw.value)
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
            Outcome.Ok(ConversationRef(profile, SessionId(receipt.session)))
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
                val agents =
                    (gateway.agents() as? Outcome.Ok)
                        ?.value
                        .orEmpty()
                        .filter { it.mode != "subagent" }
                        .map { it.name }
                if (registry.live.value[profile] !== graph) return@withLock refused()
                val previous = stores.prefs.string("oc.newChatProject.${profile.raw}")
                val current = gateway.location.value.directory
                val directory =
                    listOf(previous, current).firstOrNull { candidate ->
                        !candidate.isNullOrBlank() &&
                            candidate != "/" &&
                            candidate != "/root" &&
                            !candidate.startsWith("/tmp/")
                    }
                val importer = gateway.facet<ProviderConversationImportGateway>()
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
                            providers.defaultProviderId?.let { p ->
                                providers.defaultModelId?.let { ModelRef(p, it) }
                            },
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
                        held.value?.let { ref ->
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
                    attempt = attempt.copy(creationAttempted = true)
                    when (
                        val created =
                            gateway.createSession(
                                NewSession(location = ServerLocation(directory = attempt.directory))
                            )
                    ) {
                        is Outcome.Failed ->
                            return@withLock attempt.copy(
                                failure = created.failure,
                                creationAttempted = created.failure !is Failure.ProjectGit,
                            )
                        is Outcome.Ok ->
                            attempt = attempt.copy(ref = ConversationRef(profile, created.value.id))
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
                                WireJson.encodeToString(
                                    FirstMessageReview(ref.sessionId.raw, directory, expectedTarget)
                                ),
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
                    awaitFirstMessage(delivery, graph, ref, directory, expectedTarget, request)
                if (!currentTarget(graph, directory) || target(graph, directory) != expectedTarget)
                    return@withLock attempt.copy(
                        ref = null,
                        deliveryUnconfirmed = true,
                        failure = refused().failure,
                    )
                when (receipt) {
                    is FirstMessage.Accepted -> {
                        stores.prefs.commit(stores.lanes.forProfile(profile)) {
                            remove(draftKey(profile, directory))
                            remove(reviewKey(profile, directory))
                            putString("oc.newChatProject.${profile.raw}", directory)
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
        data class Accepted(
            val outcome: io.github.eslamasabry.oc.engine.delivery.SendOutcomeId? = null
        ) : FirstMessage

        data class PutBack(
            val failure: Failure,
            val outcome: io.github.eslamasabry.oc.engine.delivery.SendOutcomeId? = null,
        ) : FirstMessage

        data class Unknown(val failure: Failure? = null) : FirstMessage
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
                                            receipts[it] = FirstMessage.Unknown(outcome.reason)
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
                    // Commands return only after their synchronous gateway receipt, with no
                    // bubble/outcome row.
                    if (
                        sent.value.raw.startsWith("cmd-") &&
                            observed.value.current &&
                            currentTarget(owner, directory)
                    )
                        FirstMessage.Accepted()
                    else {
                        val state =
                            withTimeoutOrNull(95_000) {
                                observed.first { !it.current || sent.value in it.receipts }
                            }
                        if (
                            state == null ||
                                !state.current ||
                                !currentTarget(owner, directory) ||
                                target(owner, directory) != expectedTarget
                        )
                            FirstMessage.Unknown()
                        else state.receipts[sent.value] ?: FirstMessage.Unknown()
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
