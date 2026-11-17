package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.collections.immutable.ImmutableMap

/** Fresh run truth, never a cached status or a resend. The connection coalesces calls on its reconcile lane. */
public interface RunRecoveryGateway {
    /**
     * One status read for [sessions]. For an idle run, refetch its newest history before publishing RunEnded
     * with the refetched history. A failed, stale or cancelled read must never end a run. Busy and Retry keep waiting.
     */
    public suspend fun reconcileRuns(sessions: Set<SessionId>): Outcome<ImmutableMap<SessionId, SessionStatus>>

    /** Report a failed bounded read to the normal reconnect policy, preserving backoff and the reconnect switch. */
    public fun reconnectAfterRunCheck()
}
