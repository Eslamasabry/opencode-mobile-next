package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KitInlineStateViewTest {
    @Test
    fun `B-NAV-068 inline state keeps its explanation on the heading rail and retries once`() =
        runComposeUiTest {
            var retries = 0
            setContent {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    KitInlineStateView(
                        title = "Couldn’t load references",
                        icon = KitIcons.bookmark,
                        body = "Connect to the server, then try again.",
                        primary = KitAction("Retry", { retries++ }),
                    )
                }
            }
            val title = onNodeWithText("Couldn’t load references").fetchSemanticsNode().boundsInRoot
            val body =
                onNodeWithText("Connect to the server, then try again.")
                    .fetchSemanticsNode()
                    .boundsInRoot
            assertEquals(title.left, body.left)
            assertTrue(body.top > title.bottom)
            onNodeWithText("Retry").performClick()
            assertEquals(1, retries)
        }
}
