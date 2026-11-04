package io.github.eslamasabry.oc.engine.runtime.messages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/setup_ui_messages_test.dart (B-L10N-019); the Arabic words are read back in :ui:words SetupWordsTest. */
class SetupMessagesTest {
    @Test
    fun `B-L10N-019 English keeps actionable validation detail and unknown detail verbatim`() {
        // validateServerProfileUrl("https://remote.example/api") answers this sentence (lib/state/pairing.dart).
        assertEquals(
            SetupMessage.Known("e7SetupUrlPath"),
            SetupMessages.classify("Remove the path from the server URL. Enter only its origin."),
        )
        // The exit code is a status value: it is kept as the argument.
        assertEquals(
            SetupMessage.Known("e7SetupServerExit", listOf("17")),
            SetupMessages.classify("OpenCode server exited (code 17)"),
        )
        // A script's own detail is not the app's sentence: it stays exactly as written.
        assertEquals(
            SetupMessage.Mixed(listOf(SetupSegment.Text("custom server detail: /work/hello"))),
            SetupMessages.classify("custom server detail: /work/hello"),
        )
    }

    @Test
    fun `B-L10N-019 parser and manager facts map to their words keys`() {
        // parsePairingPayload("not a pairing payload").error (lib/state/pairing.dart).
        assertEquals(
            SetupMessage.Known("e7SetupPairInvalid"),
            SetupMessages.classify(
                "That is not a pairing code. Run `opencode2 pair` on the server and scan or copy what it prints.",
            ),
        )
        assertEquals(SetupMessage.Known("e7SetupInstallingOpenCode"), SetupMessages.classify("Installing OpenCode"))
        assertEquals(
            SetupMessage.Known("e7SetupProbeHttp", listOf("404")),
            SetupMessages.classify(
                "The address responded, but not like an OpenCode server (HTTP 404). " +
                    "Check that the URL points at opencode serve.",
            ),
        )
    }

    @Test
    fun `B-L10N-019 a long app sentence inside other text is swapped in place, a short label never splits prose`() {
        val sentence = "Setup stopped before it finished. Continue setup to try again. Completed steps will be kept."
        val mixed = SetupMessages.classify("Phone said: $sentence (exit 3)") as SetupMessage.Mixed
        assertEquals(
            listOf(
                SetupSegment.Text("Phone said: "),
                SetupSegment.Word("phoneSetupErrorInterrupted"),
                SetupSegment.Text(" (exit 3)"),
            ),
            mixed.segments,
        )
        // "Installing OpenCode" is shorter than 24 characters: inside prose it stays as written.
        val prose = SetupMessages.classify("Still Installing OpenCode here") as SetupMessage.Mixed
        assertEquals(listOf(SetupSegment.Text("Still Installing OpenCode here")), prose.segments)
    }

    @Test
    fun `B-L10N-019 plain words only for a whole app message, transport words stay under Details`() {
        assertNull(SetupMessages.plain("Connection test failed: timeout"))
        assertNull(SetupMessages.plain("custom server detail: /work/hello"))
        assertEquals(SetupMessage.Known("e7SetupInstallingOpenCode"), SetupMessages.plain("  Installing OpenCode "))
        assertTrue(SetupMessageTable.entries.size > 200)
    }
}
