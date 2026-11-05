@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o49

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.*
import kotlin.test.Test

class PageStateTest {
    @Test
    fun `a page state also has finite natural height inside a scrolling list`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    KitPageState(
                        KitStateKind.Missing,
                        "Agents run on the built-in server",
                        icon = KitIcons.phone,
                    )
                }
            }
        }
        onNodeWithText("Agents run on the built-in server").assertIsDisplayed()
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
