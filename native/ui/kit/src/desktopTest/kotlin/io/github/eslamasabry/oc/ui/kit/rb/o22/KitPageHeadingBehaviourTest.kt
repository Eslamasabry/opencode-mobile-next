package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.assertHasNoClickAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.frame.KitPageHeading
import kotlin.test.Test
import kotlin.test.assertEquals

/** kit_page_heading.dart: the switcher line is one labelled target; a plain line is words, not a target. */
@OptIn(ExperimentalTestApi::class)
class KitPageHeadingBehaviourTest {
    @Test
    fun `B-KIT-PH-003 the switcher line opens and is announced with its tap label`() = runDesktopComposeUiTest {
        var taps = 0
        setContent {
            KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                KitPageHeading("Files", line = "neon-breaker", onLine = { taps++ }, lineTapLabel = "Project neon-breaker. Switch project")
            }
        }
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertCountEquals(1)
        val label = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))[0]
            .fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label
        assertEquals("Project neon-breaker. Switch project", label)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))[0].performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `B-KIT-PH-004 a plain line is not a target`() = runDesktopComposeUiTest {
        setContent { KitTheme(platform = FakeKitPlatform(), localeTag = "en") { KitPageHeading("levels", line = "neon-breaker / src") } }
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertCountEquals(0)
        onNodeWithText("neon-breaker / src").assertHasNoClickAction()
    }
}
