package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.ProjectId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ids.WorkspaceId
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.jvm.JvmInline

// Ports of lib/domain/models.dart (Session family) and server_gateway/value_types_core.dart.

/** Dart `Health`. */
public data class Health(val healthy: Boolean, val version: String?)

/** One page of a paged read; the opaque continuation always requests older rows. */
public data class ServerPage<out T>(val items: ImmutableList<T>, val nextCursor: String?)

/** Times in epoch ms (Dart SessionTime). */
public data class SessionTime(
    val createdMs: Long? = null,
    val updatedMs: Long? = null,
    val archivedMs: Long? = null,
    val idleMs: Long? = null,
    val viewedMs: Long? = null,
    val compactingMs: Long? = null,
)

public data class SessionDiffSummary(val additions: Int, val deletions: Int, val files: Int)

public data class ModelRef(val providerId: String, val modelId: String)

/** Dart SessionSelection: what the conversation will use next. */
public data class SessionSelection(
    val model: ModelRef?,
    val variant: String,
    val agent: String?,
    val modelKnown: Boolean,
    val agentKnown: Boolean,
)

/** Dart SessionRevert (a staged revert). */
public data class SessionRevert(
    val messageId: MessageId,
    val partId: PartId?,
    val snapshot: String?,
    val files: ImmutableList<FileDiff>?,
)

public data class SessionContextUse(val tokens: Long, val limit: Long)

/** Dart `Session`, field for field (models.dart:215). */
public data class Session(
    val id: SessionId,
    val title: String? = null,
    val projectId: ProjectId? = null,
    val workspaceId: WorkspaceId? = null,
    val parentId: SessionId? = null,
    val directory: String? = null,
    val path: String? = null,
    val reverted: Boolean = false,
    val stagedRevert: SessionRevert? = null,
    val shareUrl: String? = null,
    val time: SessionTime? = null,
    val cost: Double? = null,
    val tokens: Tokens? = null,
    val summary: SessionDiffSummary? = null,
    val agent: String? = null,
    val model: String? = null,
    val selection: SessionSelection? = null,
    val compactingSinceMs: Long? = null,
    val lastRunFailed: Boolean = false,
    val contextUse: SessionContextUse? = null,
)

/** Open enum (wire value kept): `idle`, `busy`, `retry`, or anything newer. */
@JvmInline
public value class SessionStatus(public val raw: String) {
    public companion object {
        public val Idle: SessionStatus = SessionStatus("idle")
        public val Busy: SessionStatus = SessionStatus("busy")
        public val Retry: SessionStatus = SessionStatus("retry")
    }
}

public data class SessionRetryAction(val title: String, val message: String, val label: String, val link: String?)

/** Dart SessionRetryState; [message] is server prose (Details only). */
public data class SessionRetryState(val attempt: Int, val message: String?, val action: SessionRetryAction?, val nextAtMs: Long?)

/** Dart `Todo`. */
public data class Todo(val content: String, val status: String, val priority: String?)

/** Options for a new session (plan 04 §11.1 `NewSession`). */
public data class NewSession(val title: String? = null, val parentId: SessionId? = null, val location: ServerLocation? = null)

public data class GlobalSessionResult(val session: Session, val projectName: String?, val projectDirectory: String?)

public enum class BackgroundWorkSupport { Unavailable, Subagents, SubagentsAndShells }

public enum class BackgroundWorkResult { Promoted, Unchanged, Requested }

public val NoSessions: ImmutableList<Session> = persistentListOf()
