package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.feature.chat.header.world.AgentHandoffChoice
import io.github.eslamasabry.oc.feature.chat.header.world.ChatAgentHandoff
import io.github.eslamasabry.oc.feature.chat.header.world.ChatAgentHandoffs
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.genUiHistoryPage
import io.github.eslamasabry.oc.protocol.paseo.gateway.writeAgentHandoff
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The current daemon is app-owned evidence of one filesystem. A second saved server, a matching URL
 * or a matching folder name is deliberately insufficient: no cross-server destination is
 * synthesized from those strings.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ChatAgentHandoffs>())
class AppAgentHandoffs(private val registry: ConnectionRegistry, stores: PreviewStores) :
    ChatAgentHandoffs {
    val links = HandoffLinks(KvAgentPrefs(stores.prefs, stores.lanes.app))

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
    private var currentChoices: List<AgentHandoffChoice> = emptyList()

    private fun current(): Boolean = registry.graphFor(ref) === graph && !gateway.isClosed

    private fun refused(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    override suspend fun choices(): Outcome<List<AgentHandoffChoice>> {
        if (!current() || !gateway.capabilities.value.agentHandoffFiles)
            return Outcome.Ok(emptyList())
        val read = gateway.providers()
        if (!current()) return refused()
        return read.map { inventory ->
            inventory.providers
                .flatMap { provider ->
                    provider.modelIds.map { model ->
                        AgentHandoffChoice(provider.id, model, "${provider.name} · $model")
                    }
                }
                .also { currentChoices = it }
        }
    }

    override suspend fun prepare(choice: AgentHandoffChoice): Outcome<HandoffPrepared> {
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

/**
 * Removes outgoing metadata and references inside surviving owners before the generic profile
 * sweep.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class AgentHandoffDeletion(private val handoffs: AppAgentHandoffs) : ProfileResourceCleaner {
    override val owner: String = ":engine:agents (conversation handoffs)"
    override val order: Int = DeletionStep.DEVICE_SURFACES
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        when (val removed = handoffs.links.removeProfile(profileId.raw)) {
            is Outcome.Failed -> removed
            is Outcome.Ok ->
                when (removed.value) {
                    HandoffLinkSave.Saved -> Outcome.Ok(1)
                    is HandoffLinkSave.Refused -> Outcome.Failed(Failure.StorageRefused())
                }
        }
}

/** Blocks every handoff involving the profile before its connections and data begin closing. */
@Inject
@ContributesIntoSet(
    AppScope::class,
    binding = binding<io.github.eslamasabry.oc.engine.profiles.ProfileCloser>(),
)
class AgentHandoffCloser(private val handoffs: AppAgentHandoffs) :
    io.github.eslamasabry.oc.engine.profiles.ProfileCloser {
    override val owner: String = ":engine:agents (conversation handoffs)"

    override fun stopAdmission(profile: ProfileId) {
        handoffs.links.stopAdmission(profile.raw)
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        handoffs.links.closeProfile(profile.raw)
        return Outcome.Ok(Unit)
    }

    override fun reopen(profile: ProfileId) {
        handoffs.links.reopenProfile(profile.raw)
    }
}
