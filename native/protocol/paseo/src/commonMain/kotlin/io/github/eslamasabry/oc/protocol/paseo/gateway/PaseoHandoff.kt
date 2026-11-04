package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import kotlinx.coroutines.withContext

/**
 * A fresh agent may be created by this one reviewed handoff, fenced immediately before its first
 * prompt.
 */
public suspend fun PaseoGateway.promptNewHandoff(
    sessionId: String,
    prompt: OutgoingPrompt,
    beforeSend: () -> Boolean,
): Outcome<Unit> =
    withContext(lane) {
        when (
            val sent = paseoAttempt {
                sendPrompt(
                    sessionId,
                    paseoUuid(random),
                    prompt,
                    emptyList(),
                    existingOnly = false,
                    beforeSend = beforeSend,
                )
            }
        ) {
            is Outcome.Failed -> sent
            is Outcome.Ok ->
                when (sent.value) {
                    is PromptAdmission.Confirmed -> Outcome.Ok(Unit)
                    PromptAdmission.Uncertain ->
                        Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
                }
        }
    }

/**
 * Any location replacement or connection epoch invalidates a reviewed transfer, even after
 * returning to the same folder.
 */
public val PaseoGateway.handoffGeneration: Long
    get() = (transport.epoch.toLong() shl 32) xor locationEpoch.toLong()
