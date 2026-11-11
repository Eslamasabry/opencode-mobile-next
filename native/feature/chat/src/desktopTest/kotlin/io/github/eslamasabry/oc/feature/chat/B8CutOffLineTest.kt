package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote
import io.github.eslamasabry.oc.feature.chat.ui.TurnEndNote
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** B8: a reply the server lost says so in Flutter's words (kit_turn.dart `KitTurnPhase.interrupted`) with its way on. */
@OptIn(ExperimentalTestApi::class)
class B8CutOffLineTest {
    private val line = "The connection dropped before this reply finished."

    @Test
    fun `B8 a reply lost before any words says so and offers Send again, which sends the prompt again`() = runComposeUiTest {
        var sent = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                TurnEndNote(TurnEndNote.CutOffBeforeReply, onContinue = {}, onSendAgain = { sent++ })
            }
        }
        onNodeWithText(line).assertExists()
        onNodeWithTag("stopped-continue").assertDoesNotExist()
        onNodeWithText("Send again").assertExists()
        onNodeWithTag("interrupted-send-again").performClick()
        assertEquals(1, sent)
    }

    @Test
    fun `B8 a reply lost after some words says so and offers Continue this reply`() = runComposeUiTest {
        var continued = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                TurnEndNote(TurnEndNote.CutOff, onContinue = { continued++ }, onSendAgain = {})
            }
        }
        onNodeWithText(line).assertExists()
        onNodeWithTag("interrupted-send-again").assertDoesNotExist()
        onNodeWithText("Continue this reply").assertExists()
        onNodeWithTag("stopped-continue").performClick()
        assertEquals(1, continued)
    }
}
