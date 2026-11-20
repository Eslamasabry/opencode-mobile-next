package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-CHAT-124: the two-column diff at 840 dp shows every added line, however long one line of the file is. */
@OptIn(ExperimentalTestApi::class)
class KitDiffSplitAddedTest {
    private val longLine = "val route = " + "segment-".repeat(18) + "end!" // 160 characters

    private fun fixture(): DiffFile {
        val old = (1..34).map { "ctx $it" }
        val new = old.toMutableList()
        new[2] = "changed alpha"
        new[13] = longLine.also { check(it.length == 160) }
        new[25] = "added beta"
        new.add(26, "added gamma")
        new.removeAt(24)
        return DiffModel.fromBeforeAfter("route.kt", old.joinToString("\n"), new.joinToString("\n"))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(width: Int, height: Int) =
        setContent {
            ChatTestTheme {
                CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(width.toFloat(), height.toFloat())) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        KitDiffView(fixture(), maxLines = 5000, hunkLabel = { _, _, _, _ -> "hunk" }, gapLabel = { "$it hidden" })
                    }
                }
            }
        }

    @Test
    fun `B-CHAT-124 a two-column diff at 840 dp shows every added line and keeps a wrapped pair level`() =
        runComposeUiTest {
            show(840, 800)
            for (added in listOf("changed alpha", longLine, "added beta", "added gamma")) {
                val b = onNode(hasText(added), useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue(b.left.value >= 0f && b.right.value <= 840.5f, "'$added' sits at ${b.left}..${b.right}, outside 0..840")
            }
            val newHalf = onAllNodes(hasTestTag("diff-new-half") and androidx.compose.ui.test.hasAnyDescendant(hasText(longLine)), useUnmergedTree = true)
                .onFirst().getUnclippedBoundsInRoot()
            val oldHalves = onAllNodes(hasTestTag("diff-old-half"), useUnmergedTree = true).fetchSemanticsNodes()
            val level = oldHalves.map { it.boundsInRoot }.firstOrNull { kotlin.math.abs(it.top - newHalf.top.value) < 1f }
            assertTrue(level != null, "no old half starts at the long new line's row")
            assertEquals(newHalf.bottom.value - newHalf.top.value, level.height, 1f, "old and new halves of the paired row differ in height")
            assertTrue(newHalf.bottom.value - newHalf.top.value > 30f, "the 160-character line should wrap inside its half")
        }

    @Test
    fun `B-CHAT-124 jumping to change 2 of 3 brings its row into view after wrapping`() =
        runComposeUiTest {
            show(840, 300)
            onAllNodes(hasContentDescription("Next change"), useUnmergedTree = true).onFirst().performClick()
            waitForIdle()
            val b = onNode(hasText(longLine), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(b.top.value >= 0f && b.bottom.value <= 300.5f, "change 2 is at ${b.top}..${b.bottom}, outside the 300 dp window")
            assertTrue(b.right.value <= 840.5f, "change 2 runs to ${b.right}")
        }
}
