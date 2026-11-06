package io.github.eslamasabry.oc.engine.integrations.catalog

import kotlinx.coroutines.test.runTest
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
            "antigravity" to "google",
            "agy" to "google",
            "qwen" to "qwen",
            "kilo" to "kilocode",
            "cline" to "cline",
            "goose" to "goose",
            "pi" to "pi",
            "omp-acp" to "pi",
            "copilot" to "copilot",
        )

    @Test
    fun `B-095 Antigravity row gets the bundled google mark, not an empty square`() {
        assertEquals("google", ProviderMarks.agentSlug("antigravity"))
        assertEquals("google", ProviderMarks.agentSlug("agy"))
        assertEquals("google", ProviderMarks.agentSlug(" Antigravity "))
    }

    @Test
    fun `B-095 every Flutter agent mark maps to the same bundled slug`() {
        for ((key, slug) in flutterAgentMarks) {
            assertEquals(slug, ProviderMarks.agentSlug(key), key)
            assertTrue(slug in ProviderMarks.slugs, "$slug is bundled")
        }
    }

    @Test
    fun `B-095 the agent loader reads the bundled google mark for Antigravity`() = runTest {
        val asked = mutableListOf<String>()
        val host =
            object : ProviderLogoHost {
                override suspend fun fetch(url: String): ProviderLogoResponse? = null

                override suspend fun bundled(slug: String): String? {
                    asked += slug
                    return "<svg/>"
                }

                override suspend fun read(name: String): ProviderLogoFile? = null

                override suspend fun write(name: String, text: String) = Unit

                override suspend fun delete(name: String) = Unit
            }
        val logos =
            ProviderLogos(
                host,
                object : kotlin.time.Clock {
                    override fun now() = kotlin.time.Instant.fromEpochMilliseconds(0)
                },
            )
        assertEquals("<svg/>", logos.agentSvg("antigravity"))
        assertEquals(listOf("google"), asked)
    }
}
