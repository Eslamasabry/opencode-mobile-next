package io.github.eslamasabry.oc.engine.integrations.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Agent rows draw the same monochrome bundled mark as Flutter (lib/domain/mark_catalog.dart `_agentMarks`). */
class AgentMarksTest {
    private val flutterAgentMarks =
        mapOf(
            "opencode" to "opencode",
            "opencode2" to "opencode",
            "claude" to "claude",
            "codex" to "codex",
            "gemini" to "gemini",
            "qwen" to "qwen",
            "kilo" to "kilocode",
            "cline" to "cline",
            "goose" to "goose",
            "pi" to "pi",
            "omp-acp" to "pi",
            "copilot" to "copilot",
        )

    @Test
    fun `B-116 Antigravity draws its own single-colour mark, never Google's G`() {
        assertEquals("antigravity", ProviderMarks.agentSlug("antigravity"))
        assertEquals("antigravity", ProviderMarks.agentSlug("agy"))
        assertTrue("antigravity" in ProviderMarks.slugs)
        assertEquals(null, ProviderMarks.agentColorFile("antigravity"))
    }

    @Test
    fun `B-095 every Flutter agent mark maps to the same bundled slug`() {
        for ((key, slug) in flutterAgentMarks) {
            assertEquals(slug, ProviderMarks.agentSlug(key), key)
            assertTrue(slug in ProviderMarks.slugs, "$slug is bundled")
        }
    }
}
