package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

private const val RENDER_PHASE = "before"

/**
 * Slow-motion 360 dp frame sequence of rapid turn arrivals while following (B-530):
 * captures pre-arrival rest, 16 ms (1st frame after arrival), 32 ms, 48 ms (2nd arrival mid-glide),
 * 80 ms, 144 ms, and settled frames into `build/smooth-follow-render`.
 */
@OptIn(ExperimentalTestApi::class)
class SmoothFollowScrollRenders {
    private object Words : ChatTranscriptWords {
        override val jumpToLatest: String = "Jump to latest"

        override fun workLine(calls: Int, failed: Int, state: WorkState): String = "$calls steps"

        override fun notice(kind: NoticeKind): String = "Notice"

        override fun footer(failed: Boolean): String = "More"

        override fun thought(firstLine: String): String = firstLine
    }

    private val out: File =
        (
            System.getenv("SMOOTH_FOLLOW_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("smooth-follow")
                ?: File("build/smooth-follow-render")
        ).also(File::mkdirs)

    private fun turns(count: Int, revision: Long = count.toLong()): TranscriptState =
        stateOf(
            *(1..count).flatMap { n ->
                turn(
                    n,
                    ask = "Turn $n prompt: check chat scroll",
                    answer = "Turn $n reply: keeping older lines steady as new turns arrive.",
                )
            }.toTypedArray(),
            revision = revision,
        )

    @Test
    fun `B-530 slow motion frame sequence at 360 dp`() =
        kitUiTest(WIDTH_PX, HEIGHT_PX) {
            var state by mutableStateOf(turns(20))
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = AppearanceMode.Dark,
                                style = Style.Solid,
                                motion = MotionLevel.Full,
                            ),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            ChatTranscript(
                                state = state,
                                words = Words,
                                list = rememberTranscriptListState(),
                                reveal = rememberRevealRegistry(),
                                motion = KitMotionLevel.Full,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            repeat(4) { mainClock.advanceTimeByFrame() }

            fun snap(tag: String) {
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "$RENDER_PHASE-$tag.png"),
                )
            }

            snap("f00-000ms-rest")
            state = turns(21, revision = 21)
            mainClock.advanceTimeByFrame()
            snap("f01-016ms-arrival")
            mainClock.advanceTimeByFrame()
            snap("f02-032ms-glide")
            state = turns(22, revision = 22)
            mainClock.advanceTimeByFrame()
            snap("f03-048ms-burst")
            repeat(2) { mainClock.advanceTimeByFrame() }
            snap("f04-080ms-glide")
            repeat(4) { mainClock.advanceTimeByFrame() }
            snap("f05-144ms-glide")
            repeat(30) { mainClock.advanceTimeByFrame() }
            snap("f06-624ms-settled")

            mainClock.autoAdvance = true
            waitForIdle()
        }

    private companion object {
        const val DENSITY = 2f
        const val WIDTH_PX = 720 // 360 dp
        const val HEIGHT_PX = 1560 // 780 dp
    }
}
