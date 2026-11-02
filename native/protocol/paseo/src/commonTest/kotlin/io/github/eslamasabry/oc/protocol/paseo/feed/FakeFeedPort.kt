package io.github.eslamasabry.oc.protocol.paseo.feed

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ProviderInfo
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow

const val FEED_DIR = "/root/projects/IPTV_King"

/** One daemon agent as the fake gateway sees it. */
data class FakeAgent(
    val id: String,
    val provider: String?,
    val title: String? = "Conversation $id",
    val status: String = "idle",
    val directory: String = FEED_DIR,
    val createdMs: Long = 1_000,
    val updatedMs: Long = 2_000,
    val key: String? = null,
    val failed: Boolean = false,
) {
    fun session(titleOverride: String? = title): Session =
        Session(
            id = SessionId(id),
            title = titleOverride ?: "${provider ?: "agent"} conversation",
            directory = directory,
            time = SessionTime(createdMs = createdMs, updatedMs = updatedMs),
        )
}

/** A scripted [PaseoFeedPort]: what a Paseo gateway scope answers, with every call recorded. */
class FakeFeedPort(override var directory: String? = FEED_DIR) : PaseoFeedPort {
    val agents = mutableListOf<FakeAgent>()
    val drafts = mutableListOf<FakeAgent>()
    override var isClosed = false
    override val attentionChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Replaces the default page answer: (cursor) -> page, or throw. */
    var pages: (suspend (String?) -> ServerPage<Session>)? = null
    val pageCalls = mutableListOf<String?>()
    val permissions = mutableSetOf<String>()
    val questions = mutableSetOf<String>()
    val titleHints = mutableMapOf<String, String>()
    val archived = mutableListOf<String>()
    val prompts = mutableListOf<Triple<String, String, ModelRef>>()
    val seeded = mutableMapOf<String, String>()
    val discarded = mutableListOf<String>()
    val created = mutableListOf<String>()
    var providerList = listOf("codex", "claude")
    var defaultProvider: String? = "claude"
    var promptFailure: PaseoException? = null
    var daemonIds = mutableMapOf<String, String>()
    private var nextDraft = 0

    private fun agent(id: SessionId) = (agents + drafts).firstOrNull { it.id == id.raw }

    override suspend fun sessionPage(cursor: String?, limit: Int): ServerPage<Session> {
        pageCalls += cursor
        pages?.let { return it(cursor) }
        val rows = drafts.map { it.session() } + agents.map { it.session(titleHints[it.id] ?: it.title) }
        return ServerPage(rows.toImmutableList(), null)
    }

    override suspend fun sessionStatuses(): Map<SessionId, SessionStatus> =
        (agents + drafts).associate {
            SessionId(it.id) to if (it.status == "running") SessionStatus.Busy else SessionStatus.Idle
        }

    override fun sessionKey(session: SessionId): String? = agent(session)?.key

    /** Like the gateway: a kept title makes the record's title its own. */
    override fun hasOwnTitle(session: SessionId): Boolean =
        !agent(session)?.title.isNullOrBlank() || !titleHints[session.raw].isNullOrBlank()

    override fun keepTitle(session: SessionId, title: String) {
        titleHints[session.raw] = title
    }

    override fun providerIdForSession(session: SessionId): String? = agent(session)?.provider ?: seeded[session.raw]

    override fun agentFailed(session: SessionId): Boolean = agent(session)?.failed == true

    override fun lastTurnFailed(session: SessionId): Boolean = false

    override fun backgroundTasksRunning(session: SessionId): Boolean = false

    override fun hasPendingPermission(session: SessionId): Boolean = session.raw in permissions

    override fun hasPendingQuestion(session: SessionId): Boolean = session.raw in questions

    override suspend fun archiveOlderCopy(session: SessionId): Boolean {
        archived += session.raw
        agents.removeAll { it.id == session.raw }
        return true
    }

    override suspend fun providers(): ProvidersResponse {
        val list = providerList.map { ProviderInfo(it, it, persistentListOf(), persistentMapOf()) }.toImmutableList()
        return ProvidersResponse(list, list, defaultProvider, null)
    }

    override suspend fun createDraft(): Session {
        val draft = FakeAgent("draft-${++nextDraft}", provider = null, title = "New conversation")
        drafts += draft
        created += draft.id
        return draft.session()
    }

    override fun seedDraftProvider(session: SessionId, providerId: String) {
        if (drafts.none { it.id == session.raw }) throw PaseoException(PaseoFailureKind.Unavailable)
        seeded[session.raw] = providerId
    }

    override fun discardDraft(session: SessionId) {
        drafts.removeAll { it.id == session.raw }
        discarded += session.raw
    }

    override suspend fun prompt(session: SessionId, text: String, model: ModelRef) {
        promptFailure?.let { throw it }
        prompts += Triple(session.raw, text, model)
        val real = "real-${session.raw}"
        daemonIds[session.raw] = real
    }

    override fun daemonSessionId(session: SessionId): SessionId = SessionId(daemonIds[session.raw] ?: session.raw)
}
