package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.feature.chats.ui.answersGoneKey
import io.github.eslamasabry.oc.feature.chats.ui.carriesAnswers
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * P69 (device QA sweep A, 2026-11-11): after Allow once from a home row, the Right now card kept an empty band where
 * the answers had been. The panel is built afresh when the last answers in it go, so it never keeps their height, and
 * stands while another row in it still waits for an answer.
 */
@OptIn(ExperimentalTestApi::class)
class HomeRightNowAnswersGoneTest {
    private val asking =
        ConversationRowUi(
            "ask",
            ConversationRef(ProfileId("p"), SessionId("s")),
            RowTitle.Text("List what's in this folder"),
            "projects",
            RowStatus.NeedsYou,
            permission = FeedPermission("req1", ".", "read"),
        )
    private val working = asking.copy(status = RowStatus.Running, permission = null)

    @Test
    fun `P69 the panel's key moves only when the last answers in it go`() {
        val other = asking.copy(key = "other", ref = ConversationRef(ProfileId("p"), SessionId("s2")))
        // Allow once on the only waiting row: the panel is rebuilt.
        assertNotEquals(0, answersGoneKey(0, carriesAnswers(listOf(asking)), carriesAnswers(listOf(working))))
        // Another row still waits (the person may be answering it): the panel stands.
        assertEquals(0, answersGoneKey(0, carriesAnswers(listOf(asking, other)), carriesAnswers(listOf(working, other))))
        // Answers arriving never rebuild it, nor rows with none coming and going.
        assertEquals(0, answersGoneKey(0, carriesAnswers(listOf(working)), carriesAnswers(listOf(working, other))))
        assertEquals(0, answersGoneKey(0, hadAnswers = false, hasAnswers = false))
    }

    @Test
    fun `P69 Right now shrinks to its row once the answers under it are gone`() = runComposeUiTest {
        var ui by mutableStateOf(ChatsUi(phase = ChatsPhase.List, rightNow = persistentListOf(asking)))
        mainClock.autoAdvance = false
        setContent {
            ChatEnvironment {
                KitTheme(appearance = KitAppearance(motion = MotionLevel.Full), platform = DesktopKitPlatform(), localeTag = "en") {
                    Box(Modifier.size(412.dp, 820.dp)) { KitSheetHost { ChatsScreen(ui, RecordingChatsActions()) } }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        val withAnswers = onAllNodesWithTag("kit-row-panel").onFirst().getBoundsInRoot()
        runOnIdle { ui = ui.copy(rightNow = persistentListOf(working)) }
        mainClock.advanceTimeBy(1_000)
        val after = onAllNodesWithTag("kit-row-panel").onFirst().getBoundsInRoot()
        val gone = (withAnswers.bottom - withAnswers.top) - (after.bottom - after.top)
        assertTrue(gone >= 40.dp, "the answers' room is gone (with answers $withAnswers, after $after)")
    }
}
