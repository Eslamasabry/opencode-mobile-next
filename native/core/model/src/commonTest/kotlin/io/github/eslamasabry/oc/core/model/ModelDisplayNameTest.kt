package io.github.eslamasabry.oc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The model name rule, case for case with Flutter's `test/model_name_from_id_test.dart`, plus the owner's picker ids. */
class ModelDisplayNameTest {
    @Test
    fun `Flutter's cases read the same`() {
        val cases =
            listOf(
                "claude-sonnet-5" to "Sonnet 5",
                "anthropic/claude-sonnet-5" to "Sonnet 5",
                "claude-opus-4-1-20250805" to "Opus 4.1",
                "claude-haiku-3-5" to "Haiku 3.5",
                "claude-sonnet-4-5@20250929" to "Sonnet 4.5",
                "gpt-6-sol" to "GPT-6 Sol",
                "gpt-4-1-mini" to "GPT-4.1 Mini",
                "gpt-4o" to "GPT-4o",
                "gpt-5-2025-08-07" to "GPT-5",
                "glm-5.3" to "GLM-5.3",
                "o3" to "o3",
                "gemini-2.5-pro" to "Gemini 2.5 Pro",
                "kimi-k2" to "Kimi K2",
                "sonnet" to "Sonnet",
                "claude" to "Claude",
            )
        for ((id, name) in cases) assertEquals(name, ModelDisplayName.fromId(id), id)
    }

    @Test
    fun `MODELS-RAW the Claude Code ids in the owner's picker read as names`() {
        val cases =
            listOf(
                "claude-opus-5-5" to "Opus 5.5",
                "claude-fable-5" to "Fable 5",
                "claude-fable-5-1" to "Fable 5.1",
                "claude-haiku-4-5" to "Haiku 4.5",
                "claude-haiku-5-5" to "Haiku 5.5",
                "claude-opus-4-6" to "Opus 4.6",
                "claude-opus-4-7" to "Opus 4.7",
                // Flutter's rule has no case for the bracketed long-context tag: it reads exactly as Flutter reads it.
                "claude-opus-4-6[1m]" to "Opus 4 6[1m]",
                "claude-opus-4-7[1m]" to "Opus 4 7[1m]",
            )
        for ((id, name) in cases) assertEquals(name, ModelDisplayName.fromId(id), id)
    }

    @Test
    fun `MODELS-RAW a reported name wins, a blank or id-only name is read as words`() {
        assertEquals("Opus", ModelDisplayName.of("opus", "Opus"))
        assertEquals("Opus 5.5", ModelDisplayName.of("claude-opus-5-5", "claude-opus-5-5"))
        assertEquals("Opus 5.5", ModelDisplayName.of("claude-opus-5-5", " "))
        assertEquals("Opus 5.5", ModelDisplayName.of("claude-opus-5-5", null))
    }
}
