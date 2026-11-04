package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.WorkspaceTransferGateway
import io.github.eslamasabry.oc.core.domain.WorkspaceTransferRequest
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.*

/**
 * Adapts the current connection's own history and negotiated writer, never another profile or
 * inferred filesystem.
 */
public class ConnectedHandoffGateway(
    private val gateway: ServerGateway,
    private val current: () -> Boolean,
    private val redactor: Redactor,
) : HandoffGateway {
    private val writer = gateway as? WorkspaceTransferGateway
    private val expectedEpoch = writer?.transferEpoch
    override val directory: String?
        get() = gateway.location.value.directory

    override val workspace: String?
        get() = gateway.location.value.workspace

    override val isClosed: Boolean
        get() =
            !current() || writer?.transferConnected != true || writer.transferEpoch != expectedEpoch

    override val capabilities: HandoffCapabilities
        get() =
            gateway.capabilities.value.let {
                HandoffCapabilities(
                    it.agentHandoffFiles && !isClosed,
                    it.sessionTodos,
                    it.sessionDiff,
                    it.sessionModelProviderSwitching,
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

    override suspend fun todos(sessionId: String): Outcome<List<HandoffTodo>> =
        gateway.todos(SessionId(sessionId)).map { rows ->
            rows.map { HandoffTodo(it.content, it.status, it.priority) }
        }

    override suspend fun diff(sessionId: String): Outcome<List<HandoffFileDiff>> =
        gateway.diff(SessionId(sessionId)).map { rows ->
            rows.map { HandoffFileDiff(it.file, it.additions, it.deletions, status = it.status) }
        }

    override suspend fun providers(): Outcome<List<HandoffProvider>> =
        gateway.providers().map { inventory ->
            inventory.providers.map { provider ->
                HandoffProvider(
                    provider.id,
                    provider.modelIds,
                    provider.modelData.mapValues { (_, model) ->
                        (model["variants"] as? JsonObject)
                            ?.mapValues { (_, value) ->
                                ((value as? JsonObject)?.get("disabled") as? JsonPrimitive)
                                    ?.booleanOrNull ?: false
                            }
                            .orEmpty()
                    },
                )
            }
        }

    override suspend fun createSession(): Outcome<HandoffSession> =
        gateway
            .createSession(NewSession(location = ServerLocation(directory, workspace)))
            .map(::session)

    override val handoffTarget: HandoffTargetPort?
        get() = if (writer == null) null else target

    private val target =
        object : HandoffTargetPort {
            override suspend fun writeRecord(
                directory: String,
                recordId: String,
                markdown: String,
            ): Outcome<String> {
                val facet = writer ?: return unavailable()
                val pinnedEpoch = expectedEpoch ?: return unavailable()
                var leased = false
                val workspace =
                    object : HandoffWorkspace {
                        override val scope: String?
                            get() = this@ConnectedHandoffGateway.directory

                        override val handoffFiles: Boolean
                            get() = capabilities.agentHandoffFiles && !isClosed

                        override val epoch: Long
                            get() = facet.transferEpoch

                        private suspend fun request(
                            value: WorkspaceTransferRequest
                        ): Outcome<JsonObject> =
                            if (!leased || isClosed) unavailable()
                            else facet.transferFileRequest(directory, pinnedEpoch, value)

                        override suspend fun create(
                            parent: String,
                            name: String,
                            kind: HandoffEntryKind,
                        ): Outcome<HandoffEntryCreated> =
                            request(
                                    WorkspaceTransferRequest.Create(
                                        parent,
                                        name,
                                        kind == HandoffEntryKind.Directory,
                                    )
                                )
                                .map {
                                    HandoffEntryCreated(
                                        it.string("cwd"),
                                        it.string("parentPath"),
                                        it.string("path"),
                                        (it["success"] as? JsonPrimitive)?.booleanOrNull == true,
                                    )
                                }

                        override suspend fun list(path: String): Outcome<HandoffListing> =
                            request(WorkspaceTransferRequest.ListEntries(path)).map {
                                HandoffListing(
                                    it.string("cwd"),
                                    it.string("path"),
                                    it["error"] != null && it["error"] != JsonNull,
                                    ((it["directory"] as? JsonObject)?.get("entries") as? JsonArray)
                                        ?.map { value ->
                                            val entry = value as? JsonObject
                                            HandoffListEntry(
                                                entry?.string("name"),
                                                entry?.string("kind"),
                                            )
                                        },
                                )
                            }

                        override suspend fun read(
                            path: String,
                            maxBytes: Int,
                        ): Outcome<HandoffFileRead> =
                            request(WorkspaceTransferRequest.ReadFile(path, maxBytes)).map {
                                val file = it["file"] as? JsonObject
                                HandoffFileRead(
                                    it.string("cwd"),
                                    it.string("path"),
                                    it["error"] != null && it["error"] != JsonNull,
                                    file?.let { f ->
                                        HandoffFileData(
                                            f.string("path"),
                                            f.string("kind"),
                                            f.string("encoding"),
                                            f.string("content"),
                                            f.string("modifiedAt"),
                                            f.string("revision"),
                                        )
                                    },
                                )
                            }

                        override suspend fun write(
                            path: String,
                            content: String,
                            expectedModifiedAt: String,
                            expectedRevision: String?,
                        ): Outcome<String?> =
                            request(
                                    WorkspaceTransferRequest.WriteFile(
                                        path,
                                        content,
                                        expectedModifiedAt,
                                        expectedRevision,
                                    )
                                )
                                .map {
                                    (it["result"] as? JsonObject)?.string("status")
                                }
                    }
                val lease =
                    object : HandoffHostLease {
                        // This is a remote saved connection. The app owns no daemon payload
                        // lifecycle there; its lease is
                        // the still-live, admitted graph, also rechecked inside every transport
                        // mutation.
                        override suspend fun acquire(): Outcome<Unit> {
                            if (isClosed || leased) return unavailable()
                            leased = true
                            return Unit.ok()
                        }

                        override fun release() {
                            leased = false
                        }
                    }
                return when (
                    val write =
                        HandoffRecordFiles(workspace, lease, redactor)
                            .write(directory, recordId, markdown)
                ) {
                    is Outcome.Failed -> write
                    is Outcome.Ok ->
                        when (val value = write.value) {
                            is HandoffFileWrite.Written -> value.relativePath.ok()
                            is HandoffFileWrite.Refused -> unavailable()
                        }
                }
            }

            override fun durableSessionId(sessionId: String): String =
                writer?.transferSessionId(SessionId(sessionId))?.raw ?: sessionId

            override fun navigationSessionId(sessionId: String): String =
                writer?.transferNavigationId(SessionId(sessionId))?.raw ?: sessionId

            override suspend fun promptHandoff(
                sessionId: String,
                text: String,
                model: HandoffModelRef,
                agent: String?,
                variant: String?,
                beforeSend: () -> Boolean,
            ): Outcome<Unit> {
                val facet = writer ?: return unavailable()
                if (isClosed) return unavailable()
                val result =
                    facet.promptTransfer(
                        SessionId(sessionId),
                        OutgoingPrompt(
                            text,
                            ModelSelection(ModelRef(model.providerId, model.modelId), variant),
                            agent,
                        ),
                        beforeSend = { !isClosed && beforeSend() },
                    )
                return when (result) {
                    is Outcome.Failed -> result
                    is Outcome.Ok ->
                        when (result.value) {
                            is PromptAdmission.Confirmed -> Unit.ok()
                            PromptAdmission.Uncertain -> Failure.Unknown().failed()
                        }
                }
            }
        }

    private fun session(value: Session): HandoffSession =
        HandoffSession(
            value.id.raw,
            value.title,
            value.directory,
            value.path,
            value.agent,
            value.model,
            value.time?.updatedMs,
        )

    private fun message(value: MessageWithParts): HandoffMessage =
        HandoffMessage(
            value.info.id,
            value.info.sessionId,
            value.info.role.name.lowercase(),
            value.parts.map { part ->
                val tool = part.tool
                HandoffPart(
                    part.rawType,
                    part.text,
                    part.synthetic,
                    part.filename,
                    part.url,
                    part.toolName,
                    HandoffToolState(
                        tool?.status?.name?.lowercase() ?: "pending",
                        tool?.input?.mapValues { jsonValue(it.value) }.orEmpty(),
                        tool?.inputRaw,
                        tool?.output,
                        tool?.outputValue?.let(::jsonValue),
                        tool?.metadata?.mapValues { jsonValue(it.value) },
                        tool?.pruned == true,
                        tool?.ran != false,
                    ),
                )
            },
            value.info.errorText,
        )

    private fun jsonValue(value: JsonElement): Any? =
        when (value) {
            JsonNull -> null
            is JsonObject -> value.mapValues { jsonValue(it.value) }
            is JsonArray -> value.map(::jsonValue)
            is JsonPrimitive ->
                if (value.isString) value.content
                else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
        }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun unavailable(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}
