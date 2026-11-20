@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.request.KitRequest
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.edgeLightAsksFetch
import io.github.eslamasabry.oc.ui.words.res.edgeLightAsksRun
import io.github.eslamasabry.oc.ui.words.res.edgeLightAsksSearch
import io.github.eslamasabry.oc.ui.words.res.edgeLightAsksTool
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import org.jetbrains.compose.resources.stringResource

/**
 * Pictures only (asserts nothing): the open edge light panel with one request and with three, at 360 dp with the status
 * bar and navigation insets, dark, Solid and Glass, text 1.0 and 1.3. Writes to `build/lamp-panel-height-renders`.
 */
class LampPanelHeightRenders {
    private val out get() = File("build/lamp-panel-height-renders").also { it.mkdirs() }

    private fun model(count: Int) =
        KitLampPanelModel(
            listOf(
                KitLampSection(
                    KitLampGroup.NeedsYou,
                    "Needs you",
                    (1..count).map {
                        KitLampRow(
                            "base$it",
                            "Reply with exactly the word: base-$it",
                            KitLampRowMark.NeedsYou,
                            state = "for 8m",
                            stateTone = KitLampTone.Attention,
                            request = KitRequest("rq$it", "Asks to fetch: example.org"),
                        )
                    },
                ),
            ),
            all = 3,
            youAreIn = "You are in “Write a 900 word story about a fox”",
        )

    @Test
    fun `open panel renders`() {
        for (style in listOf(Style.Solid, Style.Glass)) for (scale in listOf(1f, 1.3f)) for ((name, count) in listOf("one" to 1, "three" to 3)) {
            runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
                k2Host(
                    style, true, false, MotionLevel.Full,
                    fontScale = scale, density = D, widthDp = W, heightDp = H,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        KitLampPanelHost(
                            open = true,
                            anchorInWindow = { Rect((W - 24) * D, (H - 300) * D, W * D, (H - 244) * D) },
                            side = KitLampSide.End,
                            onClose = {},
                            modifier = Modifier.padding(top = 28.dp, bottom = 24.dp),
                        ) { KitLampPanel(model(count), K2.panel, KitLampPanelActions()) }
                    }
                }
                waitForIdle()
                val name2 = "lamp-panel-$name-${style.name.lowercase()}-x${(scale * 10).toInt()}.png"
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name2))
            }
        }
    }

    private companion object {
        const val W = 360
        const val H = 640
        const val D = 2.625f
    }
}
