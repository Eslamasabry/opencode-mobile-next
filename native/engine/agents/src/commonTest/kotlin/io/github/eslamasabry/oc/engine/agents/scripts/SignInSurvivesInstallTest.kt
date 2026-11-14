package io.github.eslamasabry.oc.engine.agents.scripts

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An agent's sign-in lives in the profile's own folders (`/home/oc/.oc-profiles/<id>/claude`, `.../codex`, the
 * `~/.claude` link to it): outside every directory an install, update, rollback or removal replaces. No script of
 * those operations names such a path, so none can move, delete or rewrite a credential.
 */
class SignInSurvivesInstallTest {
    private val forbidden =
        listOf(".oc-profiles", ".credentials", "CLAUDE_CONFIG_DIR", "CODEX_HOME", "/.claude", "/.codex", "auth.json")

    private fun assertClean(name: String, script: String) {
        for (word in forbidden) assertFalse(word in script, "$name touches $word")
    }

    @Test
    fun `AGENT-SIGNIN no install, check, rollback or removal script names an agent's sign-in folder`() {
        val catalog = AgentCatalog.builtIn
        var scripts = 0
        for (agent in catalog.agents) {
            if (agent.recipe == null) continue
            // An agent the phone host does not install (OpenCode 2) has no such script: skipped.
            listOf<Pair<String, () -> String>>(
                "check ${agent.id}" to { AgentPhoneScripts.check(agent) },
                "install ${agent.id}" to { AgentPhoneScripts.install(agent) },
                "removal ${agent.id}" to { AgentRemovalScripts.remove(agent, "0123456789abcdef0123456789abcdef") },
            ).forEach { (name, make) ->
                val script =
                    try {
                        make()
                    } catch (_: IllegalArgumentException) {
                        null
                    } catch (_: IllegalStateException) {
                        null
                    }
                if (script != null) {
                    assertClean(name, script)
                    scripts++
                }
            }
        }
        val claude = catalog.byId(AgentId.CLAUDE)!!
        assertClean("rollback", AgentPhoneScripts.usePreviousVersion(claude, AgentPhoneScripts.previousClaudeVersion))
        assertClean("claude component install", ClaudeScripts.install)
        assertClean("node install", PaseoPhoneScripts.nodeInstall)
        assertClean("paseo check", PaseoPhoneScripts.check)
        assertTrue(scripts > 15, "only $scripts scripts were built")
    }
}
