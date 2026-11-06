package io.github.eslamasabry.oc.ui.kit.rb.o41

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NewConversationPartsTest {
    @Test fun `B07 hero Start grows to 56 dp and keeps its control cap and dispatch guard`() = runComposeUiTest {
        val enabled = mutableStateOf(true)
        var starts = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                Box(Modifier.width(1000.dp)) {
                    KitButton("Start with OpenCode", { starts++ }, hero = true,
                        enabled = if (enabled.value) KitEnabled.Enabled else KitEnabled.Disabled("Choose a project"))
                }
            }
        }
        val button = onNodeWithTag("kit-button-hero")
        val bounds = button.getBoundsInRoot()
        assertEquals(400.dp, bounds.right - bounds.left)
        assertEquals(56.dp, bounds.bottom - bounds.top)
        button.performClick()
        assertEquals(1, starts)
        runOnIdle { enabled.value = false }
        button.assertIsNotEnabled().performClick()
        assertEquals(1, starts)
    }

    @Test fun `B07 prominent row values use the larger reading type without changing quiet values`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                Column {
                    KitRow("Model", trailing = KitRowTrailing.Value("Server default", prominent = true))
                    KitRow("Other", trailing = KitRowTrailing.Value("Quiet value"))
                }
            }
        }
        val prominent = mutableListOf<TextLayoutResult>()
        val quiet = mutableListOf<TextLayoutResult>()
        onNodeWithText("Server default", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(prominent) }
        onNodeWithText("Quiet value", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(quiet) }
        assertTrue(prominent.single().layoutInput.style.fontSize > quiet.single().layoutInput.style.fontSize)
    }
}
