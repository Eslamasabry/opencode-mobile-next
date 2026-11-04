package io.github.eslamasabry.oc.engine.delivery.port

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.CorrelatedPromptGateway
import io.github.eslamasabry.oc.core.domain.MidTurnPromptGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope

/**
 * [SendLink] over one connected `ServerGateway` and its facets (the app graph builds one per
 * gateway epoch). Engines test facets; nothing here reads the backend kind (R19).
 */
public class GatewaySendLink(
    private val profile: ProfileId,
    /**
     * The profile's stored address (Dart `profile.baseUrl`), part of the receipt scope. Never
     * logged.
     */
    private val baseUrl: String,
    private val gateway: ServerGateway,
    private val admitInteractive: suspend () -> Outcome<Unit> = { Outcome.Ok(Unit) },
) : SendLink {
    /** One gateway is one connection epoch; a status-change wrapper over it is the same target. */
    override val epoch: Any
        get() = gateway

    override val scope: DispatchScope
        get() =
            gateway.location.value.let {
                DispatchScope(profile, baseUrl, it.directory, it.workspace)
            }

    override val capabilities: ServerCapabilities
        get() = gateway.capabilities.value

    override val connected: Boolean
        get() = gateway.events.status.value == StreamStatus.Connected

    private val correlatedGateway: CorrelatedPromptGateway?
        get() = gateway.facet<CorrelatedPromptGateway>()

    override val correlated: Boolean
        get() = correlatedGateway != null && capabilities.clientPromptMessageID

    override val hasMidTurnFacet: Boolean
        get() = gateway.facet<MidTurnPromptGateway>() != null

    override suspend fun awaitActionTransport(): Outcome<Unit> {
        val admitted = admitInteractive()
        if (admitted is Outcome.Failed) return admitted
        return gateway.awaitActionTransport()
    }

    override suspend fun revertPending(session: SessionId): Outcome<Boolean> =
        gateway.session(session).map { it.reverted || it.stagedRevert != null }

    /**
     * Session-selection servers: mutate once (no retry), then read the session back — the server
     * must hold exactly the requested provider, id and normalized variant before the choice counts;
     * a stale or unknown readback fails closed and the session payload is never echoed into the
     * failure. Without the facet the prompt's inline model stays the semantics, except a server
     * that advertises `sessionModelProviderSwitching` without the facet: that fails closed rather
     * than silently dropping a person's choice.
     */
    override suspend fun applySelection(
        session: SessionId,
        model: ModelSelection?,
        agent: String?,
    ): Outcome<Unit> {
        if (model == null && agent == null) return Unit.ok()
        val selection =
            gateway.facet<SessionSelectionGateway>()
                ?: return if (model != null && capabilities.sessionModelProviderSwitching) {
                    Failure.Unavailable(FeatureId("sessionModelProviderSwitching")).failed()
                } else {
                    Unit.ok()
                }
        // "default" is the server's own no-variant sentinel (B-PROTO-018): it is never sent as a
        // variant
        // and it matches the server's normalized empty readback.
        val variant = model?.variant.orEmpty().trim().let { if (it == "default") "" else it }
        if (model != null) {
            val applied = selection.setSessionModel(session, model.model, variant)
            if (applied is Outcome.Failed) return applied
        }
        if (agent != null) {
            val applied = selection.setSessionAgent(session, agent)
            if (applied is Outcome.Failed) return applied
        }
        if (model != null) {
            when (val readback = gateway.session(session)) {
                is Outcome.Failed -> return readback
                is Outcome.Ok -> {
                    val chosen = readback.value.selection
                    val matches =
                        chosen != null &&
                            chosen.modelKnown &&
                            chosen.model?.providerId == model.model.providerId &&
                            chosen.model?.modelId == model.model.modelId &&
                            chosen.variant.trim().let { if (it == "default") "" else it } == variant
                    if (!matches) return Failure.Refused(RefusalReason.StaleScope).failed()
                }
            }
        }
        return Unit.ok()
    }

    override fun newClientMessageId(): MessageId? =
        correlatedGateway?.takeIf { correlated }?.createPromptMessageId()

    override suspend fun prompt(
        session: SessionId,
        prompt: OutgoingPrompt,
        beforeSend: () -> Boolean,
    ): Outcome<PromptAdmission> {
        val admitted = admitInteractive()
        if (admitted is Outcome.Failed) return admitted
        val id = prompt.clientMessageId
        val correlatedGateway = correlatedGateway
        if (id != null && correlatedGateway != null)
            return correlatedGateway.promptWithMessageId(session, id, prompt, beforeSend)
        if (!beforeSend()) return Failure.Refused(RefusalReason.StaleScope).failed()
        return gateway.prompt(session, prompt.copy(clientMessageId = null))
    }

    /**
     * Reads only. The inbox first (pending admitted work, prompt-typed items with our id and
     * session), then the conversation's pages for a **user** message with our id. Never infers
     * acceptance from equal text, assistant progress or an empty page; at most [MAX_PAGES] pages.
     */
    override suspend fun lookupAdmission(
        session: SessionId,
        messageId: MessageId,
    ): Outcome<Boolean> {
        // B-PROTO-041: without the receipt capability, or on a closed (stopped) connection, nothing
        // is read and nothing
        // is confirmed.
        if (!capabilities.commandReceipts || gateway.events.status.value is StreamStatus.Stopped)
            return false.ok()
        if (capabilities.inbox) {
            when (val inbox = gateway.inbox(session)) {
                is Outcome.Failed -> return inbox
                is Outcome.Ok ->
                    if (
                        inbox.value.any {
                            it.id.raw == messageId.raw &&
                                it.sessionId == session &&
                                it.type in PROMPT_TYPES
                        }
                    ) {
                        return true.ok()
                    }
            }
        }
        var cursor: String? = null
        val seen = HashSet<String>()
        repeat(MAX_PAGES) {
            val page =
                when (val p = gateway.messagePage(session, cursor)) {
                    is Outcome.Failed -> return p
                    is Outcome.Ok -> p.value
                }
            if (
                page.items.any {
                    it.info.id == messageId.raw &&
                        it.info.sessionId == session.raw &&
                        it.info.role == Role.User
                }
            )
                return true.ok()
            cursor = page.nextCursor ?: return false.ok()
            if (!seen.add(cursor!!)) return false.ok()
        }
        return false.ok()
    }

    override suspend fun stop(session: SessionId): Outcome<StopResult> = gateway.stop(session)

    override fun midTurnPrompt(session: SessionId): MidTurnPrompt? =
        gateway.facet<MidTurnPromptGateway>()?.midTurnPrompt(session)

    override suspend fun slashCommand(
        session: SessionId,
        command: String,
        args: String,
    ): Outcome<Unit> {
        val admitted = admitInteractive()
        if (admitted is Outcome.Failed) return admitted
        return gateway.slashCommand(session, command, args)
    }

    /**
     * v2 uses the session's own agent for a shell line; other backends read [agent] (empty = their
     * default).
     */
    override suspend fun shell(session: SessionId, command: String): Outcome<Unit> {
        val admitted = admitInteractive()
        if (admitted is Outcome.Failed) return admitted
        return gateway.shell(session, command, agent = "")
    }

    public companion object {
        public const val MAX_PAGES: Int = 100

        /** The real server answers `user` (plan 04 N4-D13); Flutter's check expected `prompt`. */
        public val PROMPT_TYPES: Set<String> = setOf("user", "prompt")
    }
}
