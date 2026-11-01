package io.github.eslamasabry.oc.engine.delivery.port

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope
import kotlinx.coroutines.flow.StateFlow

/**
 * One connected server as delivery sees it (ports instead of engine edges, like S2-ENTRY D-E8). The app graph binds
 * it over the connection's `ServerGateway` and facets ([GatewaySendLink]). A reconnect publishes a **new instance**:
 * instance identity is the gateway epoch, so "a changed connection dispatches to neither server" (B-STATE-119) is an
 * identity check.
 */
public interface SendLink {
    public val scope: DispatchScope
    public val capabilities: ServerCapabilities

    /** The event stream is connected (Dart `status == StreamStatus.connected`). */
    public val connected: Boolean

    /** True when the gateway can post a client message id (`CorrelatedPromptGateway` + `clientPromptMessageID`). */
    public val correlated: Boolean

    /** Dart `prepareActionTransport`: wakes the link before a mutation. */
    public suspend fun awaitActionTransport(): Outcome<Unit>

    /** Staged-revert servers: true while a revert is staged or saving. Reads only; never commits the revert. */
    public suspend fun revertPending(session: SessionId): Outcome<Boolean>

    /** Applies a queued entry's intentional model and agent choice (session-selection servers); others answer Ok. */
    public suspend fun applySelection(session: SessionId, model: ModelSelection?, agent: String?): Outcome<Unit>

    /** A fresh wire id (`Oc2ClientIds.message()`), or null when [correlated] is false. */
    public fun newClientMessageId(): MessageId?

    /**
     * Sends once. [beforeSend] runs immediately before bytes leave; false refuses with `Failure.Refused` and nothing is
     * sent. Contract: a `Refused`, `Unavailable` or `StagedRevert` answer means **nothing left the device**; every other
     * failure, and `PromptAdmission.Uncertain`, may have been delivered. `Confirmed(receiptId)` is admission only.
     */
    public suspend fun prompt(session: SessionId, prompt: OutgoingPrompt, beforeSend: () -> Boolean): Outcome<PromptAdmission>

    /** Reads only: is [messageId] admitted (a pending inbox item or a promoted user message)? */
    public suspend fun lookupAdmission(session: SessionId, messageId: MessageId): Outcome<Boolean>

    public suspend fun stop(session: SessionId): Outcome<StopResult>

    /** `/name args` typed by the person (09 §4.2 step 1). Transport-first; never queued. */
    public suspend fun slashCommand(session: SessionId, command: String, args: String): Outcome<Unit>

    /** `!command` typed by the person. Transport-first; never queued. */
    public suspend fun shell(session: SessionId, command: String): Outcome<Unit>

    /** Gateways that never hold a mid-turn message; null while the agent is not known (or the facet is absent). */
    public fun midTurnPrompt(session: SessionId): MidTurnPrompt?

    /** True when the gateway has a `MidTurnPromptGateway` facet at all. */
    public val hasMidTurnFacet: Boolean
}

/** The current link of the open connection; null while there is none. */
public interface DeliveryConnection {
    public val link: StateFlow<SendLink?>

    /** The active profile, connected or not (Dart `profile?.id`): queue counts and online holds use it. */
    public val activeProfile: StateFlow<ProfileId?>
}

/** The automation policy's `reconcileQueuedSends` (B-STATE-038). Read at every step of a flush. */
public fun interface AutomationPolicy {
    public fun allowsQueuedSends(profile: ProfileId): Boolean
}

/** What the transcript and the review engine know about one conversation (bound by the app graph). */
public interface ConversationFacts {
    /** The server says a turn runs (`transcript.run.busy`). */
    public fun running(ref: ConversationRef): Boolean

    /** A staged undo waits for the person (09 §4.2 step 2, B-CHAT-106). */
    public fun undoUnresolved(ref: ConversationRef): Boolean
}

/** `:engine:connection`'s stop memory (09 §4.4, port of `markStoppedByPerson`). */
public interface StopMemory {
    /** Before the call, so the server's interrupt does not read as another device. */
    public fun noteStopFromThisApp(ref: ConversationRef)

    public fun markStoppedByPerson(ref: ConversationRef)

    public fun clearStoppedByPerson(ref: ConversationRef)
}

/** Failures that carry a server status: before dispatch they keep the entry retryable and the batch going. */
public val Failure.hasStatus: Boolean
    get() = when (this) {
        is Failure.Server -> status != null
        is Failure.Rejected -> status != null
        is Failure.Conflict, is Failure.NotFound, is Failure.Busy, is Failure.SignIn, is Failure.StagedRevert -> true
        else -> false
    }

/** [SendLink.prompt]'s contract: these answers promise that nothing left the device. */
public val Failure.nothingSent: Boolean
    get() = this is Failure.Refused || this is Failure.Unavailable || this is Failure.StagedRevert
