@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.platform.KitVisibility
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Background CPU 2026-11-14: a wait's clock ("Working 12 s") ticks in its composition's coroutine, which keeps running
 * while the app is hidden. It now parks there and reads the time afresh when the app shows again.
 */
class KitSinceHiddenTest {
    @Test
    fun `BG-CPU-003 a ticking wait reads no clock while the app is hidden and reads it at once when it shows`() =
        runComposeUiTest {
            val visibility = KitVisibility()
            val start = Clock.System.now()
            var reads = 0
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalKitVisibility provides visibility) {
                    KitSince(since = start, ticks = KitSinceTicks.Seconds, now = { reads++; start }) {}
                }
            }
            mainClock.advanceTimeBy(5_000)
            val shown = reads
            assertTrue(shown >= 4, "every second while the app shows ($shown reads in 5 s)")

            visibility.set(false)
            mainClock.advanceTimeBy(1_100) // at most the tick already under way
            val parked = reads
            mainClock.advanceTimeBy(60_000)
            assertEquals(parked, reads, "no clock read in a minute while the app is hidden")

            visibility.set(true)
            mainClock.advanceTimeBy(100)
            assertTrue(reads > parked, "back on screen the wait reads the time at once")
        }
}
