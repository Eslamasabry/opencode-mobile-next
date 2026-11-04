package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.attempt as capture
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.chat.handoff.*
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.handoffGeneration
import io.github.eslamasabry.oc.protocol.paseo.gateway.promptNewHandoff
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import java.util.UUID
import kotlinx.serialization.json.*

/**
 * B-STATE-001: one durable link owner used by both the transfer and counterpart navigation after
 * restart.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<StateHandoffSource>())
class AndroidStateHandoffSource(
    stores: PreviewStores,
    private val profiles: ProfileStore,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val saved: NativeHandoffLinks,
    private val nativeSource: NativeHandoffSource,
) : StateHandoffSource {
    internal val links: HandoffLinks
        get() = saved.links

    /** The navigation alias names a runtime; persisted links always name its durable parent. */
    private fun parent(ref: ConversationRef): ProfileId? {
        val id =
            if (ref.profileId.raw.endsWith(".agents"))
                ProfileId(ref.profileId.raw.removeSuffix(".agents"))
            else ref.profileId
        return id.takeIf { Regex("[A-Za-z0-9_-]{1,128}").matches(it.raw) && saved.admitting(it) }
    }

    private fun origin(ref: ConversationRef): HandoffOrigin =
        if (ref.profileId.raw.endsWith(".agents")) HandoffOrigin.PhoneAgent
        else HandoffOrigin.Server

    override suspend fun links(ref: ConversationRef): Outcome<List<HandoffLink>> =
        when (
            val result =
                capture({ Failure.StorageRefused() }) {
                    val parent = parent(ref) ?: return@capture null
                    links.forSession(parent.raw, canonicalSession(ref), origin(ref))
                }
        ) {
            is Outcome.Failed -> result
            is Outcome.Ok -> result.value?.ok() ?: Failure.StorageRefused().failed()
        }

    override suspend fun destinations(ref: ConversationRef): Outcome<List<HandoffDestination>> {
        val graph = registry.graphFor(ref) ?: return Failure.Network(NetworkWhy.Offline).failed()
        val gateway = graph.session.server
        if (!gateway.capabilities.value.agentHandoffFiles || gateway !is PaseoGateway)
            return emptyList<HandoffDestination>().ok()
        return when (val inventory = gateway.providers()) {
            is Outcome.Failed -> inventory
            is Outcome.Ok ->
                if (registry.graphFor(ref) !== graph)
                    Failure.Refused(RefusalReason.StaleScope).failed()
                else
                    inventory.value.providers
                        .flatMap { provider ->
                            provider.modelIds.map {
                                HandoffDestination(provider.id, it, "${provider.name} · $it")
                            }
                        }
                        .ok()
        }
    }

    override suspend fun attempt(
        ref: ConversationRef,
        destination: HandoffDestination,
        current: () -> Boolean,
    ): Outcome<HandoffEngine> {
        val graph = registry.graphFor(ref) ?: return Failure.Network(NetworkWhy.Offline).failed()
        val gateway =
            graph.session.server as? PaseoGateway
                ?: return Failure.Unavailable(FeatureId("agentHandoffFiles")).failed()
        val directory =
            gateway.location.value.directory
                ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val adapter = NativeHandoffGateway(gateway, graph, registry, ref, redactor)
        // A single live gateway is app-owned evidence of the same filesystem; no address or
        // reported folder establishes it.
        val scope =
            HandoffScope(
                (parent(ref) ?: return Failure.Refused(RefusalReason.ProfileClosing).failed()).raw,
                directory,
                gateway.location.value.workspace,
                "live-connection",
                origin(ref),
            )
        val target =
            HandoffTarget(
                adapter,
                scope,
                HandoffModelRef(destination.provider, destination.model),
                destination.label,
            )
        if (!gateway.capabilities.value.agentHandoffFiles)
            return Failure.Unavailable(FeatureId("agentHandoffFiles")).failed()
        return HandoffEngine(
                adapter,
                scope,
                ref.sessionId.raw,
                target,
                links,
                isCurrent = {
                    current() && registry.graphFor(ref) === graph && parent(ref) != null
                },
                recordId = { UUID.randomUUID().toString() },
                redactor = redactor,
            )
            .ok()
    }

    private fun canonicalSession(ref: ConversationRef): String =
        (registry.graphFor(ref)?.session?.server as? PaseoGateway)
            ?.daemonSessionId(ref.sessionId)
            ?.raw ?: ref.sessionId.raw

    override suspend fun counterpart(
        ref: ConversationRef,
        link: HandoffLink,
    ): Outcome<ConversationRef> {
        val durableParent =
            parent(ref) ?: return Failure.Refused(RefusalReason.ProfileClosing).failed()
        val canonical = canonicalSession(ref)
        val sourceOrigin = origin(ref)
        fun admitted(): Boolean =
            parent(ref) == durableParent &&
                links.forSession(durableParent.raw, canonical, sourceOrigin)?.any { it == link } ==
                    true
        if (!admitted()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val outgoing =
            link.sourceProfileId == durableParent.raw &&
                link.sourceSessionId == canonical &&
                link.sourceOrigin == sourceOrigin
        val incoming =
            link.targetProfileId == durableParent.raw &&
                link.targetSessionId == canonical &&
                link.targetOrigin == sourceOrigin
        if (!outgoing && !incoming) return Failure.Refused(RefusalReason.StaleScope).failed()
        val targetParent = ProfileId(if (outgoing) link.targetProfileId else link.sourceProfileId)
        val session = SessionId(if (outgoing) link.targetSessionId else link.sourceSessionId)
        val targetOrigin = if (outgoing) link.targetOrigin else link.sourceOrigin
        if (!saved.admitting(targetParent))
            return Failure.Refused(RefusalReason.ProfileClosing).failed()
        val target =
            ConversationRef(
                if (targetOrigin == HandoffOrigin.PhoneAgent)
                    ProfileId("${targetParent.raw}.agents")
                else targetParent,
                session,
            )
        val graph =
            if (targetOrigin == HandoffOrigin.PhoneAgent) {
                if (!nativeSource.project(link.projectDirectory))
                    return Failure.Refused(RefusalReason.StaleScope).failed()
                when (val recovered = registry.recoverTransient(target)) {
                    is Outcome.Failed -> return recovered
                    is Outcome.Ok ->
                        recovered.value ?: return Failure.Refused(RefusalReason.StaleScope).failed()
                }
            } else {
                val profile =
                    profiles.profile(targetParent)
                        ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
                when (
                    val opened =
                        registry.open(
                            profile.toServerProfile()
                                ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
                        )
                ) {
                    is Outcome.Failed -> return opened
                    is Outcome.Ok -> opened.value
                }
            }
        if (
            !admitted() ||
                !saved.admitting(targetParent) ||
                registry.graphFor(target) !== graph ||
                (targetOrigin == HandoffOrigin.PhoneAgent &&
                    graph.session.server.location.value.directory != link.projectDirectory)
        )
            return Failure.Refused(RefusalReason.StaleScope).failed()
        return when (val read = graph.session.server.session(session)) {
            is Outcome.Failed -> read
            is Outcome.Ok ->
                if (
                    !admitted() ||
                        !saved.admitting(targetParent) ||
                        registry.graphFor(target) !== graph
                )
                    Failure.Refused(RefusalReason.StaleScope).failed()
                else target.copy(sessionId = read.value.id).ok()
        }
    }
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class StateHandoffDeletion(private val source: AndroidStateHandoffSource) : ProfileResourceCleaner {
    override val owner: String = ":engine:agents (handoff links)"
    override val order: Int = DeletionStep.DEVICE_SURFACES
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        when (val removed = source.links.removeProfile(profileId.raw)) {
            is Outcome.Failed -> removed
            is Outcome.Ok ->
                if (removed.value is HandoffLinkSave.Saved) 1.ok()
                else Failure.StorageRefused().failed()
        }
}

private class NativeHandoffGateway(
    private val gateway: PaseoGateway,
    private val graph: ConnectionGraph,
    private val registry: ConnectionRegistry,
    private val ref: ConversationRef,
    private val redactor: Redactor,
) : HandoffGateway, HandoffTargetPort {
    private val admittedGeneration = gateway.handoffGeneration
    override val directory: String?
        get() = gateway.location.value.directory

    override val workspace: String?
        get() = gateway.location.value.workspace

    override val isClosed: Boolean
        get() = registry.graphFor(ref) !== graph || gateway.handoffGeneration != admittedGeneration

    override val capabilities: HandoffCapabilities
        get() =
            gateway.capabilities.value.let {
                HandoffCapabilities(
                    it.agentHandoffFiles,
                    it.sessionTodos,
                    it.sessionDiff,
                    it.sessionModelProviderSwitching,
                )
            }

    override val handoffTarget: HandoffTargetPort
        get() = this

    override val historyReader: HandoffHistoryReader =
        HandoffHistoryReader.Paged { session, cursor, limit ->
            gateway.messagePage(SessionId(session), cursor, limit).map { page ->
                HandoffHistoryPage(
                    page.items.map(::handoffMessage),
                    page.nextCursor,
                    page.nextCursor != null,
                )
            }
        }

    override suspend fun session(id: String): Outcome<HandoffSession> =
        gateway.session(SessionId(id)).map(::handoffSession)

    override suspend fun createSession(): Outcome<HandoffSession> =
        gateway.createSession().map(::handoffSession)

    override suspend fun todos(sessionId: String): Outcome<List<HandoffTodo>> =
        gateway.todos(SessionId(sessionId)).map { list ->
            list.map { HandoffTodo(it.content, it.status, it.priority) }
        }

    override suspend fun diff(sessionId: String): Outcome<List<HandoffFileDiff>> =
        gateway.diff(SessionId(sessionId)).map { list ->
            list.map { HandoffFileDiff(it.file, it.additions, it.deletions) }
        }

    override suspend fun providers(): Outcome<List<HandoffProvider>> =
        gateway.providers().map { response ->
            response.providers.map { HandoffProvider(it.id, it.modelIds) }
        }

    override fun durableSessionId(sessionId: String): String =
        gateway.daemonSessionId(SessionId(sessionId)).raw

    override fun navigationSessionId(sessionId: String): String = durableSessionId(sessionId)

    override suspend fun promptHandoff(
        sessionId: String,
        text: String,
        model: HandoffModelRef,
        agent: String?,
        variant: String?,
        beforeSend: () -> Boolean,
    ): Outcome<Unit> =
        gateway.promptNewHandoff(
            sessionId,
            OutgoingPrompt(
                text,
                ModelSelection(ModelRef(model.providerId, model.modelId), variant),
                agent,
            ),
            beforeSend,
        )

    override suspend fun writeRecord(
        directory: String,
        recordId: String,
        markdown: String,
    ): Outcome<String> {
        val workspace = PaseoHandoffWorkspace(gateway, { !isClosed })
        val files =
            HandoffRecordFiles(
                workspace,
                object : HandoffHostLease {
                    override suspend fun acquire(): Outcome<Unit> =
                        if (isClosed) Failure.Refused(RefusalReason.StaleScope).failed()
                        else Unit.ok()

                    override fun release() {}
                },
                redactor,
            )
        return when (val written = files.write(directory, recordId, markdown)) {
            is Outcome.Failed -> written
            is Outcome.Ok ->
                when (val value = written.value) {
                    is HandoffFileWrite.Written -> value.relativePath.ok()
                    is HandoffFileWrite.Refused ->
                        Failure.Refused(RefusalReason.StaleScope).failed()
                }
        }
    }
}

private fun handoffSession(s: Session): HandoffSession =
    HandoffSession(s.id.raw, s.title, s.directory, s.path, s.agent, s.model, s.time?.updatedMs)

private fun handoffMessage(m: MessageWithParts): HandoffMessage =
    HandoffMessage(
        m.info.id,
        m.info.sessionId,
        m.info.role.name.lowercase(),
        m.parts.map { p ->
            HandoffPart(
                p.rawType,
                p.text,
                p.synthetic,
                p.filename,
                p.url,
                p.toolName,
                p.tool?.let { t ->
                    HandoffToolState(
                        t.status.name.lowercase(),
                        inputJson = t.input.toString(),
                        output = t.output,
                        pruned = t.pruned,
                        executed = t.ran,
                    )
                } ?: HandoffToolState("pending"),
            )
        },
        m.info.errorText,
    )

/**
 * Exact negotiated RPCs, fenced before every frame and checked again by HandoffRecordFiles after
 * each answer.
 */
private class PaseoHandoffWorkspace(
    private val gateway: PaseoGateway,
    private val current: () -> Boolean,
) : HandoffWorkspace {
    override val scope: String?
        get() = gateway.location.value.directory

    override val handoffFiles: Boolean
        get() = current() && gateway.transport.connected && gateway.transport.workspaceFileEditing

    override val epoch: Long
        get() = gateway.handoffGeneration

    private suspend fun request(
        type: String,
        body: Map<String, JsonElement>,
        mutation: Boolean = false,
    ): Outcome<JsonObject> {
        val folder = scope ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val epoch = gateway.transport.epoch
        val generation = gateway.handoffGeneration
        return paseoAttempt {
            gateway.transport.request(
                type,
                JsonObject(mapOf("cwd" to JsonPrimitive(folder)) + body),
                mutation = mutation,
                expectedEpoch = epoch,
                answerErrors = true,
                beforeSend = {
                    if (
                        !current() ||
                            scope != folder ||
                            !handoffFiles ||
                            gateway.handoffGeneration != generation
                    )
                        throw PaseoException(PaseoFailureKind.StaleRequest)
                },
            )
        }
    }

    override suspend fun create(
        parent: String,
        name: String,
        kind: HandoffEntryKind,
    ): Outcome<HandoffEntryCreated> =
        request(
                "fs.entry.create.request",
                mapOf(
                    "parentPath" to JsonPrimitive(parent),
                    "name" to JsonPrimitive(name),
                    "kind" to JsonPrimitive(kind.wire),
                ),
                true,
            )
            .map { r ->
                HandoffEntryCreated(
                    r["cwd"].stringOrNull(),
                    r["parentPath"].stringOrNull(),
                    r["path"].stringOrNull(),
                    r["success"] == JsonPrimitive(true),
                )
            }

    override suspend fun list(path: String): Outcome<HandoffListing> =
        request(
                "file_explorer_request",
                mapOf("path" to JsonPrimitive(path), "mode" to JsonPrimitive("list")),
            )
            .map { r ->
                val entries = (r["directory"] as? JsonObject)?.get("entries") as? JsonArray
                HandoffListing(
                    r["cwd"].stringOrNull(),
                    r["path"].stringOrNull(),
                    r["error"] != null && r["error"] != JsonNull,
                    entries?.map { e ->
                        val o = e as? JsonObject
                        HandoffListEntry(
                            o?.get("name").stringOrNull(),
                            o?.get("kind").stringOrNull(),
                        )
                    },
                )
            }

    override suspend fun read(path: String, maxBytes: Int): Outcome<HandoffFileRead> =
        request(
                "file_explorer_request",
                mapOf(
                    "path" to JsonPrimitive(path),
                    "mode" to JsonPrimitive("file"),
                    "maxBytes" to JsonPrimitive(maxBytes),
                ),
            )
            .map { r ->
                val f = r["file"] as? JsonObject
                HandoffFileRead(
                    r["cwd"].stringOrNull(),
                    r["path"].stringOrNull(),
                    r["error"] != null && r["error"] != JsonNull,
                    f?.let {
                        HandoffFileData(
                            it["path"].stringOrNull(),
                            it["kind"].stringOrNull(),
                            it["encoding"].stringOrNull(),
                            it["content"].stringOrNull(),
                            it["modifiedAt"].stringOrNull(),
                            it["revision"].stringOrNull(),
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
                "fs.file.write.request",
                buildMap {
                    put("path", JsonPrimitive(path))
                    put("content", JsonPrimitive(content))
                    put("expectedModifiedAt", JsonPrimitive(expectedModifiedAt))
                    expectedRevision?.let { put("expectedRevision", JsonPrimitive(it)) }
                },
                true,
            )
            .map { r -> (r["result"] as? JsonObject)?.get("status").stringOrNull() }
}
