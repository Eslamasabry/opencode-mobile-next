package io.github.eslamasabry.oc.feature.chat.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReasoningPreviewTest {
    @Test
    fun `a short line stays whole`() {
        assertEquals("Reading the tests", reasoningPreview("  Reading the tests "))
    }

    @Test
    fun `a long line ends at a word with an ellipsis`() {
        val line = "I need to look at how the queue settles a resend before I change the bubble that shows it"
        val preview = reasoningPreview(line)
        assertTrue(preview.endsWith("…"), preview)
        assertTrue(preview.length <= 81, preview)
        assertTrue(line.startsWith(preview.dropLast(1)), preview)
        assertTrue(line[preview.length - 1] == ' ', "cut at a word: $preview")
    }
}
