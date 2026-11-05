@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.agents.rb.o49

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import kotlin.test.Test
import kotlin.test.assertEquals

class InlineStateTest {
    @Test fun `a full allowance states the limit in words as well as its measured bar`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            io.github.eslamasabry.oc.ui.kit.state.KitProgressRow("Codex allowance", 1f, valueLabel = "100% used")
        } }
        onNodeWithText("100% used · Limit reached", useUnmergedTree = true).assertExists()
        onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo, androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f)), useUnmergedTree = true).assertExists()
    }

    @Test fun `an inline state reads its body and dispatches its single primary action`() = runComposeUiTest {
        var called = 0
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            KitInlineState("Ready to sign in", KitIcons.account, body = "Finish in the browser", primary = KitAction("Sign in", { called++ }))
        } }
        onNodeWithText("Finish in the browser").assertExists()
        onNodeWithText("Sign in").performClick()
        assertEquals(1, called)
    }
}
