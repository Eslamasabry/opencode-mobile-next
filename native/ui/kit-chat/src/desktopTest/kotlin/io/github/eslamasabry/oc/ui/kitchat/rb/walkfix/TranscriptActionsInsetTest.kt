@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.rb.walkfix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptActions
import kotlin.test.Test
import kotlin.test.assertTrue

/** B-099: a turn's buttons sit on the quiet lines' edge, never on the screen edge. */
class TranscriptActionsInsetTest {
    @Test
    fun `B-099 transcript actions line up with the quiet line above them`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) {
                    Column {
                        KitQuietLine("Didn't finish")
                        KitTranscriptActions {
                            KitButton("Choose another model", {}, role = KitButtonRole.Secondary)
                        }
                    }
                }
            }
        }
        val words = onNodeWithText("Didn't finish").getBoundsInRoot()
        // The merged node is the whole button (its shape), not only its words.
        val button = onNodeWithText("Choose another model").getBoundsInRoot()
        assertTrue(words.left >= 16.dp, "quiet line inset $words")
        // The button's edge starts at the quiet line's edge, never at the screen edge.
        assertTrue(button.left >= words.left - 1.dp, "button $button vs words $words")
    }
}
