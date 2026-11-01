package io.github.eslamasabry.oc.engine.settings.defaults

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionDefaultsTest {
    private fun project(id: String) = ProjectOption(id, "Project $id")

    private fun model(id: String, provider: String = "provider", enabled: Boolean = true) =
        ModelOption(provider, id, "Friendly $id", enabled)

    @Test
    fun `B-STATE-021 pickers with one option skip the picker and the default project follows the rule`() {
        assertNull(InteractionDefaults.picker(emptyList<String>(), { it }).value)
        val single = InteractionDefaults.picker(listOf("one"), { it })
        assertEquals("one", single.value)
        assertTrue(single.skipPicker)
        assertFalse(single.canChange)
        val many = InteractionDefaults.picker(listOf("one", "two"), { it })
        assertFalse(many.skipPicker)
        assertTrue(many.canChange)

        val first = InteractionDefaults.project(emptyList())
        assertEquals("my-app", first.value!!.name)
        assertTrue(first.value.needsCreation)
        assertEquals(DefaultReason.FirstProject, first.reason)
        val projects = listOf(project("a"), project("b"))
        assertEquals("a", InteractionDefaults.project(listOf(projects.first())).value!!.project!!.id)
        assertEquals(DefaultReason.OnlyOption, InteractionDefaults.project(listOf(projects.first())).reason)
        assertEquals("b", InteractionDefaults.project(projects, lastUsedId = "b").value!!.project!!.id)
        assertEquals(DefaultReason.LastUsed, InteractionDefaults.project(projects, lastUsedId = "b").reason)
        assertNull(InteractionDefaults.project(projects, lastUsedId = "gone").value)
        val explicit = InteractionDefaults.project(projects, lastUsedId = "b", explicitId = "a")
        assertEquals(DefaultReason.ExplicitChoice, explicit.reason)
        assertEquals("a", explicit.value!!.project!!.id)
    }

    @Test
    fun `B-STATE-022 the default model is the server's by id with its friendly name and never overrides a choice`() {
        val models = listOf(model("a"), model("b"), model("b", provider = "other"), model("off", enabled = false))
        fun resolve(signedIn: Boolean = true, explicit: ModelOption? = null) =
            InteractionDefaults.model(models, signedIn, serverProviderId = "provider", serverModelId = "b", explicitChoice = explicit)
        assertNull(resolve(signedIn = false).value)
        assertEquals("provider", resolve().value!!.providerId)
        assertEquals("Friendly b", resolve().label)
        assertEquals(DefaultReason.ServerDefault, resolve().reason)
        assertEquals("a", resolve(explicit = model("a")).value!!.id)
        assertEquals("b", resolve(explicit = model("off")).value!!.id, "a disabled explicit model does not count")
        assertNull(InteractionDefaults.model(models, signedIn = true).value)
        assertEquals("only", InteractionDefaults.model(listOf(model("only")), signedIn = true).value!!.id)
        assertNull(InteractionDefaults.model(listOf(model("off", enabled = false)), signedIn = true).value)
    }

    @Test
    fun `B-STATE-023 send defaults to Queue and review picks the scope with confirmed changes, keeping overrides`() {
        assertEquals(DeliveryChoice.Queue, InteractionDefaults.delivery().value)
        assertTrue(InteractionDefaults.delivery().isAutomatic)
        assertFalse(InteractionDefaults.delivery(DeliveryChoice.Steer).isAutomatic)
        val session = DefaultReviewScope.Session
        val working = DefaultReviewScope.WorkingTree
        val branch = DefaultReviewScope.Branch
        assertEquals(working, InteractionDefaults.review(mapOf(session to 0, working to 3, branch to 4)).value)
        assertEquals(session, InteractionDefaults.review(mapOf(session to 2, working to 3)).value)
        assertEquals(branch, InteractionDefaults.review(mapOf(session to null, branch to 1)).value)
        assertNull(InteractionDefaults.review(mapOf(session to null, working to 0)).value)
        assertNull(InteractionDefaults.review(mapOf(working to 0)).value)
        assertEquals(branch, InteractionDefaults.review(mapOf(working to 1, branch to 0), explicitChoice = branch).value)
        assertEquals(working, InteractionDefaults.review(mapOf(working to 1), explicitChoice = session).value)
    }

    @Test
    fun `B-STATE-023 voice pack and voice defaults use physical memory, exact locale first and explicit choices`() {
        val packs = listOf(VoicePackOption("base", "Balanced", 1536), VoicePackOption("small", "Best", 3400), VoicePackOption("tiny", "Fast", 1024))
        fun pack(ram: Int?, explicit: String? = null) = InteractionDefaults.voicePack(ram, packs, explicit).value?.id
        assertNull(pack(1023))
        assertEquals("tiny", pack(1024))
        assertEquals("tiny", pack(1535))
        assertEquals("base", pack(1536))
        assertEquals("base", pack(3399))
        assertEquals("small", pack(3400))
        assertEquals("tiny", pack(null))
        assertEquals("base", pack(8000, explicit = "base"))
        assertEquals("tiny", pack(1100, explicit = "small"))
        assertNull(InteractionDefaults.voicePack(8000, emptyList()).value)

        val voices = listOf(VoiceOption("en", "English", "en-US"), VoiceOption("ar-eg", "Arabic Egypt", "ar-EG"), VoiceOption("ar-ae", "Arabic UAE", "ar-AE"))
        assertEquals("ar-ae", InteractionDefaults.voice(voices, "AR_ae").value!!.id)
        assertEquals("ar-eg", InteractionDefaults.voice(voices, "ar-SA").value!!.id)
        assertNull(InteractionDefaults.voice(voices, "fr-FR").value)
        assertEquals("en", InteractionDefaults.voice(voices, "ar-AE", explicitId = "en").value!!.id)
        assertNull(InteractionDefaults.voice(emptyList(), "ar").value)
        assertTrue(InteractionDefaults.voice(listOf(voices.first()), "ar").skipPicker)
    }
}
