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

    /**
     * P-021/P-023 at font scale 1.0 and 1.3: one agent (no tiles), a finished group above the list headed and ruled
     * like Today, two connections (the chip with its chevron).
     */
    @Test
    fun homeOneAgentWithBrief() {
        val items = listOf(
            item("a", "/work/api", now - 2 * min, "Fix login redirect loop", ChatStatus.Running),
            item("c", "/work/api", now - 40 * min, "Rate limit the upload endpoint", unseen = true),
            item("h", "/work/web", now - 50 * min, "Tidy the theme tokens", unseen = true),
            item("d", "/work/mobile", now - 5 * hour, "Migrate the chat list to Compose"),
            item("e", "/work/web", now - 30 * hour, "Why is the build slow on CI?"),
        )
        val brief =
            ReturnBrief.build(
                listOf(
                    io.github.eslamasabry.oc.core.model.Session(io.github.eslamasabry.oc.core.common.ids.SessionId("c"), directory = "/work/api", time = io.github.eslamasabry.oc.core.model.SessionTime(idleMs = now - 40 * min)),
                    io.github.eslamasabry.oc.core.model.Session(io.github.eslamasabry.oc.core.common.ids.SessionId("h"), directory = "/work/web", time = io.github.eslamasabry.oc.core.model.SessionTime(idleMs = now - 50 * min)),
                ),
                true, false, { true }, { false }, { null }, scope = BRIEF_SCOPE,
            )
        val sources = kotlinx.collections.immutable.persistentListOf(
            io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi(P1, "My computer", shown = true, main = true, onThisPhone = false, unreachable = false),
            io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi(io.github.eslamasabry.oc.core.common.ids.ProfileId("p2"), "Build VPS", shown = false, main = false, onThisPhone = false, unreachable = false),
        )
        val ui = ChatsState.build(ChatFeedSnapshot(items.toImmutableList()), brief, ChatsFilters(), now - 6 * hour, true, now)
            .copy(home = io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState(sources = sources))
        for ((suffix, scale) in listOf("" to 1f, "-text13" to 1.3f))
            Looks.shoot("p021-home-one-agent-brief$suffix", fontScale = scale) { ChatsScreen(ui, RecordingChatsActions()) }
    }
}
