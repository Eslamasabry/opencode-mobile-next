package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * What a failed effect turns into (07 §7.3 rule 6): a notice plus a fallback target, never a broken page.
 * [keepTarget] keeps the target built so far (a created chat whose draft could not be seeded); otherwise a null
 * [target] means "stay where the person is".
 */
public data class Fallback(val target: EntryTarget?, val notice: EntryNotice?, val keepTarget: Boolean = false)

/**
 * Effects run in the router, in order, after a [EntryDecision.Go] (07 §7.3 rule 6). Each one may rewrite the target
 * the decision named; a failure ends the run with its [onFailure]. Only [Connect] and [CreateSession] change anything
 * on a server, and [EntryDecider] emits them only for [EntryOrigin.Internal] requests (Astra M2) or, for
 * [CreateSession], a share (07 §7.7: an empty session holding an unsent draft).
 */
public sealed interface EntryEffect {
    public val onFailure: Fallback

    /** Switch to [profileId] first (internal arrivals only). */
    public data class Connect(val profileId: ProfileId, override val onFailure: Fallback) : EntryEffect

    /** Turns the ids an arrival carries into the canonical ref (main or phone agents) and rewrites matching Chats. */
    public data class ResolveRef(val ref: ConversationRef, override val onFailure: Fallback) : EntryEffect

    /** Creates one empty session (never a message); the target becomes `Chat(created, opened = NewlyCreated)`. */
    public data class CreateSession(
        val profileId: ProfileId,
        val focusComposer: Boolean,
        override val onFailure: Fallback,
    ) : EntryEffect

    /** Writes shared text as the current Chat's unsent draft (:engine:composer). */
    public data class SeedDraft(val text: String, override val onFailure: Fallback) : EntryEffect {
        override fun toString(): String = "SeedDraft(chars=${text.length}, onFailure=$onFailure)"
    }

    /** Revalidates profile, location and exact request for a monitored alert; never answers anything. */
    public data class ResolveMonitoredRequest(
        val token: MonitorToken,
        val profileId: ProfileId,
        val sessionId: SessionId,
        override val onFailure: Fallback,
    ) : EntryEffect

    /** A widget row of another project or connection: found → Chat; not found → Land(null) (07 §7.8). */
    public data class ResolveWidgetConversation(
        val profileId: ProfileId,
        val sessionId: SessionId,
        val focusComposer: Boolean,
        override val onFailure: Fallback,
    ) : EntryEffect

    /** A quota alert's token must still belong to a source; otherwise the notice "That plan changed". */
    public data class VerifyQuotaToken(
        val profileId: ProfileId?,
        val token: String,
        override val onFailure: Fallback,
    ) : EntryEffect

    /** Keeps a session link for a server not saved here; the target becomes `SessionAddress(ticket)`. */
    public data class ParkSessionLink(val profileId: ProfileId, val sessionId: SessionId) : EntryEffect {
        override val onFailure: Fallback get() = Fallback(null, null)
    }
}

/** The pure decision (07 §7.3 rule 2). */
public sealed interface EntryDecision {
    /** Keep the request; [EntryWaiting] drives the one status line. */
    public data class Wait(val reason: WaitReason, val profileId: ProfileId? = null) : EntryDecision

    public data class Go(
        val target: EntryTarget,
        val effects: List<EntryEffect> = emptyList(),
        val notice: EntryNotice? = null,
    ) : EntryDecision

    /** Consume; nothing navigates. A non-null [notice] still reaches the shell (handoff with a null target). */
    public data class Drop(val notice: EntryNotice? = null) : EntryDecision
}
