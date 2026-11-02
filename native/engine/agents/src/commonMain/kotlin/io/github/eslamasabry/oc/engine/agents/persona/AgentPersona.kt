package io.github.eslamasabry.oc.engine.agents.persona

/**
 * Who an agent is, defined once (port of lib/domain/agent_persona.dart): identity, maker and mark. Names and words
 * (name, strength ≤ 28 chars, about, needs) are `:ui:words` keys per [AgentPersonaId] (AgentPersonaWordsTest,
 * en + ar).
 *
 * [of] takes any id, alias or icon key the app sees and returns [AgentPersonaId.Other] with the agent's own mark (or a
 * neutral one) for anything it does not know.
 */
public enum class AgentPersonaId { ClaudeCode, Codex, Gemini, Kilo, Cline, OpenCode, Other }

/** Who makes the agent, for the "by …" line. */
public enum class AgentMaker { Anthropic, OpenAi, Google, OpenSource, Unknown }

public data class AgentPersona(
    val id: AgentPersonaId,
    /** The bundled single-colour mark (`marks/<slug>.svg`); empty for the neutral mark. */
    val markSlug: String,
    val maker: AgentMaker,
) {
    val hasMark: Boolean get() = markSlug.isNotEmpty()

    public companion object {
        public val ClaudeCode: AgentPersona = AgentPersona(AgentPersonaId.ClaudeCode, "claude", AgentMaker.Anthropic)
        public val Codex: AgentPersona = AgentPersona(AgentPersonaId.Codex, "codex", AgentMaker.OpenAi)
        public val Gemini: AgentPersona = AgentPersona(AgentPersonaId.Gemini, "gemini", AgentMaker.Google)
        public val Kilo: AgentPersona = AgentPersona(AgentPersonaId.Kilo, "kilocode", AgentMaker.OpenSource)
        public val Cline: AgentPersona = AgentPersona(AgentPersonaId.Cline, "cline", AgentMaker.OpenSource)
        public val OpenCode: AgentPersona = AgentPersona(AgentPersonaId.OpenCode, "opencode", AgentMaker.OpenSource)

        /** Anything else: no words of its own, a neutral mark. */
        public val Other: AgentPersona = AgentPersona(AgentPersonaId.Other, "", AgentMaker.Unknown)

        /** The six named personas, in the order a picker shows them. */
        public val all: List<AgentPersona> = listOf(ClaudeCode, Codex, Gemini, Kilo, Cline, OpenCode)

        private val aliases: Map<String, AgentPersona> = mapOf(
            "claude" to ClaudeCode, "claude code" to ClaudeCode, "claude-code" to ClaudeCode,
            "claude_code" to ClaudeCode, "claudecode" to ClaudeCode,
            "codex" to Codex, "openai-codex" to Codex,
            "gemini" to Gemini, "gemini cli" to Gemini, "gemini-cli" to Gemini,
            "kilo" to Kilo, "kilocode" to Kilo, "kilo-code" to Kilo,
            "cline" to Cline,
            "opencode" to OpenCode, "opencode2" to OpenCode, "opencode1" to OpenCode, "open-code" to OpenCode,
        )

        /** lib/domain/mark_catalog.dart `_agentMarks` (agent icon key → bundled mark slug). */
        public val agentMarks: Map<String, String> = mapOf(
            "opencode" to "opencode", "opencode2" to "opencode", "claude" to "claude", "codex" to "codex",
            "gemini" to "gemini", "qwen" to "qwen", "kilo" to "kilocode", "cline" to "cline", "goose" to "goose",
            "pi" to "pi", "omp-acp" to "pi", "copilot" to "copilot",
        )

        public fun agentMarkSlug(iconKey: String): String? = agentMarks[iconKey.lowercase()]

        /** The persona for [agentIdOrKey]; [Other] (with the agent's own mark when one is bundled) otherwise. */
        public fun of(agentIdOrKey: String): AgentPersona {
            val key = agentIdOrKey.trim().lowercase()
            aliases[key]?.let { return it }
            val mark = agentMarkSlug(key) ?: return Other
            return AgentPersona(AgentPersonaId.Other, mark, AgentMaker.Unknown)
        }
    }
}
