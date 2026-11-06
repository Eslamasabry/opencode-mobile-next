package io.github.eslamasabry.oc.feature.chats.rb.o41

import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.newConversationModelSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelChoicesTest {
    private val sonnet = NewConversationModel(ModelRef("anthropic", "sonnet"), "Sonnet", providerName = "Anthropic")
    private val opus = NewConversationModel(ModelRef("anthropic", "opus"), "Opus", providerName = "Anthropic")
    private val glm = NewConversationModel(ModelRef("zai", "glm"), "GLM", providerName = "Z.AI")
    private val alias = NewConversationModel(ModelRef("anthropic", "default"), "Default alias")

    @Test
    fun `B08 model choices show plain names and never the default alias`() {
        val rows = newConversationModelSections(listOf(alias, sonnet), null, emptyList(), "").flatMap { it.rows }
        assertEquals(listOf("Sonnet"), rows.map { it.model.name })
    }

    @Test
    fun `N-51 favourites lead, the chosen provider and model come first, each model once`() {
        val sections =
            newConversationModelSections(listOf(glm, sonnet, opus), sonnet.model, listOf(glm.model), "")
        assertTrue(sections.first().favorites)
        assertEquals(listOf("GLM"), sections.first().rows.map { it.model.name })
        assertEquals("Anthropic", sections[1].providerName)
        assertEquals(listOf("Sonnet", "Opus"), sections[1].rows.map { it.model.name })
        assertTrue(sections[1].rows.first().current)
        assertEquals(2, sections.size)
    }
}
