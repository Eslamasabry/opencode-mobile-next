package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.composer.presentation.ModelPicker
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The chat's rules around the kit model sheet; the list's order is tested as `KitModelOrder` in :ui:kit. */
class ModelPickerTest {
    private fun m(provider: String, name: String, id: String = name.lowercase(), providerName: String = provider.uppercase()) =
        ComposerModel(provider, id, name, true, providerName = providerName)

    private val glmZai = m("zai-coding-plan", "GLM-5.3-Flash", providerName = "Z.AI Coding Plan")
    private val glmOr = m("openrouter", "GLM-5.3-Flash", providerName = "OpenRouter")
    private val terra = m("openai", "GPT-6.1 Terra", providerName = "OpenAI")
    private val fable = m("anthropic", "Claude Fable 5.1", providerName = "Anthropic")
    private val all = listOf(glmOr, terra, glmZai, fable)

    @Test
    fun `Server default is a choice in the list once a model is known, not while searching`() {
        assertTrue(ModelPicker.offersServerDefault(ComposerModels(choices = all, selected = terra)))
        assertTrue(ModelPicker.offersServerDefault(ComposerModels(choices = all, selected = terra, usesDefault = true), "  "))
        assertFalse(ModelPicker.offersServerDefault(ComposerModels(choices = all, selected = terra), "glm"))
        assertFalse(ModelPicker.offersServerDefault(ComposerModels(choices = all)), "nothing known yet, nothing claimed")
    }

    @Test
    fun `thinking levels are one line led by the model's own default`() {
        val thinker = terra.copy(variants = listOf("minimal", "low", "medium", "high", "xhigh", "max"))
        assertEquals(
            listOf("", "minimal", "low", "medium", "high", "xhigh", "max"),
            ModelPicker.thinkingLevels(ComposerModels(choices = listOf(thinker), selected = thinker)),
        )
        assertTrue(ModelPicker.thinkingLevels(ComposerModels(choices = all, selected = terra)).isEmpty())
        assertTrue(ModelPicker.thinkingLevels(ComposerModels(choices = all)).isEmpty())
    }
}
