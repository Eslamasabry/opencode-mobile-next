package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.agents.context.*
import io.github.eslamasabry.oc.feature.chat.context.*
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitContextMessagePart
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ActiveContextScreenTest {
    @Test fun `C32 opens content first with copy and collapsed technical details`() = runComposeUiTest {
        val message = ActiveContextMessage("msg_checkout", "assistant", listOf(
            ContextContent(ContextContentKind.ToolOutput, "lib/checkout/checkout_page.dart: 412 lines, 3 rebuilds per frame", "read"),
            ContextContent(ContextContentKind.Text, "The cart total rebuilds the whole page on every keystroke."),
        ))
        lateinit var clipboard: ClipboardManager
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                clipboard = LocalClipboardManager.current
                KitStandaloneRedaction {
                    ActiveContextScreen(true, ActiveContextUi.Ready(persistentListOf(message)), {}, {})
                }
            }
        }
        onNodeWithText("Assistant").performClick()
        onNodeWithText("Assistant message").assertExists()
        onNodeWithTag("kit-details-body").assertDoesNotExist()
        onNodeWithText("msg_checkout").assertDoesNotExist()
        onNodeWithContentDescription("Copy Tool output · read").performClick()
        runOnIdle { assertEquals(message.content.first().text, clipboard.getText()?.text) }
        onNodeWithText("Details").performClick()
        onNodeWithText("msg_checkout").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Assistant").assertExists()
    }

    @Test fun `C32 list labels and filtering follow every keystroke`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                ActiveContextScreen(true, ActiveContextUi.Ready(persistentListOf(
                    ActiveContextMessage("u", "user", listOf(ContextContent(ContextContentKind.Text, "Profile checkout"))),
                    ActiveContextMessage("s", "compaction", listOf(ContextContent(ContextContentKind.Text, "Earlier changes"))),
                )), {}, {})
            }
        }
        onNodeWithText("User prompt").assertExists()
        onNodeWithText("Compaction").assertExists()
        mainClock.autoAdvance = false
        onNodeWithTag("kit-search-field").performTextInput("checkout")
        waitForIdle()
        onNodeWithText("User prompt").assertExists()
        onNodeWithText("Compaction").assertDoesNotExist()
    }

    @Test fun `C32 dropped parts have no copy or body and empty parts explain the absence`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                androidx.compose.foundation.layout.Column {
                    KitContextMessagePart("Pruned", "not displayed", "Copy pruned", "No text", dropped = true)
                    KitContextMessagePart("Text", "", "Copy text", "No supported text content in this entry.")
                }
            }
        }
        onNodeWithText("not displayed").assertDoesNotExist()
        onNodeWithContentDescription("Copy pruned").assertDoesNotExist()
        onNodeWithContentDescription("Copy text").assertDoesNotExist()
        onNodeWithText("No supported text content in this entry.").assertExists()
    }
}
