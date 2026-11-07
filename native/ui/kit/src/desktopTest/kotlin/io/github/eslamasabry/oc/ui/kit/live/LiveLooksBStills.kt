package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.row.KitConversation
import io.github.eslamasabry.oc.ui.kit.row.KitConversationRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The owner's pictures (E20-S28): each of builder B's looks behind a home list of real kit conversation rows, dark and
 * light. A PNG at 1080x2400 (density 2.625) and 30 frames of 3 s at four times the real speed for a GIF. Runs only with
 * LIVE_STILLS_OUT set to the output folder (tool: docs/design/live-backgrounds-native/make-gifs.sh).
 */
@OptIn(ExperimentalTestApi::class)
class LiveLooksBStills {
    private val ids = listOf("dotRipple", "topo", "flowThreads", "lightRays", "constellation", "bokeh", "halftone")

    private val needsYou = KitRowState("Needs you", KitRowStateTone.NeedsYou)

    private val rows =
        listOf(
            KitConversation("Fix the login redirect on slow networks", "web-app", "2m", null, "main", "laptop"),
            KitConversation("Why does the build cache miss?", "build-tools", "14m", needsYou, server = "laptop"),
            KitConversation("Write release notes for 2.2", "docs", "1h", null, branch = "release/2.2"),
            KitConversation("Move settings into one screen", "mobile", "3h", null, branch = "settings", server = "vps"),
            KitConversation("Find the leak in the image cache", "mobile", "Yesterday", null, server = "vps"),
            KitConversation("Tidy the onboarding words", "mobile", "Mon", null),
            KitConversation("Speed up search on big projects", "search", "Sun", null, branch = "fts"),
            KitConversation("Plan the next sprint", "notes", "Sat", null),
        )

    @Test fun `owner stills and frames`() {
        val out = System.getenv("LIVE_STILLS_OUT")?.let(::File) ?: return
        val only = System.getenv("LIVE_STILLS_ONLY")?.split(',')?.toSet()
        out.mkdirs()
        for (dark in listOf(true, false)) {
            val kc = LiveLooksBTest.kitColors(dark)
            val colors = LiveColors(kc.ground, kc.accent, kc.text1)
            val mode = if (dark) "dark" else "light"
            for (id in ids.filter { only == null || it in only }) {
                val look = SkiaLiveLook(LiveLookRegistry.of(id)!!)
                shoot(look, Shot(colors, dark, 1080, 2400, 2.625f, listOf(0f))) { _, img ->
                    ImageIO.write(img, "png", File(out, "$id-$mode.png"))
                }
                val frames = File(out, "frames/$id-$mode").also { it.mkdirs() }
                shoot(look, Shot(colors, dark, 412, 915, 1f, List(30) { it * 0.4f })) { i, img ->
                    ImageIO.write(img, "png", File(frames, "f%02d.png".format(i)))
                }
            }
        }
    }

    /** One capture run: theme, window in pixels, density and the clock times to capture. */
    private class Shot(
        val colors: LiveColors,
        val dark: Boolean,
        val w: Int,
        val h: Int,
        val density: Float,
        val times: List<Float>,
    )

    private fun shoot(look: SkiaLiveLook, shot: Shot, save: (Int, java.awt.image.BufferedImage) -> Unit) {
        val colors = shot.colors
        val dark = shot.dark
        val density = shot.density
        val times = shot.times
        val time = mutableFloatStateOf(times.first())
        runDesktopComposeUiTest(shot.w, shot.h) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    KitTheme(
                        KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        val ground = LocalKitColors.current.ground
                        val paint = remember { org.jetbrains.skia.Paint() }
                        Box(
                            Modifier.fillMaxSize().drawBehind {
                                drawRect(ground)
                                val frame = LiveFrame(size.width, size.height, density, time.floatValue, colors)
                                val shader = look.shader(frame)
                                drawContext.canvas.nativeCanvas.drawPaint(paint.apply { this.shader = shader })
                            }
                        ) {
                            Column(Modifier.padding(top = 56.dp)) {
                                val title = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                KitText("Conversations", title, role = TypeRole.LargeTitle)
                                rows.forEach { KitConversationRow(it, onClick = {}) }
                            }
                        }
                    }
                }
            }
            times.forEachIndexed { i, t ->
                time.floatValue = t
                waitForIdle()
                save(i, onAllNodes(isRoot())[0].captureToImage().toAwtImage())
            }
        }
    }
}
