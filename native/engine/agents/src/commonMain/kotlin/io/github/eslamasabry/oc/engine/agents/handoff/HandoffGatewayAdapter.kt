package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.domain.AgentHandoffGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Protocol-neutral projection of a real, pinned graph into the bounded handoff engine. */
public class HandoffGatewayAdapter(
    private val gateway: ServerGateway,
    private val current: () -> Boolean,
) : HandoffGateway {
    private val target: AgentHandoffGateway? = gateway as? AgentHandoffGateway

    public fun durableId(id: String): String =
        target?.durableHandoffSessionId(SessionId(id))?.raw ?: id

    override val directory: String?
        get() = gateway.location.value.directory

    override val workspace: String?
        get() = gateway.location.value.workspace

    override val isClosed: Boolean
        get() = !current()

    override val capabilities: HandoffCapabilities
        get() =
            gateway.capabilities.value.let {
                HandoffCapabilities(
                    agentHandoffFiles = it.agentHandoffFiles && target?.handoffFilesReady == true,
                    sessionTodos = it.sessionTodos,
                    sessionDiff = it.sessionDiff,
                    sessionModelProviderSwitching = it.sessionModelProviderSwitching,
                )
            }

    override val historyReader: HandoffHistoryReader =
        HandoffHistoryReader.Paged { id, cursor, limit ->
            gateway.messagePage(SessionId(id), cursor, limit).map { page ->
                HandoffHistoryPage(page.items.map(::message), page.nextCursor)
            }
        }

    override suspend fun session(id: String): Outcome<HandoffSession> =
        gateway.session(SessionId(id)).map(::session)

    override suspend fun createSession(): Outcome<HandoffSession> =
        gateway.createSession().map(::session)

    override suspend fun todos(sessionId: String): Outcome<List<HandoffTodo>> =
        gateway.todos(SessionId(sessionId)).map { rows ->
            rows.map { HandoffTodo(it.content, it.status, it.priority) }
        }

    override suspend fun diff(sessionId: String): Outcome<List<HandoffFileDiff>> =
        gateway.diff(SessionId(sessionId)).map { rows ->
            rows.map { HandoffFileDiff(it.file, it.additions, it.deletions, status = it.status) }
        }

    override suspend fun providers(): Outcome<List<HandoffProvider>> =
        gateway.providers().map { response ->
            response.providers.map { provider ->
                val variants =
                    provider.modelData.mapValues { (_, model) ->
                        (model["variants"] as? JsonObject).orEmpty().mapValues { (_, raw) ->
                            ((raw as? JsonObject)?.get("disabled") as? JsonPrimitive)
                                ?.booleanOrNull == true
                        }
                    }
                HandoffProvider(provider.id, provider.modelIds, variants)
            }
        }

    override val handoffTarget: HandoffTargetPort? = target?.let { port ->
        object : HandoffTargetPort {
            override suspend fun writeRecord(
                directory: String,
                recordId: String,
                markdown: String,
            ): Outcome<String> = port.writeHandoffRecord(directory, recordId, markdown)

            override fun durableSessionId(sessionId: String): String =
                port.durableHandoffSessionId(SessionId(sessionId)).raw

            override fun navigationSessionId(sessionId: String): String =
                port.handoffNavigationSessionId(SessionId(sessionId)).raw

            override suspend fun promptHandoff(
                sessionId: String,
                text: String,
                model: HandoffModelRef,
                agent: String?,
                variant: String?,
                beforeSend: () -> Boolean,
            ): Outcome<Unit> =
                port.promptHandoff(
                    SessionId(sessionId),
                    text,
                    ModelRef(model.providerId, model.modelId),
                    agent,
                    variant,
                    beforeSend,
                )
        }
    }

    private fun session(value: Session): HandoffSession =
        HandoffSession(
            id = durableId(value.id.raw),
            title = value.title,
            directory = value.directory,
            path = value.path,
            agent = value.agent,
            model = value.model,
            updated = value.time?.updatedMs,
        )

    private fun message(value: MessageWithParts): HandoffMessage =
        HandoffMessage(
            id = value.info.id,
            sessionId = durableId(value.info.sessionId),
            role = if (value.info.role == Role.User) "user" else "assistant",
            parts = value.parts.map(::part),
            errorText = value.info.errorText,
        )

    private fun part(value: Part): HandoffPart =
        HandoffPart(
            type = value.rawType,
            text = value.text,
            synthetic = value.synthetic,
            filename = value.filename,
            url = value.url,
            toolName = value.toolName,
            tool =
                value.tool?.let { tool ->
                    HandoffToolState(
                        status = tool.status.name.lowercase(),
                        input = tool.input.mapValues { plain(it.value) },
                        inputJson = tool.inputRaw,
                        output = tool.output,
                        outputValue = tool.outputValue?.let(::plain),
                        metadata = tool.metadata?.mapValues { plain(it.value) },
                        pruned = tool.pruned,
                        executed = tool.ran,
                    )
                } ?: HandoffToolState("pending"),
        )

    private fun plain(value: JsonElement): Any? =
        when (value) {
            JsonNull -> null
            is JsonObject -> value.mapValues { plain(it.value) }
            is JsonArray -> value.map(::plain)
            is JsonPrimitive ->
                when {
                    value.isString -> value.content
                    value.booleanOrNull != null -> value.booleanOrNull
                    value.longOrNull != null -> value.longOrNull
                    else -> value.doubleOrNull
                }
        }
}
