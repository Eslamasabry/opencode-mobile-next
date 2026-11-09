@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.status.glyphFor
import io.github.eslamasabry.oc.ui.kit.status.statusControlsStack
import io.github.eslamasabry.oc.ui.kit.status.toneFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The rules of lib/ui/kit/kit_status_line.dart the native line keeps. */
class KitStatusLineTest {
    @Test
    fun `B-KIT-status every tone has its own glyph and the kit's tone colour, never amber`() {
        assertEquals(KitIcons.info, glyphFor(StatusTone.Neutral))
        assertEquals(KitIcons.sync, glyphFor(StatusTone.Progress))
        assertEquals(KitIcons.check, glyphFor(StatusTone.Ok))
        assertEquals(KitIcons.warning, glyphFor(StatusTone.Attention))
        assertEquals(KitIcons.error, glyphFor(StatusTone.Failure))
        assertEquals(TextTone.Primary, toneFor(StatusTone.Attention))
        assertEquals(TextTone.Primary, toneFor(StatusTone.Failure))
        assertEquals(TextTone.Accent, toneFor(StatusTone.Progress))
    }

    @Test
    fun `B-KIT-status the action stacks only when message, action and frame overflow the width`() {
        assertFalse(statusControlsStack(available = 412, lead = 100, message = 200, controls = listOf(80), gap = 0))
        assertTrue(statusControlsStack(available = 412, lead = 100, message = 240, controls = listOf(80), gap = 0))
        assertFalse(statusControlsStack(available = 100, lead = 30, message = 900, controls = emptyList(), gap = 0))
    }

    @Test
    fun `B-KIT-status a wait past 8 s says still waiting and its first way out takes the action slot`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) {
                    KitStatusLine(
                        "Reconnecting to laptop…",
                        icon = KitIcons.cloudOff,
                        tone = StatusTone.Progress,
                        since = TimeSource.Monotonic.markNow() - 16.seconds,
                        onSlow = listOf(KitLineAction("Retry", {}), KitLineAction("Leave it running", {})),
                    )
                }
            }
        }
        onNodeWithText("Still waiting after 8 s", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Retry", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("kit-status-more").assertIsDisplayed()
    }

    @Test
    fun `B-KIT-status stacked, the action goes under the words while More stays on the top line`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) {
                    KitStatusLine(
                        "Can’t reach the laptop over Tailscale; it may be asleep or offline",
                        icon = KitIcons.cloudOff,
                        tone = StatusTone.Attention,
                        action = KitLineAction("Wake it", {}),
                        more = listOf(KitLineAction("Servers", {})),
                    )
                }
            }
        }
        val words = onNodeWithTag("kit-status-message", useUnmergedTree = true).getBoundsInRoot()
        val action = onNodeWithTag("kit-status-action").getBoundsInRoot()
        val more = onNodeWithTag("kit-status-more").getBoundsInRoot()
        assertTrue(action.top >= words.bottom - 1.dp, "action $action under words $words")
        assertTrue(more.top < words.bottom, "More $more beside the words $words")
    }
}
