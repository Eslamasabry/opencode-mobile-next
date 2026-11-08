@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.ui.kit.rb.o48

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitCheckedInputDialog
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckedInputDialogTest {
    @Test fun `an asynchronous rejection retains the checked input and permits correction`() = runComposeUiTest {
        val field = TextFieldState("htop")
        var checked = 0
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            KitScreen(page = true, edges = KitScreenEdges.Bleed) {
                KitCheckedInputDialog(true, "Install", field, "Package", "Check", { checked++ }, {}, error = "Not available")
            }
        } }
        onNodeWithText("Not available").assertExists()
        onNode(hasSetTextAction()).performTextReplacement("git")
        onNodeWithText("Check").performClick()
        assertEquals("git", field.text.toString())
        assertEquals(1, checked)
    }
}
