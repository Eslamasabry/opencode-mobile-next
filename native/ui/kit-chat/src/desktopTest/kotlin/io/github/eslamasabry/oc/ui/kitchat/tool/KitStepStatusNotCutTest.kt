package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import kotlin.test.Test
import kotlin.test.assertFalse

/** Issue 147: at 360 dp a step's status in words ("Waiting for you") wraps or fits; it is never cut to "Waiting for y…". */
@OptIn(ExperimentalTestApi::class)
class KitStepStatusNotCutTest {
    @Composable
    private fun Row(status: KitToolStatus, words: String) {
        ChatTestTheme(localeTag = "en") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KitWorkLine("Wrote 1 file", null, KitWorkState.Done, expanded = true, onToggle = {}, icon = KitIcons.terminal) {
                    KitToolRow(
                        KitToolView("a", KitToolKind.Write, "Write", "agnote.py", null, status, null, null, null, words),
                        opened = false,
                        onToggle = {},
                        body = null,
                    )
                }
            }
        }
    }

    @Test
    fun `I147-FIRST-RUN a status in words is not cut at 360 dp`() =
        runDesktopComposeUiTest(360, 600) {
            for ((status, words) in listOf(
                KitToolStatus.WaitingForYou to "Waiting for you",
                KitToolStatus.Pending to "Waiting to start",
                KitToolStatus.Stopped to "Stopped by you",
            )) {
                setContent { Row(status, words) }
                waitForIdle()
                val results = mutableListOf<TextLayoutResult>()
                onNodeWithText(words, useUnmergedTree = true).fetchSemanticsNode()
                    .config[SemanticsActions.GetTextLayoutResult].action!!(results)
                assertFalse(results.first().hasVisualOverflow, "\"$words\" is cut")
            }
        }
}
