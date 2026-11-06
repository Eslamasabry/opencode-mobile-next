@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("DEPRECATION")

package io.github.eslamasabry.oc.ui.kit.rb.o48

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitCopyButton
import io.github.eslamasabry.oc.ui.kit.action.KitCopyMask
import io.github.eslamasabry.oc.ui.kit.action.KitCopyText
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.*

class CopyButtonTest {
    @Test
    fun `copy reads the current value on tap and returns from its copied state`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var writes = 0
            var value = "first redacted command"
            var copied: String? = null
            val clipboard =
                object : ClipboardManager {
                    override fun getText(): AnnotatedString? = null

                    override fun setText(annotatedString: AnnotatedString) {
                        writes++
                        copied = annotatedString.text
                    }
                }
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        KitCopyButton("Copy command", "Copied", { value })
                    }
                }
            }
            assertEquals(0, writes)
            value = "current redacted command"
            onNodeWithText("Copy command").performClick()
            mainClock.advanceTimeByFrame()
            onNodeWithText("Copied").assertExists()
            assertEquals(value, copied)
            mainClock.advanceTimeBy(
                io.github.eslamasabry.oc.ui.design.motion.MotionTokens.COPIED_HOLD.toLong() + 100
            )
            onNodeWithText("Copy command").assertExists()
            assertEquals(1, writes)
        }

    @Test
    fun `B-SEC-145 a masking copy writes the mask's text and its copied state says something was hidden`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var copied: String? = null
            val clipboard =
                object : ClipboardManager {
                    override fun getText(): AnnotatedString? = null

                    override fun setText(annotatedString: AnnotatedString) {
                        copied = annotatedString.text
                    }
                }
            val mask = KitCopyMask { KitCopyText(it.replace("sk-fake-0000", "•••"), masked = "sk-fake-0000" in it) }
            var value = "deploy --key sk-fake-0000"
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        KitCopyButton(
                            "Copy command",
                            "Copied",
                            { value },
                            mask = mask,
                            maskedLabel = "Copied, key hidden",
                        )
                    }
                }
            }
            onNodeWithText("Copy command").performClick()
            mainClock.advanceTimeByFrame()
            assertEquals("deploy --key •••", copied)
            onNodeWithText("Copied, key hidden").assertExists()
            mainClock.advanceTimeBy(
                io.github.eslamasabry.oc.ui.design.motion.MotionTokens.COPIED_HOLD.toLong() + 100
            )
            value = "npm test"
            onNodeWithText("Copy command").performClick()
            mainClock.advanceTimeByFrame()
            assertEquals("npm test", copied)
            onNodeWithText("Copied").assertExists()
        }
}
