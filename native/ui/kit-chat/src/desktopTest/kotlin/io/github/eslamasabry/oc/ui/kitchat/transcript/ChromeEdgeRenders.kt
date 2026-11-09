@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
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
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarScope
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStatusStrip
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Proof renders for the owner's 2026-11-09 reports (docs/native/qa/composer-edge-2026-11-09): the chat scrolled up
 * ("Jump to latest" showing) over the painted aurora, header under a 28 dp status bar, chips and message box over a
 * 20 dp navigation bar. Off unless CHROME_EDGE_OUT names a folder; CHROME_EDGE_TAG names the files (before / after).
 */
class ChromeEdgeRenders {
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true
    }

    private val words =
        (0 until 30).map { i ->
            "Paragraph ${30 - i}: the build finished and every check passed; the next step reads the log, compares the " +
                "timings with yesterday's and writes what changed into the notes, so a crash between them never loses a record."
        }

    @Test
    fun `chrome edge renders`() {
        val out = System.getenv("CHROME_EDGE_OUT")?.let(::File) ?: return
        val tag = System.getenv("CHROME_EDGE_TAG") ?: "render"
        out.mkdirs()
        for (style in listOf(Style.Glass, Style.Solid)) {
            val platform: KitPlatform = if (style == Style.Glass) LensPlatform() else DesktopKitPlatform()
            runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                        KitTheme(
                            KitAppearance(
                                mode = AppearanceMode.Dark,
                                style = style,
                                motion = MotionLevel.Calm,
                                liveBackground = true,
                                liveStyle = "auroraImage",
                            ),
                            platform,
                            "en",
                        ) {
                            CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
                                Box(Modifier.size(W.dp, H.dp).testTag("scene")) {
                                    val source = rememberKitFrostState()
                                    val state = rememberTranscriptListState()
                                    KitScreen(
                                        page = true,
                                        overlayChrome = true,
                                        frost = source,
                                        topBar = {
                                            KitTopBar(
                                                "What's latest news on DeepSeek harness?",
                                                scope = { KitTopBarScope("new", "This phone", "Files of new", onProject = {}, branch = "Git", onBranch = {}) },
                                                exit = KitTopBarExit.Back,
                                                exitLabel = "Back",
                                                onExit = {},
                                                backBesideTitle = true,
                                            )
                                        },
                                        bottom = {
                                            Column(Modifier.fillMaxWidth()) {
                                                KitComposerStatusStrip(
                                                    hasChips = true,
                                                    model = { KitChip("DeepSeek V4.1 Flash Free", kind = KitChipKind.Action, onClick = {}) },
                                                ) {
                                                    KitChip("Auto-approve on", kind = KitChipKind.Action, onClick = {})
                                                }
                                                KitComposerBox(
                                                    running = false,
                                                    field = { KitText("Ask OpenCode…", role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Frame) },
                                                    leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
                                                    trailing = { KitComposerSend("Send", onClick = {}) },
                                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                )
                                            }
                                        },
                                        edges = KitScreenEdges.Bleed,
                                    ) {
                                        Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                                            KitTranscriptList(state, words.first(), "Jump to latest", KitMotionLevel.Off) {
                                                items(words, key = { it }) {
                                                    KitText(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), role = TypeRole.Body)
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
                onNodeWithTag("transcript-list").performScrollToIndex(6)
                waitForIdle()
                val image = onNodeWithTag("scene").captureToImage().toAwtImage()
                ImageIO.write(image, "png", File(out, "$tag-${style.name.lowercase()}-dark-scrolled-up.png"))
            }
        }
    }

    private companion object {
        const val W = 412
        const val H = 880
        const val DENSITY = 2.5f
    }
}
