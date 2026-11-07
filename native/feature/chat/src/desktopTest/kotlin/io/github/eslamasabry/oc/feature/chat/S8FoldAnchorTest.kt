package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.ui.foldAnchor
import io.github.eslamasabry.oc.feature.chat.ui.rememberFoldAnchor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-010 (auditor strip_expand): the transcript is a reverse list anchored at the bottom, so opening a tool group
 * grew it upward and its row slid about 390 px away from the finger. The touched row is held: its growth is paid
 * back with an equal scroll, so its top stays where it was.
 */
@OptIn(ExperimentalTestApi::class)
class S8FoldAnchorTest {
    private fun headerTopAfterOpening(anchored: Boolean): Pair<Float, Float> {
        var before = 0f
        var after = 0f
        runDesktopComposeUiTest(360, 640) {
            setContent {
                val lazy = rememberLazyListState()
                val anchor = rememberFoldAnchor()
                LazyColumn(Modifier.fillMaxSize(), state = lazy, reverseLayout = true) {
                    // Newest first: two short rows under the one that opens, many above it.
                    items(40) { i ->
                        var open by remember { mutableStateOf(false) }
                        val key = "row-$i"
                        Column(
                            Modifier.fillMaxWidth().let { if (anchored) it.foldAnchor(key, anchor, lazy) else it }
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(48.dp).testTag("header-$i").clickable { open = !open }
                            )
                            if (open) Box(Modifier.fillMaxWidth().height(150.dp))
                        }
                    }
                }
            }
            waitForIdle()
            before = onNodeWithTag("header-2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            onNodeWithTag("header-2", useUnmergedTree = true).performClick()
            waitForIdle()
            after = onNodeWithTag("header-2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        }
        return before to after
    }

    @Test
    fun `P-010 without the hold the opened row slides up by its growth`() {
        val (before, after) = headerTopAfterOpening(anchored = false)
        assertTrue(before - after > 100f, "the bottom-anchored list pushes the row up: $before → $after")
    }

    @Test
    fun `P-010 an opened row stays under the finger`() {
        val (before, after) = headerTopAfterOpening(anchored = true)
        assertTrue(abs(after - before) <= 1f, "the row's top stays put: $before → $after")
    }
}
