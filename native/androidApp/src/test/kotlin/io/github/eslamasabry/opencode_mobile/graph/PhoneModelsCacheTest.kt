package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Owner 11-12: a phone agent's last model list survives an app start, and a fresh read always replaces it. */
class PhoneModelsCacheTest {
    private val sol = ModelRef("codex", "gpt-6.1-sol")
    private val terra = ModelRef("codex", "gpt-6.1-terra")
    private val opus = ModelRef("claude", "opus")

    private fun list(vararg models: NewConversationModel) = NewConversationPhoneModels(models.toList(), models.first().model)

    @Test
    fun aStoredListReadsBackWithItsNamesEffortsAndChoice() {
        val stored =
            PhoneModelsCache.put(
                null,
                "codex",
                list(NewConversationModel(sol, "GPT-6.1-Sol", runtime = "Codex", providerName = "Codex", variants = listOf("low", "xhigh"))),
            )
        val back = PhoneModelsCache.get(stored, "codex")!!
        assertEquals(sol, back.model)
        val model = back.models.single()
        assertEquals("GPT-6.1-Sol", model.name)
        assertEquals("Codex", model.runtime)
        assertEquals(listOf("low", "xhigh"), model.variants)
        assertNull(PhoneModelsCache.get(stored, "claude"))
    }

    @Test
    fun aFreshReadReplacesTheStaleListAndLeavesTheOtherAgents() {
        // The earlier fault: Claude Code's models showed only after a restart because a list read too early was kept.
        var stored = PhoneModelsCache.put(null, "claude", list(NewConversationModel(ModelRef("claude", "default"), "Default model")))
        stored = PhoneModelsCache.put(stored, "codex", list(NewConversationModel(sol, "GPT-6.1-Sol")))
        stored = PhoneModelsCache.put(stored, "claude", list(NewConversationModel(opus, "Opus")))
        assertEquals(listOf(opus), PhoneModelsCache.get(stored, "claude")!!.models.map { it.model })
        assertEquals(listOf(sol), PhoneModelsCache.get(stored, "codex")!!.models.map { it.model })
        stored = PhoneModelsCache.put(stored, "codex", list(NewConversationModel(terra, "GPT-6.1 Terra")))
        assertEquals(listOf(terra), PhoneModelsCache.get(stored, "codex")!!.models.map { it.model })
    }

    @Test
    fun aReadThatNamesNoModelForTheAskedAgentDropsItsOldList() {
        var stored: String? = PhoneModelsCache.put(null, "codex", list(NewConversationModel(sol, "GPT-6.1-Sol")))
        stored = PhoneModelsCache.put(stored, "claude", list(NewConversationModel(opus, "Opus")))
        stored = PhoneModelsCache.remove(stored, "codex")
        assertNull(PhoneModelsCache.get(stored, "codex"))
        assertEquals(listOf(opus), PhoneModelsCache.get(stored, "claude")!!.models.map { it.model })
        // The last one gone: nothing is left to keep (the key is removed).
        assertNull(PhoneModelsCache.remove(stored, "claude"))
        assertNull(PhoneModelsCache.remove("not json", "claude"))
    }

    @Test
    fun aStoredListIsBounded() {
        val many = (1..400).map { NewConversationModel(ModelRef("codex", "m$it"), "x".repeat(5000), variants = (1..40).map { v -> "v$v" }) }
        val stored = PhoneModelsCache.put(null, "codex", NewConversationPhoneModels(many, many.first().model))
        val back = PhoneModelsCache.get(stored, "codex")!!
        assertEquals(300, back.models.size)
        assertEquals(256, back.models.first().name.length)
        assertEquals(16, back.models.first().variants.size)
        var all: String? = null
        for (i in 1..40) all = PhoneModelsCache.put(all, "agent$i", list(NewConversationModel(sol, "GPT-6.1-Sol")))
        assertNull(PhoneModelsCache.get(all, "agent1"), "the oldest agents fall off past 32")
        assertTrue(PhoneModelsCache.get(all, "agent40") != null)
    }

    @Test
    fun unreadableTextIsNoCache() {
        assertNull(PhoneModelsCache.get("not json", "codex"))
        assertNull(PhoneModelsCache.get(null, "codex"))
        val fresh = PhoneModelsCache.put("{broken", "codex", list(NewConversationModel(sol, "GPT-6.1-Sol")))
        assertEquals(listOf(sol), PhoneModelsCache.get(fresh, "codex")!!.models.map { it.model })
    }

    @Test
    fun theKeyIsPerProfileSoDeletingTheProfileRemovesIt() {
        val key = PhoneModelsCache.key(ProfileId("p1"))
        assertEquals("oc.agentModelList.p1", key)
        assertTrue(key in ProfileKeyRule.scopedKeys("p1", listOf(key), setOf("p1", "p2")))
        assertFalse(key in ProfileKeyRule.scopedKeys("p2", listOf(key), setOf("p1", "p2")))
        // Names and ids only.
        val stored = PhoneModelsCache.put(null, "codex", list(NewConversationModel(sol, "GPT-6.1-Sol")))
        assertFalse("password" in stored || "token" in stored)
    }
}
