@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.platform.KitBackdropSampler
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoothness guards for the words' ink sampling on a liquid pane (smoothness 2026-11-09). On the owner's phone every
 * sample made a new GPU renderer and waited for the GPU on the UI thread; now a pane keeps one platform sampler for its
 * life, samples only while the content behind it draws again, and releases the sampler when it leaves.
 */
class KitLiquidInkSamplerTest {
    private class CountingSampler : KitBackdropSampler {
        var samples = 0
        var releases = 0

        override suspend fun sample(width: Int, height: Int, density: Density, draw: DrawScope.() -> Unit): IntArray? {
            samples++
            return IntArray(width * height) { OPAQUE_BLACK }
        }

        override fun release() {
            releases++
        }
    }

    private class SamplingPlatform(val sampler: CountingSampler, base: KitPlatform = FakeKitPlatform(liquidGlass = true)) : KitPlatform by base {
        var made = 0
        var snapshots = 0

        override fun backdropSampler(): KitBackdropSampler {
            made++
            return sampler
        }

        override fun readPixels(image: ImageBitmap, out: IntArray, width: Int, height: Int): Boolean {
            snapshots++
            return false
        }
    }

    @Test
    fun `B-KIT-018 a liquid pane samples what lies behind it only when that content draws again, through one platform sampler`() =
        runComposeUiTest {
            val sampler = CountingSampler()
            val platform = SamplingPlatform(sampler)
            var tint by mutableStateOf(Color(0xFF3355FF))
            var pane by mutableStateOf(true)
            mainClock.autoAdvance = false
            setContent {
                KitTheme(appearance = KitAppearance(style = Style.Glass), platform = platform, localeTag = "en", stillFrame = true) {
                    Box(Modifier.size(360.dp, 480.dp)) {
                        val source = rememberKitFrostState()
                        Column(Modifier.fillMaxSize().kitFrostSource(source)) {
                            repeat(12) {
                                // The tint is read in draw only: a change redraws (and re-records) the content, nothing else.
                                Box(Modifier.fillMaxWidth().height(40.dp).drawBehind { drawRect(tint) })
                                Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFFFCC22)))
                            }
                        }
                        if (pane) KitJumpPill("Latest", {}, visible = true, modifier = Modifier.align(Alignment.Center), backdrop = source)
                    }
                }
            }
            mainClock.advanceTimeBy(2_000)
            assertEquals(1, platform.made, "one sampler for the pane's life")
            val settled = sampler.samples
            assertTrue(settled >= 1, "the pane read its content once")

            // Still content: no samples at all, however long the pane shows.
            mainClock.advanceTimeBy(3_000)
            assertEquals(settled, sampler.samples, "a still backdrop is never sampled again")

            // The content draws again: the pane samples it.
            tint = Color(0xFFEEEEEE)
            mainClock.advanceTimeBy(500)
            assertTrue(sampler.samples > settled, "moving content is sampled")
            assertEquals(0, platform.snapshots, "never a snapshot read back on the UI thread")
            assertEquals(1, platform.made, "the same sampler throughout")

            pane = false
            mainClock.advanceTimeBy(500)
            assertEquals(1, sampler.releases, "the sampler is released with its pane")
        }

    private companion object {
        const val OPAQUE_BLACK: Int = -0x1000000
    }
}
