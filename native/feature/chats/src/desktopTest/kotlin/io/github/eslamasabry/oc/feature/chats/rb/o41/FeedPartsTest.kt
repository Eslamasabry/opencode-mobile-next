package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.frame.KitHeadingLine
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.row.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FeedPartsTest {
    @Test fun aSourceSwitchWithLeadingGlyphKeepsItsSwitchBehavior() = runComposeUiTest {
        var checked = false
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { KitSwitchRow("Laptop", false, leading = KitRowLeading.Glyph(io.github.eslamasabry.oc.ui.kit.icon.KitIcons.server), onCheckedChange = { checked = it }) } }
        onNodeWithText("Laptop").performClick()
        assertTrue(checked)
    }

    @Test fun feedRowIsOneAccessibleTargetWithAllItsContext() = runComposeUiTest {
        var opened = 0
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { KitFeedItem("Fix login", "alpha", "2 min ago", KitRowState("Needs you", KitRowStateTone.NeedsYou), { opened++ }) } }
        onNodeWithContentDescription("Fix login, Needs you, alpha, 2 min ago").performClick()
        assertEquals(1, opened)
    }
    @Test fun headingLineOpensInbox() = runComposeUiTest {
        var opened = 0
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { KitHeadingLine("2 need you", { opened++ }) } }
        onNodeWithText("2 need you").performClick()
        assertEquals(1, opened)
    }
    @Test fun largeTitleHasNoEmptyToolbarAboveIt() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { KitTopBar("Conversations", large = true) } }
        assertTrue(onNodeWithText("Conversations").fetchSemanticsNode().boundsInRoot.top < 32)
    }
}
