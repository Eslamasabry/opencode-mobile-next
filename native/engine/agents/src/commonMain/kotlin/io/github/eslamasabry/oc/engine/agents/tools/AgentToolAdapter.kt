package io.github.eslamasabry.oc.engine.agents.tools

import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Phone tools and the per-agent adapters that carry them (Flutter `lib/domain/agent_tools/agent_tool_adapter.dart`,
 * B-AGENT-029). A phone tool is defined once (an app-owned MCP server and its tools); each agent differs only in how it
 * names an MCP tool, where it registers servers, which Linux user runs it and how its own question tool is answered.
 * Every caller asks [AgentToolAdapters] instead of naming an agent.
 */
public class PhoneTool(public val server: String, tools: List<String>) {
    public val tools: ImmutableList<String> = tools.toImmutableList()

    public companion object {
        /** Agent cards: native choice, form, confirm, report and photo cards, plus the connector search. */
        public val CARDS: PhoneTool = PhoneTool("oc-ui", listOf("show", "find_connectors"))
    }
}

/** How an agent names tool `t` of MCP server `s` in its transcript (from captured runtime fixtures, never guessed). */
public enum class McpToolNaming {
    /** `mcp__oc-ui__show` (Claude Code). */
    McpPrefixed,

    /** `oc-ui_show` (OpenCode 2). */
    ServerUnderscore,
}

/** Where and how an agent keeps its MCP server list; one installer writer per format. */
public enum class McpConfigFormat {
    /** `claude mcp add --scope user` in the profile's CLAUDE_CONFIG_DIR. */
    ClaudeCli,

    /** OpenCode 2: `mcp.servers.<name>` in its isolated config directory. */
    OpenCodeV2,
}

/** Which Linux user inside the in-app Ubuntu runs the agent. */
public enum class AgentRunUser {
    /** uid 1000 with the profile's own HOME (Paseo agents). */
    AgentUser,

    /** The shared root OpenCode server. */
    Root,
}

/** How an agent's own multi-question tool expects its answers keyed. */
public enum class NativeAnswerKeys {
    /** By the question text (Claude Code's AskUserQuestion). */
    QuestionText,

    /** By the prompt header (Pi and Oh My Pi's ask_user). */
    Header,
}

/** Everything the app needs to give one agent the phone tools. */
public class AgentToolAdapter private constructor(
    /** Stable id; it names the agent's helper directory (`.oc-genui/<id>`), so it never changes once shipped. */
    public val id: String,
    public val displayName: String,
    /** The native agent catalog id ([io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog]); null when absent. */
    public val catalogAgent: AgentId?,
    /** The Paseo provider id of this agent's chats; null for OpenCode. */
    public val paseoProvider: String? = null,
    /** Null when the agent cannot call MCP tools. */
    public val naming: McpToolNaming? = null,
    /** Null when the app cannot register MCP servers for this agent yet. */
    public val config: McpConfigFormat? = null,
    public val runsAs: AgentRunUser = AgentRunUser.AgentUser,
    /** Recorded runtime evidence qualifies the card transport; registration alone never sets this. */
    public val cardsQualified: Boolean = false,
    /** The display-only card tool is pre-allowed in the agent's own permission settings. */
    public val preAllowsCards: Boolean = false,
    public val answerKeys: NativeAnswerKeys? = null,
    /** The agent's own question tool ("AskUserQuestion"); null accepts its question requests as sent. */
    public val questionToolName: String? = null,
    /** The agent's plan-approval tool ("ExitPlanMode"); null when none is answered. */
    public val planToolName: String? = null,
) {
    /** The app answers this agent's own questions with its sheet. */
    public val answersNativeQuestions: Boolean get() = answerKeys != null

    /** The app can register [PhoneTool]s for this agent. */
    public val supportsTools: Boolean get() = naming != null && config != null

    /** The name this agent gives [tool] of [phoneTool]; null without MCP. */
    public fun toolName(phoneTool: PhoneTool, tool: String): String? = when (naming) {
        McpToolNaming.McpPrefixed -> "mcp__${phoneTool.server}__$tool"
        McpToolNaming.ServerUnderscore -> "${phoneTool.server}_$tool"
        null -> null
    }

    public val cardShowName: String? get() = toolName(PhoneTool.CARDS, "show")

    public val connectorSearchName: String? get() = toolName(PhoneTool.CARDS, "find_connectors")

    override fun toString(): String = "AgentToolAdapter($id)"

    public companion object {
        public val CLAUDE: AgentToolAdapter = AgentToolAdapter(
            id = "claude",
            displayName = "Claude Code",
            catalogAgent = AgentId.CLAUDE,
            paseoProvider = "claude",
            naming = McpToolNaming.McpPrefixed,
            config = McpConfigFormat.ClaudeCli,
            cardsQualified = true,
            preAllowsCards = true,
            answerKeys = NativeAnswerKeys.QuestionText,
            questionToolName = "AskUserQuestion",
            planToolName = "ExitPlanMode",
        )

        public val OPEN_CODE_2: AgentToolAdapter = AgentToolAdapter(
            id = "openCode2",
            displayName = "OpenCode 2",
            catalogAgent = AgentId("opencode2"),
            naming = McpToolNaming.ServerUnderscore,
            config = McpConfigFormat.OpenCodeV2,
            runsAs = AgentRunUser.Root,
        )

        /** Pi: no MCP; its ask_user questions are answered by header. Not in the native catalog. */
        public val PI: AgentToolAdapter = AgentToolAdapter(
            id = "pi",
            displayName = "Pi",
            catalogAgent = null,
            paseoProvider = "pi",
            answerKeys = NativeAnswerKeys.Header,
        )

        public val OH_MY_PI: AgentToolAdapter = AgentToolAdapter(
            id = "omp",
            displayName = "Oh My Pi",
            catalogAgent = AgentId("omp-acp"),
            paseoProvider = "omp",
            answerKeys = NativeAnswerKeys.Header,
        )

        /** The former enum values: agents the phone tools can be registered for. */
        public val VALUES: ImmutableList<AgentToolAdapter> = persistentListOf(CLAUDE, OPEN_CODE_2)
    }
}

/** The registry every caller asks; no other file names an agent. */
public object AgentToolAdapters {
    public val all: ImmutableList<AgentToolAdapter> = persistentListOf(
        AgentToolAdapter.CLAUDE,
        AgentToolAdapter.OPEN_CODE_2,
        AgentToolAdapter.PI,
        AgentToolAdapter.OH_MY_PI,
    )

    public val withTools: List<AgentToolAdapter> get() = all.filter { it.supportsTools }

    public fun byId(id: String): AgentToolAdapter? = all.firstOrNull { it.id == id }

    /** The adapter of a Paseo chat's provider; null for an unknown provider or a non-string (daemon data is untrusted). */
    public fun forPaseoProvider(provider: Any?): AgentToolAdapter? {
        if (provider !is String) return null
        return all.firstOrNull { it.paseoProvider == provider }
    }

    public fun forOpenCode(): AgentToolAdapter = AgentToolAdapter.OPEN_CODE_2

    /** Every transcript name of the card `show` tool, across agents. */
    public val cardShowNames: Set<String> by lazy { all.mapNotNull { it.cardShowName }.toSet() }

    /** Exact tool identity of a card `show` call. */
    public fun isAgentCardTool(id: String): Boolean = id.trim() in cardShowNames

    /** Exact tool identity of the connector search; callers still verify runtime/provider provenance. */
    public fun isConnectorSearchTool(id: String): Boolean = all.any { it.connectorSearchName == id.trim() }
}
