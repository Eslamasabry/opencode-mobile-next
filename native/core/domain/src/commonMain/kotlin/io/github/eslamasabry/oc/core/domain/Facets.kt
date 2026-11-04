package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.IntegrationAuthLaunch
import io.github.eslamasabry.oc.core.model.IntegrationAuthStatus
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionRetryState
import io.github.eslamasabry.oc.core.model.SessionRevert
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.WebSearchProvider
import io.github.eslamasabry.oc.core.model.WebSearchResponse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

// Optional facets defined in interfaces.dart / value_types_core.dart / web_search.dart. The other
// facets plan 04
// §11.1 names (SessionNote, SessionExport/Import, SessionSkill, ActiveContext, Plugin,
// UsageStatistics,
// HistoryMigration, RemoteShell, OneShotCommand, GenUiHistory, AgentHandoffHistory,
// TerminalCommand,
// HostAgentProvider, ProviderConversationImport, HostDaemonUpdate, AgentAccount) are frozen by the
// slice that
// ports them, in that slice's contract.

/**
 * v1 status endpoints carry retry details; v2 relies on live events and does not implement this.
 */
public interface SessionRetryGateway {
    public suspend fun sessionRetryStates(): Outcome<ImmutableMap<SessionId, SessionRetryState>>
}

/**
 * Exact dispatch correlation (capability `clientPromptMessageID`). Not an idempotency or retry
 * contract. [beforeSend] runs after preflight and immediately before dispatch; returning false
 * refuses a stale scope.
 */
public interface CorrelatedPromptGateway {
    public fun createPromptMessageId(): MessageId

    public suspend fun promptWithMessageId(
        session: SessionId,
        messageId: MessageId,
        prompt: OutgoingPrompt,
        beforeSend: () -> Boolean = { true },
    ): Outcome<PromptAdmission>
}

/** Gateways whose agents never hold a mid-turn message: they join the turn or restart from it. */
public interface MidTurnPromptGateway {
    /** Null while the agent is not known yet. */
    public fun midTurnPrompt(session: SessionId): MidTurnPrompt?
}

/** Servers whose model and agent are persistent session state. Defaults apply only at creation. */
public interface SessionSelectionGateway {
    public suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session>

    public suspend fun setSessionModel(
        session: SessionId,
        model: ModelRef,
        variant: String,
    ): Outcome<Unit>

    public suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit>
}

/** V2's explicit stage/review/commit revert lifecycle. */
public interface StagedRevertGateway {
    /** Null for a non-user message; empty text is a valid attachment-only prompt. */
    public suspend fun sessionRevertPrompt(session: SessionId, message: MessageId): Outcome<String?>

    public suspend fun stageSessionRevert(
        session: SessionId,
        message: MessageId,
        applyFiles: Boolean,
    ): Outcome<SessionRevert>

    public suspend fun clearSessionRevert(session: SessionId): Outcome<Unit>

    public suspend fun commitSessionRevert(session: SessionId): Outcome<Unit>
}

/** V2 read receipts: acknowledge exactly the idle transition the client displayed. */
public interface SessionReadStateGateway {
    public suspend fun viewSession(session: SessionId, idleMs: Long): Outcome<Unit>
}

/**
 * Runtime-only MCP removal; never deletes persistent configuration. Refetch inventory after
 * success.
 */
public interface McpRemovalGateway {
    public suspend fun removeMcpServer(name: String): Outcome<Unit>
}

/**
 * Stored-credential management using ids from integration metadata. Success does not say which is
 * active.
 */
public interface IntegrationCredentialGateway {
    public suspend fun renameCredential(id: String, label: String): Outcome<Unit>

    public suspend fun activateCredential(id: String): Outcome<Unit>

    public suspend fun removeCredential(id: String): Outcome<Unit>
}

/**
 * Metadata-only recovery into a replacement gateway. MUST NOT contact the server or start
 * authentication.
 */
public interface IntegrationAuthRecoveryGateway {
    public fun restoreIntegrationAuthAttempt(
        integrationId: String,
        attemptId: String,
        command: Boolean,
        directory: String?,
        workspace: String?,
    )
}

/** Server-run command authentication; never execute or copy a command locally. */
public interface IntegrationCommandGateway {
    public suspend fun startIntegrationCommand(
        integrationId: String,
        methodId: String,
        label: String? = null,
    ): Outcome<IntegrationAuthLaunch>

    public suspend fun integrationCommandStatus(
        integrationId: String,
        attemptId: String,
    ): Outcome<IntegrationAuthStatus>

    public suspend fun cancelIntegrationCommand(
        integrationId: String,
        attemptId: String,
    ): Outcome<Unit>
}

/** Failures exclude remote error bodies, URLs, queries and credentials (Dart WebSearchFailure). */
public interface WebSearchGateway {
    public suspend fun webSearchProviders(): Outcome<ImmutableList<WebSearchProvider>>

    public suspend fun searchWeb(query: String, providerId: String): Outcome<WebSearchResponse>
}

/**
 * Project files of one conversation's own folder, whatever the gateway's shared location is (P12
 * fix B-NAV-026: Flutter reads a conversation's tree from that conversation's directory). Each call
 * carries [location] itself.
 */
public interface LocatedFileGateway {
    public fun filesAt(location: ServerLocation): FileGateway
}

/**
 * The uncommitted changes of one conversation's folder (OC2 `GET /vcs/diff?mode=working` with its
 * location).
 */
public interface LocatedVcsGateway {
    public suspend fun workingTreeAt(location: ServerLocation): Outcome<ImmutableList<FileDiff>>
}

/** Recovery reads on a private capped transport, independent of the live chat stream. */
public interface BoundedCardHistoryGateway {
    public suspend fun cardHistoryPage(
        session: SessionId,
        cursor: String?,
        limit: Int,
    ): Outcome<
        io.github.eslamasabry.oc.core.model.ServerPage<
            io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
        >
    >

    /** Fresh authoritative idle proof; drafts and read-only children cannot qualify. */
    public suspend fun cardSessionIdle(session: SessionId): Outcome<Boolean>
}
