package io.github.eslamasabry.oc.engine.aisetup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Structured local guidance, not a model or a server agent session (Dart `SetupIntent`). */
public enum class SetupIntent {
    ChooseModel, DefaultAgent, Permission, ConnectRemoteMcp, AddLocalMcp, RemoveMcp, ConfigureProvider, ConfigureAgent,
    ConfigureCommand,
}

public enum class SetupPlannerState { NeedsInput, Proposed, Unsupported }

/** The planner's fixed guidance, as a value; the words are the Dart copy on each entry. User text is never echoed. */
public enum class SetupGuidance {
    /** "This guided setup session has ended. Start a new one." */
    SessionEnded,

    /** "Provider setup requires a separate secure sign-in flow. Do not enter credentials in the setup assistant." */
    ProviderNeedsSecureSignIn,

    /**
     * "Creating or editing agents is not available in guided setup. Choose an existing default agent or use the server
     * configuration tools."
     */
    AgentEditingUnavailable,

    /** "Creating or editing commands is not available in guided setup. Use the server configuration tools." */
    CommandEditingUnavailable,

    /**
     * "Use the requested fields without credentials. URLs must not contain a password, query, or fragment. No change
     * has been proposed."
     */
    InvalidInput,

    /**
     * "Choose the requested values to prepare a proposed change. Guided setup runs on this device and does not
     * interpret free-form questions."
     */
    ChooseValues,

    /**
     * "Review this proposed change. Nothing has been applied. Applying requires server support and your confirmation.
     * MCP proposals start disabled. Local MCP commands run on your server when enabled."
     */
    ReviewProposal,
}

public data class SetupPlannerReply(
    val sessionId: String,
    val state: SetupPlannerState,
    val guidance: SetupGuidance,
    val requiredInputs: List<String> = emptyList(),
    val edits: List<SetupEdit> = emptyList(),
)

/**
 * Memory-only guided sessions (Dart `SetupPlanner`): no network, persistence, credentials or tools. A proposal is never
 * a server write; it still needs controller validation, review and an explicit Apply.
 *
 * `question` is deliberately ignored: the UI requests an explicit intent and structured inputs, and labels this mode
 * "Guided setup (on this device)". Inputs are a `String`, or a `List<String>` for `command`. At most eight sessions;
 * starting a ninth discards the oldest. Dispose on profile switch or deletion; never a singleton.
 */
public class SetupPlanner(private val redaction: SetupRedaction) {
    private val sessions = LinkedHashMap<String, PlannerSession>()
    private var nextId = 0

    @Suppress("UNUSED_PARAMETER")
    public fun start(
        intent: SetupIntent,
        question: String? = null,
        inputs: Map<String, Any?> = emptyMap(),
    ): SetupPlannerReply {
        if (sessions.size >= MAX_SESSIONS) sessions.remove(sessions.keys.first())
        val id = "setup-${++nextId}"
        sessions[id] = PlannerSession(intent)
        return continueSession(id, inputs = inputs)
    }

    @Suppress("UNUSED_PARAMETER")
    public fun continueSession(
        sessionId: String,
        question: String? = null,
        inputs: Map<String, Any?> = emptyMap(),
    ): SetupPlannerReply {
        val session = sessions[sessionId]
            ?: return SetupPlannerReply("", SetupPlannerState.Unsupported, SetupGuidance.SessionEnded)
        val fields = fields(session.intent)
            ?: return SetupPlannerReply(
                sessionId,
                SetupPlannerState.Unsupported,
                when (session.intent) {
                    SetupIntent.ConfigureProvider -> SetupGuidance.ProviderNeedsSecureSignIn
                    SetupIntent.ConfigureAgent -> SetupGuidance.AgentEditingUnavailable
                    else -> SetupGuidance.CommandEditingUnavailable
                },
            )
        if (inputs.keys.any { it !in fields } || inputs.entries.any { !valid(it.key, it.value) }) {
            return SetupPlannerReply(sessionId, SetupPlannerState.NeedsInput, SetupGuidance.InvalidInput, fields)
        }
        for ((key, value) in inputs) {
            session.inputs[key] = if (value is List<*>) value.map { it as String }.toList() else value
        }
        val missing = fields.filter { it !in session.inputs }
        if (missing.isNotEmpty()) {
            return SetupPlannerReply(sessionId, SetupPlannerState.NeedsInput, SetupGuidance.ChooseValues, missing)
        }
        return SetupPlannerReply(
            sessionId,
            SetupPlannerState.Proposed,
            SetupGuidance.ReviewProposal,
            edits = listOf(edit(session)),
        )
    }

    public fun closeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    public fun dispose() {
        sessions.clear()
    }

    private fun valid(field: String, value: Any?): Boolean {
        if (field == "command") {
            if (value !is List<*> || value.isEmpty() || value.size > MAX_ARGS) return false
            if (value.any { it !is String || !safeText(it) }) return false
            // Positional secret flags cannot be recognised by text redaction once split into argv elements.
            val encoded = JsonArray(value.map { JsonPrimitive(it as String) }).toString()
            if (redaction.containsSecret(encoded)) return false
            return value.none { SECRET_ARG.containsMatchIn(it as String) }
        }
        if (value !is String || !safeText(value)) return false
        return when (field) {
            "url" -> isAllowedMcpUrl(value)
            "effect" -> value in EFFECTS
            "tool" -> value in TOOLS
            "model" -> MODEL.matches(value)
            else -> NAME.matches(value)
        }
    }

    private fun safeText(value: String): Boolean =
        value.isNotEmpty() &&
            value.length <= MAX_TEXT &&
            !CONTROL.containsMatchIn(value) &&
            !redaction.containsSecret(value) &&
            !value.contains(redaction.mask)

    private fun edit(session: PlannerSession): SetupEdit {
        val input = session.inputs
        return when (session.intent) {
            SetupIntent.ChooseModel -> SetupEdit(listOf("model"), JsonPrimitive(input["model"] as String))
            SetupIntent.DefaultAgent -> SetupEdit(listOf("default_agent"), JsonPrimitive(input["agent"] as String))
            SetupIntent.Permission ->
                SetupEdit(listOf("permission", input["tool"] as String), JsonPrimitive(input["effect"] as String))
            SetupIntent.ConnectRemoteMcp ->
                SetupEdit(
                    listOf("mcp", input["name"] as String),
                    buildJsonObject {
                        put("type", "remote")
                        put("url", input["url"] as String)
                        put("enabled", false)
                    },
                )
            SetupIntent.AddLocalMcp ->
                SetupEdit(
                    listOf("mcp", input["name"] as String),
                    buildJsonObject {
                        put("type", "local")
                        put("command", JsonArray((input["command"] as List<*>).map { JsonPrimitive(it as String) }))
                        put("enabled", false)
                    },
                )
            SetupIntent.RemoveMcp -> SetupEdit(listOf("mcp", input["name"] as String), remove = true)
            else -> error("Unsupported guided setup intent")
        }
    }

    private class PlannerSession(val intent: SetupIntent) {
        val inputs: MutableMap<String, Any?> = LinkedHashMap()
    }

    private companion object {
        const val MAX_SESSIONS = 8
        const val MAX_ARGS = 32
        const val MAX_TEXT = 2048
        val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
        val SECRET_ARG =
            Regex("(?:token|password|passwd|secret|api[_-]?key|authorization|bearer)", RegexOption.IGNORE_CASE)
        val MODEL = Regex("^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_./:#-]+$")
        val NAME = Regex("^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$")
        val EFFECTS = setOf("allow", "ask", "deny")
        val TOOLS = setOf(
            "*", "read", "edit", "bash", "glob", "grep", "list", "task", "webfetch", "websearch", "external_directory",
            "doom_loop",
        )

        fun fields(intent: SetupIntent): List<String>? =
            when (intent) {
                SetupIntent.ChooseModel -> listOf("model")
                SetupIntent.DefaultAgent -> listOf("agent")
                SetupIntent.Permission -> listOf("tool", "effect")
                SetupIntent.ConnectRemoteMcp -> listOf("name", "url")
                SetupIntent.AddLocalMcp -> listOf("name", "command")
                SetupIntent.RemoveMcp -> listOf("name")
                else -> null
            }
    }
}
