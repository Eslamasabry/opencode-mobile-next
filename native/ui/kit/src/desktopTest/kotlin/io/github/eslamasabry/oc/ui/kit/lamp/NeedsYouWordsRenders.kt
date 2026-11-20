@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.layout.Box
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

    @Test
    fun `needs you words renders`() {
        val out = File("build/edge-fix-renders").also { it.mkdirs() }
        val cases =
            listOf(
                "fetch" to "Asks to use: https://example.org/docs/getting-started/a-very-long-address-that-goes-on-and-on",
                "run" to "Asks to run: git push",
            )
        for (style in listOf(Style.Solid, Style.Glass)) for (scale in listOf(1f, 1.3f)) for ((name, words) in cases) {
            runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
                k2Host(style, true, false, MotionLevel.Full, fontScale = scale, density = D, widthDp = W, heightDp = H) {
                    Box(Modifier.fillMaxSize()) {
                        KitLampPanelHost(
                            open = true,
                            anchorInWindow = { Rect((W - 24) * D, (H - 300) * D, W * D, (H - 244) * D) },
                            side = KitLampSide.End,
                            onClose = {},
                            modifier = Modifier.padding(top = 200.dp, bottom = 24.dp),
                        ) { KitLampPanel(model(words), K2.panel, KitLampPanelActions()) }
                    }
                }
                waitForIdle()
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, "${name}-${style.name.lowercase()}-x${(scale * 10).toInt()}.png"))
            }
        }
    }

    private companion object {
        const val W = 360
        const val H = 640
        const val D = 2.625f
    }
}
