@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidInkRule
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.platform.KitBackdropSampler
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitVisibility
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Background CPU 2026-11-14 (emulator, Glass): about 40 % of a core with the app behind the launcher, gone under Solid,
 * and a busy render thread in the foreground. Every Glass pane's words' ink sampler woke ten times a second in the
 * pane's coroutine scope, shown or hidden, and sampled whenever its content had recorded again (every frame of a live
 * background). Now it waits for a change, samples once the change settles (at most every [INK_STALE_MS] while the
 * content keeps moving), and parks while the app is hidden.
 */
class KitLiquidInkHiddenTest {
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
        override fun backdropSampler(): KitBackdropSampler = sampler
    }

    @Test
    fun `BG-CPU-002 the ink sampler of a glass pane sleeps while the app is hidden and samples again when it shows`() =
        runComposeUiTest {
            val sampler = CountingSampler()
            val platform = SamplingPlatform(sampler)
            val visibility = KitVisibility()
            var tint by mutableStateOf(Color(0xFF3355FF))
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalKitVisibility provides visibility) {
                    KitTheme(appearance = KitAppearance(style = Style.Glass), platform = platform, localeTag = "en", stillFrame = true) {
                        Box(Modifier.size(360.dp, 480.dp)) {
                            val source = rememberKitFrostState()
                            Column(Modifier.fillMaxSize().kitFrostSource(source)) {
                                repeat(6) {
                                    Box(Modifier.fillMaxWidth().height(40.dp).drawBehind { drawRect(tint) })
                                    Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFFFCC22)))
                                }
                            }
                            KitJumpPill("Latest", {}, visible = true, modifier = Modifier.align(Alignment.Center), backdrop = source)
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(2_000)
            assertTrue(sampler.samples >= 1, "the pane read its content once")
            val first = sampler.samples
            tint = Color(0xFFEEEEEE)
            mainClock.advanceTimeBy(1_000)
            assertTrue(sampler.samples > first, "while the app shows, changed content is sampled")

            visibility.set(false)
            mainClock.advanceTimeBy(200)
            assertTrue(sampler.releases >= 1, "hidden: the GPU target is freed")
            val parked = sampler.samples
            repeat(10) { i ->
                tint = if (i % 2 == 0) Color(0xFF112233) else Color(0xFFDDEEFF)
                mainClock.advanceTimeBy(1_000)
            }
            assertEquals(parked, sampler.samples, "nothing is sampled while the app is hidden, whatever moves")

            visibility.set(true)
            mainClock.advanceTimeBy(1_000)
            assertTrue(sampler.samples > parked, "back on screen the pane samples what changed meanwhile")
        }

    @Test
    fun `BG-CPU-004 still content costs no sample and no wake, moving content is sampled at a capped rate`() =
        runComposeUiTest {
            val sampler = CountingSampler()
            val platform = SamplingPlatform(sampler)
            var version by mutableIntStateOf(0)
            var reads = 0
            mainClock.autoAdvance = false
            setContent {
                KitTheme(appearance = KitAppearance(style = Style.Glass), platform = platform, localeTag = "en", stillFrame = true) {
                    val words =
                        remember {
                            val backdrop = KitLiquidBackdrop(version = { reads++; version }) { _ -> drawRect(Color.Black) }
                            KitLiquidWords(flipInk = Color.Black, themeInk = LiquidInkRule.of(true), backdrop = backdrop, flip = LiquidFlip())
                        }
                    Box(Modifier.size(200.dp, 64.dp).kitLiquidPlateHost(words))
                }
            }
            mainClock.advanceTimeBy(1_000)
            assertEquals(1, sampler.samples, "the pane reads its content once")

            // Nothing changes for half a minute: not one sample, not even a look at the version.
            val readsStill = reads
            mainClock.advanceTimeBy(30_000)
            assertEquals(1, sampler.samples, "zero samples while nothing behind the pane changes")
            assertEquals(readsStill, reads, "the sampler does not wake while nothing changes")

            // One change: one sample, once it settled.
            version++
            Snapshot.sendApplyNotifications()
            mainClock.advanceTimeBy(1_000)
            assertEquals(2, sampler.samples, "a change is sampled once")

            // Content recording on every frame for 3 s (a live picture, a fling): a few samples, not one per 100 ms.
            repeat(188) {
                version++
                Snapshot.sendApplyNotifications()
                mainClock.advanceTimeBy(16)
            }
            val moving = sampler.samples - 2
            assertTrue(moving in 3..6, "about one sample per ${INK_STALE_MS} ms while the content keeps moving, got $moving in 3 s")
        }

    private companion object {
        const val OPAQUE_BLACK: Int = -0x1000000
    }
}
