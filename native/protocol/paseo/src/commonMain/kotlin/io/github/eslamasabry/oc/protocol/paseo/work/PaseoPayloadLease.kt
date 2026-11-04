package io.github.eslamasabry.oc.protocol.paseo.work

import io.github.eslamasabry.oc.core.common.Outcome

/** A real phone payload reservation, acquired synchronously before the first suspension. */
public interface PaseoPayloadLease {
    /** Recheck ownership/install/remove/delete fences before daemon work begins. */
    public suspend fun awaitReady(): Outcome<Unit>

    /** Release once; a release failure never changes an acknowledged RPC's outcome. */
    public fun release()
}
