package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Open-conversation-plain (Galaxy S22, build 10054): opening an existing conversation grew the page out of its row
 * while Home stayed drawn around it. Glass showed the conversation's skeleton over Home and a first conversation frame
 * with no header; Solid showed the title twice and Home's words through the conversation. Conversations now open with
 * the app's ordinary page transition (fade-forwards) and Back runs its reverse: Home and the conversation are both on
 * screen only inside that transition's own cross-over (the covered page's fade-out), the conversation is whole in the
 * first frame it shows (its bar and title in place), and only one copy of the title is drawn from then on.
 *
 * Home carries a red block and the conversation's page is blue, so each page's visibility is read from the pixels of
 * every frame on the test clock (a semantics node exists while a page is composed, visible or not).
 */
@OptIn(ExperimentalTestApi::class)
class OpenConversationPlainTest {
    private val navigator = ShellNavigator()

    private val installer = EntryInstaller {
        entry<ChatsRoute.Home> {
            KitScreen(title = "Home") {
                Box(Modifier.fillMaxWidth().height(120.dp).background(HOME_MARK))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items((0 until 12).toList(), key = { it }) { i ->
                        KitRow(title(i), "preview $i", onClick = { navigator.openChat(ref(i)) }, growKey = ref(i))
                    }
                }
            }
        }
        entry<ChatRoute> { route ->
            val i = route.ref.sessionId.raw.removePrefix("s").toInt()
            KitGrowPage(growKey = route.ref) {
                KitScreen(title = title(i), backLabel = "Back", onBack = navigator::back) {
                    Box(Modifier.fillMaxSize().background(CHAT_MARK))
                }
            }
        }
    }

    private fun title(i: Int) = "Talk $i"

    private fun ComposeUiTest.show() {
        // As the app ships it (the default); written here so a grow test run before this one cannot leave it changed.
        ChatRoute.openMotion = RouteMotion.SharedAxisX
        val platform = DesktopKitPlatform()
        val deps = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(1f))
        setContent {
            CompositionLocalProvider(LocalKitPlatform provides platform) {
                KitTheme(appearance = KitAppearance(mode = AppearanceMode.Light), platform = platform, localeTag = "en") {
                    NavShell(start = ChatsRoute.Home, deps = deps, gate = RowTransformGate())
                }
            }
        }
        waitForIdle()
    }

    private class Frame(val ms: Long, val home: Boolean, val chat: Boolean, val chatTop: Int?, val chatTrace: Boolean)

    /** Count of sampled pixels where [channel] (16 red, 0 blue) leads the other two by more than [lead]. */
    private fun BufferedImage.marked(channel: Int, lead: Int = LEAD): Pair<Int, Int?> {
        var n = 0
        var top: Int? = null
        for (y in 0 until height step 2) for (x in 0 until width step 2) {
            val p = getRGB(x, y)
            val r = p shr 16 and 255
            val g = p shr 8 and 255
            val b = p and 255
            val by = if (channel == 16) r - maxOf(g, b) else b - maxOf(r, g)
            if (by > lead) {
                n++
                if (top == null) top = y
            }
        }
        return n to top
    }

    private fun ComposeUiTest.frames(span: Long): List<Frame> {
        val out = ArrayList<Frame>()
        var t = 0L
        while (t < span) {
            mainClock.advanceTimeByFrame()
            t += FRAME_MS
            val image = onRoot().captureToImage().toAwtImage()
            val (home, _) = image.marked(16)
            val (chat, chatTop) = image.marked(0)
            val (trace, _) = image.marked(0, TRACE)
            out += Frame(t, home > SEEN, chat > SEEN, chatTop.takeIf { chat > SEEN }, trace > SEEN)
        }
        return out
    }

    /** No frame after [after] ms shows both pages clearly. */
    private fun assertNeverBoth(frames: List<Frame>, what: String, after: Long) {
        for (f in frames) {
            if (f.ms <= after) continue
            assertTrue(
                !(f.home && f.chat),
                "$what, frame at ${f.ms} ms: Home and the conversation are both clearly on screen (allowed only in the " +
                    "first $after ms)",
            )
        }
    }

    /** The bar titles drawn on the page that is on top: nodes with the title above the page's content. */
    private fun ComposeUiTest.barTitles(text: String, contentTop: Int): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().count { it.boundsInRoot.bottom <= contentTop + 1 }

    @Test
    fun `OCP-01 opening a conversation shows Home and the conversation together only in the page transition's cross-over`() =
        runDesktopComposeUiTest(W, H) {
            show()
            mainClock.autoAdvance = false
            runOnIdle { navigator.openChat(ref(3)) }
            val frames = frames(SPAN_MS)
            mainClock.autoAdvance = true
            waitForIdle()
            assertNeverBoth(frames, "open", after = CROSS_OVER_MS)
            val last = frames.last()
            assertTrue(last.chat && !last.home, "the conversation is on screen alone at the end")
            // The conversation is whole in the first frame it shows: its content starts right under its bar (not at a
            // row's place, as a page growing out of the row does), so the bar and its title are on screen with it.
            val rest = assertNotNull(last.chatTop)
            val first = frames.first { it.chat }
            val firstTop = assertNotNull(first.chatTop)
            assertTrue(
                firstTop <= rest + 4,
                "first conversation frame (${first.ms} ms): the page starts at y=$firstTop, not under its bar (y=$rest)",
            )
            // One title, the bar's: Home (with the row's copy of the title) is not drawn after the cross-over.
            assertEquals(1, barTitles(title(3), rest), "the conversation's bar carries its title once")
        }

    @Test
    fun `OCP-02 the first frame of an opened conversation has its title in the bar`() =
        runDesktopComposeUiTest(W, H) {
            show()
            mainClock.autoAdvance = false
            runOnIdle { navigator.openChat(ref(5)) }
            mainClock.advanceTimeByFrame()
            // The page's first composed frame already holds the title its bar shows (the list knows it), above the
            // page's content, once.
            val nodes = onAllNodesWithText(title(5)).fetchSemanticsNodes()
            assertTrue(nodes.isNotEmpty(), "the title is in the conversation's first frame")
            val frames = frames(SPAN_MS)
            mainClock.autoAdvance = true
            waitForIdle()
            val rest = assertNotNull(frames.last().chatTop)
            assertEquals(1, barTitles(title(5), rest), "the bar's title, once")
            val first = frames.first { it.chat }
            assertTrue(assertNotNull(first.chatTop) <= rest + 4, "the first visible frame is the whole page, bar included")
        }

    @Test
    fun `OCP-03 Back from a conversation to Home never shows both clearly in one frame`() =
        runDesktopComposeUiTest(W, H) {
            show()
            runOnIdle { navigator.openChat(ref(4)) }
            waitForIdle()
            mainClock.autoAdvance = false
            runOnIdle { navigator.back() }
            val frames = frames(SPAN_MS)
            mainClock.autoAdvance = true
            waitForIdle()
            // Fade-forwards' reverse: the conversation fades out over the first quarter while Home is still faint, so
            // Home is never clearly drawn while any trace of the conversation is left. A page shrinking into its row
            // over a fully drawn Home is.
            for (f in frames) {
                assertTrue(
                    !(f.home && f.chatTrace),
                    "back, frame at ${f.ms} ms: Home is fully drawn while the conversation is still on screen",
                )
            }
            val last = frames.last()
            assertTrue(last.home && !last.chat, "Home is on screen alone at the end")
        }

    private companion object {
        const val W = 412
        const val H = 860
        const val FRAME_MS = 16L

        /** Longer than the page transition (450 ms) and the grow's spring. */
        const val SPAN_MS = 900L

        /** The covered page's fade-out (fade-forwards' first quarter) plus one frame. */
        val CROSS_OVER_MS: Long = ShellMotion.PAGE_FADE_OUT_MS + FRAME_MS

        /** A channel leads the others by this much: the block is clearly drawn, not a near-transparent trace. */
        const val LEAD = 80

        /** A trace of a page: still drawn, however faint. */
        const val TRACE = 20

        /** Sampled pixels needed to call a page on screen (anti-aliased edges never reach it). */
        const val SEEN = 400

        val HOME_MARK = Color(0xFFE00000)
        val CHAT_MARK = Color(0xFF0000E0)
    }
}
