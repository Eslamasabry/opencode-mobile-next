package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Port of lib/domain/setup_planner.dart: structured local guidance, not a model or a server agent
// session.

public enum class SetupIntent {
    ChooseModel,
    DefaultAgent,
    Permission,
    ConnectRemoteMcp,
    AddLocalMcp,
    RemoveMcp,
    ConfigureProvider,
    ConfigureAgent,
    ConfigureCommand,
}

public enum class SetupPlannerState {
    NeedsInput,
    Proposed,
    Unsupported,
}

/**
 * All copy is fixed and redacted; questions are never retained or echoed. A proposal is never a
 * server write.
 */
public data class SetupPlannerReply(
    val sessionId: String,
    val state: SetupPlannerState,
    val guidance: String,
    val requiredInputs: List<String> = emptyList(),
    val edits: List<SetupEdit> = emptyList(),
)

/**
 * Memory-only guided sessions: no network, persistence, credentials or tools. `question` is
 * deliberately ignored; the UI asks for an explicit intent and structured inputs ("Guided setup (on
 * this device)"). At most eight sessions; a ninth discards the oldest. Dispose on profile switch or
 * deletion; never a singleton.
 */
public class SetupPlanner(private val redactor: Redactor) {
    private class Session(val intent: SetupIntent) {
        val inputs = LinkedHashMap<String, JsonElement>()
    }

    private val sessions = LinkedHashMap<String, Session>()
    private var nextId = 0
    private var disposed = false

    private fun reply(
        sessionId: String,
        state: SetupPlannerState,
        guidance: String,
        requiredInputs: List<String> = emptyList(),
        edits: List<SetupEdit> = emptyList(),
    ) =
        SetupPlannerReply(
            sessionId,
            state,
            redactor.redact(guidance),
            requiredInputs.toList(),
            edits.toList(),
        )

    @Suppress("UNUSED_PARAMETER")
    public fun start(
        intent: SetupIntent,
        question: String? = null,
        inputs: Map<String, JsonElement> = emptyMap(),
    ): SetupPlannerReply {
        if (disposed)
            return reply(
                "",
                SetupPlannerState.Unsupported,
                "This guided setup session has ended. Start a new one.",
            )
        if (sessions.size >= MAX_SESSIONS) sessions.remove(sessions.keys.first())
        val id = "setup-${++nextId}"
        sessions[id] = Session(intent)
        return continueSession(id, inputs = inputs)
    }

    @Suppress("UNUSED_PARAMETER")
    public fun continueSession(
        sessionId: String,
        question: String? = null,
        inputs: Map<String, JsonElement> = emptyMap(),
    ): SetupPlannerReply {
        val session =
            sessions[sessionId]
                ?: return reply(
                    "",
                    SetupPlannerState.Unsupported,
                    "This guided setup session has ended. Start a new one.",
                )
        val fields =
            fields(session.intent)
                ?: return reply(
                    sessionId,
                    SetupPlannerState.Unsupported,
                    when (session.intent) {
                        SetupIntent.ConfigureProvider ->
                            "Provider setup requires a separate secure sign-in flow. Do not enter credentials in the setup assistant."
                        SetupIntent.ConfigureAgent ->
                            "Creating or editing agents is not available in guided setup. Choose an existing default agent or use " +
                                "the server configuration tools."
                        else ->
                            "Creating or editing commands is not available in guided setup. Use the server configuration tools."
                    },
                )
        if (inputs.keys.any { it !in fields } || inputs.any { (k, v) -> !valid(k, v) }) {
            return reply(
                sessionId,
                SetupPlannerState.NeedsInput,
                "Use the requested fields without credentials. URLs must not contain a password, query, or fragment. " +
                    "No change has been proposed.",
                requiredInputs = fields,
            )
        }
        session.inputs.putAll(inputs)
        val missing = fields.filter { it !in session.inputs }
        if (missing.isNotEmpty()) {
            return reply(
                sessionId,
                SetupPlannerState.NeedsInput,
                "Choose the requested values to prepare a proposed change. Guided setup runs on this device and does not " +
                    "interpret free-form questions.",
                requiredInputs = missing,
            )
        }
        return reply(
            sessionId,
            SetupPlannerState.Proposed,
            "Review this proposed change. Nothing has been applied. Applying requires server support and your confirmation. " +
                "MCP proposals start disabled. Local MCP commands run on your server when enabled.",
            edits = listOf(edit(session)),
        )
    }

    public fun closeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    public fun dispose() {
        disposed = true
        sessions.clear()
    }

    private fun valid(field: String, value: JsonElement): Boolean {
        if (field == "command") {
            if (value !is JsonArray || value.isEmpty() || value.size > 32) return false
            val parts = value.map {
                (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return false
            }
            if (parts.any { !safeText(it) }) return false
            // Positional secret flags cannot be recognised by text redaction once split into argv
            // elements.
            if (redactor.containsSecret(value.toString())) return false
            return parts.none { SECRET_ARG.containsMatchIn(it) }
        }
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return false
        if (!safeText(text)) return false
        return when (field) {
            "url" -> SetupUri.isSafeMcpUrl(text)
            "effect" -> text in EFFECTS
            "tool" -> text in TOOLS
            "model" -> MODEL.matches(text)
            else -> NAME.matches(text)
        }
    }

    private fun safeText(value: String): Boolean =
        value.isNotEmpty() &&
            value.length <= 2048 &&
            !CONTROL.containsMatchIn(value) &&
            !redactor.containsSecret(value) &&
            !value.contains(Redactor.MASK)

    private fun edit(session: Session): SetupEdit {
        val input = session.inputs
        fun text(key: String) = (input.getValue(key) as JsonPrimitive).content
        return when (session.intent) {
            SetupIntent.ChooseModel -> SetupEdit(listOf("model"), input["model"])
            SetupIntent.DefaultAgent -> SetupEdit(listOf("default_agent"), input["agent"])
            SetupIntent.Permission -> SetupEdit(listOf("permission", text("tool")), input["effect"])
            SetupIntent.ConnectRemoteMcp ->
                SetupEdit(
                    listOf("mcp", text("name")),
                    buildJsonObject {
                        put("type", "remote")
                        put("url", input.getValue("url"))
                        put("enabled", false)
                    },
                )
            SetupIntent.AddLocalMcp ->
                SetupEdit(
                    listOf("mcp", text("name")),
                    buildJsonObject {
                        put("type", "local")
                        put("command", input.getValue("command"))
                        put("enabled", false)
                    },
                )
            SetupIntent.RemoveMcp -> SetupEdit(listOf("mcp", text("name")), remove = true)
            else -> error("Unsupported guided setup intent")
        }
    }

    private companion object {
        const val MAX_SESSIONS = 8
        val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
        val SECRET_ARG =
            Regex(
                "(?:token|password|passwd|secret|api[_-]?key|authorization|bearer)",
                RegexOption.IGNORE_CASE,
            )
        val EFFECTS = setOf("allow", "ask", "deny")
        val TOOLS =
            setOf(
                "*",
                "read",
                "edit",
                "bash",
                "glob",
                "grep",
                "list",
                "task",
                "webfetch",
                "websearch",
                "external_directory",
                "doom_loop",
            )
        val MODEL = Regex("^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_./:#-]+$")
        val NAME = Regex("^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$")

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
