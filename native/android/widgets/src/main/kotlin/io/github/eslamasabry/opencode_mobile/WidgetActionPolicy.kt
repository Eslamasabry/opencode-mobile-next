package io.github.eslamasabry.opencode_mobile

import java.security.MessageDigest

/** Pure admission rules; no server data or entered text is persisted here. */
internal object WidgetActionPolicy {
    val actions = setOf("allowOnce", "reject", "stop", "steer", "voice", "approvePlan", "resume")
    const val MAX_TEXT_LENGTH = 8000
    val identityFields = listOf(
        "profileID", "scopeToken", "sessionID", "requestID", "runID", "gateID", "lastProjectID", "expectedStartedAt",
    )

    fun token(payload: Map<String, Any?>): String = MessageDigest.getInstance("SHA-256")
        .digest((listOf("action") + identityFields).joinToString("\u0000") {
            payload[it]?.toString().orEmpty()
        }.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun validate(payload: Map<String, Any?>, current: Map<String, Any?>?): String? {
        val action = payload["action"] as? String
        return when {
            action !in actions || (payload["id"] as? String).isNullOrBlank() -> "invalidRequest"
            current == null -> "staleRequest"
            !matchesIdentity(payload, current) -> "staleRequest"
            else -> validateAction(requireNotNull(action), payload, current)
        }
    }

    private fun matchesIdentity(payload: Map<String, Any?>, current: Map<String, Any?>): Boolean =
        identityFields.all { payload[it]?.toString().orEmpty() == current[it]?.toString().orEmpty() } &&
            !(payload["profileID"] as? String).isNullOrBlank() &&
            !(payload["scopeToken"] as? String).isNullOrBlank()

    private fun validateAction(
        action: String,
        payload: Map<String, Any?>,
        current: Map<String, Any?>,
    ): String? = when {
        action != "voice" && (payload["sessionID"] as? String).isNullOrBlank() -> "staleRequest"
        !hasActionIdentity(action, payload, current) -> "staleRequest"
        action == "voice" && (payload["lastProjectID"] as? String).isNullOrBlank() -> "unavailable"
        action in setOf("steer", "voice") && !validText(payload["text"] as? String) -> "invalidRequest"
        else -> null
    }

    private fun hasActionIdentity(
        action: String,
        payload: Map<String, Any?>,
        current: Map<String, Any?>,
    ): Boolean = when (action) {
        "allowOnce", "reject" -> !(payload["requestID"] as? String).isNullOrBlank()
        "stop", "steer" -> current["busy"] == true &&
            (current["expectedStartedAt"] as? Number)?.toLong().let { it != null && it > 0 }
        "approvePlan", "resume" -> current["teamAction"] == action &&
            !(payload["runID"] as? String).isNullOrBlank() &&
            (action != "approvePlan" || !(payload["gateID"] as? String).isNullOrBlank())
        else -> true
    }

    private fun validText(text: String?): Boolean =
        text != null && text.isNotBlank() && text.length <= MAX_TEXT_LENGTH
}
