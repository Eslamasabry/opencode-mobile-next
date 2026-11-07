package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentRow
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentSwatch
import kotlin.test.Test
import kotlin.test.assertEquals

/** kit_option_card.dart `KitAccentRow`: Match is null, a swatch is its id, the chosen one does not call again. */
@OptIn(ExperimentalTestApi::class)
class KitAccentRowBehaviourTest {
    private val swatches = listOf(KitAccentSwatch("teal", Color(0xFF3CCFCF), "Teal"), KitAccentSwatch("blue", Color(0xFF5AB0FF), "Blue"))

    private fun run(grid: Boolean) = runDesktopComposeUiTest {
        val calls = mutableListOf<String?>()
        setContent {
            KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                KitAccentRow("Accent", "Match theme", Color(0xFF3DDC97), swatches, selected = "blue", onChanged = { calls += it }, grid = grid)
            }
        }
        onNodeWithContentDescription("Teal").performClick()
        onNodeWithContentDescription("Blue").performClick()
        onNodeWithContentDescription("Match theme").performClick()
        assertEquals(listOf("teal", null), calls)
        // One chosen swatch at a time.
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(1)
    }

    @Test fun `B-KIT-OC-001 the accent row reports a swatch's id and null for Match`() = run(grid = false)

    @Test fun `B-KIT-OC-002 the accent grid reports a swatch's id and null for Match`() = run(grid = true)
}
