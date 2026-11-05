@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.ui.kit.rb.o40

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class HeroButtonTest {
    @Test fun `the hero keeps its larger target and ordinary click behavior`() = runComposeUiTest {
        var called = false
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            KitButton("Set up OpenCode", { called = true }, hero = true, fullWidth = true)
        } }
        onNodeWithText("Set up OpenCode").assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(true, called)
    }
}
