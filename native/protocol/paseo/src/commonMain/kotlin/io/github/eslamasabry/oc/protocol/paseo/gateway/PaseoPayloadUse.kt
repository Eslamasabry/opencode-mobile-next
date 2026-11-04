package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import kotlinx.coroutines.CancellationException

/*
 * Payload-use admission, a port of lib/paseo/gateway/payload_use.dart (B-AGENT-173). A trusted phone host leases its
 * installed payloads across daemon work: the lease is taken before the first request of a send and held until the
 * daemon acknowledged it. A refused lease dispatches nothing and is never released; an admitted one is always
 * released, also after a stale scope, a closed gateway or a refused request, and a failing release never replaces the
 * send's own result. Without callbacks (every remote gateway) the operation simply runs.
 */
internal suspend fun <T> PaseoGateway.withPayloadUse(operation: suspend () -> T): T {
    val scope = directoryValue
    val epoch = locationEpoch
    var acquired = false
    pendingPayloadWork++
    payloadWorkChanged()
    try {
        val acquire = beforePayloadUse
        if (acquire != null) {
            val admitted =
                try {
                    acquire()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    paseoFail(PaseoFailureKind.Unavailable)
                }
            if (admitted is Outcome.Failed)
                throw PaseoException(
                    PaseoFailureKind.Unavailable,
                    "The phone refused new agent work",
                )
            acquired = true
            if (closed || scope != directoryValue || epoch != locationEpoch)
                paseoFail(PaseoFailureKind.ScopeMismatch)
        }
        return operation()
    } finally {
        pendingPayloadWork--
        payloadWorkChanged()
        if (acquired) {
            try {
                afterPayloadUse?.invoke()
            } catch (_: Exception) {
                // Releasing a host lease must never replace the send's delivery result.
            }
        }
    }
}
