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
