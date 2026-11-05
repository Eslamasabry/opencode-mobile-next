package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PermissionListCardTest {
    @Test fun `B02 list answers are neutral and dispatch only the displayed choice`() = runComposeUiTest {
        var allows = 0
        var rejects = 0
        var fill = Color.Unspecified
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                fill = LocalKitColors.current.surface3
                Box(Modifier.width(400.dp)) {
                    KitPermissionCard("Run tests?", null, "flutter test", "Allow once", null, "Reject",
                        { allows++ }, null, { rejects++ }, secondary = true)
                }
            }
        }
        val pixels = onNodeWithTag("permission-card-allow").captureToImage().toPixelMap()
        assertEquals(fill, pixels[12, pixels.height / 2])
        onNodeWithTag("permission-card-allow").performClick()
        assertEquals(1, allows)
        assertEquals(0, rejects)
    }

    @Test fun `B02 narrow list answers stack Allow before Reject and explain their disabled state`() = runComposeUiTest {
        var calls = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                Box(Modifier.width(220.dp)) {
                    KitPermissionCard("Run tests?", null, null, "Run this command once", null, "Do not run this command",
                        { calls++ }, null, { calls++ }, secondary = true, enabled = false, disabledReason = "Sending answer")
                }
            }
        }
        val allow = onNodeWithTag("permission-card-allow").assertIsNotEnabled().fetchSemanticsNode().boundsInRoot
        val reject = onNodeWithTag("permission-card-reject").assertIsNotEnabled().fetchSemanticsNode().boundsInRoot
        assertTrue(allow.bottom <= reject.top)
        onNodeWithText("Sending answer").assertExists()
        onNodeWithTag("permission-card-allow").performClick()
        assertEquals(0, calls)
    }
}
