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
    fun `A1-HOME-07 the rows lie on a grouped panel in every look, Solid included`() =
        runDesktopComposeUiTest(360, 1200) {
            for (style in listOf(Style.Solid, Style.Soft)) {
                home(mixed(), style = style)
                val image = onRoot().captureToImage().toAwtImage()
                val last = onNode(hasContentDescriptionContaining("Write the notes")).getBoundsInRoot()
                val ground = image.getRGB(2, 2)
                // Near the panel's end edge, just under the row's top (clear of its words): the panel, not the page.
                val y = last.top.value.toInt() + 3
                assertTrue(ground != image.getRGB(330, y), "$style: the last row lies on the page, not on its panel")
                // Outside the panel, in the page gutter: the page.
                assertEquals(ground, image.getRGB(4, y), "$style: the panel reaches into the page gutter")
            }
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
    fun `A1-HOME-08 a live row says Working once, and a waiting one says Needs you`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed())
            // A1: the state word sits under the time in plain coloured text, said once.
            val live = onNode(hasContentDescriptionContaining("Fix the reconnect loop")).fetchSemanticsNode()
            val words = live.config[SemanticsProperties.ContentDescription].single()
            assertTrue(words.split("Working").size == 2, words)
            val asking = onNode(hasContentDescriptionContaining("Theme packs")).fetchSemanticsNode()
            assertTrue("Needs you" in asking.config[SemanticsProperties.ContentDescription].single())
        }

    @Test
    fun `A1-HOME-09 rows draw no project tile and no branch, and one agent kind is not said on each row`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed())
            // Owner 2026-11-08: no icons in boxes; the project is a small dot and its name when several are listed.
            assertEquals(0, onAllNodesWithTag("kit-project-tile", useUnmergedTree = true).fetchSemanticsNodes().size)
            assertTrue(onAllNodesWithTag("kit-recent-project-dot", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            val git = onNode(hasContentDescriptionContaining("Theme packs")).fetchSemanticsNode()
            val gitWords = git.config[SemanticsProperties.ContentDescription].single()
            assertTrue("web" in gitWords && "main" !in gitWords, gitWords)
            // One agent kind: its mark shows on every row, its name is not said on each.
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
    fun `A1-HOME-10 the list ends with All conversations and its count, which opens the whole list`() =
        runDesktopComposeUiTest(360, 1200) {
            val actions = RecordingChatsActions()
            home(mixed().copy(usage = HomeUsageLimit("Claude Code", 34)), actions)
            listOf("Projects", "Activity", "Usage").forEach { onAllNodesWithText(it).assertCountEquals(0) }
            val all = onNode(hasContentDescriptionContaining("All conversations, 4"))
            val last = onNode(hasContentDescriptionContaining("Write the notes")).getBoundsInRoot()
            assertTrue(all.getBoundsInRoot().top >= last.bottom, "All conversations closes the list")
            all.performClick()
            assertTrue("all" in actions.calls, actions.calls.toString())
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
    fun `B-FB-050 a section another feature places shows under the notes, above Right now`() =
        runDesktopComposeUiTest(360, 900) {
            home(mixed(), sections = listOf({ io.github.eslamasabry.oc.ui.kit.KitText("Memory section") }))
            val memory = onNodeWithText("Memory section").getBoundsInRoot()
            val today = onNodeWithText("Right now").getBoundsInRoot()
            assertTrue(memory.bottom <= today.top, "the section sits above Right now")
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
