package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote
import io.github.eslamasabry.oc.feature.chat.presentation.stopNote
import io.github.eslamasabry.oc.feature.chat.ui.TurnEndNote
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Flutter `_stoppedLine`: a server pause or restart says so and offers "Continue this reply". */
@OptIn(ExperimentalTestApi::class)
class StoppedReplyContinueTest {
    @Test
    fun `B-CHAT-111 only a pause or a server restart can be continued, the person's own stop cannot`() {
        assertEquals(TurnEndNote.PausedInactive, stopNote(InterruptReason.Inactivity))
        assertEquals(TurnEndNote.StoppedServerRestart, stopNote(InterruptReason.Shutdown))
        assertEquals(TurnEndNote.Superseded, stopNote(InterruptReason.Superseded))
        assertEquals(TurnEndNote.Stopped, stopNote(InterruptReason.User))
        assertTrue(TurnEndNote.PausedInactive.canContinue && TurnEndNote.StoppedServerRestart.canContinue)
        assertFalse(TurnEndNote.Superseded.canContinue || TurnEndNote.Stopped.canContinue)
    }

    @Test
    fun `B-CHAT-111 a paused reply shows Continue this reply and tapping it asks to continue`() = runComposeUiTest {
        var continued = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                TurnEndNote(TurnEndNote.PausedInactive, onContinue = { continued++ })
            }
        }
        onNodeWithText("The server paused this reply after an hour with no new work.").assertExists()
        onNodeWithTag("stopped-continue").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun `B-CHAT-111 a replaced reply is said but has no Continue`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                TurnEndNote(TurnEndNote.Superseded, onContinue = {})
            }
        }
        onNodeWithText("A newer run took this reply's place.").assertExists()
        onNodeWithTag("stopped-continue").assertDoesNotExist()
    }
}
