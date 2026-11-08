package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedRunning
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.BranchPlace
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeRows
import io.github.eslamasabry.oc.feature.chats.presentation.HomeUsageLimit
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitSwipeRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/** E20-S24, B-161 and B-162 on the real home screen (desktop, headless, density 1: 1 px = 1 dp). */
@OptIn(ExperimentalTestApi::class)
class S10HomeScreenTest {
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun ComposeUiTest.home(
        state: ChatsUi,
        actions: RecordingChatsActions = RecordingChatsActions(),
        style: Style = Style.Solid,
        sections: List<@androidx.compose.runtime.Composable () -> Unit> = emptyList(),
    ) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                ChatEnvironment {
                    KitTheme(
                        appearance =
                            KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off, style = style),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            ChatsScreen(state, actions, sections = sections)
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `E20-S24 a swipe row at rest draws no face of its own, so the page shows through`() =
        runDesktopComposeUiTest(200, 100) {
            val page = Color(0xFFFF00FF)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Themed {
                        Box(Modifier.fillMaxSize().background(page)) {
                            KitSwipeRow("Archive", {}) { Box(Modifier.fillMaxWidth().height(60.dp)) }
                        }
                    }
                }
            }
            waitForIdle()
            val image = onRoot().captureToImage().toAwtImage()
            // Inside the row (it is at least 48 dp tall), away from any content: the page's own colour.
            assertEquals(page.toArgbInt(), image.getRGB(100, 30), "the row painted a fill over the page")
        }

    @Test
    fun `E20-S24 the rows lie on the page, not on a panel, in a panelled look`() =
        runDesktopComposeUiTest(360, 1200) {
            home(mixed(), style = Style.Soft)
            val image = onRoot().captureToImage().toAwtImage()
            val last = onNode(hasContentDescriptionContaining("Write the notes")).getBoundsInRoot()
            val ground = image.getRGB(2, 2)
            // Near the right edge, just under the last row's top edge (clear of its words): no panel behind it.
            val y = last.top.value.toInt() + 3
            assertEquals(ground, image.getRGB(350, y), "the last row sits on a panel of its own")
        }

    @Test
    fun `E20-S24 P-107 the last row scrolls clear of the New conversation button with 16 dp to spare`() =
        runDesktopComposeUiTest(360, 640) {
            home(mixed(extra = 12))
            val list = SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)
            val lastTitle = "Older conversation 11"
            onNode(list).performScrollToNode(hasContentDescriptionContaining(lastTitle))
            onNode(list).performTouchInput { swipeUp() }
            waitForIdle()
            val last = onNode(hasContentDescriptionContaining(lastTitle)).getBoundsInRoot()
            val root = onRoot().getBoundsInRoot()
            // The round button: 56 dp, 12 dp above the bottom edge. The row ends at least 16 dp above its top.
            val buttonTop = root.bottom - 12.dp - 56.dp
            assertTrue(last.bottom <= buttonTop - 16.dp, "the last row ends at ${last.bottom}, the button at $buttonTop")
        }

    @Test
    fun `B-CHATS-161 a live row says Working 34m instead of the plain state word and keeps Needs you on another`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed())
            // Flutter's row (KitFeedItem): the title, then one meta line led by the state word.
            val live = onNode(hasContentDescriptionContaining("Fix the reconnect loop")).fetchSemanticsNode()
            val words = live.config[SemanticsProperties.ContentDescription].single()
            assertTrue("Working 34m" in words && words.split("Working").size == 2, words)
            val asking = onNode(hasContentDescriptionContaining("Theme packs")).fetchSemanticsNode()
            assertTrue("Needs you" in asking.config[SemanticsProperties.ContentDescription].single())
        }

    @Test
    fun `B-CHATS-161 rows are Flutter's title and meta line, with no project tile, branch or agent for one agent`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed())
            // F: lib/ui/screens/chats/chats_home_screen.dart:900-933: KitFeedItem has no tile and no branch.
            assertEquals(0, onAllNodesWithTag("kit-project-tile", useUnmergedTree = true).fetchSemanticsNodes().size)
            val git = onNode(hasContentDescriptionContaining("Theme packs")).fetchSemanticsNode()
            val gitWords = git.config[SemanticsProperties.ContentDescription].single()
            assertTrue("web" in gitWords && "main" !in gitWords, gitWords)
            // One agent kind: no row names an agent.
            assertEquals(0, onAllNodes(hasContentDescriptionContaining("OpenCode")).fetchSemanticsNodes().size)
        }

    @Test
    fun `B-CHATS-161 with two agent kinds each row names its agent`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed(claude = true))
            assertTrue(onAllNodes(hasContentDescriptionContaining("Claude Code")).fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodes(hasContentDescriptionContaining("OpenCode")).fetchSemanticsNodes().isNotEmpty())
        }

    @Test
    fun `B-CHATS-162 the list ends with its conversations, as Flutter's home does, with no place rows after it`() =
        runDesktopComposeUiTest(360, 1200) {
            home(mixed().copy(usage = HomeUsageLimit("Claude Code", 34)))
            // Owner order 2026-11-08: Flutter's home has no All conversations, Projects, Activity or Usage rows; those
            // places are reached from Search and Settings.
            listOf("Show all conversations", "Projects", "Activity", "Usage").forEach {
                onAllNodesWithText(it).assertCountEquals(0)
            }
            assertEquals(0, onAllNodesWithText("left", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        }

    private fun hasContentDescriptionContaining(text: String) =
        SemanticsMatcher("content description contains $text") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text in it } == true
        }

    private fun Color.toArgbInt(): Int {
        val a = (alpha * 255).toInt()
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun `B-FB-050 a section another feature places shows under the notes, above Today`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed(), sections = listOf({ io.github.eslamasabry.oc.ui.kit.KitText("Memory section") }))
            val memory = onNodeWithText("Memory section").getBoundsInRoot()
            val today = onNodeWithText("Today").getBoundsInRoot()
            assertTrue(memory.bottom <= today.top, "the section sits above Today")
        }

}

internal const val S10_NOW: Long = 1_800_000_000_000L

/** The home with mixed rows: live, needs you, done, git and no git, archivable; optionally a Claude Code row. */
internal fun mixed(extra: Int = 0, claude: Boolean = false): ChatsUi {
    val items = buildList<ChatFeedItem> {
        val run =
            item("run", "/work/api", S10_NOW - 60_000, "Fix the reconnect loop", ChatStatus.Running)
        add(run.copy(running = FeedRunning(sinceMs = S10_NOW - 34 * 60_000L)))
        add(item("ask", "/work/web", at = S10_NOW - 120_000, title = "Theme packs", status = ChatStatus.NeedsYou))
        val done = item("done", "/work/opencode-mobile-next", S10_NOW - 3_600_000, "Ship the widget", unseen = true)
        add(done)
        add(item("plain", "/notes/journal", at = S10_NOW - 7_200_000, title = "Write the notes"))
        if (claude)
            add(
                item("cc", "/work/api", at = S10_NOW - 9_000_000, title = "Review the diff")
                    .copy(agentId = "claude-code", sourceId = "paseo:p1")
            )
        repeat(extra) {
            add(item("x$it", "/work/api", at = S10_NOW - 10_000_000 - it, title = "Older conversation $it"))
        }
    }
    val base =
        ChatsState.build(
            ChatFeedSnapshot(items.toImmutableList()),
            ReturnBrief.None,
            ChatsFilters(),
            S10_NOW - 50_000_000,
            true,
            nowMs = S10_NOW,
        )
    return HomeRows.withBranches(
            base,
            mapOf(
                BranchPlace(P1, "/work/api") to "fix/reconnect",
                BranchPlace(P1, "/work/web") to "main",
                BranchPlace(P1, "/work/opencode-mobile-next") to "main",
            ),
        )
        .copy(archivable = items.map { it.identity }.toImmutableSet(), pinned = persistentSetOf())


