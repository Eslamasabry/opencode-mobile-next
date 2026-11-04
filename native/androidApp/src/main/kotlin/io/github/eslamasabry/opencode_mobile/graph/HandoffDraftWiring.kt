package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.agents.hub.*
import io.github.eslamasabry.oc.protocol.codex.CodexGateway
import kotlinx.collections.immutable.*
import kotlinx.coroutines.flow.*

/**
 * Read-only source review. No destination, file write, prompt, new conversation or link is created.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(
    AppScope::class,
    binding = binding<AgentHandoffDrafts>(),
    replaces = [NoAgentHandoffDrafts::class],
)
class AppHandoffDrafts(
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val application: Application,
    appScope: AppCoroutineScope,
) : AgentHandoffDrafts {
    override val sources =
        registry.live
            .map { live ->
                live
                    .map { (id, graph) -> AgentDraftSource(id, graph.profile.name) }
                    .toImmutableList()
            }
            .stateIn(appScope, SharingStarted.Eagerly, persistentListOf())

    override suspend fun conversations(
        profileId: ProfileId
    ): Outcome<ImmutableList<AgentDraftConversation>> {
        val graph = registry.live.value[profileId] ?: return stale()
        val gateway = graph.session.server
        val revision = gateway.scopeRevision
        val sessions = mutableListOf<AgentDraftConversation>()
        var cursor: String? = null
        val cursors = mutableSetOf<String>()
        repeat(100) {
            val page = gateway.sessionPage(cursor, 50)
            if (registry.live.value[profileId] !== graph || gateway.scopeRevision != revision)
                return stale()
            if (page is Outcome.Failed) return page
            val value = (page as Outcome.Ok).value
            sessions += value.items.map { AgentDraftConversation(it.id, it.title.orEmpty()) }
            cursor = value.nextCursor
            if (cursor == null) return sessions.distinctBy { it.id }.toImmutableList().ok()
            if (cursor!!.isBlank() || !cursors.add(cursor!!))
                return Failure.UnreadableReply().failed()
        }
        return Failure.UnreadableReply().failed()
    }

    override suspend fun prepare(
        profileId: ProfileId,
        sessionId: SessionId,
    ): Outcome<AgentDraftRecord> {
        val graph = registry.live.value[profileId] ?: return stale()
        val gateway = graph.session.server
        val revision = gateway.scopeRevision
        val location = gateway.location.value
        fun current() =
            registry.live.value[profileId] === graph &&
                gateway.scopeRevision == revision &&
                gateway.location.value == location &&
                gateway.events.status.value == StreamStatus.Connected
        if (!current() || sessionId.raw.isBlank()) return stale()
        val snapshot = if (gateway is CodexGateway) gateway.handoffSnapshot(sessionId) else null
        if (!current()) return stale()
        if (snapshot is Outcome.Failed) return snapshot
        val codexSnapshot = (snapshot as? Outcome.Ok)?.value
        val before = codexSnapshot?.session?.ok() ?: gateway.session(sessionId)
        if (!current()) return stale()
        if (before is Outcome.Failed) return before
        val session = (before as Outcome.Ok).value
        if (
            location.directory != null &&
                session.directory != null &&
                session.directory != location.directory
        )
            return stale()
        val reader =
            if (gateway is CodexGateway)
                HandoffHistoryReader.Snapshot { id ->
                    codexSnapshot!!.messages.map(::message).ok()
                }
            else
                HandoffHistoryReader.Paged { id, cursor, limit ->
                    gateway.messagePage(SessionId(id), cursor, limit).map { page ->
                        HandoffHistoryPage(page.items.map(::message), page.nextCursor)
                    }
                }
        val source =
            object : HandoffHistorySource {
                override val directory: String?
                    get() = gateway.location.value.directory

                override val workspace: String?
                    get() = gateway.location.value.workspace

                override val isClosed: Boolean
                    get() = !current()

                override val historyReader: HandoffHistoryReader = reader
            }
        val history = readHandoffHistory(source, sessionId.raw, ::current)
        if (history !is HandoffHistoryRead.Read) return Failure.UnreadableReply().failed()
        val todos =
            if (gateway.capabilities.value.sessionTodos) gateway.todos(sessionId)
            else persistentListOf<io.github.eslamasabry.oc.core.model.Todo>().ok()
        if (!current()) return stale()
        if (todos is Outcome.Failed) return todos
        val diffs =
            if (gateway.capabilities.value.sessionDiff) gateway.diff(sessionId)
            else persistentListOf<io.github.eslamasabry.oc.core.model.FileDiff>().ok()
        if (!current()) return stale()
        if (diffs is Outcome.Failed) return diffs
        if (codexSnapshot == null) {
            val after = gateway.session(sessionId)
            if (!current()) return stale()
            if (after is Outcome.Failed) return after
            if ((after as Outcome.Ok).value.time?.updatedMs != session.time?.updatedMs)
                return stale()
        }
        val built =
            HandoffRecordBuilder(redactor)
                .build(
                    HandoffSession(
                        session.id.raw,
                        session.title,
                        session.directory,
                        session.path,
                        session.agent,
                        session.model,
                        session.time?.updatedMs,
                    ),
                    history.messages,
                    (todos as Outcome.Ok).value.map {
                        HandoffTodo(it.content, it.status, it.priority)
                    },
                    (diffs as Outcome.Ok).value.map {
                        HandoffFileDiff(it.file, it.additions, it.deletions)
                    },
                    lastState = if (session.lastRunFailed) "failed" else "observed",
                )
        if (!current()) return stale()
        return when (built) {
            is HandoffRecordResult.Built ->
                built.record
                    .let {
                        AgentDraftRecord(
                            it.markdown,
                            it.omittedMessages,
                            it.redacted,
                            it.byteLength,
                        )
                    }
                    .ok()
            is HandoffRecordResult.TooLarge ->
                Failure.Rejected(413, tag = WireTag("handoff_record_too_large")).failed()
        }
    }

    override fun copy(markdown: String): Boolean =
        try {
            application
                .getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Conversation handoff", markdown))
            true
        } catch (_: RuntimeException) {
            false
        }

    private fun message(value: MessageWithParts): HandoffMessage =
        HandoffMessage(
            value.info.id,
            value.info.sessionId,
            if (value.info.role == Role.User) "user" else "assistant",
            value.parts.map { part ->
                HandoffPart(
                    part.rawType,
                    part.text,
                    part.synthetic,
                    part.filename,
                    part.url,
                    part.toolName,
                    part.tool?.let { tool ->
                        HandoffToolState(
                            tool.status.name.lowercase(),
                            jsonMap(tool.input.toString()),
                            tool.inputRaw,
                            tool.output,
                            tool.outputValue?.let { DartJson.decodeOrNull(it.toString()) },
                            tool.metadata?.let { jsonMap(it.toString()) },
                            tool.pruned,
                            tool.executedFlag,
                        )
                    } ?: HandoffToolState("pending"),
                )
            },
            value.info.errorText,
        )

    private fun jsonMap(value: String): Map<String, Any?> =
        (DartJson.decodeOrNull(value) as? Map<*, *>)
            ?.entries
            ?.associate { (key, item) -> key.toString() to item }
            .orEmpty()

    private fun <T> stale(): Outcome<T> = Failure.Refused(RefusalReason.StaleScope).failed()
}
