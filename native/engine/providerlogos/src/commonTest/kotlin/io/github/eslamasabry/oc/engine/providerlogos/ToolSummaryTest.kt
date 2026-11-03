package io.github.eslamasabry.oc.engine.providerlogos

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolSummaryTest {
    @Test
    fun `B-NAV-076 a tool summary is the first sentence of its description`() {
        assertEquals(
            "Use this tool when you need to ask the user questions during execution.",
            toolSummary(
                "Use this tool when you need to ask the user questions during " +
                    "execution. This allows you to:\n1. Gather preferences",
            ),
        )
        assertEquals(
            "Fast file pattern matching tool that works with any codebase size",
            toolSummary(
                "- Fast file pattern matching tool that works with any codebase " +
                    "size\n- Supports glob patterns like \"**/*.js\"",
            ),
        )
        assertEquals("Reads a file.", toolSummary("Reads a file.\n\nUsage:\n- The path must be absolute"))
        assertEquals(
            "Executes a given bash command in a persistent shell.",
            toolSummary("Executes a given bash command in a\npersistent shell. More."),
        )
        assertEquals("", toolSummary("  "))
        assertEquals("Version 1.2 of the tool", toolSummary("Version 1.2 of the tool"))
    }

    @Test
    fun `B-NAV-076 list markers, sentence ends and whitespace follow the Flutter rules`() {
        assertEquals("", toolSummary(""))
        assertEquals("Lists files!", toolSummary("* Lists files! Then more"))
        assertEquals("Why?", toolSummary("• Why? Because"))
        assertEquals("Step one", toolSummary("1) Step one\n2) Step two"))
        assertEquals("Step one.", toolSummary("12. Step one. Done"))
        // A marker needs whitespace after it; a dash inside the first line is kept.
        assertEquals("-flag sets x", toolSummary("-flag sets x"))
        // Continuation lines join with one space; tabs and CRLF collapse.
        assertEquals("Joins two lines", toolSummary("  Joins\ttwo \r\n  lines  "))
        // The next list item ends the first thought, but only after something was collected.
        assertEquals("Intro line", toolSummary("Intro line\n- item"))
        // A sentence end needs whitespace or the end after it.
        assertEquals("See a.b.c and stop.", toolSummary("See a.b.c and stop. Next"))
        // Unicode spaces count as whitespace (Dart RegExp \s).
        assertEquals("Ends here.", toolSummary("Ends here. Next"))
        assertEquals("Ends here.", toolSummary("　Ends here."))
    }
}
