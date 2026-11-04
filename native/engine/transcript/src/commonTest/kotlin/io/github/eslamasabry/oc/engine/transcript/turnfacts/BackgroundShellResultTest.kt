package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports test/background_shell_result_test.dart. */
class BackgroundShellResultTest {
    private fun notice(text: String, filename: String? = null) =
        Part("n", "m", "s", PartType.Notice, rawType = "v2:notice", text = text, toolName = "synthetic", filename = filename)

    @Test
    fun `B-AGENT-033 reads the command, the output and how it ended`() {
        val result = assertNotNull(
            BackgroundShellResult.fromPart(
                notice(
                    "<shell id=\"sh_1\" state=\"completed\" command=\"/tmp/opencode/flutter/" +
                        "bin/flutter test --concurrency=1 test/a_test.dart\">\n" +
                        "00:07 +6: All tests passed!\n\n" +
                        "Command exited with code 0.\n</shell>",
                ),
            ),
        )
        assertTrue(result.command.startsWith("/tmp/opencode/flutter/bin/flutter"))
        assertEquals("00:07 +6: All tests passed!", result.output)
        assertEquals(0, result.exitCode)
        assertEquals(BackgroundShellOutcome.Finished, result.outcome)
    }

    @Test
    fun `B-AGENT-033 a non-zero exit failed and a signal is someone stopping it`() {
        fun read(code: Int) =
            assertNotNull(BackgroundShellResult.parse("<shell id=\"s\" state=\"completed\" command=\"make\">x\nCommand exited with code $code.</shell>"))
        assertEquals(BackgroundShellOutcome.Failed, read(1).outcome)
        assertEquals(BackgroundShellOutcome.Stopped, read(143).outcome)
        assertEquals(BackgroundShellOutcome.Stopped, read(130).outcome)
        assertEquals(BackgroundShellOutcome.Stopped, read(137).outcome)
    }

    @Test
    fun `B-AGENT-033 escaped quotes in the command round-trip`() {
        val result = assertNotNull(BackgroundShellResult.parse("<shell state=\"completed\" command=\"echo &quot;hi &amp; bye&quot;\">hi</shell>"))
        assertEquals("echo \"hi & bye\"", result.command)
    }

    @Test
    fun `B-AGENT-033 anything else is not a shell result`() {
        assertNull(BackgroundShellResult.fromPart(notice("Context added")))
        assertNull(BackgroundShellResult.fromPart(Part("t", "m", "s", PartType.Text, rawType = "text", text = "<shell command=\"x\">y</shell>")))
    }
}
