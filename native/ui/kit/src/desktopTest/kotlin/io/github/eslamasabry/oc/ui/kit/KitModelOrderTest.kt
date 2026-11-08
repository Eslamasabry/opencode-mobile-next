package io.github.eslamasabry.oc.ui.kit

import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOrder
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSectionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N-51 / QA wave 1 B3: the one model list of the chat and of New conversation (moved from the chat's picker test). */
class KitModelOrderTest {
    private fun m(provider: String, name: String, providerName: String = provider.uppercase()) =
        KitModelOption(provider, name.lowercase(), name, providerName)

    private val glmZai = m("zai-coding-plan", "GLM-5.3-Flash", "Z.AI Coding Plan")
    private val glmOr = m("openrouter", "GLM-5.3-Flash", "OpenRouter")
    private val terra = m("openai", "GPT-6.1 Terra", "OpenAI")
    private val fable = m("anthropic", "Claude Fable 5.1", "Anthropic")
    private val all = listOf(glmOr, terra, glmZai, fable)

    @Test
    fun `B3 models are grouped under their provider's name with the current provider first`() {
        val sections = KitModelOrder.sections(all, current = glmZai.key)
        assertEquals(listOf("Z.AI Coding Plan", "Anthropic", "OpenAI", "OpenRouter"), sections.map { it.providerName })
    }

    @Test
    fun `B3 recent picks come first and search matches name, id or provider`() {
        val sections = KitModelOrder.sections(all, recent = listOf(glmZai), current = terra.key)
        assertEquals(KitModelSectionKind.Recent, sections.first().kind)
        assertEquals(listOf(glmZai), sections.first().rows)
        val zai = KitModelOrder.sections(all, recent = listOf(glmZai), current = terra.key, query = "z.ai")
        assertEquals(listOf(glmZai), zai.flatMap { it.rows }.distinct())
        assertTrue(KitModelOrder.sections(all, query = "nothing like this").isEmpty())
    }

    @Test
    fun `B-NAV-034 the current model leads its provider's group, before its alphabetical siblings`() {
        val siblings = (1..40).map { m("openai", "GPT-A$it") }
        val zed = m("openai", "Zed Turbo")
        val group = KitModelOrder.sections(siblings + zed, current = zed.key).single { it.providerName == "OPENAI" }
        assertEquals(zed, group.rows.first())
        assertEquals(siblings.map { it.label }.sortedBy { it.lowercase() }, group.rows.drop(1).map { it.label })
    }

    @Test
    fun `B3 a favourite or recent model shows once, not again in its provider's group`() {
        val sections =
            KitModelOrder.sections(all, favorites = listOf(terra), recent = listOf(glmZai, terra), current = terra.key)
        val shown = sections.flatMap { it.rows }
        assertEquals(shown.distinct(), shown, "no model is listed twice")
        assertEquals(KitModelSectionKind.Favorites, sections.first().kind)
        assertEquals(listOf(terra), sections.first().rows)
        assertEquals(listOf(glmZai), sections[1].rows, "Recent leaves out the favourite")
    }

    @Test
    fun `N-51 a favourite the server no longer offers is not listed`() {
        val gone = m("openai", "Retired")
        val sections = KitModelOrder.sections(all, favorites = listOf(gone))
        assertTrue(sections.none { it.kind == KitModelSectionKind.Favorites })
    }

    @Test
    fun `B-169-020 a switch suggestion leads under its own heading and shows once`() {
        val sections = KitModelOrder.sections(all, recent = listOf(fable), current = terra.key, suggested = fable.key)
        assertEquals(KitModelSectionKind.Suggested, sections.first().kind)
        assertEquals(listOf(fable), sections.first().rows)
        assertEquals(1, sections.sumOf { s -> s.rows.count { it.key == fable.key } })
        // A suggestion the search does not match is not forced in.
        assertTrue(KitModelOrder.sections(all, suggested = fable.key, query = "glm").none { it.kind == KitModelSectionKind.Suggested })
    }
}
