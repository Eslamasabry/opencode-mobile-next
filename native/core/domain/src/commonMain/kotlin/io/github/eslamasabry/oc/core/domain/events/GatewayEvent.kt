package io.github.eslamasabry.oc.core.domain.events

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.ProjectId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.EventLocation
import io.github.eslamasabry.oc.core.model.FormSpec
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** One adapted event with where it came from (plan 04 §11.2). */
public data class GatewayEnvelope(
    val event: GatewayEvent,
    val location: EventLocation?,
    val receivedAtMs: Long,
)

/**
 * The neutral event vocabulary (N4-D11). OpenCode 2, Paseo and Codex adapters all emit these; the
 * transcript reducer and the other engines consume only these. Adapter rules per event are plan 04
 * §7.6. The stream is volatile: after every Connected the consumer reconciles by refetch, never by
 * replay.
 */
public sealed interface GatewayEvent {
    /** First frame of every (re)connection. */
    public data object Connected : GatewayEvent

    // ---- sessions
    public data class SessionUpserted(val session: Session) : GatewayEvent

    /**
     * A move replaces both directory and workspace ([replacesLocation]); a rename changes [title]
     * only. OC2 moves may also change the project and the subpath (INTEGRATE-W1 additions,
     * defaulted; Dart Api2EventAdapter).
     */
    public data class SessionChanged(
        val sessionId: SessionId,
        val title: String?,
        val directory: String?,
        val workspaceId: String?,
        val replacesLocation: Boolean,
        val projectId: ProjectId? = null,
        val subpath: String? = null,
        val replacesSubpath: Boolean = false,
    ) : GatewayEvent

    public data class SessionDeleted(val sessionId: SessionId) : GatewayEvent

    /**
     * Empty values are ignored by the adapter; never creates an unhydrated session (B-PROTO-102).
     */
    public data class SessionSelection(
        val sessionId: SessionId,
        val model: ModelRef?,
        val agent: String?,
        val variant: String = "",
    ) : GatewayEvent

    /** Merged in place, never a full session replace. */
    public data class SessionUsage(val sessionId: SessionId, val cost: Double, val tokens: Tokens) :
        GatewayEvent

    /** Read watermark. */
    public data class SessionViewed(val sessionId: SessionId, val idleMs: Long) : GatewayEvent

    /** Refresh the row and reload the open chat (plan 04 §11.2; added by INTEGRATE-W1). */
    public data class SessionCompacted(val sessionId: SessionId) : GatewayEvent

    // ---- runs
    public data class RunStarted(val sessionId: SessionId) : GatewayEvent

    /** Retry text only ever goes to [details]. */
    public data class RunRetrying(
        val sessionId: SessionId,
        val attempt: Int,
        val nextAtMs: Long?,
        val tag: WireTag?,
        val details: Redacted?,
    ) : GatewayEvent

    /** Never followed by an idle that would overwrite a failure. */
    public data class RunEnded(val sessionId: SessionId, val outcome: RunOutcome) : GatewayEvent

    // ---- transcript
    public data class MessageUpserted(
        val message: MessageInfo,
        /**
         * Atomic server-origin user echo. An adapter-generated fallback id is never correlation
         * proof.
         */
        val userEcho: UserPromptEcho? = null,
    ) : GatewayEvent

    public data class PartUpserted(val part: Part) : GatewayEvent

    public data class PartDelta(
        val sessionId: SessionId,
        val messageId: MessageId,
        val partId: PartId,
        val field: DeltaField,
        val delta: String,
    ) : GatewayEvent

    /**
     * "Re-read the real result" (a result for a call never seen, open calls settled at run end).
     */
    public data class TranscriptStale(val sessionId: SessionId) : GatewayEvent

    /**
     * Server instruction/skill/shell text is never shown; only the kind. [messageId] is null for
     * session-level notices (OC2 `session.shell`, `session.skill.activated` carry no message;
     * INTEGRATE-W1 relaxed plan 04's non-null).
     */
    public data class Notice(
        val sessionId: SessionId,
        val messageId: MessageId?,
        val kind: NoticeKind,
    ) : GatewayEvent

    /** Resets history cursors downstream. */
    public data class RevertChanged(val sessionId: SessionId, val kind: RevertKind) : GatewayEvent

    // ---- requests and inbox
    /** Asks are never replayed; refetch on reconnect. */
    public data class PermissionAsked(val request: PermissionRequest) : GatewayEvent

    public data class PermissionSettled(val sessionId: SessionId, val requestId: RequestId) :
        GatewayEvent

    /**
     * Native agent question lifecycle; carries typed identities through the same location-stamped
     * stream.
     */
    public data class QuestionAsked(val question: PendingQuestion) : GatewayEvent

    public data class QuestionSettled(val sessionId: SessionId, val requestId: RequestId) :
        GatewayEvent

    public data class FormCreated(val form: FormSpec) : GatewayEvent

    public data class FormSettled(
        val sessionId: SessionId,
        val formId: FormId,
        val answered: Boolean,
    ) : GatewayEvent

    public data class InboxChanged(
        val sessionId: SessionId,
        val inboxId: InboxItemId,
        val change: InboxChange,
    ) : GatewayEvent

    // ---- hints and server facts (freshness hints only, never messages)
    /** Bursts share one reload downstream. */
    public data class RefreshHint(val kind: HintKind) : GatewayEvent

    /** Only when well-formed; never a credential payload. */
    public data class CredentialSwitched(val integrationId: String, val credentialId: String?) :
        GatewayEvent

    /** Exact versions kept (B-PROTO-047). */
    public data class Installation(val kind: InstallationKind, val version: String) : GatewayEvent

    /**
     * [ptyId] null = the event named no terminal: reconcile the whole list (OC2 wire;
     * INTEGRATE-W1).
     */
    public data class TerminalChanged(val ptyId: String?, val kind: TerminalChange) : GatewayEvent

    /**
     * [shellId] null = reconcile every managed shell from the scoped shell API (OC2 wire;
     * INTEGRATE-W1).
     */
    public data class ShellChanged(val shellId: String?) : GatewayEvent

    /** A copy's setup result, correlated by project and directory; server prose is Details only. */
    public data class WorktreeOutcome(
        val ready: Boolean,
        val directory: String? = null,
        val projectId: String? = null,
        val name: String? = null,
        val branch: String? = null,
        val details: Redacted? = null,
        val workspace: Boolean = false,
    ) : GatewayEvent
}

/** The server's correlation id and complete user parts observed in that same event. */
public data class UserPromptEcho(val clientMessageId: MessageId, val parts: List<Part>)

public sealed interface RunOutcome {
    public data object Succeeded : RunOutcome

    /**
     * The server went idle (plan 04 §11.2/§7.6). Never replaces an earlier Failed or Interrupted
     * for the same run.
     */
    public data object Idle : RunOutcome

    public data class Interrupted(val reason: InterruptReason) : RunOutcome

    /** [kind] null = words come from the wire type alone; server prose only in [details]. */
    public data class Failed(
        val kind: MessageErrorKind?,
        val tag: WireTag?,
        val details: Redacted?,
    ) : RunOutcome
}

/** OC2 `user|inactivity|shutdown|superseded`; anything else is Unknown. */
public enum class InterruptReason {
    User,
    Inactivity,
    Shutdown,
    Superseded,
    Unknown,
}

public enum class DeltaField {
    Text,
    Reasoning,
    ToolInputRaw,
}

/**
 * Open enum of notice kinds (InstructionsUpdated, SkillChanged, ShellChanged, …); the wire name is
 * kept.
 */
@JvmInline
public value class NoticeKind(public val raw: String) {
    public companion object {
        public val InstructionsUpdated: NoticeKind = NoticeKind("instructions.updated")
        public val SkillChanged: NoticeKind = NoticeKind("skill.activated")
        public val ShellChanged: NoticeKind = NoticeKind("shell")
    }
}

public enum class RevertKind {
    Staged,
    Cleared,
    Committed,
    Unknown,
}

public enum class InboxChange {
    Enqueued,
    Delivered,
    Cancelled,
    DeliveryChanged,
}

/**
 * Open enum of refresh pings: the wire family (`agent`, `config`, `mcp`, `vcs.branch`,
 * `filesystem`, …).
 */
@JvmInline public value class HintKind(public val raw: String)

public enum class InstallationKind {
    Available,
    Installed,
}

public enum class TerminalChange {
    Created,
    Updated,
    Exited,
    Deleted,
}

/**
 * One gateway's live stream seen two ways (plan 04 §6.5): both views share one HTTP stream and its
 * [status]. Collecting opens nothing extra; the gateway owns the loop. Durable events are never
 * dropped.
 */
public interface GatewayEvents {
    public val status: StateFlow<StreamStatus>

    /** Envelopes for the current directory, plus location-less ones. */
    public val project: Flow<GatewayEnvelope>

    /** Everything, location stamped (attention for other projects, installation, worktree). */
    public val all: Flow<GatewayEnvelope>

    /** Successful (re)connects so far (Flutter `_streamConnects`). */
    public val connects: StateFlow<Int>

    /** Cancel a pending wait and try at once (counts as attempt 1). */
    public fun retryNow()
}
