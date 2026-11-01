package io.github.eslamasabry.oc.protocol.paseo.feed

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * What [PaseoChatFeed] needs from ONE Paseo gateway scope (Dart `PaseoChatFeedSource` reads these off `PaseoGateway`).
 * The gateway implements it; tests use a fake. Every suspend member throws `PaseoException` on failure (the module's
 * internal convention); the feed turns failures into `complete = false` or an `Outcome.Failed` at its own boundary.
 * Nothing here reads a timeline or loads an agent.
 *
 * | Member | Gateway source (Dart) |
 * |---|---|
 * | [directory], [isClosed] | `gateway.directory`, `gateway.isClosed` |
 * | [attentionChanges] | merged `nativePermissionChanges`, `nativeQuestionChanges`, `backgroundTaskChanges` (local only; collecting never touches the transport) |
 * | [sessionPage] | `sessionPage(cursor, limit)`: this folder's unarchived agents (drafts first on the first page) |
 * | [sessionStatuses] | `sessionStatuses()` (busy / idle / retry / error) |
 * | [sessionKey] | `sessionKey(id)` = `<persistence.provider>:<persistence.sessionId>` or null ([PaseoTitles.sessionKey]) |
 * | [hasOwnTitle], [keepTitle] | `hasOwnTitle(id)` ([PaseoTitles.hasOwnTitle] of the remembered record), `keepTitle(id, title)`: a hint only, nothing written to the helper; it puts the title into the remembered record (and session), so `hasOwnTitle` is true afterwards |
 * | [providerIdForSession] | the agent's `provider`, else the draft's seeded provider |
 * | [agentFailed] | agent status `error` or [lastTurnFailed] |
 * | [lastTurnFailed], [backgroundTasksRunning] | `BackgroundTaskBook` reads (false until that book is ported) |
 * | [hasPendingPermission], [hasPendingQuestion] | a current-epoch native permission snapshot / native question for the session |
 * | [archiveOlderCopy] | `archiveOlderCopy(id)`: archives an older idle record of a resumed conversation; false on refusal |
 * | [providers] | `providers()`: ready runtimes (providers) |
 * | [createDraft], [seedDraftProvider], [discardDraft] | `createSession()`, `seedDraftProviderForSession`, `discardDraftSession` |
 * | [prompt] | `promptAsync(id, text, model)`: the first prompt creates the agent (mode rule: [paseoCreateMode]) |
 * | [daemonSessionId] | `daemonSessionId(id)`: the daemon's id for a draft this gateway created |
 */
public interface PaseoFeedPort {
    public val directory: String?
    public val isClosed: Boolean
    public val attentionChanges: Flow<Unit>

    public suspend fun sessionPage(cursor: String?, limit: Int): ServerPage<Session>

    public suspend fun sessionStatuses(): Map<SessionId, SessionStatus>

    public fun sessionKey(session: SessionId): String?

    public fun hasOwnTitle(session: SessionId): Boolean

    public fun keepTitle(session: SessionId, title: String)

    public fun providerIdForSession(session: SessionId): String?

    public fun agentFailed(session: SessionId): Boolean

    public fun lastTurnFailed(session: SessionId): Boolean

    public fun backgroundTasksRunning(session: SessionId): Boolean

    public fun hasPendingPermission(session: SessionId): Boolean

    public fun hasPendingQuestion(session: SessionId): Boolean

    public suspend fun archiveOlderCopy(session: SessionId): Boolean

    public suspend fun providers(): ProvidersResponse

    public suspend fun createDraft(): Session

    /** Throws `PaseoException(Unavailable)` when [session] is not a draft. */
    public fun seedDraftProvider(session: SessionId, providerId: String)

    public fun discardDraft(session: SessionId)

    public suspend fun prompt(session: SessionId, text: String, model: ModelRef)

    public fun daemonSessionId(session: SessionId): SessionId
}
