package io.github.eslamasabry.oc.engine.attention.feed

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.time.Instant

/** Reasons to visit work, rather than an activity history or a busy reminder (Flutter `AttentionKind`). */
public enum class AttentionKind { Permission, Question, Form, FailedRun, TeamGate;

    /** Flutter's `kind.name`, used in the structured identity. */
    public val wire: String get() = name.replaceFirstChar { it.lowercaseChar() }
}

/** Shared vocabulary for a row, its header and notification copy (Flutter `WorkRowPhase`). */
public enum class AttentionPhase { Working, NeedsYou, Stalled, Done, Failed, Stopped, Waiting, Queued, Ready, Review, Unknown }

/**
 * One observation from an existing transport (Flutter `AttentionObservation`). Routing ids are opaque: never redact
 * or display them. Titles are redacted at the feed boundary. No request body, answer, exception or credential.
 */
public data class AttentionObservation(
    val id: String,
    val kind: AttentionKind,
    val phase: AttentionPhase,
    val observedAt: Instant,
    val sessionId: String? = null,
    val requestId: String? = null,
    val taskId: String? = null,
    val runId: String? = null,
    val title: String? = null,
    val directory: String? = null,
    val workspace: String? = null,
    /** False for retained evidence after its source failed or was not sampled. */
    val isFresh: Boolean = true,
) {
    /** Structured encoding avoids collisions when opaque ids contain separators. The profile is added by the feed. */
    val identity: String
        get() = DartJson.encode(listOf(kind.wire, sessionId, requestId ?: id, directory, workspace))
}

/**
 * Resolve the profile/location first, then open the conversation and focus its request card. A team gate without a
 * worker session opens its task; a missing session is never replaced with a guessed conversation.
 */
public data class AttentionTarget(
    val profileId: String,
    val kind: AttentionKind,
    val sessionId: String? = null,
    val requestId: String? = null,
    val taskId: String? = null,
    val runId: String? = null,
    val directory: String? = null,
    val workspace: String? = null,
) {
    val hasConversation: Boolean get() = !sessionId.isNullOrEmpty()
}

/** One row of the attention feed: names/titles are display-safe; [target] is navigation metadata, not copy. */
public data class AttentionFeedItem(
    val identity: String,
    val profileId: String,
    val serverName: String,
    val kind: AttentionKind,
    val target: AttentionTarget,
    val phase: AttentionPhase,
    val isFresh: Boolean,
    val observedAt: Instant,
    val title: String? = null,
)
