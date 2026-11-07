package io.github.eslamasabry.oc.ui.kit.content.log

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rules of lib/ui/kit/kit_log_panel.dart the Flutter-shaped KitLogPanel keeps. */
@OptIn(ExperimentalTestApi::class)
class KitLogViewTest {
    @Test
    fun headerWordsFollowDart() {
        assertEquals(LogState.None, logState(live = true, ended = null, readFailed = true, quietSeconds = null)) // a failed read hides them
        val end = KitLogEnd(exitCode = 1, failed = true)
        assertEquals(LogState.End(end), logState(live = true, ended = end, readFailed = false, quietSeconds = 8)) // an end wins
        assertEquals(LogState.Quiet(8), logState(live = true, ended = null, readFailed = false, quietSeconds = 8))
        assertEquals(LogState.Live, logState(live = true, ended = null, readFailed = false, quietSeconds = null))
        assertEquals(LogState.None, logState(live = false, ended = null, readFailed = false, quietSeconds = null))
    }

    @Test
    fun shownTailCountsBufferAndPanelDrops() {
        val all = (1..5).map { KitLogLine("$it") }
        val (shown, dropped) = logShown(all, bufferDropped = 10, maxLines = 3)
        assertEquals(listOf("3", "4", "5"), shown.map { it.text })
        assertEquals(12, dropped)
    }

    @Test
    fun glyphScalesWithTextUpToOneAndAHalf() {
        assertEquals(20f, logGlyph(20, 1f, 1.5f))
        assertEquals(30f, logGlyph(20, 2f, 1.5f)) // the text-2.0 gutter (golden kit_log_panel_live_text2)
    }

    @Test
    fun bufferIsSnapshotStateSoThePanelRedraws() {
        val buffer = KitLogBuffer()
        var seen = 0
        val handle = Snapshot.registerApplyObserver { changed, _ -> if (changed.isNotEmpty()) seen++ }
        try {
            Snapshot.withMutableSnapshot { buffer.add(KitLogLine("a")) }
            assertTrue(seen > 0, "adding a line must notify readers")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun scrolledUpCollectsNewLinesInThePill() = runDesktopComposeUiTest(412, 915) {
        val buffer = KitLogBuffer().apply { repeat(40) { add(KitLogLine("[oc] line $it")) } }
        setContent { KitTheme(platform = DesktopKitPlatform(), stillFrame = true) { KitLogPanel(buffer, live = true, wrap = false) } }
        waitForIdle()
        onNodeWithTag("kit-log-lines").performScrollToIndex(5)
        waitForIdle()
        repeat(12) { buffer.add(KitLogLine("[oc] new $it")) }
        waitForIdle()
        onNodeWithText("12 new lines").assertExists()
    }

    @Test
    fun droppedLinesAreSaid() = runDesktopComposeUiTest(412, 915) {
        val buffer = KitLogBuffer(capacity = 10).apply { repeat(1250) { add(KitLogLine("[oc] indexed file ${it + 1}")) } }
        setContent { KitTheme(platform = DesktopKitPlatform(), stillFrame = true) { KitLogPanel(buffer, ended = KitLogEnd(exitCode = 0)) } }
        waitForIdle()
        onNodeWithText("1240 earlier lines not shown").assertExists()
        onNodeWithText("Ended · exit ⁦0⁩", substring = true, useUnmergedTree = true).assertExists()
    }
}
