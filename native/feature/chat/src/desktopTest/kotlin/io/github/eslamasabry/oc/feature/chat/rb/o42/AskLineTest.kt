package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitAskLine
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AskLineTest {
    @Test fun `C12 narrow invitation shows both answers and dispatches only the selected answer`() = runComposeUiTest {
        var accepts = 0
        var declines = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                Box(Modifier.width(320.dp)) {
                    KitAskLine(KitIcons.privacy, "Asked 3 times. Always allow npm install?",
                        accept = KitAction("Always allow", { accepts++ }),
                        decline = KitAction("Keep asking", { declines++ }))
                }
            }
        }
        onNodeWithTag("kit-ask-line-stacked").assertExists()
        onNodeWithText("Always allow").assertIsDisplayed()
        onNodeWithText("Keep asking").performClick()
        assertEquals(0, accepts)
        assertEquals(1, declines)
    }

    @Test fun `C12 short invitation fits inline at conversation width`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                Box(Modifier.width(700.dp)) {
                    KitAskLine(KitIcons.privacy, "Allow this?", KitAction("Allow", {}), KitAction("Ask", {}))
                }
            }
        }
        onNodeWithTag("kit-ask-line-inline").assertExists()
    }
}
