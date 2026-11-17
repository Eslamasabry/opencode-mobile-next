package io.github.eslamasabry.oc.feature.chat.composer

import io.github.eslamasabry.oc.feature.chat.composer.presentation.ModelPicker
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import kotlin.test.Test
import kotlin.test.assertEquals

/** The model sheet's effort line must not offer a second, disagreeing choice beside a name that carries the effort. */
class ThinkingLevelsOnceTest {
    private fun models(label: String) =
        ComposerModels(
            choices = emptyList(),
            selected = ComposerModel("antigravity", "flash", label, true, variants = listOf("low", "high")),
        )

    @Test
    fun `B-CHAT-EFFORT-7 a model whose name carries the effort has no second effort line`() {
        assertEquals(emptyList(), ModelPicker.thinkingLevels(models("Gemini 3.8 Flash (High)")))
    }

    @Test
    fun `B-CHAT-EFFORT-8 a model whose name does not carry it keeps its effort line`() {
        assertEquals(listOf("", "low", "high"), ModelPicker.thinkingLevels(models("Gemini 4")))
    }
}
