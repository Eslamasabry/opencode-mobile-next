package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Outcome

/** Which runtime a conversation lives in under its durable parent profile. [wire] is the stored spelling. */
public enum class HandoffOrigin(public val wire: String) {
    Server("server"),
    PhoneAgent("phoneAgent"),
    ;

    public companion object {
        public fun fromWire(raw: String): HandoffOrigin? = entries.firstOrNull { it.wire == raw }
    }
}

/**
 * Port of Flutter `HandoffScope` (lib/domain/agent_handoff.dart). [filesystemId] is app-owned evidence of a shared
 * filesystem (never derived from a URL, matching directory strings or an agent's assertion); null is unknown.
 */
public data class HandoffScope(
    /** Persisted parent profile, never an ephemeral agent transport profile. */
    val profileId: String,
    val directory: String,
    val workspace: String? = null,
    val filesystemId: String? = null,
    val origin: HandoffOrigin = HandoffOrigin.Server,
) {
    public fun sharesProject(other: HandoffScope): Boolean =
        !filesystemId.isNullOrEmpty() &&
            filesystemId == other.filesystemId &&
            directory == other.directory &&
            directory.startsWith("/") &&
            directory.split('/').none { it == "." || it == ".." }
}

/** The gateway flags the handoff gates on (ServerCapabilities, never the backend kind). */
public data class HandoffCapabilities(
    val agentHandoffFiles: Boolean = false,
    val sessionTodos: Boolean = true,
    val sessionDiff: Boolean = true,
    // Defaults mirror Flutter ServerCapabilities.
    val sessionModelProviderSwitching: Boolean = true,
)

/** One provider in the target's connected inventory. [variants]: model id → variant id → disabled. */
public data class HandoffProvider(
    val id: String,
    val modelIds: List<String>,
    val variants: Map<String, Map<String, Boolean>> = emptyMap(),
)

/** The gateway operations the handoff uses, on either side. */
public interface HandoffGateway : HandoffHistorySource {
    public val capabilities: HandoffCapabilities

    public suspend fun session(id: String): Outcome<HandoffSession>

    public suspend fun todos(sessionId: String): Outcome<List<HandoffTodo>>

    public suspend fun diff(sessionId: String): Outcome<List<HandoffFileDiff>>

    /** The transport's connected provider inventory. */
    public suspend fun providers(): Outcome<List<HandoffProvider>>

    public suspend fun createSession(): Outcome<HandoffSession>

    /** Null when this gateway cannot receive a handoff (Flutter `is! AgentHandoffTargetGateway`). */
    public val handoffTarget: HandoffTargetPort?
}

/**
 * Flutter `AgentHandoffTargetGateway`: an explicit record-file API plus a first prompt with a dispatch-time fence, and the
 * durable identity of the conversation it creates.
 */
public interface HandoffTargetPort {
    /** Writes `.handoff/<recordId>/record.md` in [directory]; the relative path only after read-back. */
    public suspend fun writeRecord(directory: String, recordId: String, markdown: String): Outcome<String>

    public fun durableSessionId(sessionId: String): String

    /** The id the app opens [sessionId] by on the connection it opens it on (identity after restart). */
    public fun navigationSessionId(sessionId: String): String

    /** [beforeSend] runs right before dispatch; false refuses the send and nothing is dispatched. */
    public suspend fun promptHandoff(
        sessionId: String,
        text: String,
        model: HandoffModelRef,
        agent: String?,
        variant: String?,
        beforeSend: () -> Boolean,
    ): Outcome<Unit>
}

public data class HandoffTarget(
    val gateway: HandoffGateway,
    val scope: HandoffScope,
    val model: HandoffModelRef,
    /** The destination's display name, kept as link metadata. */
    val label: String,
    val agent: String? = null,
    val variant: String = "",
    /** False only for a model choice in the current conversation's runtime. */
    val startNewAgent: Boolean = true,
)

public enum class HandoffRoute { SwitchModel, NewConversation, Unavailable }

public enum class HandoffUnavailableReason { ProjectNotShared, SafeFileWriterUnavailable, TargetDispatchUnavailable }

public enum class HandoffFailureKind {
    Unavailable, ProjectMismatch, StaleScope, TargetUnavailable, SourceChanged, PreparationFailed, InvalidDraft,
    FileWriteFailed, CreationUnknown, DeliveryUnknown, LinkFailed,
}

public fun handoffRoute(
    source: HandoffGateway,
    sourceScope: HandoffScope,
    sourceModel: HandoffModelRef?,
    target: HandoffTarget,
): HandoffRoute {
    if (source === target.gateway &&
        sourceScope.profileId == target.scope.profileId &&
        sourceScope.origin == target.scope.origin &&
        sourceScope.directory == target.scope.directory &&
        sourceScope.workspace == target.scope.workspace &&
        !target.startNewAgent &&
        (source.capabilities.sessionModelProviderSwitching || sourceModel?.providerId == target.model.providerId)
    ) {
        return HandoffRoute.SwitchModel
    }
    if (!sourceScope.sharesProject(target.scope) ||
        !target.gateway.capabilities.agentHandoffFiles ||
        target.gateway.handoffTarget == null
    ) {
        return HandoffRoute.Unavailable
    }
    return HandoffRoute.NewConversation
}

/** For disabled destination rows. A generic filesystem RPC alone does not enable the verified file writer. */
public fun handoffUnavailableReason(
    source: HandoffGateway,
    sourceScope: HandoffScope,
    sourceModel: HandoffModelRef?,
    target: HandoffTarget,
): HandoffUnavailableReason? {
    if (handoffRoute(source, sourceScope, sourceModel, target) != HandoffRoute.Unavailable) return null
    if (!sourceScope.sharesProject(target.scope)) return HandoffUnavailableReason.ProjectNotShared
    if (!target.gateway.capabilities.agentHandoffFiles) return HandoffUnavailableReason.SafeFileWriterUnavailable
    return HandoffUnavailableReason.TargetDispatchUnavailable
}
