package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.action.KitStopButton
import io.github.eslamasabry.oc.ui.kitchat.request.ANSWER_HAPTIC
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-402: approving, denying and stopping are felt as the one consequential haptic, never as a send. */
@OptIn(ExperimentalTestApi::class)
class HapticMeaningChatTest {
    @Test
    fun `B-402 every permission answer is on the consequential role`() {
        assertEquals(KitHaptic.Commit, ANSWER_HAPTIC)
    }

    @Test
    fun `B-402 the stop button, the stop ring and the stop-and-send pill play the consequential role`() = runComposeUiTest {
        val platform = RecordingPlatform()
        setContent {
            MotionTheme(level = MotionLevel.Off, platform = platform) {
                androidx.compose.foundation.layout.Column {
                    KitStopButton("Stop one", onClick = {})
                    KitComposerStopRing("Stop two", onClick = {})
                    KitStopAndSendPill("Stop and send", onClick = {})
                }
            }
        }
        onNodeWithContentDescription("Stop one").performClick()
        onNodeWithContentDescription("Stop two").performClick()
        onNodeWithContentDescription("Stop and send").performClick()
        assertEquals(listOf(KitHaptic.Commit, KitHaptic.Commit, KitHaptic.Commit), platform.haptics)
    }

    @Test
    fun `B-402 Allow once and Deny on the permission card are not a send`() = runComposeUiTest {
        val platform = RecordingPlatform()
        setContent {
            MotionTheme(level = MotionLevel.Off, platform = platform) {
                KitPermissionCard(
                    title = "Run npm test?", summary = null, subject = "npm test",
                    allowOnceLabel = "Allow once", alwaysLabel = null, denyLabel = "Deny",
                    onAllowOnce = {}, onAlways = null, onDeny = {},
                )
            }
        }
        onNodeWithText("Allow once").performClick()
        onNodeWithText("Deny").performClick()
        assertEquals(listOf(KitHaptic.Commit, KitHaptic.Commit), platform.haptics)
    }
}
