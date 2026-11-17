@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarScope
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-438 (owner 2026-11-17, build 10073): a chat's chrome floats. One header capsule on top, the live message box at the
 * bottom, the transcript running behind both with nothing painted but each part's own fill: no band above the box, no
 * mask, no fade, and "Jump to latest" a round arrow at the box's end corner. One scene, every state; [Scene.floating]
 * false is the old frame, which the red runs use.
 */
class ChatFloatingEdgesTest {
    private data class Scene(
        val floating: Boolean = true,
        val rtl: Boolean = false,
        val keyboard: Boolean = false,
        val multiline: Boolean = false,
        val request: Boolean = false,
        val fontScale: Float = 1f,
        val longTitle: Boolean = false,
        val white: Boolean = false,
        val dark: Boolean = true,
        val picture: Color = Color.White,
        val motion: MotionLevel = MotionLevel.Off,
        val style: Style = Style.Solid,
        /** A phone's real numbers: its density and its status and gesture bars, dp (the default is the harness's own). */
        val density: Float = 1f,
        val statusDp: Int = STATUS_DP,
        val navDp: Int = NAV_DP,
    )

    private val rows = (0 until 40).map { i -> if (i == 0) "Row zero the newest line of the conversation" else "Row $i: the build finished and every check passed, then the notes were written." }

    private fun ComposeUiTest.show(scene: Scene) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(scene.density, scene.fontScale),
                LocalLayoutDirection provides if (scene.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                KitTheme(
                    KitAppearance(mode = if (scene.dark) AppearanceMode.Dark else AppearanceMode.Light, style = scene.style, motion = scene.motion),
                    DesktopKitPlatform(),
                    if (scene.rtl) "ar" else "en",
                    stillFrame = true,
                ) {
                    CompositionLocalProvider(
                        LocalKitScreenInsets provides WindowInsets(top = scene.statusDp.dp, bottom = if (scene.keyboard) 330.dp else scene.navDp.dp),
                    ) {
                        Box(Modifier.size(W.dp, H.dp).testTag("scene")) {
                            val source = rememberKitFrostState()
                            val state = rememberTranscriptListState()
                            KitScreen(
                                page = true,
                                overlayChrome = true,
                                frost = source,
                                floating = scene.floating,
                                topBar = {
                                    KitTopBar(
                                        if (scene.longTitle) "Tidy the docs demo README and check every link in the demo-shop repository" else "Tidy the README",
                                        scope = { KitTopBarScope("demo-shop", "This phone", "Files of demo-shop", onProject = {}, branch = "Git", onBranch = {}) },
                                        exit = KitTopBarExit.Back,
                                        exitLabel = "Back",
                                        onExit = {},
                                        backBesideTitle = true,
                                    )
                                },
                                bottom = {
                                    Column(Modifier.fillMaxWidth()) {
                                        if (scene.request) Box(Modifier.fillMaxWidth().height(80.dp).background(Color(0xFF303036)).testTag("request-card"))
                                        KitComposerBox(
                                            running = false,
                                            stacked = true,
                                            hasLeading = true,
                                            field = {
                                                KitText(
                                                    if (scene.multiline) "A long message that grows the box to several lines so the last line of the conversation must follow the box up when it is tall, and again when it grows."
                                                    else "Ask OpenCode…",
                                                    role = TypeRole.Body,
                                                    tone = TextTone.Tertiary,
                                                    kind = KitTextKind.Frame,
                                                )
                                            },
                                            leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
                                            trailing = { KitComposerSend("Send", onClick = {}) },
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        )
                                    }
                                },
                                edges = KitScreenEdges.Bleed,
                            ) {
                                Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                                    KitTranscriptList(state, rows.first(), "Jump to latest", KitMotionLevel.Off) {
                                        items(rows.indices.toList(), key = { it }) { i ->
                                            if (scene.white && i == 3) Box(Modifier.fillMaxWidth().height(900.dp).background(scene.picture).testTag("white-picture"))
                                            else KitText(rows[i], Modifier.padding(horizontal = 16.dp, vertical = 8.dp), role = TypeRole.Body)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun lum(c: Color): Float {
        fun f(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * f(c.red) + 0.7152f * f(c.green) + 0.0722f * f(c.blue)
    }

    private fun ComposeUiTest.bounds(tag: String): Rect = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.boundsOfText(text: String): Rect = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.scrollAway(white: Boolean = false) {
        onNodeWithTag("transcript-list").performScrollToIndex(if (white) 3 else 12)
        waitForIdle()
    }

    private fun run(scene: Scene, body: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest((W * scene.density).toInt(), (H * scene.density).toInt()) { show(scene); body() }

    @Test
    fun `B-CHAT-EDGE-001 the last line of a conversation rests fully above the box at rest`() =
        run(Scene()) {
            val last = boundsOfText(rows.first())
            val box = bounds("kit-composer-box")
            assertTrue(last.bottom <= box.top, "the newest line ends at ${last.bottom}, the box starts at ${box.top}")
        }

    @Test
    fun `B-CHAT-EDGE-002 no band stands above the box at rest, the last line is close above it`() =
        run(Scene()) {
            val last = boundsOfText(rows.first())
            val box = bounds("kit-composer-box")
            // The list's own 16 dp, the row's 8, the block's 8 and the scene's 8 above the box, and no more: the old frame kept 8 dp of fade too.
            assertTrue(box.top - last.bottom <= 46f, "a band of ${box.top - last.bottom} dp lies above the box")
        }

    @Test
    fun `B-CHAT-EDGE-003 the last line rests above a box that grew to several lines`() =
        run(Scene(multiline = true)) {
            val last = boundsOfText(rows.first())
            val box = bounds("kit-composer-box")
            assertTrue(box.height > 140f, "the box grew to ${box.height} dp")
            assertTrue(last.bottom <= box.top, "the newest line ends at ${last.bottom}, the box starts at ${box.top}")
        }

    @Test
    fun `B-CHAT-EDGE-004 the last line rests above the box with the keyboard open`() =
        run(Scene(keyboard = true)) {
            val last = boundsOfText(rows.first())
            val box = bounds("kit-composer-box")
            assertTrue(last.bottom <= box.top, "the newest line ends at ${last.bottom}, the box starts at ${box.top}")
        }

    @Test
    fun `B-CHAT-EDGE-005 the last line rests above a request showing over the box`() =
        run(Scene(request = true)) {
            val last = boundsOfText(rows.first())
            val card = bounds("request-card")
            assertTrue(last.bottom <= card.top, "the newest line ends at ${last.bottom}, the request starts at ${card.top}")
        }

    @Test
    fun `B-CHAT-EDGE-006 the first line of a conversation rests fully below the header capsule`() =
        run(Scene()) {
            onNodeWithTag("transcript-list").performScrollToIndex(39)
            waitForIdle()
            val first = boundsOfText(rows.last())
            val capsule = bounds("kit-header-capsule")
            assertTrue(first.top >= capsule.bottom, "the first line starts at ${first.top}, the capsule ends at ${capsule.bottom}")
        }

    @Test
    fun `B-CHAT-EDGE-007 the header is one capsule across the width with the chat's side margin on both sides`() =
        run(Scene()) {
            val capsule = bounds("kit-header-capsule")
            assertEquals(14f, capsule.left, 1f, "the capsule's start margin")
            assertEquals(W - 14f, capsule.right, 1f, "the capsule's end margin")
            assertTrue(capsule.top >= STATUS_DP, "the capsule stands clear of the status area")
        }

    @Test
    fun `B-CHAT-EDGE-008 the jump control is a round arrow centred above the box, 48 dp, still named in words`() =
        run(Scene()) {
            scrollAway()
            val button = bounds("jump-to-latest")
            val box = bounds("kit-composer-box")
            assertTrue(button.width >= 48f && button.height >= 48f, "the target is ${button.width} x ${button.height}")
            assertEquals(W / 2f, button.center.x, 1f, "the button is centred above the box")
            assertTrue(button.bottom <= box.top + 1f, "the button stands above the box")
            onNodeWithContentDescription("Jump to latest").assertExists()
            // No words on it: its label is spoken, never drawn.
            assertTrue(onAllNodesWithText("Jump to latest", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "words are drawn on the button")
        }

    @Test
    fun `B-CHAT-EDGE-023 with motion on, the jump arrow stands centred just above the box and inside the screen`() {
        // Device check 46 (build 10077): with motion on, the arrow sat at [0,0][126,126] under the status bar, because
        // its place (align + padding) was given to the face inside the enter/exit transition, which ignores it. The
        // earlier tests ran with motion off, where it is placed right, and only asked that the arrow exist.
        for (style in listOf(Style.Solid, Style.Glass, Style.Soft)) for (keyboard in listOf(false, true)) for (rtl in listOf(false, true))
        run(Scene(motion = MotionLevel.Full, rtl = rtl, keyboard = keyboard, style = style)) {
            scrollAway()
            mainClock.advanceTimeBy(800)
            waitForIdle()
            val button = bounds("jump-to-latest")
            val box = bounds("kit-composer-box")
            assertEquals(W / 2f, button.center.x, 1f, "the arrow is centred ($style, keyboard $keyboard, rtl $rtl): $button")
            assertTrue(button.bottom <= box.top + 1f && button.bottom >= box.top - 40f, "the arrow stands just above the box: $button, box $box")
            assertTrue(button.top >= STATUS_DP + 48f, "the arrow is clear of the status area and the header: $button")
            assertTrue(button.right <= W && button.left >= 0f && button.bottom <= H, "the arrow is inside the screen: $button")
        }
    }

    @Test
    fun `B-CHAT-EDGE-009 the jump control stays centred in a right-to-left reading and leaves at the end`() =
        run(Scene(rtl = true)) {
            scrollAway()
            assertEquals(W / 2f, bounds("jump-to-latest").center.x, 1f, "the button is centred")
            onNodeWithTag("transcript-list").performScrollToIndex(0)
            waitForIdle()
            assertTrue(onAllNodesWithTag("jump-to-latest", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "the button stays at the end")
        }

    @Test
    fun `B-CHAT-EDGE-013 the jump arrow reads over a white picture and over text, in dark and in light`() {
        for (dark in listOf(true, false)) for (white in listOf(true, false)) for (picture in listOf(Color.White, Color(0xFF0D1117))) {
            run(Scene(white = white, dark = dark, picture = picture)) {
                scrollAway(white)
                val b = bounds("jump-to-latest")
                val image = onNodeWithTag("scene").captureToImage().toPixelMap()
                val px = ArrayList<Color>()
                var y = b.top.toInt() + 6
                while (y < b.bottom.toInt() - 6) {
                    var x = b.left.toInt() + 6
                    while (x < b.right.toInt() - 6) { px += image[x, y]; x += 1 }
                    y += 1
                }
                px.sortBy { lum(it) }
                val fill = px[px.size / 2]
                val ink = px.maxByOrNull { kotlin.math.abs(lum(it) - lum(fill)) }!!
                val ratio = (maxOf(lum(fill), lum(ink)) + 0.05f) / (minOf(lum(fill), lum(ink)) + 0.05f)
                assertTrue(ratio >= 4.5f, "the arrow's contrast is $ratio (dark=$dark, picture behind=$white $picture)")
            }
        }
    }

    @Test
    fun `B-CHAT-EDGE-014 the jump arrow is announced as a button named exactly Jump to latest`() =
        run(Scene()) {
            scrollAway()
            val node = onNodeWithTag("jump-to-latest", useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(listOf("Jump to latest"), node.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription])
            assertEquals(androidx.compose.ui.semantics.Role.Button, node.config[androidx.compose.ui.semantics.SemanticsProperties.Role])
        }

    @Test
    fun `B-CHAT-EDGE-016 the jump arrow never sits on the box's top edge when the box grows, or with the keyboard open`() {
        for (scene in listOf(Scene(multiline = true), Scene(keyboard = true), Scene(multiline = true, request = true))) {
            run(scene) {
                scrollAway()
                val button = bounds("jump-to-latest")
                val box = bounds("kit-composer-box")
                assertTrue(button.bottom <= box.top, "the arrow's target ends at ${button.bottom}, the box starts at ${box.top} ($scene)")
            }
        }
    }

    @Test
    fun `B-CHAT-EDGE-010 the header's targets are 48 dp, its title is one line and still a heading`() =
        run(Scene(longTitle = true)) {
            val back = onNodeWithContentDescription("Back", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(back.width >= 48f && back.height >= 48f, "Back is ${back.width} x ${back.height}")
            val title = bounds("kit-top-bar-title")
            assertTrue(title.height < 40f, "the title takes more than one line (${title.height} dp)")
        }

    @Test
    fun `B-CHAT-EDGE-011 nothing is cut at text 1_3, the capsule and the box stay inside the width`() =
        run(Scene(fontScale = 1.3f, longTitle = true)) {
            val capsule = bounds("kit-header-capsule")
            val box = bounds("kit-composer-box")
            assertTrue(capsule.left >= 0f && capsule.right <= W, "the capsule is cut: $capsule")
            assertTrue(box.left >= 0f && box.right <= W, "the box is cut: $box")
        }

    @Test
    fun `B-CHAT-EDGE-012 nothing of the transcript is drawn under the status area or under the box in the gesture area`() =
        run(Scene(white = true)) {
            onNodeWithTag("transcript-list").performScrollToIndex(3)
            waitForIdle()
            val image = onNodeWithTag("scene").captureToImage().toPixelMap()
            // The white picture runs through the whole page; the clock's row must not show it.
            for (y in listOf(2, STATUS_DP / 2)) {
                val c = image[W / 2, y]
                assertTrue(c.red < 0.5f && c.green < 0.5f && c.blue < 0.5f, "the picture shows under the status area at y=$y")
            }
            val under = image[W / 2, H - NAV_DP - 3]
            assertTrue(under.red < 0.5f, "the picture shows between the box and the gesture area")
        }


    /** A phone's numbers (a 2.625 density, a tall status bar, a gesture bar), every style that floats, dark and light. */
    private val phone = listOf(Style.Solid, Style.Glass, Style.Soft).flatMap { st -> listOf(true, false).map { d -> Scene(density = 2.625f, statusDp = 52, navDp = 24, style = st, dark = d, motion = MotionLevel.Full) } }

    private fun ComposeUiTest.scrolled(): androidx.compose.ui.graphics.ImageBitmap {
        onNodeWithTag("transcript-list").performScrollToIndex(12)
        mainClock.advanceTimeBy(800)
        waitForIdle()
        return onNodeWithTag("scene").captureToImage()
    }

    private fun same(a: Color, b: Color, tol: Float = 0.02f) =
        kotlin.math.abs(a.red - b.red) <= tol && kotlin.math.abs(a.green - b.green) <= tol && kotlin.math.abs(a.blue - b.blue) <= tol

    @Test
    fun `B-CHAT-EDGE-024 the status area shows the ground only, down to the capsule, with the phone's real insets`() {
        for (scene in phone.filter { it.style != Style.Soft }) run(scene.copy(white = false)) {
            val image = scrolled().toPixelMap()
            val capsule = bounds("kit-header-capsule")
            val d = scene.density
            val ground = image[2, 2]
            // From the top of the window to the capsule's top edge, the whole width: one colour, the page's ground.
            var y = 0
            while (y < capsule.top.toInt() - 1) {
                var x = 0
                while (x < (W * d).toInt()) {
                    assertTrue(same(image[x, y], ground), "words show above the capsule at ($x,$y), ${scene.style} dark=${scene.dark}: ${image[x, y]} vs $ground; capsule top ${capsule.top}")
                    x += 3
                }
                y += 1
            }
        }
    }

    @Test
    fun `B-CHAT-EDGE-025 no transcript text shows below the box's top, outside its rounded corners, nor in the gesture area`() {
        for (scene in phone) run(scene) {
            val image = scrolled().toPixelMap()
            val box = bounds("kit-composer-box")
            val d = scene.density
            val r = 24f * d
            // The box's two lower corners: what lies outside the rounded outline is page ground, never a letter.
            for (corner in listOf(true, false)) {
                var y = (box.bottom - r).toInt()
                while (y < box.bottom.toInt()) {
                    var x = 0
                    while (x < r.toInt()) {
                        val px = if (corner) box.left.toInt() + x else box.right.toInt() - 1 - x
                        val cx = if (corner) box.left + r else box.right - r
                        val cy = box.bottom - r
                        val out = Math.hypot((px - cx).toDouble(), (y - cy).toDouble()) > r + 2
                        if (out && y > cy) assertTrue(lum(image[px, y]) < 0.35f, "a letter shows outside the box's corner at ($px,$y), ${scene.style} dark=${scene.dark}")
                        x += 1
                    }
                    y += 1
                }
            }
            // Below the box: the ground, one colour, to the bottom of the window (the gesture area).
            val ground = image[2, (H * d).toInt() - 2]
            var y = (box.bottom + 14f * d).toInt()
            while (y < (H * d).toInt()) {
                var x = 0
                while (x < (W * d).toInt()) {
                    assertTrue(same(image[x, y], ground, 0.05f), "the gesture area is not one ground at ($x,$y), ${scene.style} dark=${scene.dark}: ${image[x, y]} vs $ground")
                    x += 7
                }
                y += 3
            }
        }
    }

    @Test
    fun `B-CHAT-EDGE-026 the gesture area is dark in Glass dark with the live background off`() =
        run(Scene(density = 2.625f, statusDp = 52, navDp = 24, style = Style.Glass, dark = true, motion = MotionLevel.Full)) {
            val image = scrolled().toPixelMap()
            val d = 2.625f
            for (y in listOf((H * d).toInt() - 3, (H * d).toInt() - (12 * d).toInt(), (H * d).toInt() - (23 * d).toInt())) for (x in listOf(3, (W * d / 2).toInt(), (W * d).toInt() - 4)) {
                val c = image[x, y]
                assertTrue(c.alpha > 0.99f && lum(c) < 0.1f, "the gesture area at ($x,$y) is $c, not the dark ground")
            }
        }

    @Test
    fun `B-CHAT-EDGE-027 the header capsule is one part in every style, nothing is cast beside or under it`() {
        for (style in listOf(Style.Brutal, Style.Paper, Style.Signal, Style.Solid)) for (dark in listOf(true, false)) run(Scene(style = style, dark = dark)) {
            val image = onNodeWithTag("scene").captureToImage().toPixelMap()
            val c = bounds("kit-header-capsule")
            val ground = image[2, c.center.y.toInt()]
            // The margin beside the capsule (x just past its end edge, over its height) is the page's ground: no offset block.
            for (dx in listOf(3, 6)) {
                var y = c.top.toInt() + 12
                while (y < c.bottom.toInt() - 12) {
                    val px = image[c.right.toInt() + dx, y]
                    assertTrue(same(px, ground, 0.05f), "something is cast beside the capsule at (${c.right.toInt() + dx},$y), $style dark=$dark: $px vs $ground")
                    y += 2
                }
            }
        }
    }

    private companion object {
        const val W = 360
        const val H = 780
        const val STATUS_DP = 28
        const val NAV_DP = 20
    }
}
