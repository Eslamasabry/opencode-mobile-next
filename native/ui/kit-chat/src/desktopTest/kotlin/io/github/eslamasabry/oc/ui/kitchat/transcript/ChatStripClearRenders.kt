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
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import androidx.compose.ui.graphics.toAwtImage
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import java.io.File
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import javax.imageio.ImageIO

/**
 * Not a check: pictures of a floating chat's foot (B-CHAT-EDGE-025, transcript-strip-clear) for a person to look at.
 * The transcript scrolled so lines pass behind the message box; Solid and Glass, dark and light, over the live
 * background; gesture mode (24 dp strip) and three-button mode (48 dp bar); densities 2.0, 2.625 and 3.0. Each picture is
 * the whole phone screen. Written only when `STRIP_RENDER_DIR` names a folder (default: build/strip-renders).
 */
@Suppress("MaxLineLength")
class ChatStripClearRenders {
    private val rows = (0 until 40).map { i -> "Row $i: the build finished and every check passed, then the notes were written." }

    /** Every style, theme and navigation mode at 2.625; the dark gesture case also at 2.0 and 3.0. */
    private val cases =
        listOf(Style.Solid, Style.Glass).flatMap { style ->
            listOf(true, false).flatMap { dark ->
                listOf("gesture" to 24, "buttons" to 48).flatMap { (mode, nav) ->
                    val densities = if (dark && mode == "gesture") listOf(2.0f, 2.625f, 3.0f) else listOf(2.625f)
                    densities.map { d -> Case(style, dark, mode, nav, d) }
                }
            }
        }

    private data class Case(val style: Style, val dark: Boolean, val mode: String, val nav: Int, val density: Float)

    @Test
    fun renders() {
        val out = File(System.getenv("STRIP_RENDER_DIR") ?: "build/strip-renders").apply { mkdirs() }
        for (c in cases) {
            val name = "strip-${c.style.name.lowercase()}-${if (c.dark) "dark" else "light"}-${c.mode}-d${c.density}.png"
            shot(File(out, name), c.style, c.dark, c.nav, c.density)
        }
    }

    private fun shot(file: File, style: Style, dark: Boolean, navDp: Int, density: Float) =
        runDesktopComposeUiTest((W * density).toInt(), (H * density).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    KitTheme(
                        KitAppearance(
                            mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                            style = style,
                            motion = MotionLevel.Full,
                            liveBackground = true,
                            liveStyle = "auroraImage",
                        ),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 52.dp, bottom = navDp.dp)) {
                            KitLiveBackground(Modifier.size(W.dp, H.dp)) { Page() }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(12)
            mainClock.advanceTimeBy(800)
            waitForIdle()
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", file)
        }

    @Composable
    private fun Page() {
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
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                KitTranscriptList(state, rows.first(), "Jump to latest", KitMotionLevel.Off) {
                    items(rows.indices.toList(), key = { it }) { i ->
                        KitText(rows[i], Modifier.padding(horizontal = 16.dp, vertical = 8.dp), role = TypeRole.Body)
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

