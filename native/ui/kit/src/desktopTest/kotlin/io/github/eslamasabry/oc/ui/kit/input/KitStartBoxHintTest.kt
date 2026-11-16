@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** I147-HINT: the New conversation box always says what it is for when it looks empty, in Arabic (right to left) too. */
class KitStartBoxHintTest {
    private fun hintShown(locale: String, question: String, typed: String) = runComposeUiTest {
        val field = TextFieldState(typed)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), locale, stillFrame = true) {
                InWindow(360, 780) { KitStartBox(field, question, autofocus = false) }
            }
        }
        waitForIdle()
        val hint = onNodeWithTag(START_BOX_HINT_TAG, useUnmergedTree = true)
        hint.assertIsDisplayed()
        assertTrue(hint.fetchSemanticsNode().size.width > 0, "the hint has width")
    }

    @Test fun `I147-HINT-1 the empty box shows its Arabic question`() =
        hintShown("ar", "ماذا تريد أن تبني؟", "")

    @Test fun `I147-HINT-2 a box holding only a space still shows its question`() =
        hintShown("ar", "ماذا تريد أن تبني؟", " ")

    @Test fun `I147-HINT-3 a box holding only a line break still shows its English question`() =
        hintShown("en", "What would you like to build?", "\n")
}
