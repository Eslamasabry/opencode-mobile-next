@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app crashed on the owner's phone (2026-11-14, `LookaheadPassDelegate.forEachChildAlignmentLinesOwner`) right after
 * an open conversation went from loading to live: the frame drew the loading line in its stacked layout and the live
 * page in its overlaid one, and moved the bar, the body and the bottom block (the message box, with its own
 * subcomposition) from one to the other inside the navigation's lookahead pass.
 *
 * Guard for the class: a page with overlaid chrome stays in ONE layout whatever it shows (loading, a status line, live),
 * so nothing is ever moved between two parents. The frame's own nodes keep their identity across the changes (a move
 * to the other layout builds new ones), the loading line lies right under the bar and its status line, and the message
 * box keeps its focus and words.
 */
class KitScreenOneLayoutTest {
    @Test
    fun `B-NAV-121 a page with overlaid chrome keeps one layout through loading, a status line and live`() = runComposeUiTest {
        var loading by mutableStateOf(true)
        var line by mutableStateOf<KitStatus?>(null)
        val words = TextFieldState()
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                Box(Modifier.size(412.dp, 720.dp)) {
                    val frost = rememberKitFrostState()
                    KitScreen(
                        page = true,
                        overlayChrome = true,
                        frost = frost,
                        topBar = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("bar")) },
                        status = line,
                        loading = loading,
                        loadingLabel = "Loading the conversation",
                        bottom = {
                            KitField(
                                state = words,
                                label = "Message",
                                kind = KitFieldKind.Multiline,
                                labelShown = false,
                                framed = false,
                                composer = true,
                                multilineLines = 1..5,
                            )
                        },
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        waitForIdle()
        val frame = ids()
        assertUnderTheBar("loading")

        onNode(hasSetTextAction()).performTouchInput { click() }
        onNode(hasSetTextAction()).assertIsFocused()
        onNode(hasSetTextAction()).performTextInput("he")

        // A status line arrives while the page loads.
        line = KitStatus(KitStatusKind.Connection, "Reconnecting…", id = "link")
        waitForIdle()
        assertEquals(frame, ids(), "a status line while loading: the frame's nodes are the same (nothing moved)")
        assertUnderTheBar("loading under a status line")

        // The load ends with the line still up, then the line goes: the live page.
        loading = false
        waitForIdle()
        assertEquals(frame, ids(), "loading to live: the frame's nodes are the same (nothing moved)")
        line = null
        waitForIdle()
        assertEquals(frame, ids(), "the line leaves: the frame's nodes are the same (nothing moved)")
        onNode(hasSetTextAction()).assertIsFocused()
        onNode(hasSetTextAction()).performTextInput("llo")

        // A reload: back to loading.
        loading = true
        waitForIdle()
        assertEquals(frame, ids(), "live to loading: the frame's nodes are the same (nothing moved)")
        assertUnderTheBar("loading again")
        onNode(hasSetTextAction()).assertIsFocused()
        runOnIdle { assertEquals("hello", words.text.toString(), "the message box keeps its words") }
    }

    /** The frame's box and its bottom block's box: new nodes whenever the frame changes layout. */
    private fun ComposeUiTest.ids(): List<Int> =
        listOf("kit-screen", "kit-screen-bottom").map { onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().id }

    /** The loading line lies right under the bar, and under the status line when one shows. */
    private fun ComposeUiTest.assertUnderTheBar(at: String) {
        val bar = onNodeWithTag("bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val status = runCatching { onNodeWithTag("kit-screen-status", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }.getOrNull()
        val top = maxOf(bar.bottom, status?.bottom ?: bar.bottom)
        val loadingLine = onNodeWithTag("kit-screen-loading", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(loadingLine.height > 0f, "$at: the loading line is drawn ($loadingLine)")
        assertEquals(top, loadingLine.top, 1f, "$at: the loading line $loadingLine lies under the bar $bar and the status line $status")
    }
}
