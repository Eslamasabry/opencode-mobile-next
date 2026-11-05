package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitTeachingRow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitTeachingRowTest {
    @Test
    fun `B-NAV-068 Show me replays the tip and its bubble dismisses independently`() =
        runComposeUiTest {
            var replaying by mutableStateOf(false)
            var shown = 0
            var dismissed = 0
            setContent {
                KitTheme(
                    KitAppearance(motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    KitTeachingRow(
                        "Terminal links",
                        KitIcons.idea,
                        "Show me",
                        {
                            shown++
                            replaying = true
                        },
                        "Tap a link to open it.",
                        replaying,
                        {
                            dismissed++
                            replaying = false
                        },
                        Modifier.width(412.dp),
                    )
                }
            }
            onNodeWithText("Terminal links").assertHasNoClickAction()
            onNodeWithText("Tap a link to open it.").assertDoesNotExist()
            onNodeWithText("Show me").performClick()
            onNodeWithTag("kit-tip").assertExists().performTouchInput { click() }
            onNodeWithTag("kit-tip").assertDoesNotExist()
            assertEquals(1, shown)
            assertEquals(1, dismissed)
        }
}
