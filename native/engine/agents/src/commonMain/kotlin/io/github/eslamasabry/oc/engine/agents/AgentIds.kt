package io.github.eslamasabry.oc.engine.agents

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** A catalog agent id (`claude`, `codex`, `omp-acp`…) or [OPENCODE] for the server's own chat. Opaque; never parsed. */
@Serializable
@JvmInline
public value class AgentId(public val raw: String) {
    override fun toString(): String = raw

    public companion object {
        /** The chat choice that is the connected OpenCode server, not a phone agent (Flutter `openCodeChatAgentId`). */
        public val OPENCODE: AgentId = AgentId("opencode")
        public val CLAUDE: AgentId = AgentId("claude")
    }
}

/**
 * The profile whose Linux home holds the phone agents (STATE-DATA-05). Several protocol profiles can alias one owner;
 * every [io.github.eslamasabry.oc.engine.agents.host.AgentHost] call is keyed by the owner, never an alias.
 */
public typealias OwnerId = ProfileId

/** The agents backend pseudo-profile `"<owner>.agents"` (04 §12.1); never saved in `oc.profiles`. */
public fun OwnerId.agentsBackend(): ProfileId = ProfileId(raw + AgentKeys.AGENTS_SUFFIX)
