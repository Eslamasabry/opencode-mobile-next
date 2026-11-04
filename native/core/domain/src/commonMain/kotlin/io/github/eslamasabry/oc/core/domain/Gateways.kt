package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvents
import io.github.eslamasabry.oc.core.model.AgentInfo
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.core.model.FormAnswer
import io.github.eslamasabry.oc.core.model.FormSpec
import io.github.eslamasabry.oc.core.model.FormState
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.InboxDelivery
import io.github.eslamasabry.oc.core.model.InboxItem
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.core.model.Todo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

/*
 * The live gateway (PROTOCOLS-37): a port of lib/domain/server_gateway/interfaces.dart with three mechanical rules
 * (plan 04 §11.1): every call is `suspend` and returns Outcome; ids are value classes; OC2-only types moved into
 * :core:model (FormSpec, InboxItem, FormState). Facets keep agents' briefs small; ServerGateway joins them.
 *
 * Inert facets (forms, inbox, legacy questions) have default bodies, exactly like Dart's v1 gateways: reads answer
 * an empty list, mutations answer Failure.Unavailable naming the capability. Everything else a backend must
 * implement. Expected failures are Outcome.Failed; cancellation is rethrown, never a Failure.
 */

public interface HealthGateway {
    public suspend fun health(): Outcome<Health>
}

public interface SessionGateway {
    /** Newest first; the opaque continuation always requests older rows. */
    public suspend fun sessionPage(
        cursor: String? = null,
        limit: Int = 100,
    ): Outcome<ServerPage<Session>>

    public suspend fun createSession(spec: NewSession = NewSession()): Outcome<Session>

    public suspend fun session(id: SessionId): Outcome<Session>

    public suspend fun renameSession(id: SessionId, title: String): Outcome<Unit>

    public suspend fun deleteSession(id: SessionId): Outcome<Unit>

    /** Strict: an unreadable entry fails the read rather than reading as idle (plan 04 §7.4). */
    public suspend fun sessionStatuses(): Outcome<ImmutableMap<SessionId, SessionStatus>>

    /** Chronological items of one page; the continuation requests older rows. */
    public suspend fun messagePage(
        id: SessionId,
        cursor: String? = null,
        limit: Int = 100,
    ): Outcome<ServerPage<MessageWithParts>>

    public suspend fun todos(id: SessionId): Outcome<ImmutableList<Todo>>

    public suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>>
}

public interface PromptGateway {
    /**
     * Sends one prompt. [commandId] is the app's dispatch identity for command receipts; it never
     * authorises a resend. A lost response is [PromptAdmission.Uncertain], never a retry (plan 04
     * §13).
     */
    public suspend fun prompt(
        session: SessionId,
        prompt: OutgoingPrompt,
        commandId: String? = null,
    ): Outcome<PromptAdmission>

    public suspend fun shell(
        session: SessionId,
        command: String,
        agent: String,
        model: ModelSelection? = null,
    ): Outcome<Unit>

    public suspend fun slashCommand(
        session: SessionId,
        command: String,
        args: String,
        model: ModelSelection? = null,
    ): Outcome<Unit>

    /** Dart `abort`. [resume] asks a backend that supports it to continue after stopping. */
    public suspend fun stop(session: SessionId, resume: Boolean = false): Outcome<StopResult>
}

/**
 * Permission replies. [message] rides only on OpenCode 2 rejections (shown to the model); other
 * backends accept and ignore it, so callers need no protocol branch.
 */
public interface PermissionGateway {
    public suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>>

    public suspend fun replyPermission(
        session: SessionId,
        request: RequestId,
        reply: PermissionReply,
        message: String? = null,
    ): Outcome<Unit>
}

/** OpenCode 2 structured forms. Inert by default (capability `forms` = false). */
public interface FormGateway {
    public suspend fun sessionForms(session: SessionId): Outcome<ImmutableList<FormSpec>> =
        persistentListOf<FormSpec>().ok()

    /** Across every session of the pinned location, including global (MCP elicitation) forms. */
    public suspend fun pendingForms(): Outcome<ImmutableList<FormSpec>> =
        persistentListOf<FormSpec>().ok()

    public suspend fun formState(session: SessionId, form: FormId): Outcome<FormState> =
        unavailable("forms")

    /**
     * 400 invalid answer and 409 already settled arrive as Failure.Rejected / Failure.Conflict with
     * their tag.
     */
    public suspend fun replyForm(
        session: SessionId,
        form: FormId,
        answer: FormAnswer,
    ): Outcome<Unit> = unavailable("forms")

    public suspend fun cancelForm(session: SessionId, form: FormId): Outcome<Unit> =
        unavailable("forms")
}

/**
 * OpenCode 2 session inbox: durably admitted, not-yet-delivered work. Inert by default (capability
 * `inbox`).
 */
public interface InboxGateway {
    public suspend fun inbox(session: SessionId): Outcome<ImmutableList<InboxItem>> =
        persistentListOf<InboxItem>().ok()

    /** Conflict when already delivered. */
    public suspend fun cancelInboxItem(session: SessionId, item: InboxItemId): Outcome<Unit> =
        unavailable("inbox")

    /** Dart steerInboxItem / queueInboxItem. */
    public suspend fun setInboxDelivery(
        session: SessionId,
        item: InboxItemId,
        delivery: InboxDelivery,
    ): Outcome<Unit> = unavailable("inbox")
}

/**
 * Legacy question dialogs through session-scoped endpoints. Inert by default (capability
 * `legacyQuestionRequests`).
 */
public interface QuestionGateway {
    public suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> =
        persistentListOf<PendingQuestion>().ok()

    public suspend fun answerQuestion(
        session: SessionId,
        request: RequestId,
        answers: ImmutableList<ImmutableList<String>>,
    ): Outcome<Unit> = unavailable("legacyQuestionRequests")

    public suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit> =
        unavailable("legacyQuestionRequests")
}

/** Provider and agent inventories backing the model picker. */
public interface ProviderGateway {
    public suspend fun providers(): Outcome<ProvidersResponse>

    public suspend fun configuredProviders(): Outcome<ProvidersResponse>

    public suspend fun agents(): Outcome<ImmutableList<AgentInfo>>
}

public interface FileGateway {
    public suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>>

    public suspend fun fileContent(path: String): Outcome<FileContent>

    public suspend fun findFile(query: String): Outcome<ImmutableList<String>>

    public suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>>
}

/** Live events: one stream per gateway feeding two views (plan 04 §6.5). */
public interface EventGateway {
    public val events: GatewayEvents
}

/** The protocol-neutral transport for one connected server location. */
public interface ServerGateway :
    HealthGateway,
    SessionGateway,
    PromptGateway,
    PermissionGateway,
    QuestionGateway,
    FormGateway,
    InboxGateway,
    ProviderGateway,
    FileGateway,
    EventGateway {
    /** Computed from the probed server version and runtime latches, not fixed per backend. */
    public val capabilities: StateFlow<ServerCapabilities>

    /**
     * Changes on every location transition, including a move away and back, and on transport
     * replacement.
     */
    public val scopeRevision: Long
        get() = events.connects.value.toLong()

    public val location: StateFlow<ServerLocation>

    public fun setLocation(location: ServerLocation)

    /** Dart prepareActionTransport (plan 04 §12.6): wakes the link before a mutation. */
    public suspend fun awaitActionTransport(): Outcome<Unit> = Outcome.Ok(Unit)

    /** Stops the event stream and releases the transport. Idempotent. */
    public suspend fun close()
}

/**
 * Optional facets (separate interfaces) are tested for by ENGINES only; features gate on
 * capabilities. `gateway.facet<CorrelatedPromptGateway>()`.
 */
public inline fun <reified F : Any> ServerGateway.facet(): F? = this as? F
