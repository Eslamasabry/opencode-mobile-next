@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.request.KitRequest
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.edgeLightAsksFetch
import io.github.eslamasabry.oc.ui.words.res.edgeLightAsksRun
import io.github.eslamasabry.oc.ui.words.res.edgeLightAsksSearch
import io.github.eslamasabry.oc.ui.words.res.edgeLightAsksTool
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures only (asserts nothing): the Needs-you row with the words of what it allows, at 360 dp, dark, Solid and Glass,
 * text 1.0 and 1.3. Writes to `build/edge-fix-renders` of the module.
 */
class NeedsYouWordsRenders {
    private fun model(words: String) =
        KitLampPanelModel(
            listOf(
                KitLampSection(
                    KitLampGroup.NeedsYou, "Needs you",
                    listOf(
                        KitLampRow(
                            "base", "Reply with exactly the word: base-one", KitLampRowMark.NeedsYou, state = "for 8m",
                            stateTone = KitLampTone.Attention, request = KitRequest("rq", words),
                        ),
                    ),
                ),
            ),
            all = 3,
            youAreIn = "You are in “Write a 900 word story about a fox”",
        )

    /** The words the app composes: the real string, filled with what the engine hands it (a host, masked words, a tool). */
    @androidx.compose.runtime.Composable
    private fun realWords(name: String): String =
        when (name) {
            "fetch" -> stringResource(Res.string.edgeLightAsksFetch, "example.org")
            "search" -> stringResource(Res.string.edgeLightAsksSearch, "how do foxes keep their tags clear of the river mud in the long, wet spring…")
            "tool" -> stringResource(Res.string.edgeLightAsksTool, "task")
            else -> stringResource(Res.string.edgeLightAsksRun, "git push")
        }

    @Test
    fun `needs you words renders`() {
        val out = File("build/edge-fix-renders").also { it.mkdirs() }
        for (style in listOf(Style.Solid, Style.Glass)) for (scale in listOf(1f, 1.3f)) for (name in listOf("fetch", "search", "tool")) {
            runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
                k2Host(style, true, false, MotionLevel.Full, fontScale = scale, density = D, widthDp = W, heightDp = H) {
                    Box(Modifier.fillMaxSize()) {
                        KitLampPanelHost(
                            open = true,
                            anchorInWindow = { Rect((W - 24) * D, (H - 300) * D, W * D, (H - 244) * D) },
                            side = KitLampSide.End,
                            onClose = {},
                            modifier = Modifier.padding(top = 200.dp, bottom = 24.dp),
                        ) { KitLampPanel(model(realWords(name)), K2.panel, KitLampPanelActions()) }
                    }
                }
                waitForIdle()
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, "words-${name}-${style.name.lowercase()}-x${(scale * 10).toInt()}.png"))
            }
        }
    }

    @Test
    fun `closed light beside a bubble renders`() {
        val out = File("build/edge-fix-renders").also { it.mkdirs() }
        val faces = listOf("quiet" to KitLampFace(), "busy" to KitLampFace(2, listOf(KitLampMark("n", KitLampMarkKind.NeedsYou), KitLampMark("w", KitLampMarkKind.Working, 0.4f))), "nine" to KitLampFace(12, listOf(KitLampMark("n", KitLampMarkKind.NeedsYou), KitLampMark("f", KitLampMarkKind.Failed), KitLampMark("w", KitLampMarkKind.Working, 0.4f))))
        for (style in listOf(Style.Solid, Style.Glass)) for (scale in listOf(1f, 1.3f)) for ((name, face) in faces) {
            runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
                k2Host(style, true, false, MotionLevel.Full, fontScale = scale, density = D, widthDp = W, heightDp = H) {
                    Box(Modifier.fillMaxSize()) {
                        // A bubble flush at the content's edge: 16 dp in from each side.
                        Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 16.dp).background(LocalKitColors.current.surface2, RoundedCornerShape(16.dp)).padding(16.dp)) {
                            KitText("Reply with exactly the word: base-one. The light keeps to the gutter beside this bubble.", role = TypeRole.Body)
                        }
                        KitLamp(face, "Other conversations", {}, Modifier.align(Alignment.CenterEnd))
                    }
                }
                waitForIdle()
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, "light-bubble-${name}-${style.name.lowercase()}-x${(scale * 10).toInt()}.png"))
            }
        }
    }

    private companion object {
        const val W = 360
        const val H = 640
        const val D = 2.625f
    }
}
