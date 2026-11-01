package io.github.eslamasabry.opencode_mobile

/** One tap owns at most thirty seconds. Another tap never renews that lease. */
internal class WidgetActionLease(val id: String, val startedAt: Long) {
    val deadline = startedAt + TIMEOUT_MS
    var settled = false
        private set

    fun remaining(now: Long): Long = (deadline - now).coerceIn(0L, TIMEOUT_MS)
    fun settle(): Boolean {
        if (settled) return false
        settled = true
        return true
    }

    companion object {
        const val TIMEOUT_MS = 30_000L
        val actions = setOf("allowOnce", "reject", "stop", "approvePlan", "resume")

        fun safeResult(payload: Map<String, Any?>, response: Map<*, *>?): Map<String, Any?> {
            val status = response?.get("status") as? String
            if (response?.get("id") != payload["id"] || response?.get("action") != payload["action"] ||
                status !in setOf("accepted", "rejected", "uncertain")) {
                return mapOf(
                    "id" to payload["id"], "action" to payload["action"],
                    "status" to "uncertain", "error" to "uncertainDelivery",
                )
            }
            return mutableMapOf<String, Any?>(
                "id" to payload["id"], "action" to payload["action"], "status" to status,
            ).apply {
                (response?.get("error") as? String)?.takeIf {
                    it in setOf(
                        "invalidRequest", "staleRequest", "unavailable", "unsupported", "busy", "uncertainDelivery",
                    )
                }?.let { put("error", it) }
            }
        }
    }
}
