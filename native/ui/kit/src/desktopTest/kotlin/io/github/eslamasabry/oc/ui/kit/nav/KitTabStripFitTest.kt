package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A strip's words are the kit's label size (capped at 1.3x), and a strip that does not fit scrolls to the chosen tab. */
@OptIn(ExperimentalTestApi::class)
class KitTabStripFitTest {
    private val long = listOf("Appearance and looks", "Language and region", "Notifications", "Text & motion")

    @Composable
    private fun Strip(fontScale: Float, width: Int, selected: Int) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(width, 200) { KitTabStrip(long.map { KitTab(it) }, selected, {}) }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.labelSize(text: String): Float {
        val node = onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().first()
        val layout = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
        return layout.first().layoutInput.style.fontSize.value
    }

    @Test
    fun `a pill label is 13 at the system size and never above 1_3 times that`() = runComposeUiTest {
        setContent { Strip(1f, 412, 0) }
        waitForIdle()
        assertEquals(13f, labelSize("Notifications"), 0.01f, "label size at scale 1")
    }

    @Test
    fun `a pill label stops growing at 1_3 times`() = runComposeUiTest {
        setContent { Strip(2f, 412, 0) }
        waitForIdle()
        assertEquals(16.9f, labelSize("Notifications"), 0.05f, "label size at scale 2")
    }

    @Test
    fun `at 1_3 times and 360 wide the last of four long tabs is fully visible once chosen`() = runComposeUiTest {
        var selected by mutableStateOf(0)
        setContent { Strip(1.3f, 360, selected) }
        waitForIdle()
        selected = 3
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val strip = onNodeWithTag("kit-tab-strip").fetchSemanticsNode().boundsInRoot
        val last = onAllNodes(hasContentDescription("Text & motion")).fetchSemanticsNodes().first().boundsInRoot
        assertTrue(last.left >= strip.left - 0.5f && last.right <= strip.right + 0.5f, "last tab $last inside the strip $strip")
    }
}
