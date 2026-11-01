package io.github.eslamasabry.opencode_mobile

import org.json.JSONObject

internal object SessionWidgetActions {
    fun advertisedActions(session: JSONObject): Set<String> {
        val actions = session.optJSONArray("onDemandActions") ?: return emptySet()
        return (0 until actions.length()).map { actions.optString(it) }.toSet()
    }

    fun candidateActions(session: JSONObject): Set<String> = when {
        session.optString("requestID").isNotBlank() -> setOf("allowOnce", "reject")
        session.optJSONObject("team")?.optString("action") == "approvePlan" -> setOf("approvePlan")
        session.optJSONObject("team")?.optString("action") == "resume" -> setOf("resume")
        session.optBoolean("busy") -> setOf("stop")
        else -> emptySet()
    }

    /** Gate cached controls on the same identity contract used by dispatch. */
    fun validActions(snapshot: JSONObject, session: JSONObject): Set<String> {
        val identity = WidgetV2Bridge.identity(snapshot, session)
        val current = identity + mapOf(
            "busy" to session.optBoolean("busy"),
            "teamAction" to session.optJSONObject("team")?.optString("action"),
        )
        return candidateActions(session).filter { action ->
            val payload = identity + mapOf("action" to action, "id" to "render")
            WidgetActionPolicy.validate(payload, current) == null
        }.toSet()
    }

}
