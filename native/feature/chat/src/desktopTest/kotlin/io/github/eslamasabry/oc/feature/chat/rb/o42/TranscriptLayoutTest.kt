package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptList
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TranscriptLayoutTest {
    // C-002 (parity audit 2026-11-07): Flutter's reversed list rests a short chat on the composer, not under the header.
    @Test fun shortHistorySitsOnTheBottomAndKeepsReadingOrder() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitTranscriptList(rememberTranscriptListState(), "reply", "Latest", KitMotionLevel.Off, Modifier.height(600.dp)) {
                    item("reply") { KitText("Reply") }
                    item("prompt") { KitText("Prompt") }
                }
            }
        }
        val prompt = onNodeWithText("Prompt").fetchSemanticsNode().boundsInRoot
        val reply = onNodeWithText("Reply").fetchSemanticsNode().boundsInRoot
        val list = onNodeWithTag("transcript-list").fetchSemanticsNode().boundsInRoot
        assertTrue(prompt.top - list.top > list.height / 2, "A short history sits on the bottom, as Flutter does")
        assertTrue(prompt.bottom <= reply.top)
    }
}
