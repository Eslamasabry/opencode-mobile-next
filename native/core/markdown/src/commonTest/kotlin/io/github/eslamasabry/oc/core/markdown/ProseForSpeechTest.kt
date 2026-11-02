package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ported from test/kit/kit_markdown_test.dart:625 and the read-aloud rules of plan 09 §9.1 / 10 §20. */
class ProseForSpeechTest {
    @Test
    fun `B-CHAT-060 speech drops fences, tables and link targets`() {
        val spoken =
            proseForSpeech(
                "Hello **there**\n\n| a | b |\n| --- | --- |\n\n```\nx\n```\n[label](https://secret.example)",
            )
        assertFalse(spoken.contains("secret"))
        assertFalse(spoken.contains("```"))
        assertFalse(spoken.contains("---"))
        assertTrue(spoken.startsWith("Hello there"))
        assertTrue(spoken.endsWith("label"))
    }

    @Test
    fun `B-CHAT-060 speech drops heading, quote and list marks and rules, keeps the words`() {
        assertEquals(
            // Blank lines stay (a pause); the rule line is dropped.
            "Plan\nquoted words\none\ntwo\n\n\nThen bold and italic.",
            proseForSpeech("# Plan\n> quoted words\n- one\n1. two\n\n---\n\nThen **bold** and *italic*."),
        )
    }

    @Test
    fun `B-CHAT-060 a fence closes only on a matching marker with nothing after it`() {
        assertEquals("after", proseForSpeech("````md\n```\ninner\n```\n````\nafter"))
        assertEquals("", proseForSpeech("~~~\ncode\n``` \nstill code"))
    }

    @Test
    fun `B-CHAT-060 inline code is a pause, never read as symbols`() {
        assertEquals("Run   now", proseForSpeech("Run `rm -rf build` now"))
    }

    @Test
    fun `B-CHAT-060 CRLF text speaks like LF text`() {
        assertEquals(proseForSpeech("a\n```\nx\n```\nb"), proseForSpeech("a\r\n```\r\nx\r\n```\r\nb"))
    }

    @Test
    fun `B-CHAT-060 text over the limit is refused`() {
        assertFailsWith<SpeechTooLongException> { proseForSpeech("a".repeat(SPEECH_SOURCE_LIMIT + 1)) }
        assertFailsWith<SpeechTooLongException> { proseForSpeech("word\n".repeat(3_000)) }
        proseForSpeech("word\n".repeat(2_000))
    }
}
