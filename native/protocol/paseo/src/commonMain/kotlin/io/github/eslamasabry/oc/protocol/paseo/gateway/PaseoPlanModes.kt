package io.github.eslamasabry.oc.protocol.paseo.gateway

/**
 * The app's Plan mode ("plan") as each agent kind names its own read-only mode. The helper switches a conversation's
 * mode only to a name the agent lists, and Codex lists read-only, auto, auto-review and full-access: before, a Codex
 * conversation in Plan ran in Codex's normal mode. An agent with no such mode is refused in Plan (never run silently in
 * its normal mode): [planMode] answers null.
 */
internal object PaseoPlanModes {
    const val APP_PLAN: String = "plan"

    /** Per agent kind, its own read-only or plan mode by the names it lists, first match wins. */
    private val TABLE: Map<String, List<String>> =
        mapOf(
            "codex" to listOf("read-only"),
            "claude" to listOf("plan"),
            "opencode" to listOf("plan"),
            "copilot" to listOf("plan"),
            "pi" to emptyList(),
        )

    /** Any other agent (ACP agents): the plan or read-only names agents use. */
    private val GENERIC: List<String> = listOf("plan", "read-only", "readonly", "read_only")

    /** The mode to send for the app's Plan, from the modes [provider]'s agent [listed]; null when it has none. */
    fun planMode(provider: String?, listed: List<String>): String? {
        if (APP_PLAN in listed) return APP_PLAN
        return (TABLE[provider] ?: GENERIC).firstOrNull { it in listed }
    }
}
