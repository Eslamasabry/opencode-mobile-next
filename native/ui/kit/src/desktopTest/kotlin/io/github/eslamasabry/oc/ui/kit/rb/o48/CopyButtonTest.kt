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
}
