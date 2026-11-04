package io.github.eslamasabry.oc.engine.agents.probe

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor

/** Which private script to run. */
public enum class AuthScriptAction(internal val wire: String) {
    Probe("probe"),
    SignOut("logout"),
}

/**
 * The app-authored private sign-in status scripts (port of `AgentPhoneScripts.authProbe` /
 * `signOut` / `supportsSignOut`, lib/builtin/agents/agent_scripts.dart, and the profile environment
 * line of lib/builtin/agents/phone_agents_host.dart). The script runs as uid 1000 in the profile's
 * home; raw agent output stays in its bounded Python child (8 s per CLI call) and only the narrow
 * projection (`AuthProjectionJson`) is printed. An unknown status protocol is an explicit error,
 * never a guessed sign-out:
 *
 * - Claude: `auth status --json`; an explicit `loggedIn:false` wins over a zero exit; only `email`
 *   is the account.
 * - Codex: `login status` on stderr ("Logged in using …" exit 0, "Not logged in" exit 1); anything
 *   else is invalid.
 * - Kilo: whether its credential store holds an oauth/api/wellknown entry. Cline: the chosen
 *   provider's key or sign-in.
 * - fx: `status --json` credential source; expired is `signInExpired`, an unknown future source is
 *   invalid.
 * - Sign-out runs the agent's logout and then its status: an exit code is never proof.
 *
 * The native deadline is eight seconds for every agent; both Android admission copies pin this
 * exact script by digest.
 */
public object AgentAuthScripts {
    /** Null when the agent has no usable pinned installation (Dart `_recipe` throws). */
    public fun script(agent: AgentDescriptor, action: AuthScriptAction): String? {
        val recipe = agent.recipe ?: return null
        if (agent.availability == AgentAvailability.Hidden || !VERSION.matches(recipe.version))
            return null
        if (
            recipe.artifacts.values.any { a ->
                a.archiveMember?.let { !MEMBER.matches(it) } == true
            }
        )
            return null
        return "set -eu\n" +
            "command -v python3 >/dev/null 2>&1 || {\n" +
            "  printf '%s\\n' '{\"state\":\"error\",\"error\":\"hostUnavailable\"}'; exit 0\n" +
            "}\n" +
            "python3 - ${quote(agent.id.raw)} ${quote(recipe.executable)} ${action.wire} <<'OC_AUTH_PROBE'\n" +
            AUTH_PROBE_BODY +
            "\n" +
            "OC_AUTH_PROBE\n"
    }

    /** Only Claude and fx have a sign-out the bridge can run. */
    public fun supportsSignOut(agent: AgentId): Boolean =
        agent == AgentId.CLAUDE || agent.raw == "fx"

    /**
     * The script as sent to the native side: the profile's native environment line, then [script].
     */
    public fun forProfile(profile: ProfileId, script: String): String {
        val home = "/home/oc/.oc-profiles/${profile.raw}"
        return "export HOME='$home' CLAUDE_CONFIG_DIR='$home/claude' CODEX_HOME='$home/codex' " +
            "DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1\n" +
            script
    }

    /** POSIX single quotes (Dart `_quote`). */
    internal fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

    private val VERSION = Regex("^[0-9][a-zA-Z0-9.+_-]*$")
    private val MEMBER = Regex("^(?:\\./)?[a-zA-Z0-9_./+-]+$")
}
