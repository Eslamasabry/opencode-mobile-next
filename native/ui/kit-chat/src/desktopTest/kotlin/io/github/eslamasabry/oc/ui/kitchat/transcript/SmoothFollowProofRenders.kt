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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
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
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Slow-motion 360 dp BEFORE (1-frame snap-up) vs AFTER (smooth follow-scroll) proof renders
 * for B-530, written to `build/smooth-follow-renders` (and `~/render-out/smooth-follow` if present).
 */
class SmoothFollowProofRenders {
    private val outputs: List<File> =
        buildList {
            add(File("build/smooth-follow-renders").also(File::mkdirs))
            File(System.getProperty("user.home"), "render-out")
                .takeIf { it.isDirectory }
                ?.resolve("smooth-follow")
                ?.also(File::mkdirs)
                ?.let(::add)
        }

    private fun initialRows(): List<String> =
        (20 downTo 1).map { i -> "Turn $i: older transcript line kept steady while new turns arrive." }

    @Test
    fun `B-530 before and after 360 dp slow-motion sequence`() {
        capture(phase = "before", motion = KitMotionLevel.Off, level = MotionLevel.Off)
        capture(phase = "after", motion = KitMotionLevel.Full, level = MotionLevel.Full)
    }

    private fun capture(phase: String, motion: KitMotionLevel, level: MotionLevel) =
        runDesktopComposeUiTest(W_PX, H_PX) {
            var rows by mutableStateOf(initialRows())
            lateinit var list: TranscriptListState
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = level),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        Page(rows, motion) { list = it }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            repeat(4) { mainClock.advanceTimeByFrame() }

            fun snap(tag: String) {
                val img = onAllNodes(isRoot())[0].captureToImage().toAwtImage()
                for (dir in outputs) ImageIO.write(img, "png", File(dir, "$phase-$tag.png"))
            }

            val restTop = onNodeWithTag("Turn 20").getBoundsInRoot().top
            snap("f00-000ms-rest")

            rows = listOf("Turn 21: newly arrived reply streaming into the bottom edge.") + rows
            mainClock.advanceTimeByFrame()
            val frame1Top = onNodeWithTag("Turn 20").getBoundsInRoot().top
            snap("f01-016ms-arrival")

            mainClock.advanceTimeByFrame()
            snap("f02-032ms-glide")

            rows = listOf("Turn 22: second burst arrival coalesced mid-glide cleanly.") + rows
            mainClock.advanceTimeByFrame()
            snap("f03-048ms-burst")

            repeat(2) { mainClock.advanceTimeByFrame() }
            snap("f04-080ms-glide")

            repeat(4) { mainClock.advanceTimeByFrame() }
            snap("f05-144ms-glide")

            repeat(30) { mainClock.advanceTimeByFrame() }
            snap("f06-624ms-settled")

            if (phase == "before") {
                assertTrue(abs((restTop - frame1Top).value) > 24f, "before snaps up in 1 frame")
            } else {
                assertTrue(abs((restTop - frame1Top).value) <= 1f, "after stays anchored on frame 1")
            }
            assertTrue(list.lazy.firstVisibleItemIndex == 0, "$phase settled on index 0")
            mainClock.autoAdvance = true
            waitForIdle()
        }

    @Composable
    private fun Page(rows: List<String>, motion: KitMotionLevel, onState: (TranscriptListState) -> Unit) {
        val source = rememberKitFrostState()
        val state = rememberTranscriptListState()
        onState(state)
        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
            Box(Modifier.size(W_DP.dp, H_DP.dp)) {
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = source,
                    floating = false,
                    topBar = { Bar() },
                    bottom = { Composer() },
                    edges = KitScreenEdges.Bleed,
                ) {
                    Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                        KitTranscriptList(state, rows.firstOrNull(), "Jump to latest", motion) {
                            items(rows, key = { it }, contentType = { "row" }) { text ->
                                val tag = text.substringBefore(':')
                                KitText(
                                    text,
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag(tag),
                                    role = TypeRole.Body,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Bar() {
        KitTopBar(
            "Smooth follow-scroll",
            scope = {
                KitTopBarScope(
                    "demo-shop",
                    "This phone",
                    "Files of demo-shop",
                    onProject = {},
                    branch = "Git",
                    onBranch = {},
                )
            },
            exit = KitTopBarExit.Back,
            exitLabel = "Back",
            onExit = {},
            backBesideTitle = true,
        )
    }

    @Composable
    private fun Composer() {
        KitComposerBox(
            running = false,
            stacked = true,
            hasLeading = true,
            field = {
                KitText("Ask OpenCode…", role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Frame)
            },
            leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
            trailing = { KitComposerSend("Send", onClick = {}) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    private companion object {
        const val W_DP = 360
        const val H_DP = 780
        const val DENSITY = 2f
        const val W_PX = 720
        const val H_PX = 1560
    }
}
