package io.github.eslamasabry.oc.ui.kit.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Background CPU 2026-11-14: a ticker that only moves pixels parks while the app is hidden (no timer armed, so no
 * wakes) and runs at once when the app shows again.
 */
class KitVisibilityTest {
    @Test
    fun `BG-CPU-001 a ticker parks while the app is hidden and runs at once when it shows again`() = runTest {
        val visibility = KitVisibility()
        var ticks = 0
        val job = launch {
            while (true) {
                visibility.tick(100)
                ticks++
            }
        }
        advanceTimeBy(1_050)
        assertEquals(10, ticks, "ten times a second while the app shows")

        visibility.set(false)
        advanceTimeBy(60_000)
        assertEquals(10, ticks, "not once in a minute while the app is hidden")

        visibility.set(true)
        runCurrent()
        assertEquals(11, ticks, "back on screen it runs at once")
        advanceTimeBy(1_050)
        assertEquals(21, ticks, "then at its own pace again")
        job.cancel()
    }

    @Test
    fun `BG-CPU-001 the Duration tick parks the same way`() = runTest {
        val visibility = KitVisibility(visible = false)
        var ticks = 0
        val job = launch {
            while (true) {
                visibility.tick(250.milliseconds)
                ticks++
            }
        }
        advanceTimeBy(10_000)
        assertEquals(0, ticks, "hidden from the start: nothing runs")
        visibility.set(true)
        runCurrent()
        assertEquals(1, ticks)
        job.cancel()
    }

    @Test
    fun `BG-CPU-001 the signal of the app starts visible so desktop and tests run as before`() {
        assertEquals(true, KitVisibility.App.visible.value)
    }
}
