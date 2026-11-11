package io.github.eslamasabry.oc.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** The one preview rule the home list and the conversation's timeline share (W4 gate: no second copy to drift). */
class PlainMarkdownTest {
    @Test
    fun `W4 a reply's list marks are dropped from its one-line preview`() {
        assertEquals("Folder: second/ Files: bg5.txt", PlainMarkdown.oneLine("**Folder:**\n- `second/`\nFiles:\n- bg5.txt"))
        assertEquals("a link and bold", PlainMarkdown.oneLine("# a [link](http://x) and **bold**"))
    }

    @Test
    fun `W4 plain keeps the lines and words that are not marks`() {
        assertEquals("\nval a = 2 * 3\nquoted _as is_", PlainMarkdown.plain("```kotlin\nval a = 2 * 3\n> quoted _as is_"))
    }
}
