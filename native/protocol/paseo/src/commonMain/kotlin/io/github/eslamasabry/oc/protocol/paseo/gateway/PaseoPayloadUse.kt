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
    val acquire = beforePayloadUse ?: return operation()
    val scope = directoryValue
    val epoch = locationEpoch
    val admitted = try {
        acquire()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Host exceptions may carry private native detail: fixed words only.
        paseoFail(PaseoFailureKind.Unavailable)
    }
    if (admitted is Outcome.Failed) throw PaseoException(PaseoFailureKind.Unavailable, "The phone refused new agent work")
    try {
        if (closed || scope != directoryValue || epoch != locationEpoch) paseoFail(PaseoFailureKind.ScopeMismatch)
        return operation()
    } finally {
        try {
            afterPayloadUse?.invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The host owns its counter's bookkeeping; its errors never replace delivery truth.
        }
    }
}
