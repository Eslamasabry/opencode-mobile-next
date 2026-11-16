package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.world.holdStages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The hold rule on its own: each stage shows at least [HOLD] before the next, and nothing holds a null. */
@OptIn(ExperimentalCoroutinesApi::class)
class StageHoldTest {
    @Test
    fun `SH-1 two stages 100 ms apart hold the first for about 500 ms`() = runTest {
        val source = MutableStateFlow<String?>(null)
        val seen = mutableListOf<Pair<Long, String?>>()
        backgroundScope.launch { source.holdStages(500) { true }.collect { seen += currentTime to it } }
        runCurrent()
        source.value = "a"
        runCurrent()
        advanceTimeBy(100)
        source.value = "b"
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf<Pair<Long, String?>>(0L to null, 0L to "a", 500L to "b"), seen)
    }

    @Test
    fun `SH-2 a stage that lasted longer than the hold is replaced at once`() = runTest {
        val source = MutableStateFlow<String?>(null)
        val seen = mutableListOf<Pair<Long, String?>>()
        backgroundScope.launch { source.holdStages(500) { true }.collect { seen += currentTime to it } }
        runCurrent()
        source.value = "a"
        runCurrent()
        advanceTimeBy(700)
        source.value = "b"
        runCurrent()
        assertEquals(listOf<Pair<Long, String?>>(0L to null, 0L to "a", 700L to "b"), seen)
    }

    @Test
    fun `SH-3 null passes at once and ends the hold`() = runTest {
        val source = MutableStateFlow<String?>(null)
        val seen = mutableListOf<Pair<Long, String?>>()
        backgroundScope.launch { source.holdStages(500) { true }.collect { seen += currentTime to it } }
        runCurrent()
        source.value = "a"
        runCurrent()
        advanceTimeBy(100)
        source.value = "b"
        runCurrent()
        advanceTimeBy(50)
        source.value = null
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf<Pair<Long, String?>>(0L to null, 0L to "a", 150L to null), seen)
    }
}
