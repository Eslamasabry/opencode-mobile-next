@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("DEPRECATION")

package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitContextMessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E15-S04: server text (commands, tool output, reasoning, skill text) is masked before it reaches the clipboard, as
 * Flutter's `KitCopy.copy` does by default (`redact: true`), and the copied state says when something was hidden.
 * Every key here is an obviously fake value.
 */
class KitCopyTest {
    private val fakeKey = "sk-ant-api03-AbCdEfGhIjKlMnOpQrSt"
    private val maskedWords = "Copied. Anything that looked like a password or key was hidden."

    /** A recording clipboard: a headless test JVM's system clipboard reads back nothing. */
    private class RecordingClipboard : ClipboardManager {
        val writes = mutableListOf<String>()

        override fun getText(): AnnotatedString? = writes.lastOrNull()?.let(::AnnotatedString)

        override fun setText(annotatedString: AnnotatedString) {
            writes += annotatedString.text
        }
    }

    @Test
    fun `B-SEC-145 the redacting copy masks a key and says so, and leaves plain text alone`() {
        val redactor = Redactor()
        val command = "curl -H 'x-api-key: $fakeKey' https://api.example.com/v1/messages"
        val masked = KitCopy.safeText(redactor, command)
        assertTrue(masked.masked)
        assertFalse(fakeKey in masked.text, masked.text)
        assertTrue(masked.text.startsWith("curl -H "), masked.text)
        val plain = KitCopy.safeText(redactor, "npm install --save-dev vitest")
        assertEquals("npm install --save-dev vitest", plain.text)
        assertFalse(plain.masked)
    }

    @Test
    fun `B-SEC-145 skill text copied from the Library has its key masked`() {
        // LibraryWiring copies through KitCopy.safeText(redactor, text): the same mask, checked here on skill markdown.
        val skill = "# Deploy\n\nRun `ANTHROPIC_API_KEY=$fakeKey ./deploy.sh` then check the logs.\n"
        val copied = KitCopy.safeText(Redactor(), skill)
        assertTrue(copied.masked)
        assertFalse(fakeKey in copied.text, copied.text)
        assertTrue(copied.text.startsWith("# Deploy"), copied.text)
    }

    @Test
    fun `B-SEC-145 the permission prompt copies its requested command masked and says a key was hidden`() =
        runComposeUiTest {
            val clipboard = RecordingClipboard()
            val command = "export ANTHROPIC_API_KEY=$fakeKey && npm run deploy"
            setContent {
                ChatTestTheme {
                    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        KitPermissionCard(
                            title = "Run a command?",
                            summary = null,
                            subject = null,
                            allowOnceLabel = "Allow once",
                            alwaysLabel = null,
                            denyLabel = "Deny",
                            onAllowOnce = {},
                            onAlways = null,
                            onDeny = {},
                            targets = listOf(KitRequestTarget(command)),
                            copyLabel = "Copy command",
                            copiedLabel = "Copied",
                        )
                    }
                }
            }
            onNodeWithContentDescription("Copy command").performClick()
            runOnIdle {
                val copied = assertNotNull(clipboard.writes.singleOrNull())
                assertFalse(fakeKey in copied, copied)
                assertTrue(copied.startsWith("export ANTHROPIC_API_KEY="), copied)
            }
            onNodeWithContentDescription(maskedWords).assertExists()
        }

    @Test
    fun `B-SEC-145 a permission command with nothing secret copies as asked and just says Copied`() =
        runComposeUiTest {
            val clipboard = RecordingClipboard()
            setContent {
                ChatTestTheme {
                    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        KitPermissionCard(
                            title = "Run a command?",
                            summary = null,
                            subject = null,
                            allowOnceLabel = "Allow once",
                            alwaysLabel = null,
                            denyLabel = "Deny",
                            onAllowOnce = {},
                            onAlways = null,
                            onDeny = {},
                            targets = listOf(KitRequestTarget("npm install"), KitRequestTarget("npm test")),
                            copyLabel = "Copy command",
                            copiedLabel = "Copied",
                        )
                    }
                }
            }
            onNodeWithContentDescription("Copy command").performClick()
            runOnIdle { assertEquals(listOf("npm install\nnpm test"), clipboard.writes) }
            onNodeWithContentDescription("Copied").assertExists()
        }

    @Test
    fun `B-SEC-145 Active Context copies tool input and reasoning masked and says a key was hidden`() =
        runComposeUiTest {
            val clipboard = RecordingClipboard()
            val toolInput = """{"command": "curl -H 'Authorization: Bearer $fakeKey' https://example.com"}"""
            val reasoning = "The key $fakeKey is in the env file, so I will read it from there."
            setContent {
                ChatTestTheme {
                    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        androidx.compose.foundation.layout.Column {
                            KitContextMessagePart("Tool input · bash", toolInput, "Copy tool input", "No text", technical = true)
                            KitContextMessagePart("Reasoning", reasoning, "Copy reasoning", "No text")
                        }
                    }
                }
            }
            onNodeWithContentDescription("Copy tool input").performClick()
            onNodeWithContentDescription("Copy reasoning").performClick()
            runOnIdle {
                assertEquals(2, clipboard.writes.size)
                for (copied in clipboard.writes) assertFalse(fakeKey in copied, copied)
                assertTrue(clipboard.writes[1].startsWith("The key "), clipboard.writes[1])
            }
            onAllNodesWithContentDescription(maskedWords).assertCountEquals(2)
        }
}
