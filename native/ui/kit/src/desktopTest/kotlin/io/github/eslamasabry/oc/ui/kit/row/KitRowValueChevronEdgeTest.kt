package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-549 (polish sweep #6): from 1.3x text a row's value drops under its title, and the chevron that came with the
 * value dropped with it, beside the words, while the plain chevron rows around it kept the row's end line.
 */
@OptIn(ExperimentalTestApi::class)
class KitRowValueChevronEdgeTest {
    private fun endLines(
        scale: Float,
        locale: String,
        underTitle: Boolean,
        end: (DpRect) -> Float,
    ) = runDesktopComposeUiTest(360, 2600) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = FakeKitPlatform(),
                    localeTag = locale,
                    stillFrame = true,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        KitRow("Project scope", trailing = KitRowTrailing.Value("All projects"), onClick = {})
                        KitRow("Notifications", trailing = KitRowTrailing.Chevron, onClick = {})
                    }
                }
            }
        }
        val chevrons = onAllNodesWithTag("kit-row-chevron", useUnmergedTree = true)
        assertEquals(2, chevrons.fetchSemanticsNodes().size, "one chevron per row")
        val values = onAllNodesWithTag("kit-row-value", useUnmergedTree = true)
        assertEquals(1, values.fetchSemanticsNodes().size)
        onNodeWithText("All projects").assertExists()
        if (underTitle) {
            val title = onAllNodesWithTag("kit-row-title", useUnmergedTree = true)[0].getBoundsInRoot()
            val value = values[0].getBoundsInRoot()
            assertTrue(value.top >= title.bottom, "the value sits under its title: title $title, value $value")
        }
        val valueRow = chevrons[0].getBoundsInRoot()
        val plainRow = chevrons[1].getBoundsInRoot()
        assertTrue(abs(end(valueRow) - end(plainRow)) < 0.5f, "value row $valueRow, plain row $plainRow")
    }

    @Test
    fun `B-ROW-549 a value row's chevron ends on the plain chevron row's line at large text`() =
        endLines(1.3f, "en", underTitle = true) { it.right.value }

    @Test
    fun `B-ROW-549 in RTL a value row's chevron ends on the plain chevron row's line at large text`() =
        endLines(1.3f, "ar", underTitle = true) { it.left.value }

    @Test
    fun `B-ROW-549 beside its title, a value keeps its chevron on the plain chevron row's line`() =
        endLines(1f, "en", underTitle = false) { it.right.value }
}
