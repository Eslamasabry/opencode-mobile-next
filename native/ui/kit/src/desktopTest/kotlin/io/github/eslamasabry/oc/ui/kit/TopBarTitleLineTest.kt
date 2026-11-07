package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E20-S10 (owner's phone): in the chat header the "new · This phone" line sat far below the "Hi" title, on a row of its
 * own. Under a Back bar's title it now starts the kit's tight title/subtitle step (2 dp) under the title's words.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarTitleLineTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.gap(line: String): Float {
        val title = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val under = onNodeWithText(line, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return under.top - title.bottom
    }

    @Test
    fun `E20-S10 the chat header's project and server line sits right under a tappable title, and both still take taps`() =
        runComposeUiTest {
            var renamed = 0
            var project = 0
            setContent {
                Themed {
                    KitTopBar(
                        "Hi",
                        scope = { KitTopBarScope("new", "This phone", "Files of new", onProject = { project++ }) },
                        exit = KitTopBarExit.Back,
                        exitLabel = "Back",
                        onExit = {},
                        onTitleTap = { renamed++ },
                        titleTapLabel = "Rename",
                    )
                }
            }
            val gap = gap("new · This phone")
            assertTrue(gap in 1f..3f, "the line starts the kit's 2 dp step under the title's words, was $gap")
            onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).performClick()
            onNodeWithText("new · This phone", useUnmergedTree = true).performClick()
            assertEquals(1, renamed, "the title's own words still rename")
            assertEquals(1, project, "the line's words still open the project")
        }

    @Test
    fun `E20-S10 a plain subtitle under a Back bar's title takes the same tight step`() = runComposeUiTest {
        setContent {
            Themed {
                KitTopBar("Hi", subtitle = "shopfront", exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {})
            }
        }
        val gap = gap("shopfront")
        assertTrue(gap in 1f..3f, "the subtitle starts the kit's 2 dp step under the title's words, was $gap")
    }
}
