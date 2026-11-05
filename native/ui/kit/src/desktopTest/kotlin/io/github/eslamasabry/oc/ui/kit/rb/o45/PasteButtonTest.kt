@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("DEPRECATION")

package io.github.eslamasabry.oc.ui.kit.rb.o45

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitPasteButton
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.*

class PasteButtonTest {
    @Test
    fun `clipboard is read only on the visible paste action and empty is forwarded`() =
        runComposeUiTest {
            var reads = 0
            var pasted: String? = "not called"
            var source: AnnotatedString? = AnnotatedString("synthetic pairing text")
            val clipboard =
                object : ClipboardManager {
                    override fun getText(): AnnotatedString? {
                        reads++
                        return source
                    }

                    override fun setText(annotatedString: AnnotatedString) {
                        source = annotatedString
                    }
                }
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        KitPasteButton("Paste code", { pasted = it })
                    }
                }
            }
            assertEquals(0, reads)
            onNodeWithText("Paste code").performClick()
            assertEquals("synthetic pairing text", pasted)
            source = null
            onNodeWithText("Paste code").performClick()
            assertNull(pasted)
            assertEquals(2, reads)
        }
}
