package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BriefPlacementTest {
    @Test fun aConversationInTheReturnBriefIsSaidOnce() = runComposeUiTest {
        val row = ConversationRowUi("login", ConversationRef(ProfileId("p"), SessionId("s")), RowTitle.Text("Fix login"), "alpha", RowStatus.NeedsYou, RelativeAge.Minutes(2))
        val state = ChatsUi(phase = ChatsPhase.List, today = persistentListOf(row), brief = BriefUi(persistentListOf(row), 0, false))
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { ChatsScreen(state, RecordingChatsActions()) } }
        onAllNodesWithContentDescription("Fix login, Needs you, alpha, 2 min ago").assertCountEquals(1)
    }
}
