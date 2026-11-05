package io.github.eslamasabry.oc.feature.chatcomposer.rb.o43

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chatcomposer.ui.PromptEditorScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PromptEditorTest {
    @Test fun useInDraftKeepsTheSharedFieldWords() = runComposeUiTest {
        val field = TextFieldState("original")
        var closed = 0
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { PromptEditorScreen(true, field, { closed++ }) } }
        onNode(hasSetTextAction()).performTextReplacement("edited")
        onNodeWithText("Use in draft").performClick()
        assertEquals("edited", field.text.toString())
        assertEquals(1, closed)
    }
    @Test fun closeWithChangesAsksAndDiscardRestoresOriginalWords() = runComposeUiTest {
        val field = TextFieldState("original")
        var closed = 0
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { PromptEditorScreen(true, field, { closed++ }) } }
        onNode(hasSetTextAction()).performTextReplacement("edited")
        onNodeWithContentDescription("Close").performClick()
        assertEquals(0, closed)
        onNodeWithText("Discard changes").performClick()
        assertEquals("original", field.text.toString())
        assertEquals(1, closed)
    }
    @Test fun `C22 editor starts compact and grows with the prompt`() = runComposeUiTest {
        val field = TextFieldState("One line")
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { PromptEditorScreen(true, field, {}) } }
        val initialHeight = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.height
        onNode(hasSetTextAction()).performTextReplacement((1..6).joinToString("\n") { "Line $it" })
        val expandedHeight = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.height
        assertTrue(expandedHeight > initialHeight)
    }

}
