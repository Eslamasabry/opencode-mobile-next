package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitStartBox
import io.github.eslamasabry.oc.ui.kit.input.startBoxLines
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** lib/ui/kit/kit_start_box.dart at 8302de537: an 80 dp box, two lines growing to six, the question as the field's name. */
@OptIn(ExperimentalTestApi::class)
class KitStartBoxTest {
    private val question = "What would you like to build?"

    @Test
    fun `B-KIT-280 the field grows four lines past its least`() {
        assertEquals(2..6, startBoxLines(2))
        assertEquals(4..8, startBoxLines(4))
    }

    @Test
    fun `B-KIT-281 the empty box is Flutter's 80 dp tall and typing reports the words`() = runComposeUiTest {
        val field = TextFieldState()
        val heard = mutableListOf<String>()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                KitStartBox(field, question, onTextChanged = { heard += it })
            }
        }
        val node = onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(question)))
        // The field itself: 80 dp less the 16 dp above and below (Dart padding space4).
        val bounds = node.getBoundsInRoot()
        assertEquals(48.dp, bounds.bottom - bounds.top)
        node.performTextInput("A neon brick breaker")
        waitForIdle()
        assertEquals("A neon brick breaker", field.text.toString())
        assertEquals(listOf("A neon brick breaker"), heard)
    }
}
