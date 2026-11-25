package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.engine.agents.feed.FeedRunning
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Home at 360 dp (Solid and Glass, text 1.0 and 1.3) with a "+N" filter cue showing, before and after tapping it.
 * Writes 8 PNGs into `build/home-chips-cue/` for visual review and asserts every cue stays inside the 360 dp frame.
 */
@OptIn(ExperimentalTestApi::class)
class HomeChipsCueRenders {
    private fun feedItem(
        id: String,
        title: String,
        minutesAgo: Long,
        preview: String = "",
        dir: String = "/root/projects/oc_app",
    ): ChatFeedItem =
        item(id, dir, at = NOW - minutesAgo * 60_000L, title = title)
            .copy(preview = preview, agentId = "opencode")

    private fun homeState(project: String? = null): ChatsUi {
        val items =
            listOf(
                feedItem(
                    "explain",
                    "Explain this project",
                    0,
                    "Reading lib/ui/kit to map how the parts fit together",
                ).copy(agentId = "claude", status = ChatStatus.Running, running = FeedRunning(NOW - 4 * 60_000L)),
                feedItem("ask", "Run the tests", 5, "Wants to run npm test")
                    .copy(status = ChatStatus.NeedsYou, permission = FeedPermission("req1", "npm test")),
                feedItem(
                    "checkout",
                    "Fix flaky checkout test",
                    12,
                    "Made the test wait on the settled state",
                    dir = "/root/projects/shopfront",
                ).copy(agentId = "claude"),
                feedItem("hi", "Hi", 60 * 26, "Hi! What would you like to work on today?"),
                feedItem(
                    "who",
                    "You know who you are and what you do",
                    60 * 27,
                    "I am OpenCode, a coding agent working in your folder",
                    dir = "/root/projects/site",
                ),
            )
        val base =
            ChatsState.build(
                ChatFeedSnapshot(items.toImmutableList()),
                ReturnBrief.None,
                ChatsFilters(projectDirectory = project),
                startOfTodayMs = NOW - 12 * 3_600_000L,
                settingsAvailable = true,
                nowMs = NOW,
            )
        val sources =
            persistentListOf(
                ChatsListSourceUi(
                    profile = ProfileId("this-phone"),
                    name = "This phone",
                    shown = true,
                    main = true,
                    onThisPhone = true,
                    unreachable = false,
                ),
                ChatsListSourceUi(
                    profile = ProfileId("vps"),
                    name = "Build VPS",
                    shown = true,
                    main = false,
                    onThisPhone = false,
                    unreachable = false,
                ),
            )
        return base.copy(home = base.home.copy(sources = sources))
    }

    private fun capturePair(tag: String, style: Style, fontScale: Float, project: String? = null) {
        val out = File("build/home-chips-cue").also { it.mkdirs() }
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("en"))
        try {
            val platform = DesktopKitPlatform()
            val state = homeState(project)
            kitUiTest(W_PX, H_PX) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(DENSITY, fontScale),
                        LocalKitPlatform provides platform,
                    ) {
                        KitTheme(
                            appearance =
                                KitAppearance(
                                    mode = AppearanceMode.Dark,
                                    motion = MotionLevel.Off,
                                    style = style,
                                ),
                            platform = platform,
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                ChatEnvironment { ChatsScreen(state, RecordingChatsActions()) }
                            }
                        }
                    }
                }
                settleAndCaptureBefore(out, tag)
                tapCueAndCaptureAfter(out, tag)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.settleAndCaptureBefore(out: File, tag: String) {
        waitForIdle()
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
        val cues = onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(cues.isNotEmpty(), "$tag: expected a +N cue at 360dp")
        assertCuesInFrame(cues, "$tag before")
        saveShot(File(out, "$tag-before.png"))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.tapCueAndCaptureAfter(out: File, tag: String) {
        onAllNodes(hasText("+", substring = true), useUnmergedTree = true).onLast().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
        val line = onNodeWithTag("kit-filter-chips").fetchSemanticsNode().boundsInRoot
        val revealed =
            onAllNodes(hasText("All connections"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        assertTrue(revealed != null, "$tag after: All connections missing")
        val rb = revealed.boundsInRoot
        assertTrue(
            rb.left >= line.left - EDGE_SLACK && rb.right <= line.right + EDGE_SLACK,
            "$tag after: All connections $rb not whole inside $line",
        )
        val cues = onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        assertCuesInFrame(cues, "$tag after")
        saveShot(File(out, "$tag-after.png"))
    }

    private fun assertCuesInFrame(cues: List<androidx.compose.ui.semantics.SemanticsNode>, label: String) {
        for (cue in cues) {
            val b = cue.boundsInRoot
            assertTrue(
                b.left >= -EDGE_SLACK && b.right <= W_PX + EDGE_SLACK,
                "$label: cue $b overflows 360dp frame",
            )
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.saveShot(file: File) {
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        assertTrue(file.isFile)
    }

    @Test
    fun `home 360dp Solid text 1_0 before and after cue tap`() =
        capturePair("home-360-solid-t10", Style.Solid, 1f)

    @Test
    fun `home 360dp Solid text 1_3 before and after cue tap`() =
        capturePair("home-360-solid-t13", Style.Solid, LARGE_SCALE, project = "/root/projects/site")

    @Test
    fun `home 360dp Glass text 1_0 before and after cue tap`() =
        capturePair("home-360-glass-t10", Style.Glass, 1f)

    @Test
    fun `home 360dp Glass text 1_3 before and after cue tap`() =
        capturePair("home-360-glass-t13", Style.Glass, LARGE_SCALE, project = "/root/projects/site")

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DENSITY = 2f
        const val W_PX = 720
        const val H_PX = 1600
        const val LARGE_SCALE = 1.3f
        const val SETTLE_MS = 1_000L
        const val EDGE_SLACK = 1f
    }
}
