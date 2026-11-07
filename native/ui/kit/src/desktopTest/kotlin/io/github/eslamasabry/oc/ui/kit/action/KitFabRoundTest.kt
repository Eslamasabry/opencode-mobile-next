package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P-107: the floating "New conversation" was a 320 dp pill with its words, and on home it covered the rows' "· 9h ago".
 * Flutter's KitFloatingAction is a round 56 dp accent button with one plus; its words are heard, not drawn.
 */
@OptIn(ExperimentalTestApi::class)
class KitFabRoundTest {
    @Test
    fun `B-KIT-107 the floating action is a round 56 dp button that says its words without drawing them`() =
        runDesktopComposeUiTest(412, 200) {
            var taps = 0
            setContent { KitTheme(platform = FakeKitPlatform(), localeTag = "en") { KitFab(label = "New conversation", icon = KitIcons.add, onClick = { taps++ }) } }
            val bounds = onNodeWithTag("kit-fab").getBoundsInRoot()
            assertEquals(56.dp, bounds.right - bounds.left)
            assertEquals(56.dp, bounds.bottom - bounds.top)
            onAllNodesWithText("New conversation").assertCountEquals(0)
            onNodeWithContentDescription("New conversation").performClick()
            assertEquals(1, taps)
        }

    @Test
    fun `B-KIT-107 a floating action that cannot run yet is not floated`() =
        runDesktopComposeUiTest(412, 200) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") { KitFab(label = "New conversation", icon = KitIcons.add, onClick = {}, visible = false) }
            }
            waitForIdle()
            onAllNodesWithTag("kit-fab").assertCountEquals(0)
        }
}
