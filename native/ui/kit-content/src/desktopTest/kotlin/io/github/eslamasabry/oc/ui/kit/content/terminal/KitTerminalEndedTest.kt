package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.ContentFakePlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-18 (sign-in page): after the program ended, an empty disabled "Send command to terminal" row and a
 * second "Terminal input unavailable" stayed under the text, and the text and field touched the screen edge.
 */
@OptIn(ExperimentalTestApi::class)
class KitTerminalEndedTest {
    private val words =
        KitTerminalWords(
            terminal = "Terminal",
            accessibleMode = "Show as readable text",
            transcript = "Terminal transcript",
            noOutput = "No terminal output yet.",
            sendToTerminal = "Send to terminal",
            send = "Send",
            inputUnavailable = "Terminal input unavailable",
        )

    @Test
    fun `B-AGENT-447 an ended program's text shows with no field, no Send, no switch and no link row`() =
        runComposeUiTest {
            setContent {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = ContentFakePlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    CompositionLocalProvider(LocalTerminalLinkHost provides TerminalLinkHost { null }) {
                        Box(Modifier.width(360.dp).height(500.dp)) {
                            KitTerminalView(
                                platformView = null,
                                title = "claude",
                                transcript = "visit https://claude.com/x?y=1\nLogin successful.",
                                accessibleMode = true,
                                onAccessibleModeChange = {},
                                onSend = {},
                                words = words,
                                inputEnabled = false,
                                defaultKeyBar = false,
                                showAccessibleSwitch = false,
                                showInput = false,
                                showLinks = false,
                                gutter = true,
                            )
                        }
                    }
                }
            }
            onAllNodesWithText("Login successful.", substring = true).assertCountEquals(1)
            onAllNodesWithTag(KitTerminalTags.INPUT).assertCountEquals(0)
            onAllNodesWithTag(KitTerminalTags.SEND).assertCountEquals(0)
            onAllNodesWithText("Terminal input unavailable", substring = true).assertCountEquals(0)
            onAllNodesWithTag("terminal-links").assertCountEquals(0)
        }

    @Test
    fun `B-AGENT-450 with the page gutter the text and the field keep off the window edge`() =
        runComposeUiTest {
            var gutter = 0f
            setContent {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = ContentFakePlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    gutter = LocalKitTokens.current.gutter.toFloat()
                    Box(Modifier.width(360.dp).height(500.dp)) {
                        KitTerminalView(
                            platformView = null,
                            title = "claude",
                            transcript = "Paste code here if prompted >",
                            accessibleMode = true,
                            onAccessibleModeChange = {},
                            onSend = {},
                            words = words,
                            defaultKeyBar = false,
                            gutter = true,
                        )
                    }
                }
            }
            val field = onNodeWithTag(KitTerminalTags.INPUT).getUnclippedBoundsInRoot()
            val send = onNodeWithTag(KitTerminalTags.SEND).getUnclippedBoundsInRoot()
            val text = onNodeWithTag(KitTerminalTags.TRANSCRIPT).getUnclippedBoundsInRoot()
            assertTrue(field.left.value >= gutter - 0.5f, "field starts at ${field.left}")
            assertTrue(text.left.value >= gutter - 0.5f, "text starts at ${text.left}")
            assertTrue(send.right.value <= 360f - gutter + 0.5f, "Send ends at ${send.right}")
        }
}
