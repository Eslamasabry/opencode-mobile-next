package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * STREAM-FIX: a long block arrives as chunk rows ([KitReplyBlock] `continued` / `continues`); drawn one under the
 * other they take exactly the room of the whole block (no gap, no doubled padding at the seams), so the chunks of a
 * fence read as one code surface and a reader cannot tell where the rows meet.
 */
@OptIn(ExperimentalTestApi::class)
class ChunkSeamTest {
    private fun heightsMatch(kind: KitBlockKind, lines: List<String>) =
        runComposeUiTest {
            val registry = RevealRegistry()
            val chunks = lines.chunked(16)
            setContent {
                ChatTestTheme {
                    Column(Modifier.width(412.dp).wrapContentHeight(Alignment.Top, unbounded = true)) {
                        KitReplyBlock(
                            lines.joinToString("\n"), kind, live = false, streamed = false, revealKey = "whole", revealGroup = "w",
                            registry = registry, motion = KitMotionLevel.Off, modifier = Modifier.testTag("whole"),
                        )
                        Column(Modifier.testTag("chunks")) {
                            chunks.forEachIndexed { i, c ->
                                KitReplyBlock(
                                    c.joinToString("\n"), kind, live = false, streamed = false, revealKey = "c$i", revealGroup = "c",
                                    registry = registry, motion = KitMotionLevel.Off,
                                    continued = i > 0, continues = i < chunks.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
            val whole = onNodeWithTag("whole").getUnclippedBoundsInRoot()
            val joined = onNodeWithTag("chunks").getUnclippedBoundsInRoot()
            val dh = abs((whole.bottom - whole.top).value - (joined.bottom - joined.top).value)
            // Line heights are whole pixels at density 1; allow one pixel per seam for rounding.
            assertTrue(dh <= chunks.size - 1f, "whole ${whole.bottom - whole.top} vs chunks ${joined.bottom - joined.top}")
        }

    @Test
    fun `a 40-line fence as three chunk rows takes the room of one fence`() =
        heightsMatch(KitBlockKind.Code, listOf("```kotlin") + (1..38).map { "val line$it = compute($it)" } + "```")

    @Test
    fun `a 40-line paragraph as three chunk rows takes the room of one paragraph`() =
        heightsMatch(KitBlockKind.Prose, (1..40).map { "Line $it of a long paragraph." })
}
