package io.github.eslamasabry.oc.feature.chatcomposer

import io.github.eslamasabry.oc.feature.chatcomposer.presentation.EffortWord
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelChipLabel
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelFacts
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelLabel
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Port of `test/model_name_from_id_test.dart` and `test/model_presentation_new_test.dart`. */
class ModelChipLabelTest {
    @Test
    fun `B-KIT-057 model ids are tidied to a short readable name`() {
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
                // Runtime-prefixed ids match the model's own id (plan 09 §10.3).
                "kilo/google/gemini-2.5-pro" to "Gemini 2.5 Pro",
                "some-model-latest" to "Some Model",
            )
        for ((id, name) in cases) assertEquals(name, ModelNames.fromId(id), id)
    }

    @Test
    fun `B-CHAT-066 the chip names the model from its id while the catalog loads`() {
        val facts = ModelFacts(modelId = "anthropic/claude-sonnet-5", catalogLoading = true)
        assertEquals(ModelLabel.Named("Sonnet 5"), ModelChipLabel.label(facts))
        assertEquals(ModelLabel.Named("Sonnet 5"), ModelChipLabel.label(facts.copy(catalogLoading = false)))
    }

    @Test
    fun `B-CHAT-066 a name seen before wins over the derived one`() {
        val facts = ModelFacts(modelId = "claude-sonnet-5", knownName = "Claude Sonnet 5", catalogLoading = true)
        assertEquals(ModelLabel.Named("Claude Sonnet 5"), ModelChipLabel.label(facts))
    }

    @Test
    fun `B-CHAT-066 the catalog name replaces it once read and a raw id never shows`() {
        val facts = ModelFacts(modelId = "claude-sonnet-4", catalogName = "Claude Sonnet 4", knownName = "Old name")
        assertEquals(ModelLabel.Named("Claude Sonnet 4"), ModelChipLabel.label(facts))
        val blank = ModelFacts(modelId = "anthropic/claude-sonnet-4", catalogName = "  ")
        val named = ModelChipLabel.label(blank) as ModelLabel.Named
        assertFalse("/" in named.name || "claude-" in named.name, named.name)
    }

    @Test
    fun `P09-10_3 with no model picked the chip shows the server default, Loading, or no model signed in`() {
        assertEquals(ModelLabel.Named("Sonnet 5"), ModelChipLabel.label(ModelFacts(serverDefaultName = "Sonnet 5")))
        assertEquals(ModelLabel.Loading, ModelChipLabel.label(ModelFacts(catalogLoading = true)))
        assertEquals(ModelLabel.Loading, ModelChipLabel.label(ModelFacts()))
        assertEquals(ModelLabel.NoModelSignedIn, ModelChipLabel.label(ModelFacts(signedIn = false)))
    }

    @Test
    fun `B-CHAT-067 a model out in the last 30 days is new`() {
        val now = Instant.parse("2026-09-23T00:00:00Z")
        assertTrue(ModelChipLabel.isNew(Instant.parse("2026-09-22T00:00:00Z"), now))
        assertFalse(ModelChipLabel.isNew(Instant.parse("2026-07-24T00:00:00Z"), now))
        assertFalse(ModelChipLabel.isNew(null, now))
        // A catalog date in the future is not "new", it is wrong.
        assertFalse(ModelChipLabel.isNew(Instant.parse("2026-12-01T00:00:00Z"), now))
    }

    @Test
    fun `B-CHAT-067 effort levels read as words, only when there is a real choice`() {
        assertEquals(EffortWord.Known.ExtraHigh, ModelChipLabel.effort("xhigh"))
        assertEquals(EffortWord.Known.None, ModelChipLabel.effort("none"))
        assertEquals(EffortWord.Known.Max, ModelChipLabel.effort("MAX"))
        assertEquals(EffortWord.Other("fast"), ModelChipLabel.effort("fast"))
        val now = Instant.parse("2026-09-23T00:00:00Z")
        val chip = ModelChipLabel.chip(ModelFacts(modelId = "o3", effort = "high", effortIsChoice = false), now)
        assertNull(chip.effort)
        val chosen = ModelChipLabel.chip(ModelFacts(modelId = "o3", effort = "high", effortIsChoice = true), now)
        assertEquals(EffortWord.Known.High, chosen.effort)
    }
}
