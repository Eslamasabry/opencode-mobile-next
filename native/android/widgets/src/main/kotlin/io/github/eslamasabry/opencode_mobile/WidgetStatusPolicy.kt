package io.github.eslamasabry.opencode_mobile

/** Three ordinary one-off checks, no retries or unbounded periodic owner. */
internal object WidgetStatusPolicy {
    const val READ_BUDGET_MS = 15_000L
    private const val FIRST_DELAY_MS = 30_000L
    private const val SECOND_DELAY_MS = 90_000L
    private const val THIRD_DELAY_MS = 480_000L
    val delaysMs = listOf(FIRST_DELAY_MS, SECOND_DELAY_MS, THIRD_DELAY_MS)
    fun mayRead(runAttemptCount: Int): Boolean = runAttemptCount == 0

    /** Only a completed, persisted idle patch is allowed to end the chain. */
    fun settledStatus(status: String?, completed: Boolean, idlePatched: Boolean): String =
        if (!completed || (status == "idle" && !idlePatched)) "unavailable" else status ?: "unavailable"

    fun continues(status: String?, attempt: Int): Boolean =
        status in setOf("busy", "unavailable") && attempt in 0 until delaysMs.lastIndex

    fun matches(ticket: Map<String, Any?>, current: Map<String, Any?>): Boolean =
        listOf("profileID", "scopeToken", "sessionID", "fingerprint", "runMarker", "startedAt")
            .all { ticket[it] != null && ticket[it] == current[it] } &&
            ticket["runMarker"] is String && (ticket["runMarker"] as String).isNotBlank() &&
            current["busy"] == true && (current["requestID"] as? String).isNullOrEmpty()
}
