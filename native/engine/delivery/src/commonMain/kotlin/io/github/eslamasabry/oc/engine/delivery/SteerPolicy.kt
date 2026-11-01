package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.PromptDelivery

/** What a send does in the current situation (plan 09 §4.3). Words for each live in `:ui:words`. */
public enum class SendRoute(
    /** The wire delivery the gateway gets; null = field omitted (server default). Never [PromptDelivery.Queue]. */
    public val wireDelivery: PromptDelivery?,
) {
    /** Idle: an ordinary send that starts a turn. Composer word: "Send". */
    Plain(null),

    /** Running, `capabilities.promptSteering`: "Add to this turn"; after acceptance "Added to this turn". */
    Steer(PromptDelivery.Steer),

    /** Running, `MidTurnPrompt.JoinsTurn` (Paseo steers by itself): "Add to this turn", delivery unset. */
    JoinTurn(null),

    /** Running, `MidTurnPrompt.RestartsTurn`: "Stop and send" — stop, then send. */
    RestartTurn(null),

    /** Running, agent not loaded yet (`midTurnPrompt == null`): nothing is sent; the text stays editable. */
    Blocked(null),

    /** Running, no steering and no inbox (OpenCode-1 recognition): "Sends after this reply", the only queue word. */
    AfterReply(null),

    /** Offline: the offline queue, "Send when back online" (or a saved draft on servers without a queue). */
    Offline(null),
}

/**
 * The steer table (port of `docs/design/steering-contract.md`; plan 09 §4.3). Decided from capabilities and the
 * `MidTurnPromptGateway` facet, never from the backend kind (R19). There is **no** input that selects queue delivery:
 * no setting, long-press or stored value reaches this function, so an old stored `PromptDelivery.queue` is ignored by
 * construction (`NoQueueChoiceTest`).
 */
public object SteerPolicy {
    public fun route(
        online: Boolean,
        running: Boolean,
        capabilities: ServerCapabilities,
        hasMidTurnFacet: Boolean,
        midTurnPrompt: MidTurnPrompt?,
    ): SendRoute =
        when {
            !online -> SendRoute.Offline
            !running -> SendRoute.Plain
            capabilities.promptSteering -> SendRoute.Steer
            hasMidTurnFacet -> when (midTurnPrompt) {
                MidTurnPrompt.JoinsTurn -> SendRoute.JoinTurn
                MidTurnPrompt.RestartsTurn -> SendRoute.RestartTurn
                null -> SendRoute.Blocked
            }
            !capabilities.inbox -> SendRoute.AfterReply
            else -> SendRoute.Plain // an inbox without steering: the server's own default, no promise either way
        }

    /** Steers and voice-conversation sends stay transport-first: they need the exact message id (09 §4.2 step 6). */
    public fun bubbleFirst(route: SendRoute): Boolean = route != SendRoute.Steer

    /** The durable marker is for ordinary sends on queue-capable servers, never for steers (09 §4.2 step 5). */
    public fun holdsMarker(route: SendRoute, capabilities: ServerCapabilities): Boolean =
        capabilities.offlinePromptQueue && route != SendRoute.Steer && route != SendRoute.Blocked && route != SendRoute.Offline
}
