package io.github.eslamasabry.oc.engine.attention

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * Frozen identifier-only widget extras. No address, credential or text crosses the service ingress.
 */
public data class WidgetActionRequest(
    val id: String,
    val action: String,
    val profileId: String,
    val scopeToken: String,
    val sessionId: String,
    val requestId: String,
    val runId: String,
    val gateId: String,
    val lastProjectId: String,
    val expectedStartedAt: Long,
)

public enum class WidgetActionStatus {
    Accepted,
    Rejected,
    Uncertain,
}

public enum class WidgetActionError {
    InvalidRequest,
    StaleRequest,
    Unavailable,
    Unsupported,
    Busy,
    UncertainDelivery,
}

public data class WidgetActionResult(
    val status: WidgetActionStatus,
    val error: WidgetActionError? = null,
)

/**
 * Implemented by :android:widgets. :android:background can call this without seeing a sibling
 * Android module.
 */
public fun interface WidgetActionIngress {
    public suspend fun run(request: WidgetActionRequest): Outcome<WidgetActionResult>
}
