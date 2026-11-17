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
    )

    private val rows = (0 until 40).map { i -> if (i == 0) "Row zero the newest line of the conversation" else "Row $i: the build finished and every check passed, then the notes were written." }

    private fun ComposeUiTest.show(scene: Scene) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, scene.fontScale),
                LocalLayoutDirection provides if (scene.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                KitTheme(
                    KitAppearance(mode = if (scene.dark) AppearanceMode.Dark else AppearanceMode.Light, style = Style.Solid, motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    if (scene.rtl) "ar" else "en",
                    stillFrame = true,
                ) {
                    CompositionLocalProvider(
                        LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = if (scene.keyboard) 330.dp else NAV_DP.dp),
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

    private fun run(scene: Scene, body: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(W, H) { show(scene); body() }

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

    private companion object {
        const val W = 360
        const val H = 780
        const val STATUS_DP = 28
        const val NAV_DP = 20
    }
}
