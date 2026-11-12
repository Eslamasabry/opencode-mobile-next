package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote
import io.github.eslamasabry.oc.feature.chat.ui.TurnEndNote
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/**
 * W4 surface: a turn that ended on the person's Deny closes with "You declined this" (the step row's own words), read
 * by a screen reader like the other closing lines, and offers no way on that would run the refused step again.
 */
@OptIn(ExperimentalTestApi::class)
class W4DeclinedLineTest {
    @Test
    fun `W4 the declined closing line says You declined this and offers neither Continue nor Send again`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                // Both ways on are handed in, as the transcript does: the line itself must not show either.
                TurnEndNote(TurnEndNote.Declined, onContinue = {}, onSendAgain = {})
            }
        }
        onNodeWithText("You declined this").assertExists()
        onNodeWithText("Stopped").assertDoesNotExist()
        onNodeWithText("The connection dropped before this reply finished.").assertDoesNotExist()
        onNodeWithTag("stopped-continue").assertDoesNotExist()
        onNodeWithTag("interrupted-send-again").assertDoesNotExist()
    }

    @Test
    fun `W4 older - two declined turns each draw the line once and no turn draws a Stopped line`() = runComposeUiTest {
        val notes = mapOf("m1" to TurnEndNote.Declined, "m3" to TurnEndNote.Declined)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                androidx.compose.foundation.layout.Column {
                    for ((turn, note) in notes) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("turn-$turn")) {
                        TurnEndNote(note, onContinue = {}, onSendAgain = {})
                    }
                }
            }
        }
        onNodeWithTag("turn-m1").assertExists()
        onNodeWithTag("turn-m3").assertExists()
        androidx.compose.ui.test.onAllNodesWithText("You declined this").assertCountEquals(2)
        onNodeWithText("Stopped").assertDoesNotExist()
    }
}
