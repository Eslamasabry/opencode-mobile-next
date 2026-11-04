package io.github.eslamasabry.oc.engine.agents.models

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionModelScopeTest {
    private val p1 = ProfileId("p1")

    private fun ref(provider: String, id: String) = ModelRef(provider, id)

    @Test
    fun `B-STATE-111 per-session choice does not move the default or other sessions`() = runTest {
        val choices = ModelChoices(
            ModelStore(FakeAgentPrefs()),
            p1,
            initialDefault = SessionModelChoice(ref("opencode", "default"), "low"),
        )
        val before = choices.state.value.revision

        choices.selectForSession("ses_a", ref("anthropic", "opus"))

        val s = choices.state.value
        assertEquals("anthropic/opus", s.modelFor("ses_a")?.wireName)
        assertEquals("", s.variantFor("ses_a"))
        assertEquals("opencode/default", s.defaultChoice?.model?.wireName)
        assertEquals("opencode/default", s.modelFor("ses_b")?.wireName)
        assertEquals("low", s.variantFor("ses_b"))
        assertEquals(1, s.revision - before)
    }

    @Test
    fun `B-STATE-111 changing the default later does not override a session choice`() = runTest {
        val choices = ModelChoices(ModelStore(FakeAgentPrefs()), p1)
        choices.selectForSession("ses_a", ref("anthropic", "opus"))

        choices.selectDefault(ref("openai", "gpt"))

        val s = choices.state.value
        assertEquals("openai/gpt", s.defaultChoice?.model?.wireName)
        assertEquals("anthropic/opus", s.modelFor("ses_a")?.wireName)
        assertEquals("openai/gpt", s.modelFor("ses_new")?.wireName)
    }

    @Test
    fun `B-STATE-112 a variant the catalog does not offer is rejected`() = runTest {
        val choices = ModelChoices(ModelStore(FakeAgentPrefs()), p1)
        choices.setCatalog(ModelCatalog(listOf(CatalogModel("opus", "anthropic", "Opus", variants = listOf(CatalogVariant("high"))))))

        choices.selectForSession("ses_a", ref("anthropic", "opus"), variant = "nope")
        assertTrue(choices.state.value.sessions.isEmpty())

        choices.selectForSession("ses_a", ref("anthropic", "opus"), variant = "high")
        assertEquals("high", choices.state.value.variantFor("ses_a"))
    }

    @Test
    fun `B-STATE-112 store round-trips and prunes per-session choices`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = ModelStore(prefs)

        store.setSessionModels(
            p1,
            linkedMapOf(
                "ses_a" to SessionModelChoice(ref("anthropic", "opus")),
                "ses_b" to SessionModelChoice(ref("openai", "openai/gpt"), "high"),
            ),
        )

        val loaded = store.sessionModels(p1)
        assertEquals(listOf("ses_a", "ses_b"), loaded.keys.toList())
        assertEquals("anthropic/opus", loaded["ses_a"]!!.model.wireName)
        assertEquals("", loaded["ses_a"]!!.variant)
        // Composite ids are normalised on the way back in, like the default.
        assertEquals("openai/gpt", loaded["ses_b"]!!.model.wireName)
        assertEquals("high", loaded["ses_b"]!!.variant)
        assertTrue(store.sessionModels(ProfileId("p2")).isEmpty())

        // Bounded: only the most recent 200 survive.
        store.setSessionModels(p1, (0 until 250).associateTo(LinkedHashMap()) { "ses_$it" to SessionModelChoice(ref("p", "m$it")) })
        val pruned = store.sessionModels(p1)
        assertEquals(200, pruned.size)
        assertFalse("ses_0" in pruned)
        assertTrue("ses_249" in pruned)

        store.setSessionModels(p1, emptyMap())
        assertNull(prefs.string("oc.sessionModels.p1"))
    }

    @Test
    fun `B-STATE-112 malformed stored choices are dropped, not thrown`() {
        val store = ModelStore(
            FakeAgentPrefs(
                mapOf(
                    "oc.sessionModels.p1" to """{"ok":"a|b|","no-model":"a","empty-provider":"|b","wrong-type":3}""",
                    "oc.sessionModels.p2" to "not json",
                ),
            ),
        )
        assertEquals(listOf("ok"), store.sessionModels(p1).keys.toList())
        assertTrue(store.sessionModels(ProfileId("p2")).isEmpty())
    }

    @Test
    fun `B-STATE-112 stored per-session choices come back for the profile on restart`() = runTest {
        val prefs = FakeAgentPrefs()
        ModelChoices(ModelStore(prefs), p1).selectForSession("ses_a", ref("anthropic", "opus"))
        val reopened = ModelChoices(ModelStore(prefs), p1)
        assertEquals("anthropic/opus", reopened.state.value.modelFor("ses_a")?.wireName)
        reopened.forgetSession("ses_a")
        assertNull(prefs.string("oc.sessionModels.p1"))
    }
}
