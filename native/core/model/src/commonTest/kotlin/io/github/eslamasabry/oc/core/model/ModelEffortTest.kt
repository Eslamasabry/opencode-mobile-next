package io.github.eslamasabry.oc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The model chip said the effort twice ("Gemini 3.8 Flash (High) · high", owner, build 10073): said once now. */
class ModelEffortTest {
    @Test
    fun `B-CHAT-EFFORT-1 a name that carries the effort gets no suffix, whatever the case`() {
        assertEquals("Gemini 3.8 Flash (High)", ModelEffort.label("Gemini 3.8 Flash (High)", "high", "High"))
        assertEquals("Gemini 3.8 Flash (High)", ModelEffort.label("Gemini 3.8 Flash (High)", "high", "high"))
        assertEquals("Gemini 3.8 Flash (HIGH)", ModelEffort.label("Gemini 3.8 Flash (HIGH)", "high", "High"))
        assertEquals("Gemini 3.8 Pro (Low)", ModelEffort.label("Gemini 3.8 Pro (Low)", "low", "Low"))
        assertEquals("Claude Sonnet (Thinking)", ModelEffort.label("Claude Sonnet (Thinking)", "thinking", "thinking"))
        assertEquals("gemini-3.8-flash-high", ModelEffort.label("gemini-3.8-flash-high", "high", "High"))
        assertEquals("GPT-6 Sol Max", ModelEffort.label("GPT-6 Sol Max", "max", "Max"))
        // "Extra high" is the words of the id xhigh; a name that ends either way already says it.
        assertEquals("Sol (Extra high)", ModelEffort.label("Sol (Extra high)", "xhigh", "Extra high"))
        assertEquals("sol-xhigh", ModelEffort.label("sol-xhigh", "xhigh", "Extra high"))
    }

    @Test
    fun `B-CHAT-EFFORT-2 a name that does not carry the effort gets it after a dot`() {
        assertEquals("Gemini 4 · High", ModelEffort.label("Gemini 4", "high", "High"))
        assertEquals("Sonnet 5 · Extra high", ModelEffort.label("Sonnet 5", "xhigh", "Extra high"))
        assertEquals("Sonnet 5 · high", ModelEffort.label("Sonnet 5", "high"))
        // A bracket that is not an effort word is not the effort.
        assertEquals("Sonnet 5 (beta) · High", ModelEffort.label("Sonnet 5 (beta)", "high", "High"))
        assertEquals("Highland 2 · Low", ModelEffort.label("Highland 2", "low", "Low"))
    }

    @Test
    fun `B-CHAT-EFFORT-3 no effort chosen leaves the name alone`() {
        assertEquals("Gemini 4", ModelEffort.label("Gemini 4", null))
        assertEquals("Gemini 4", ModelEffort.label("Gemini 4", ""))
        assertEquals("Gemini 4 (High)", ModelEffort.label("Gemini 4 (High)", ""))
    }

    @Test
    fun `B-CHAT-EFFORT-4 a name that carries a different effort than the chosen one says both`() {
        assertEquals("Gemini 3.8 Flash (Low) · High", ModelEffort.label("Gemini 3.8 Flash (Low)", "high", "High"))
        assertEquals("Claude Sonnet (Thinking) · High", ModelEffort.label("Claude Sonnet (Thinking)", "high", "High"))
        assertEquals("sol-high · Low", ModelEffort.label("sol-high", "low", "Low"))
    }

    @Test
    fun `B-CHAT-EFFORT-5 the effort control is offered only where the name does not carry it`() {
        assertFalse(ModelEffort.offersChoice("Gemini 3.8 Flash (High)"))
        assertFalse(ModelEffort.offersChoice("gemini-3.8-flash-low"))
        assertTrue(ModelEffort.offersChoice("Gemini 4"))
        assertTrue(ModelEffort.offersChoice("Sonnet 5 (beta)"))
        assertEquals("High", ModelEffort.carried("Gemini 3.8 Flash (High)"))
        assertNull(ModelEffort.carried("Gemini 4"))
    }
}
