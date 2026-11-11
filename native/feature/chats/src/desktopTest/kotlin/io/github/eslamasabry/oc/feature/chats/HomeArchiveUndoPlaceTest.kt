package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chats.presentation.ArchiveUndoUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.UNDO_WINDOW_MS
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * P18 / P20 (device QA sweep A, 2026-11-11): the home's archive Undo seemed to leave within 3 to 5 s, and a late tap on
 * its place opened New conversation. The window is the kit's whole 8 s (no timer of the home's own); the New button
 * rode above the bar and dropped into Undo's place as the bar left. Flutter's bar floats above the pinned primary
 * (lib/ui/kit/kit_undo.dart:63): the button never moves.
 */
@OptIn(ExperimentalTestApi::class)
class HomeArchiveUndoPlaceTest {
    private val row =
        ConversationRowUi("login", ConversationRef(ProfileId("p"), SessionId("s")), RowTitle.Text("Fix login"), "alpha", RowStatus.None)

    private fun ComposeUiTest.tap(r: DpRect) {
        onRoot().performTouchInput { click(Offset(((r.left + r.right) / 2).toPx(), ((r.top + r.bottom) / 2).toPx())) }
    }

    @Test
    fun `P18 P20 the archive Undo stands the kit's whole window and the New button never moves into its place`() =
        runComposeUiTest {
            var ui by mutableStateOf(ChatsUi(phase = ChatsPhase.List, recent = persistentListOf(row)))
            val acts = mutableListOf<String>()
            val actions =
                object : ChatsActions by RecordingChatsActions() {
                    override fun commitArchive() {
                        acts += "commit"
                        ui = ui.copy(archiveUndo = null)
                    }

                    override fun newConversation() {
                        acts += "new"
                    }
                }
            mainClock.autoAdvance = false
            setContent {
                ChatEnvironment {
                    KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en") {
                        Box(Modifier.size(412.dp, 820.dp)) { KitSheetHost { ChatsScreen(ui, actions) } }
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            val alone = onNodeWithTag("kit-fab", useUnmergedTree = true).getBoundsInRoot()

            runOnIdle { ui = ui.copy(archiveUndo = ArchiveUndoUi(1, RowTitle.Text("Greeting"))) }
            mainClock.advanceTimeBy(1_000)
            val undo = onNodeWithText("Undo").getBoundsInRoot()
            assertEquals(alone, onNodeWithTag("kit-fab", useUnmergedTree = true).getBoundsInRoot(), "the New button stays put under the bar")
            assertTrue(undo.bottom <= alone.top, "the bar rides above the New button (Undo $undo, button $alone)")

            // The whole window: nothing is sent before 8 s, and it is sent once at 8 s.
            mainClock.advanceTimeBy(UNDO_WINDOW_MS - 1_000 - 200)
            assertEquals(emptyList(), acts, "still inside the kit's 8 s window")
            mainClock.advanceTimeBy(400)
            assertEquals(listOf("commit"), acts)

            // Long after the bar left, a tap where Undo was reaches no New conversation: the button never came there.
            mainClock.advanceTimeBy(2_000)
            tap(undo)
            mainClock.advanceTimeBy(500)
            assertEquals(listOf("commit"), acts, "a late tap on Undo's place starts nothing new")
            assertEquals(alone, onNodeWithTag("kit-fab", useUnmergedTree = true).getBoundsInRoot(), "and the button did not move")
        }

    @Test
    fun `P19 a swipe toward the line's end archives, the other way does not move the row or act`() = runComposeUiTest {
        val acts = mutableListOf<String>()
        val actions =
            object : ChatsActions by RecordingChatsActions() {
                override fun archive(row: ConversationRowUi) {
                    acts += "archive ${row.key}"
                }
            }
        val ui = ChatsUi(phase = ChatsPhase.List, recent = persistentListOf(row), archivable = persistentSetOf(row.key))
        setContent {
            ChatEnvironment {
                KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en") {
                    Box(Modifier.size(412.dp, 820.dp)) { KitSheetHost { ChatsScreen(ui, actions) } }
                }
            }
        }
        waitForIdle()
        val rowBox = onNodeWithTag("kit-recent-row", useUnmergedTree = true).getBoundsInRoot()
        /** A drag of [share] of the row's width (negative: toward the line's start). */
        fun swipe(share: Float) =
            onRoot().performTouchInput {
                val y = ((rowBox.top + rowBox.bottom) / 2).toPx()
                val x = ((rowBox.left + rowBox.right) / 2).toPx()
                val by = (rowBox.right - rowBox.left).toPx() * share
                down(Offset(x - by / 2f, y))
                repeat(10) { moveBy(Offset(by / 10f, 0f)) }
                up()
            }
        swipe(-0.7f)
        waitForIdle()
        assertEquals(emptyList(), acts, "toward the line's start there is no act")
        assertEquals(rowBox, onNodeWithTag("kit-recent-row", useUnmergedTree = true).getBoundsInRoot(), "and the row stays where it was")
        swipe(0.7f)
        waitForIdle()
        assertEquals(listOf("archive login"), acts, "toward the line's end it archives (the Undo bar follows)")
    }
}
