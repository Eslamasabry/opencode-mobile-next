@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
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
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * F1b of build 10113 (the first send): on the chat page's FIRST frame the transcript was laid out with no room for the
 * message box (the box's height was learned only after that frame), so the newest rows lay behind the box and a line
 * peeked out under it. The box's room is known before the first frame is shown.
 */
// Pixel and layout scenes: the scene builders and messages carry long lines.
@Suppress("MaxLineLength")
class ChatFirstFrameRoomTest {
    private val rows = (0 until 40).map { i -> "Row $i: the build finished and every check passed, then the notes were written." }

    /** Where the newest line and the box were laid out when the page was FIRST drawn (positions arrive before draw). */
    private class FirstDraw {
        var newestBottom = Float.NaN
        var boxTop = Float.NaN
        var drawnNewest = Float.NaN
        var drawnBox = Float.NaN
    }

    @Test
    fun `B-CHAT-EDGE-028 the first frame of a chat keeps the newest line above the message box`() {
        for (d in listOf(2.0f, 2.625f, 3.0f)) {
            val seen = FirstDraw()
            runDesktopComposeUiTest((W * d).toInt(), (H * d).toInt()) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(d)) {
                        KitTheme(KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Full), DesktopKitPlatform(), "en", stillFrame = true) {
                            CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 52.dp, bottom = 24.dp)) {
                                KitLiveBackground(Modifier.size(W.dp, H.dp)) {
                                    Box(
                                        Modifier.fillMaxSize().drawWithContent {
                                            if (seen.drawnNewest.isNaN()) {
                                                seen.drawnNewest = seen.newestBottom
                                                seen.drawnBox = seen.boxTop
                                            }
                                            drawContent()
                                        }
                                    ) { Page(seen) }
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                // The first frame as a picture of the settled page, for a person to look at beside the numbers.
                val dir = File(System.getenv("FIRST_FRAME_RENDER_DIR") ?: "build/first-frame-renders").apply { mkdirs() }
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(dir, "settled-d$d.png"))
            }
            assertTrue(!seen.drawnNewest.isNaN() && !seen.drawnBox.isNaN(), "density $d: nothing was laid out before the first draw")
            assertTrue(
                seen.drawnNewest <= seen.drawnBox,
                "density $d: on the first frame drawn the newest line ends at ${seen.drawnNewest}, the box starts at ${seen.drawnBox}",
            )
        }
    }

    @Composable
    private fun Page(seen: FirstDraw) {
        val source = rememberKitFrostState()
        val state = rememberTranscriptListState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            floating = true,
            topBar = {
                KitTopBar(
                    "Tidy the README",
                    scope = { KitTopBarScope("demo-shop", "This phone", "Files of demo-shop", onProject = {}, branch = "Git", onBranch = {}) },
                    exit = KitTopBarExit.Back,
                    exitLabel = "Back",
                    onExit = {},
                    backBesideTitle = true,
                )
            },
            bottom = {
                KitComposerBox(
                    running = false,
                    stacked = true,
                    hasLeading = true,
                    field = { KitText("Ask OpenCode…", role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Frame) },
                    leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
                    trailing = { KitComposerSend("Send", onClick = {}) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onGloballyPositioned { seen.boxTop = it.boundsInRoot().top },
                )
            },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                KitTranscriptList(state, rows.first(), "Jump to latest", KitMotionLevel.Off) {
                    items(rows.indices.toList(), key = { it }) { i ->
                        val place = if (i == 0) Modifier.onGloballyPositioned { seen.newestBottom = it.boundsInRoot().bottom } else Modifier
                        KitText(rows[i], Modifier.padding(horizontal = 16.dp, vertical = 8.dp).then(place), role = TypeRole.Body)
                    }
                }
            }
        }
    }

    private companion object {
        const val W = 360
        const val H = 780
    }
}
