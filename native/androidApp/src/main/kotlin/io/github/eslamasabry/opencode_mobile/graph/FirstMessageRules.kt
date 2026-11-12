package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import io.github.eslamasabry.oc.engine.delivery.TypedKind
import io.github.eslamasabry.oc.engine.delivery.port.nothingSent

/**
 * The new-conversation screen's reading of a first message that [io.github.eslamasabry.oc.engine.delivery.PromptDelivery.send]
 * refused at once (no bubble, no receipt to wait for).
 */
internal object FirstMessageRules {
    /**
     * True when the words provably never left the phone, so they go back in the box with "not sent" and Try again.
     * The engine dispatches a prompt only after `send` returned Ok, so every synchronous refusal of a prompt wrote
     * nothing: offline with no queue (Paseo has none: `Network.Offline`, which read as "We couldn't confirm your
     * message arrived" + an empty Review page), a blocked route, a refused hold. Two stay unknown, because a
     * duplicate is the worse error: an explicit DispatchUncertain, and a typed command (`/x`, `!x`), whose failure
     * may come after it ran.
     */
    fun notSent(failure: Failure, request: SendRequest): Boolean {
        if ((failure as? Failure.Refused)?.reason == RefusalReason.DispatchUncertain) return false
        if (failure.nothingSent) return true
        val command =
            !request.promptOnly &&
                request.attachments.isEmpty() &&
                TypedKind.of(request.typedText.trim()) !is TypedKind.Prompt
        return !command
    }
}
