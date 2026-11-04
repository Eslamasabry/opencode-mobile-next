package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.feed.PaseoFeedPort
import io.github.eslamasabry.oc.protocol.paseo.feed.PaseoTitles
import io.github.eslamasabry.oc.protocol.paseo.mappers.PaseoImages
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/**
 * The gateway as one chat-feed scope (slice D's [PaseoFeedPort]; Dart reads these off
 * `PaseoGateway`, including the `copies.dart` part: [keepTitle] and [archiveOlderCopy],
 * B-AGENT-149). Non-suspend members read lane-confined state: call them from the gateway's lane
 * (the feed runs there). Suspend members hop onto it and throw `PaseoException`.
 */
public fun PaseoGateway.feedPort(): PaseoFeedPort = GatewayFeedPort(this)

private class GatewayFeedPort(private val g: PaseoGateway) : PaseoFeedPort {
    override val directory: String?
        get() = g.directory

    override val isClosed: Boolean
        get() = g.isClosed

    /** Permission and question attention, local only (collecting never touches the transport). */
    override val attentionChanges: Flow<Unit> =
        merge(g.requests.revision.drop(1).map {}, g.backgroundTasks?.changes ?: emptyFlow())

    override suspend fun sessionPage(cursor: String?, limit: Int): ServerPage<Session> = g.onLane {
        g.readSessionPage(cursor, limit)
    }

    override suspend fun sessionStatuses(): Map<SessionId, SessionStatus> = g.onLane {
        g.statuses.entries.associate { SessionId(it.key) to it.value }
    }

    override fun sessionKey(session: SessionId): String? =
        PaseoTitles.sessionKey(g.agents[session.raw])

    override fun hasOwnTitle(session: SessionId): Boolean =
        PaseoTitles.hasOwnTitle(g.agents[session.raw])

    /**
     * The title a resume lost; nothing is written to the helper (an update would move the row to
     * the top).
     */
    override fun keepTitle(session: SessionId, title: String) {
        if (title.isBlank()) return
        val id = session.raw
        g.titleHints[id] = title
        if (hasOwnTitle(session)) return
        g.agents[id]?.let { g.agents[id] = it.with("title", JsonPrimitive(title)) }
        g.sessions[id]?.let { g.sessions[id] = it.copy(title = title) }
    }

    override fun providerIdForSession(session: SessionId): String? = g.providerIdForSession(session)

    override fun agentFailed(session: SessionId): Boolean =
        g.agents[session.raw]?.str("status") == "error" || lastTurnFailed(session)

    /**
     * The saved record failed before the helper loaded it (the background-task book is S5-PASEOP1).
     */
    override fun lastTurnFailed(session: SessionId): Boolean =
        g.backgroundTasks?.turnFailed(g.real(session.raw)) == true ||
            g.live[session.raw]?.failedBeforeLoad == true

    override fun backgroundTasksRunning(session: SessionId): Boolean =
        g.backgroundTasks?.running(g.real(session.raw), g.nowMs()) == true

    override fun hasPendingPermission(session: SessionId): Boolean =
        g.requests.hasPermission(session.raw)

    override fun hasPendingQuestion(session: SessionId): Boolean =
        g.requests.hasQuestion(session.raw)

    /**
     * Archives an older quiet record of a resumed conversation, at most once per gateway; false on
     * any refusal.
     */
    override suspend fun archiveOlderCopy(session: SessionId): Boolean = g.onLane {
        val id = session.raw
        if (g.isClosed || id in g.drafts || id in g.archivedCopies) return@onLane false
        val agent = g.agents[id] ?: return@onLane false
        if (agent.str("status") !in setOf("idle", "closed", "error")) return@onLane false
        if (g.statuses[id] == SessionStatus.Busy || id in g.turnActive) return@onLane false
        g.archivedCopies += id
        try {
            g.transport.request(
                "archive_agent_request",
                io.github.eslamasabry.oc.protocol.paseo.jsonOf("agentId" to g.real(id)),
                mutation = true,
            )
            g.forget(id)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaseoException) {
            g.archivedCopies.remove(id)
            false
        }
    }

    override suspend fun providers(): ProvidersResponse = g.onLane { g.readProviders() }

    override suspend fun createDraft(): Session = g.onLane { g.createDraft() }

    override fun seedDraftProvider(session: SessionId, providerId: String) {
        if (
            session.raw !in g.drafts ||
                !io.github.eslamasabry.oc.protocol.paseo.requests.isPaseoProviderId(providerId)
        ) {
            paseoFail(PaseoFailureKind.Unavailable)
        }
        g.draftProviders[session.raw] = providerId
    }

    override fun discardDraft(session: SessionId) {
        if (session.raw in g.drafts) g.forget(session.raw)
    }

    override suspend fun prompt(session: SessionId, text: String, model: ModelRef) {
        g.onLane {
            g.sendPrompt(
                session.raw,
                paseoUuid(g.random),
                OutgoingPrompt(text, ModelSelection(model)),
                (PaseoImages.Ok(emptyList())).images,
                false,
                null,
            )
        }
    }

    override fun daemonSessionId(session: SessionId): SessionId = g.daemonSessionId(session)
}

internal suspend fun <T> PaseoGateway.onLane(block: suspend () -> T): T =
    withContext(lane) { block() }
