package io.github.eslamasabry.oc.feature.chats.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.engine.agents.feed.FeedRunning
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.ChatEnvironment
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.item
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
import io.github.eslamasabry.oc.ui.kit.KitPrimaryButtonKey
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val PHASE = "before"

private const val W_PX = 1080
private const val H_PX = 2400

private const val NOW = 1_800_000_000_000L

/**
 * B-569 (sweep 2026-11-25, finding 26): on Home, a waiting permission's Allow once is the primary answer and Deny is
 * not green, and the pictures of that row at 360 dp, in Solid, dark and light, at text 1.0 and 1.3.
 */
@OptIn(ExperimentalTestApi::class)
class PolishSheetsColoursTest {
    private var previousLocale: Locale = Locale.getDefault()

    private val out = File("build/polish-render").also { it.mkdirs() }

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private fun feedItem(
        id: String,
        title: String,
        minutesAgo: Long,
        preview: String = "",
        dir: String = "/root/projects/oc_app",
    ): ChatFeedItem = item(id, dir, at = NOW - minutesAgo * 60_000L, title = title).copy(preview = preview)

    private fun ChatFeedItem.by(agent: String): ChatFeedItem = copy(agentId = agent)

    private fun state(): ChatsUi {
        val items =
            listOf(
                feedItem("explain", "Explain this project", 0, "Reading lib/ui/kit to map how the parts fit together")
                    .copy(status = ChatStatus.Running, running = FeedRunning(NOW - 4 * 60_000L), agentId = "claude"),
                feedItem("ask", "Run the tests", 5)
                    .copy(status = ChatStatus.NeedsYou, permission = FeedPermission("req1", "npm test")),
                feedItem("hi", "Hi", 60 * 26, "Hi! What would you like to work on today?"),
                feedItem("who", "You know who you are and what you do", 60 * 27,
                    "I\u2019m OpenCode, a coding agent working in your folder", dir = "/root/projects/site"),
                feedItem("ready", "Start the release notes", 60 * 30, "Ready when you are.").by("codex"),
                feedItem("build", "What should we build?", 60 * 50, "Hi there \u2014 what should we build?"),
                feedItem("help", "Fix the login bug", 60 * 52, dir = "/root/projects/site"),
                feedItem("older1", "Older one", 60 * 80, "This one is past the five"),
                feedItem("older2", "Older two", 60 * 90, "So is this one"),
            )
        val base =
            ChatsState.build(
                ChatFeedSnapshot(items.toImmutableList()),
                ReturnBrief.None,
                ChatsFilters(),
                startOfTodayMs = NOW - 12 * 3_600_000L,
                settingsAvailable = true,
                nowMs = NOW,
            )
        val source =
            ChatsListSourceUi(
                profile = ProfileId("this-phone"),
                name = "This phone",
                shown = true,
                main = true,
                onThisPhone = true,
                unreachable = false,
            )
        return base.copy(needsYou = 0, home = base.home.copy(sources = persistentListOf(source)))
    }

    private fun ComposeUiTest.show(ui: ChatsUi, mode: AppearanceMode, fontScale: Float) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                ChatEnvironment {
                    KitTheme(
                        appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                ChatsScreen(ui, RecordingChatsActions())
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(1_500)
        waitForIdle()
    }

    private fun ComposeUiTest.greenCount(node: SemanticsNodeInteraction): Int {
        val map = node.captureToImage().toPixelMap()
        var green = 0
        for (x in 0 until map.width)
            for (y in 0 until map.height) {
                val p = map[x, y].toArgb()
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                if (g - r >= 40 && g - b >= 40) green++
            }
        return green
    }

    private fun shot(mode: AppearanceMode, scale: Float) {
        val look = if (mode == AppearanceMode.Dark) "dark" else "light"
        val text = if (scale == 1.3f) "text13" else "text10"
        val file = File(out, "26-$PHASE-home-permission-$look-$text.png")
        kitUiTest(W_PX, H_PX) {
            show(state(), mode, scale)
            ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
        }
        assertTrue(file.isFile, "the picture ${file.name} was written")
    }

    @Test
    fun `B-569 Home's Allow once is the primary and Deny is not green`() {
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light))
            kitUiTest(W_PX, H_PX) {
                show(state(), mode, 1f)
                val allow = hasText("Allow once") and SemanticsMatcher.keyIsDefined(KitPrimaryButtonKey)
                onNode(allow).assertExists()
                val deny = onNodeWithText("Deny")
                deny.assertIsDisplayed()
                val green = greenCount(deny)
                assertEquals(0, green, "[$mode] Deny is not green")
            }
    }

    @Test
    fun pictures() {
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            shot(mode, 1f)
            shot(mode, 1.3f)
        }
    }
}
