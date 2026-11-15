package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.composer.draftWaitWords
import io.github.eslamasabry.oc.feature.chat.composer.modelUnknown
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderPresenter
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.core.common.Failure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Open-transitions (Galaxy S22 recording, build 10050): a conversation of a phone agent opened with its message box
 * saying "Ask OpenCode…" and its model chip saying "Model", and its bar saying "Conversation", all for a frame or a
 * second before the real values: placeholders of a state not read yet, shown as if they were facts. While a value is
 * unknown its slot shows nothing.
 */
class OpeningPlaceholdersTest {
    private val world = ModelWorld()
    private val p = ChatHeaderPresenter(world, FakeHeaderPort(), RecordingNavigator(), Immediate)
    private val hostAgents = ServerCapabilities(hostAgentProviders = true)
    private val gemini = ComposerModel("antigravity", "gemini-4", "Gemini 4 Hydrogen", supportsAttachments = true, providerName = "Antigravity")

    @Test
    fun `OT-06 before the server's capabilities are read no agent is named`() {
        world.capabilities.value = ServerCapabilities.Unknown
        assertFalse(p.current().agentKnown, "\"OpenCode\" would be a guess")
    }

    @Test
    fun `OT-07 a host agent's conversation names its agent only once the session's model is read`() {
        world.capabilities.value = hostAgents
        world.modelFlow.value = ComposerModels(loading = true)
        assertFalse(p.current().agentKnown, "no model read yet: the box names no one")
        world.modelFlow.value = ComposerModels(choices = listOf(gemini), selected = gemini)
        val known = p.current()
        assertTrue(known.agentKnown)
        assertEquals("Antigravity", known.agentName)
    }

    @Test
    fun `OT-08 an OpenCode server's conversation is OpenCode's from the start`() {
        world.capabilities.value = ServerCapabilities()
        assertTrue(p.current().agentKnown)
    }

    @Test
    fun `OT-09 a title not read yet is unknown, not untitled`() {
        world.title.value = null
        world.load.value = ChatLoad.Loading
        assertTrue(p.current().titleUnknown, "while reading: no title, never \"Conversation\"")
        world.load.value = ChatLoad.Live
        assertFalse(p.current().titleUnknown, "read and still nameless: an untitled conversation")
        world.title.value = "Create a file named flag.txt"
        world.load.value = ChatLoad.Loading
        assertFalse(p.current().titleUnknown, "a kept or known title shows at once")
    }

    @Test
    fun `OT-10 the model chip has no words while the model is not known`() {
        assertTrue(modelUnknown(ComposerModels(loading = true)), "catalog being read, nothing named")
        assertTrue(modelUnknown(ComposerModels()), "nothing read at all")
        assertFalse(modelUnknown(ComposerModels(selected = gemini)), "a named model")
        assertFalse(modelUnknown(ComposerModels(usesDefault = true)), "the server said: its default")
        assertFalse(modelUnknown(ComposerModels(choices = listOf(gemini))), "catalog in, nothing chosen: the Model button")
    }

    @Test
    fun `OT-11 a box waiting for its draft claims no saved draft, and says so only when the read failed`() {
        // Device frame (item2, Glass): "Opening your saved draft…" under the box of a conversation with no draft.
        assertEquals("", draftWaitWords(DraftFacts(ready = false), "Could not open"), "still read: nothing is known")
        assertEquals("Could not open", draftWaitWords(DraftFacts(ready = false, failure = Failure.StorageRefused()), "Could not open"))
    }
}
