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
                            ChatsScreen(state, actions)
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
    fun `E20-S24 the rows under the list lie on the page, not on a panel, in a panelled look`() =
        runDesktopComposeUiTest(360, 1200) {
            home(mixed(), style = Style.Soft)
            val image = onRoot().captureToImage().toAwtImage()
            val projects = onNode(hasText("Projects")).getBoundsInRoot()
            val ground = image.getRGB(2, 2)
            // Mid-screen, just under the Projects row's top edge (clear of its tile, words and chevron): inside where
            // the old panel was drawn.
            val y = projects.top.value.toInt() + 3
            assertEquals(ground, image.getRGB(180, y), "the Projects row sits on a panel of its own")
        }

    @Test
    fun `E20-S24 P-107 the last row scrolls clear of the New conversation button with 16 dp to spare`() =
        runDesktopComposeUiTest(360, 640) {
            home(mixed(extra = 12))
            val list = SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)
            onNode(list).performScrollToNode(hasText("Usage"))
            waitForIdle()
            val usage = onNode(hasText("Usage")).getBoundsInRoot()
            val root = onRoot().getBoundsInRoot()
            // The round button: 56 dp, 12 dp above the bottom edge. The row ends at least 16 dp above its top.
            val buttonTop = root.bottom - 12.dp - 56.dp
            assertTrue(usage.bottom <= buttonTop - 16.dp, "Usage ends at ${usage.bottom}, the button at $buttonTop")
        }

    @Test
    fun `B-CHATS-161 a live row says Working 34m instead of its time and keeps Needs you on another`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed())
            onNodeWithText("Working 34m", useUnmergedTree = true).assertExists()
            val liveMarks = onAllNodesWithTag("kit-conversation-live", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, liveMarks.size)
            // The heading's "Needs you" and the asking row's own mark, before its branch.
            onNodeWithText("Needs you\u00a0· \u2068main\u2069", useUnmergedTree = true).assertExists()
            // The live row is heard with its run length and without a second "Working".
            val live = onNode(hasContentDescriptionContaining("Fix the reconnect loop")).fetchSemanticsNode()
            val words = live.config[SemanticsProperties.ContentDescription].single()
            assertTrue("Working 34m" in words && words.split("Working").size == 2, words)
        }

    @Test
    fun `B-CHATS-161 rows show the project's tile, the branch where there is git, and no agent mark for one agent`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed())
            assertTrue(onAllNodesWithTag("kit-project-tile", useUnmergedTree = true).fetchSemanticsNodes().size >= 4)
            val git = onNode(hasContentDescriptionContaining("Theme packs")).fetchSemanticsNode()
            assertTrue("main" in git.config[SemanticsProperties.ContentDescription].single())
            val plain = onNode(hasContentDescriptionContaining("Write the notes")).fetchSemanticsNode()
            val plainWords = plain.config[SemanticsProperties.ContentDescription].single()
            assertTrue("main" !in plainWords, "a folder without git shows no branch")
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
    fun `B-CHATS-162 the Usage row names the tightest limit and opens Usage`() =
        runDesktopComposeUiTest(360, 1200) {
            val actions = RecordingChatsActions()
            home(mixed().copy(usage = HomeUsageLimit("Claude Code", 34)), actions)
            onNodeWithText("\u2068Claude Code\u2069 · 34% left", useUnmergedTree = true).assertExists()
            onNode(hasText("Usage")).performClick()
            assertEquals(listOf("usage"), actions.calls)
        }

    @Test
    fun `B-CHATS-162 without usage data the row says only Usage`() =
        runDesktopComposeUiTest(360, 1200) {
            home(mixed())
            onNodeWithText("Usage").assertExists()
            val left = onAllNodesWithText("left", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(0, left.size)
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
}

