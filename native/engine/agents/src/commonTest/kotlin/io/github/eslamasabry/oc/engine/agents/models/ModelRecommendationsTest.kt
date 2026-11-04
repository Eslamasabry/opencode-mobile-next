package io.github.eslamasabry.oc.engine.agents.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelRecommendationsTest {
    private fun m(id: String, provider: String = "p", cost: ModelCost? = null, enabled: Boolean = true) =
        CatalogModel(id, provider, id.uppercase(), enabled = enabled, cost = cost)

    private fun c(input: Double, output: Double) = ModelCost(input, output)

    private fun r(id: String, provider: String = "p") = ModelRef(provider, id)

    private fun rows(list: List<ModelRecommendation>) = list.map { it.reason to it.model.id }

    @Test
    fun `B-NAV-036 the server default, a recent choice and the cheapest, in order`() {
        val result = recommendModels(
            listOf(m("sonnet", cost = c(3.0, 15.0)), m("opus", cost = c(5.0, 25.0)), m("mini", cost = c(.25, 2.0)), m("sol", cost = c(2.5, 15.0))),
            current = r("sonnet"),
            defaults = listOf(r("sonnet")),
            recent = listOf(r("sonnet"), r("sol")),
        )
        assertEquals(
            listOf(ModelReason.ServerDefault to "sonnet", ModelReason.Recent to "sol", ModelReason.Cheapest to "mini"),
            rows(result),
        )
    }

    @Test
    fun `B-NAV-036 no speed is ever claimed - there is no fastest reason`() {
        assertFalse(ModelReason.entries.any { "fast" in it.name.lowercase() })
    }

    @Test
    fun `B-NAV-036 a model without a price is never the cheapest`() {
        val result = recommendModels(
            listOf(m("priced", cost = c(1.0, 5.0)), m("unknown"), m("other", cost = c(3.0, 15.0))),
            defaults = listOf(r("other")),
        )
        assertEquals(listOf(ModelReason.ServerDefault to "other", ModelReason.Cheapest to "priced"), rows(result))
    }

    @Test
    fun `B-NAV-036 equal prices name no cheapest (all free) and rows say Free`() {
        val result = recommendModels(
            listOf(m("a", cost = c(0.0, 0.0)), m("b", cost = c(0.0, 0.0)), m("c", cost = c(0.0, 0.0))),
            current = r("a"),
            defaults = listOf(r("a")),
            recent = listOf(r("a"), r("b")),
        )
        assertEquals(listOf(ModelReason.ServerDefault to "a", ModelReason.Recent to "b"), rows(result))
        assertTrue(result.all { it.free })
    }

    @Test
    fun `B-NAV-036 no prices at all (a phone agent) - no price row`() {
        val result = recommendModels(
            listOf(m("opus"), m("sonnet"), m("haiku")),
            current = r("opus"),
            defaults = listOf(r("sonnet")),
            recent = listOf(r("opus")),
        )
        assertEquals(listOf(ModelReason.InUse to "opus", ModelReason.ServerDefault to "sonnet"), rows(result))
    }

    @Test
    fun `B-NAV-036 one model is one row - the default that is also the cheapest`() {
        val result = recommendModels(
            listOf(m("free", cost = c(0.0, 0.0)), m("paid", cost = c(3.0, 15.0))),
            defaults = listOf(r("free")),
        )
        assertEquals(listOf(ModelReason.ServerDefault to "free"), rows(result))
        assertTrue(result.single().alsoCheapest)
        assertTrue(result.single().free)
    }

    @Test
    fun `B-NAV-036 a cheapest tie prefers the model in use, then a recent one`() {
        val models = listOf(m("x", cost = c(0.0, 0.0)), m("y", cost = c(0.0, 0.0)), m("paid", cost = c(3.0, 15.0)))
        assertEquals("y", recommendModels(models, current = r("y")).last().model.id)
        assertEquals(
            "y",
            recommendModels(models, current = r("paid"), recent = listOf(r("y")))
                .first { it.reason == ModelReason.Cheapest }.model.id,
        )
    }

    @Test
    fun `B-NAV-036 the model in use always shows, and the recent row gives way`() {
        val result = recommendModels(
            listOf(m("d", cost = c(3.0, 15.0)), m("cheap", cost = c(1.0, 1.0)), m("mine", cost = c(5.0, 25.0)), m("old", cost = c(4.0, 20.0))),
            current = r("mine"),
            defaults = listOf(r("d")),
            recent = listOf(r("mine"), r("old")),
        )
        assertEquals(
            listOf(ModelReason.InUse to "mine", ModelReason.ServerDefault to "d", ModelReason.Cheapest to "cheap"),
            rows(result),
        )
    }

    @Test
    fun `B-NAV-036 unavailable models and unknown refs are skipped, never padded`() {
        val result = recommendModels(
            listOf(m("off", enabled = false), m("on")),
            current = r("gone"),
            defaults = listOf(r("off"), r("nowhere")),
            recent = listOf(r("off")),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `B-NAV-036 the first default the list has wins (most authoritative first)`() {
        val result = recommendModels(
            listOf(m("a", provider = "codex"), m("b", provider = "codex")),
            defaults = listOf(r("x", provider = "claude"), r("b", provider = "codex")),
        )
        assertEquals(listOf(ModelReason.ServerDefault to "b"), rows(result))
    }
}
