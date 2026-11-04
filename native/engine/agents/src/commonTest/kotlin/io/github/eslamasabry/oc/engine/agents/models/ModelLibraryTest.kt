package io.github.eslamasabry.oc.engine.agents.models

import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun model(id: String, provider: String = "local") = ModelRef(provider, id)

internal fun testCatalog(): ModelCatalog = ModelCatalog(
    listOf("a", "b", "c", "disabled").map { CatalogModel(it, "local", "Model $it", enabled = it != "disabled") },
)

class ModelLibraryTest {
    private val one = ProfileId("one")
    private val two = ProfileId("two")

    @Test
    fun `B-STATE-032 recents normalize, deduplicate and retain the eight newest choices`() {
        var library = ModelLibrary()
        for (i in 0 until 12) library = library.remember(model("m$i"))
        library = library.remember(model("local/m7"))
        assertEquals(listOf("m7", "m11", "m10", "m9", "m8", "m6", "m5", "m4"), library.recent.map { it.modelId })
        // Read-only, like Dart's List.unmodifiable.
        assertIs<ImmutableList<ModelRef>>(library.recent)
    }

    @Test
    fun `B-STATE-032 favorites preserve backend identity and toggle normalized refs`() {
        val library = ModelLibrary()
            .toggleFavorite(model("local/a"))
            .toggleFavorite(model("a", "other"))
            .toggleFavorite(model("a"))
        assertEquals("other/a", library.favorites.single().wireName)
        assertTrue(library.recent.isEmpty())
    }

    @Test
    fun `B-STATE-032 corrupt entries are ignored and oversized history is bounded on read`() {
        val ref = { id: String -> AgentJson.encode(ModelLibrary.refJson(model(id))) }
        val recent = (0 until 30).joinToString(",") { ref("m$it") }
        val library = ModelLibrary.fromJson(
            AgentJson.parseOrNull(
                """{"favorites":[null,2,{"providerID":"","modelID":"a"},${ref("a")},${ref("local/a")}],"recent":[$recent]}""",
            ),
        )
        assertEquals("local/a", library.favorites.single().wireName)
        assertEquals(ModelLibrary.RECENT_LIMIT, library.recent.size)
        assertTrue(ModelLibrary.fromJson(AgentJson.parseOrNull("[]")).recent.isEmpty())
    }

    @Test
    fun `B-STATE-032 persistence is isolated by profile and safely reads malformed JSON`() = runTest {
        val prefs = FakeAgentPrefs(mapOf("oc.modelLibrary.two" to "broken json"))
        val store = ModelStore(prefs)
        val library = ModelLibrary().toggleFavorite(model("a")).remember(model("b"))
        store.setModelLibrary(one, library)
        val reopened = ModelStore(prefs).modelLibrary(one)
        assertEquals("local/a", reopened.favorites.single().wireName)
        assertEquals("local/b", reopened.recent.single().wireName)
        assertTrue(store.modelLibrary(two).recent.isEmpty())
        assertTrue("oc.modelLibrary.one" in ModelKeys.profileScoped(one))
        store.setModelLibrary(one, ModelLibrary())
        assertFalse("oc.modelLibrary.one" in prefs.values)
    }

    @Test
    fun `B-STATE-032 pruning removes unavailable models from both lists`() {
        val library = ModelLibrary()
            .toggleFavorite(model("a"))
            .toggleFavorite(model("b"))
            .remember(model("a"))
            .remember(model("b"))
            .retainWhere { it.modelId == "b" }
        assertEquals("b", library.favorites.single().modelId)
        assertEquals("b", library.recent.single().modelId)
    }

    @Test
    fun `B-NAV-033 cycling skips unavailable models and wraps in both directions`() {
        val library = ModelLibrary(recent = listOf(model("a"), model("gone"), model("b"), model("c")))
        val available = { m: ModelRef -> m.modelId != "gone" }
        assertEquals("b", library.next(model("a"), available = available)?.modelId)
        assertEquals("c", library.next(model("a"), reverse = true, available = available)?.modelId)
        assertEquals("a", library.next(model("c"), available = available)?.modelId)
        assertEquals("a", library.next(null, available = available)?.modelId)
        assertNull(library.next(null, available = { false }))
        assertNull(ModelLibrary(recent = listOf(model("a"))).next(model("a"), available = available))
    }

    @Test
    fun `B-NAV-033 repeated cycles visit every recent model without changing other chats`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = ModelStore(prefs)
        val choices = ModelChoices(store, one).apply { setCatalog(testCatalog()) }
        for (id in listOf("a", "b", "c")) choices.selectForSession("chat", model(id))
        choices.selectDefault(model("a"))
        // A stable MRU ring: a, c, b. Cycling must not reorder it into a toggle.
        val results = (0 until 3).map { choices.cycleForSession("chat").getOrNull()?.modelId }
        assertEquals(listOf("b", "a", "c"), results)
        assertEquals("a", choices.state.value.defaultChoice?.model?.modelId)
        assertEquals("a", choices.state.value.modelFor("other")?.modelId)
        assertEquals(listOf("a", "c", "b"), store.modelLibrary(one).recent.map { it.modelId })
        assertTrue(store.modelLibrary(two).recent.isEmpty())
    }

    @Test
    fun `B-STATE-032 favoriting never selects a model and unavailable choices are rejected`() = runTest {
        val choices = ModelChoices(ModelStore(FakeAgentPrefs()), one).apply { setCatalog(testCatalog()) }
        choices.selectForSession("chat", model("a"))
        choices.toggleFavorite(model("b"))
        assertEquals("a", choices.state.value.modelFor("chat")?.modelId)
        choices.selectForSession("chat", model("disabled"))
        choices.selectForSession("chat", model("missing"))
        assertEquals("a", choices.state.value.modelFor("chat")?.modelId)
        assertEquals("a", choices.state.value.library.recent.single().modelId)
        assertEquals("b", choices.cycleForSession("chat", favoritesOnly = true).getOrNull()?.modelId)
        assertEquals("b", choices.state.value.library.recent.first().modelId)
    }

    @Test
    fun `B-STATE-032 a refused shortcut save rolls the favorite back`() = runTest {
        val prefs = FakeAgentPrefs()
        val choices = ModelChoices(ModelStore(prefs), one).apply { setCatalog(testCatalog()) }
        prefs.refuseWrites = true
        assertIs<io.github.eslamasabry.oc.core.common.Outcome.Failed>(choices.toggleFavorite(model("a")))
        assertTrue(choices.state.value.library.favorites.isEmpty())
    }
}
