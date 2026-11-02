package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Flutter test/tool_card_test.dart:514 "formatToolDuration uses tenths under a minute and m/ss past it".
class ToolDurationTest {
    @Test
    fun `B-CHAT-109 durations show tenths under a minute and minutes with padded seconds past it`() {
        assertEquals("0.8s", toolDurationLabel(800))
        assertEquals("12.4s", toolDurationLabel(12_400))
        assertEquals("1m 05s", toolDurationLabel(65_000))
        assertEquals("12m 03s", toolDurationLabel(12 * 60_000L + 3_000))
        assertEquals(ToolDuration.Seconds("4.2"), toolDuration(4_200))
        assertEquals(ToolDuration.MinutesSeconds(1, "15"), toolDuration(75_000))
    }

    @Test
    fun `B-CHAT-109 a negative duration reads as zero and tenths round like Dart`() {
        assertEquals("0.0s", toolDurationLabel(-5))
        assertEquals("60.0s", toolDurationLabel(59_999))
        // Dart toStringAsFixed rounds the binary double: 0.05 sits above the half, 0.15 below it, 0.25 exactly on it.
        assertEquals("0.1s", toolDurationLabel(50))
        assertEquals("0.1s", toolDurationLabel(150))
        assertEquals("0.3s", toolDurationLabel(250))
        assertEquals("0.8s", toolDurationLabel(849))
    }

    @Test
    fun `B-CHAT-109 a duration needs both times`() {
        assertEquals(12_400, toolDurationMs(ToolState(ToolStatus.Completed, startedAtMs = 1_000, completedAtMs = 13_400)))
        assertNull(toolDurationMs(ToolState(ToolStatus.Running, startedAtMs = 1_000)))
        assertEquals(0, toolDurationMs(ToolState(ToolStatus.Completed, startedAtMs = 5_000, completedAtMs = 1_000)))
    }
}
