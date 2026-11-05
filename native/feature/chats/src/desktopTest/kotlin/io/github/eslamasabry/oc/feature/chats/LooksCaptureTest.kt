package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LooksCaptureTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private val hour = 60 * min

    @Test
    fun home() {
        val items = listOf(
            item("a", "/work/api", now - 2 * min, "Fix login redirect loop", ChatStatus.Running),
            item("b", "/work/web", now - 25 * min, "Add dark mode to settings", ChatStatus.NeedsYou).copy(permission = FeedPermission("r1", "npm test -- settings")),
            item("c", "/work/api", now - 2 * hour, "Rate limit the upload endpoint", unseen = true),
            item("d", "/work/mobile", now - 5 * hour, "Migrate the chat list to Compose"),
            item("e", "/work/web", now - 30 * hour, "Why is the build slow on CI?"),
            item("f", "/work/docs", now - 80 * hour, "Rewrite the install guide"),
            item("g", "/work/mobile", now - 200 * hour, "Crash when rotating the file viewer"),
        )
        val ui = ChatsState.build(ChatFeedSnapshot(items.toImmutableList()), ReturnBrief.None, ChatsFilters(), now - 6 * hour, true, now)
        Looks.shoot("native-02-conversations-home") { ChatsScreen(ui, RecordingChatsActions()) }
    }
}
