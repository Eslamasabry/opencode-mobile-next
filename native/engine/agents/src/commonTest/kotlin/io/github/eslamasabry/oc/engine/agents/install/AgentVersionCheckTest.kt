package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.scripts.AgentPhoneScripts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The version decision of a phone agent install (2026-11-12: Gemini CLI could not be installed on a busy emulator,
 * "the version that arrived is not the one the app expects", although its download had passed the pinned checksum).
 */
class AgentVersionCheckTest {
    private val catalog = AgentCatalog.builtIn

    private fun pin(id: String): String = catalog.byId(AgentId(id))!!.recipe!!.version

    /**
     * What each catalog agent's `--version` prints, with its pinned version in place. Sources: the CLIs' own code and
     * output (Gemini CLI 0.66.0 bundle: yargs `.version(getVersion())`, getVersion returns "0.66.0"; Goose 1.54.0 clap
     * `display_name = ""` gives a leading space; Codex, Claude Code, Kilo, Cline, Antigravity as their current releases
     * print it); Oh My Pi and fx passed this check on a device on 2026-10-08 (FQ-install), their exact text unrecorded.
     */
    private val realOutputs: Map<String, (String) -> String> =
        mapOf(
            "opencode2" to { v -> "$v\n" },
            "claude" to { v -> "$v (Claude Code)\n" },
            "codex" to { v -> "codex-cli $v\n" },
            "antigravity" to { v -> "$v\n" },
            "gemini" to { v -> "$v\n" },
            "qwen" to { v -> "$v\n" },
            "kilo" to { v -> "$v\n" },
            "cline" to { v -> "$v\n" },
            "goose" to { v -> " $v\n" },
            "omp-acp" to { v -> "$v\n" },
            "fx" to { v -> "$v\n" },
        )

    @Test
    fun `every catalog agent's real version output names its pin`() {
        val ids = catalog.agents.filter { it.recipe != null }.map { it.id.raw }.toSet()
        assertEquals(ids, realOutputs.keys, "an agent without its output format here")
        for ((id, output) in realOutputs) {
            val pin = pin(id)
            assertTrue(AgentVersionCheck.matches(output(pin), pin), "$id ${output(pin)}")
        }
    }

    @Test
    fun `Gemini CLI 0_66_0 prints exactly its pin`() {
        assertEquals("0.66.0", pin("gemini"))
        assertTrue(AgentVersionCheck.matches("0.66.0\n", "0.66.0"))
    }

    @Test
    fun `a neighbouring version, a longer one or a prefix of the pin is not the pin`() {
        assertFalse(AgentVersionCheck.matches("0.66.1", "0.66.0"))
        assertFalse(AgentVersionCheck.matches("0.66.0.1", "0.66.0"))
        assertFalse(AgentVersionCheck.matches("10.66.0", "0.66.0"))
        // The probe's pattern (Flutter's) refuses a v prefix; no catalog agent prints one.
        assertFalse(AgentVersionCheck.matches("v0.66.0", "0.66.0"))
        assertFalse(AgentVersionCheck.matches("", "0.66.0"))
        assertTrue(AgentVersionCheck.matches("warning: something\ncodex-cli 0.167.0", "0.167.0"))
    }

    @Test
    fun `the probe keeps only version-shaped text of another output`() {
        assertEquals("1.2.30", AgentVersionCheck.found("synthetic-secret 1.2.30"))
        assertEquals("0.68.0", AgentVersionCheck.found("0.68.0\n"))
        assertEquals("0.65.0", AgentVersionCheck.found("v0.65.0"))
        assertNull(AgentVersionCheck.found("command not found"))
        assertEquals(AgentVersionCheck.FOUND_MAX, AgentVersionCheck.found("1.2.3" + "a".repeat(100))!!.length)
    }

    @Test
    fun `a program that did not answer is NotStarted, not another version`() {
        assertEquals(AgentSetupFailure.NotStarted, AgentVersionCheck.failure(AgentVersionCheck.SLOW_LINE))
        assertEquals(
            AgentSetupFailure.NotStarted,
            AgentVersionCheck.failure("[oc] The agent did not pass its version check"),
        )
        assertEquals(
            "[oc] The agent did not answer its version check within 120 seconds",
            AgentVersionCheck.SLOW_LINE,
        )
        assertNull(AgentVersionCheck.reported(AgentVersionCheck.SLOW_LINE))
    }

    @Test
    fun `another version names both versions`() {
        val line = "[oc] The agent reported version 0.65.0; this app installs 0.66.0"
        assertEquals(AgentSetupFailure.Version, AgentVersionCheck.failure(line))
        assertEquals(ReportedVersion("0.65.0", "0.66.0"), AgentVersionCheck.reported(line))
        assertEquals(
            ReportedVersion("2.1.320", "2.1.319"),
            AgentVersionCheck.reported(
                AgentVersionCheck.OTHER_PREFIX + "2.1.320" + AgentVersionCheck.OTHER_MIDDLE + "2.1.319"
            ),
        )
    }

    @Test
    fun `a withdrawn pinned version maps to its own failure with the pin`() {
        val line = AgentVersionCheck.WITHDRAWN_PREFIX + "0.66.0"
        assertEquals(AgentSetupFailure.Withdrawn, AgentVersionCheck.failure(line))
        assertEquals(ReportedVersion(null, "0.66.0"), AgentVersionCheck.reported(line))
    }

    @Test
    fun `lines that are not the agent check's own are left to the other rules`() {
        for (line in listOf(
            null,
            "",
            "Paseo did not pass its version check. Run setup again.",
            "[oc] Download failed (curl exit 22): https://example.invalid/x.tgz",
            "[oc] The agent download did not match its checksum",
            "[oc] The agent reported version 0.65.0; this app installs 0.66.0; and more",
            "[oc] The agent reported version $(id); this app installs 0.66.0",
        )) {
            assertNull(AgentVersionCheck.failure(line), "$line")
            assertNull(AgentVersionCheck.reported(line), "$line")
        }
    }

    @Test
    fun `every agent's install fetches its pinned version and checks against it, never a latest`() {
        for (agent in catalog.agents.filter { it.recipe != null }) {
            val recipe = agent.recipe!!
            val script = AgentPhoneScripts.install(agent)
            assertTrue("oc_pin_version='${recipe.version}'" in script, agent.id.raw)
            for (artifact in recipe.artifacts.values) {
                assertTrue(recipe.version in artifact.url, "${agent.id.raw} ${artifact.url}")
                assertTrue("oc_url='${artifact.url}'" in script, agent.id.raw)
                assertTrue("oc_sha='${artifact.sha256}'" in script, agent.id.raw)
            }
            assertFalse("latest" in script.lowercase(), agent.id.raw)
            assertTrue("timeout ${AgentVersionCheck.PROBE_SECONDS}s" in script, agent.id.raw)
        }
    }
}
