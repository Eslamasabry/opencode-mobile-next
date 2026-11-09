package io.github.eslamasabry.oc.feature.agents

import io.github.eslamasabry.oc.engine.agents.persona.AgentPersona
import io.github.eslamasabry.oc.engine.integrations.catalog.ProviderMarks
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every agent mark resolves to a bundled colour file or to a deliberate single-colour mark (owner 2026-11-09). */
class AgentMarkColorFilesTest {
    private val marks = File(System.getProperty("user.dir"), "../../androidApp/src/main/assets/marks")

    /** Brands drawn in the text colour on purpose: single-colour by design, or no colour version exists. */
    private val deliberateMono = setOf("opencode", "codex", "kilocode", "cline", "goose", "pi")

    @Test
    fun `B-AGENTS-290 every persona mark slug has a colour file or is a deliberate mono mark`() {
        val slugs = AgentPersona.agentMarks.values.toSet()
        assertTrue(slugs.isNotEmpty())
        for (slug in slugs) {
            val colour = ProviderMarks.colorFile(slug)
            if (colour != null) {
                val file = File(marks, "$colour.svg")
                assertTrue(file.isFile, "$slug: $colour.svg is bundled")
                val text = file.readText()
                assertTrue(!text.contains("currentColor"), "$slug: a colour file carries its own colours")
                assertTrue(text.length < 64 * 1024, "$slug: fits the logo size cap")
            } else {
                assertTrue(slug in deliberateMono, "$slug has neither a colour file nor a deliberate mono entry")
            }
            assertTrue(File(marks, "$slug.svg").isFile, "$slug: the mono fallback is bundled")
        }
    }

    @Test
    fun `B-AGENTS-291 the agent ids map to the colour file of their brand`() {
        assertTrue(ProviderMarks.agentColorFile("claude") == "claude-color")
        assertTrue(ProviderMarks.agentColorFile("antigravity") == "google-color")
        assertTrue(ProviderMarks.agentColorFile("opencode") == null)
        assertTrue(ProviderMarks.agentColorFile("codex") == null)
    }
}
