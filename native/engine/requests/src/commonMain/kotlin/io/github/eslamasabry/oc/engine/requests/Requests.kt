package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred

/**
 * Plan 08 `HELD_ANSWER` / Flutter `DelayedAnswers.window`: how long an answer can be undone before
 * it is sent.
 */
public val HELD_ANSWER: Duration = 3.seconds

/**
 * What the person answered (plan 09 §5.1). Slice 2 has permissions and questions; forms arrive with
 * S3-REQFORMS.
 */
public sealed interface RequestAnswer {
    /** [note] rides only on an OpenCode 2 rejection (shown to the model). */
    public data class Permission(val reply: PermissionReply, val note: String? = null) :
        RequestAnswer

    /** One list of chosen labels (or typed text) per prompt, in prompt order. */
    public data class Question(val answers: ImmutableList<ImmutableList<String>>) : RequestAnswer

    /** Dismiss a question (nobody can restore it; the UI confirms first). */
    public data object Decline : RequestAnswer
}

/**
 * Where one request's answer stands. A request is in exactly one phase; the engine moves it forward
 * with one atomic claim, which is what makes "exactly one reply" hold (B-CHAT-074).
 */
public sealed interface AnswerPhase {
    /** Nobody has answered on this phone. */
    public data object Waiting : AnswerPhase

    /**
     * Answered, inside the undo window ([HELD_ANSWER]); [token] tells one hold from a later one.
     */
    public data class Held(val answer: RequestAnswer, val untilMs: Long, internal val token: Long) :
        AnswerPhase

    /** The one write is on its way; every other caller waits on the same [result] (B-AGENT-031). */
    public class Sending
    internal constructor(
        public val answer: RequestAnswer,
        public val sinceMs: Long,
        internal val result: CompletableDeferred<Outcome<Unit>>,
    ) : AnswerPhase {
        override fun toString(): String = "Sending($answer, sinceMs=$sinceMs)"
    }

    /**
     * The server took the answer; the request leaves when it settles or a refetch no longer lists
     * it. Never "Done".
     */
    public data class Sent(val answer: RequestAnswer, val sinceMs: Long) : AnswerPhase

    /** The server refused the answer; the request stays, and Try again resends the same answer. */
    public data class NotAccepted(val answer: RequestAnswer, val failure: Failure) : AnswerPhase
}

/** One request waiting on the person, oldest first in [RequestsEngine.pending]. */
public sealed interface PendingRequest {
    public val id: RequestId
    public val sessionId: SessionId
    public val phase: AnswerPhase

    /**
     * When this phone first showed it (null until shown): the card's "waiting 20 s" counts from
     * here.
     */
    public val firstShownAtMs: Long?

    public data class Permission(
        val request: PermissionRequest,
        override val phase: AnswerPhase = AnswerPhase.Waiting,
        override val firstShownAtMs: Long? = null,
    ) : PendingRequest {
        override val id: RequestId
            get() = request.id

        override val sessionId: SessionId
            get() = request.sessionId

        /**
         * The tool call the ask is for (`tool.callID`), so the step that asks is not repeated above
         * its card.
         */
        val callId: String?
            get() = request.tool?.callId
    }

    public data class Question(
        val question: PendingQuestion,
        override val phase: AnswerPhase = AnswerPhase.Waiting,
        override val firstShownAtMs: Long? = null,
    ) : PendingRequest {
        override val id: RequestId
            get() = question.id

        override val sessionId: SessionId
            get() = question.sessionId

        /**
         * One prompt with one answer: the options sit on the card and a tap answers (plan 09 §5.4).
         */
        val oneAnswer: Boolean
            get() =
                question.prompts.size == 1 &&
                    question.prompts.single().let { prompt ->
                        !prompt.multiple &&
                            !prompt.optional &&
                            prompt.choices.none { it.description.length > 140 }
                    }
    }
}

/**
 * The card one chat shows: the oldest waiting request of the session, and how many more wait behind
 * it.
 */
public data class SessionRequests(val current: PendingRequest?, val moreWaiting: Int) {
    public companion object {
        public val None: SessionRequests = SessionRequests(null, 0)

        /** Pure: [all] is the engine's oldest-first list. */
        public fun of(all: List<PendingRequest>, session: SessionId): SessionRequests {
            val mine = all.filter { it.sessionId == session }
            return if (mine.isEmpty()) None else SessionRequests(mine.first(), mine.size - 1)
        }
    }
}

internal val NoRequests: ImmutableList<PendingRequest> = persistentListOf()
