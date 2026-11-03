package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.composer.presentation.ModelPicker
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** QA wave 1 B3: 372 flat chips with repeated names and no provider. */
class ModelPickerTest {
    private fun m(provider: String, name: String, id: String = name.lowercase(), providerName: String = provider.uppercase()) =
        ComposerModel(provider, id, name, true, providerName = providerName)

    private val glmZai = m("zai-coding-plan", "GLM-5.3-Flash", providerName = "Z.AI Coding Plan")
    private val glmOr = m("openrouter", "GLM-5.3-Flash", providerName = "OpenRouter")
    private val terra = m("openai", "GPT-6.1 Terra", providerName = "OpenAI")
    private val fable = m("anthropic", "Claude Fable 5.1", providerName = "Anthropic")
    private val all = listOf(glmOr, terra, glmZai, fable)

    @Test
    fun `B3 QA wave1 models are grouped under their provider's name with the current provider first`() {
        val sections = ModelPicker.sections(ComposerModels(choices = all, selected = glmZai))
        assertEquals(listOf("Z.AI Coding Plan", "Anthropic", "OpenAI", "OpenRouter"), sections.map { it.providerName })
        val current = sections.flatMap { it.rows }.filter { it.current }
        assertEquals(listOf(glmZai), current.map { it.model }, "only the session's model is marked, not its twin")
    }

    @Test
    fun `B3 QA wave1 recent picks come first and search matches name, id or provider`() {
        val models = ComposerModels(choices = all, selected = terra, recent = listOf(glmZai))
        val sections = ModelPicker.sections(models)
        assertNull(sections.first().providerName, "the Recent group leads")
        assertEquals(listOf(glmZai), sections.first().rows.map { it.model })
        val zai = ModelPicker.sections(models, "z.ai")
        assertEquals(listOf(glmZai), zai.flatMap { it.rows }.map { it.model }.distinct())
        assertTrue(ModelPicker.sections(models, "nothing like this").isEmpty())
    }

    @Test
    fun `B3 QA wave1 with the server default nothing else is marked current`() {
        val sections = ModelPicker.sections(ComposerModels(choices = all, selected = terra, usesDefault = true))
        assertTrue(sections.flatMap { it.rows }.none { it.current })
    }
}
