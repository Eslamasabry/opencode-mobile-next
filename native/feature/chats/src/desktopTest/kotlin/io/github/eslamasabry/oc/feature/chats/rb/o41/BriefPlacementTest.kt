package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.feature.chats.ChatEnvironment
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BriefPlacementTest {
    @Test fun `B15 a brief request retains the feed's in-flight answer guard`() = runComposeUiTest {
        val answers = mutableListOf<Boolean>()
        val actions = object : ChatsActions by RecordingChatsActions() {
            override fun answerPermission(row: ConversationRowUi, allow: Boolean) { answers += allow }
        }
        val row = ConversationRowUi(
            "checkout", ConversationRef(ProfileId("p"), SessionId("s")), RowTitle.Text("Fix checkout"), "shopfront", RowStatus.NeedsYou,
            permission = FeedPermission("request", "flutter test"),
        )
        val state = ChatsUi(
            phase = ChatsPhase.List,
            today = persistentListOf(row),
            brief = BriefUi(persistentListOf(row), 0, false),
            answering = persistentSetOf("request"),
        )
        setContent { ChatEnvironment { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { ChatsScreen(state, actions) } } }
        onNodeWithText("Allow once").assertIsNotEnabled().performClick()
        onNodeWithText("Reject").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), answers)
    }

    @Test fun aConversationInTheReturnBriefIsSaidOnce() = runComposeUiTest {
        val row = ConversationRowUi("login", ConversationRef(ProfileId("p"), SessionId("s")), RowTitle.Text("Fix login"), "alpha", RowStatus.NeedsYou, RelativeAge.Minutes(2))
        val state = ChatsUi(phase = ChatsPhase.List, today = persistentListOf(row), brief = BriefUi(persistentListOf(row), 0, false))
        setContent { ChatEnvironment { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { ChatsScreen(state, RecordingChatsActions()) } } }
        // The brief takes the row out of the list under it: one row says the conversation, whatever else it reads.
        onAllNodesWithContentDescription("Fix login", substring = true).assertCountEquals(1)
    }
}
