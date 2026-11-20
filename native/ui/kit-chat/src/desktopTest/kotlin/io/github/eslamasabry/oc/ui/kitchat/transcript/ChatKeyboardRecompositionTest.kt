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
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onNodeWithTag
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
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-CHAT-EDGE-025's smoothness half: the keyboard's inset moves every frame while it opens, and a floating chat reads
 * the bottom inset only where its foot fade is drawn, so the frame is not composed again on each frame of that travel.
 * The keyboard is a WindowInsets whose bottom is snapshot state (as the platform's animated inset is), moved 20 times;
 * the Recomposer's change count must not follow it: the transcript list reads the chrome's bottom padding (the bottom
 * block's measured height, which carries the inset) only where it is measured and placed.
 */
// Pixel and layout scenes: the scene builders and messages carry long lines.
@Suppress("MaxLineLength")
class ChatKeyboardRecompositionTest {
    private val rows = (0 until 40).map { i -> "Row $i: the build finished and every check passed, then the notes were written." }

    private class MovingInsets(val bottom: MutableIntState) : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0
        override fun getTop(density: Density): Int = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0
        override fun getBottom(density: Density): Int = bottom.intValue
    }

    @Test
    fun `B-CHAT-EDGE-025 the chat frame is not composed again while the keyboard inset moves`() {
        val bottom = mutableIntStateOf(63)
        val insets = MovingInsets(bottom)
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Full), DesktopKitPlatform(), "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitScreenInsets provides insets) {
                            KitLiveBackground(Modifier.size(W.dp, H.dp)) { Page() }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(12)
            waitForIdle()
            val recomposer = Recomposer.runningRecomposers.value.first()
            val before = recomposer.changeCount
            for (i in 1..20) {
                bottom.intValue = 63 + i * 40
                mainClock.advanceTimeByFrame()
            }
            waitForIdle()
            val changes = recomposer.changeCount - before
            println("CHAT-KEYBOARD-RECOMPOSE changes over 20 inset frames: $changes")
            // Public 3.0.0's level was 4 (its frame did not float); before this fix the floating chat was composed again on
            // every frame (22 over 20).
            assertTrue(changes <= 4, "the chat frame was composed again $changes times over 20 frames of the keyboard's travel")
        }
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
        const val D = 2.625f
    }
}
