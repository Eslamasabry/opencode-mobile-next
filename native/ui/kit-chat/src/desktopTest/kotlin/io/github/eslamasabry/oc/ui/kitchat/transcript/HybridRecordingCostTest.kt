package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HybridRecordingCostTest {
    @Test
    fun `STEPS-SCROLL-012 hybrid recomposition does not touch every row`() = runComposeUiTest {
        var label by mutableStateOf("Jump to latest")
        var keyCalls = 0
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalTranscriptAnchor provides TranscriptAnchor.Hybrid) {
                ChatTestTheme(dark = true) {
                    KitTranscriptList(
                        state = rememberTranscriptListState(),
                        newestKey = "k0",
                        jumpLabel = label,
                        motion = KitMotionLevel.Off,
                        modifier = Modifier.width(VIEWPORT_W_DP.dp).height(VIEWPORT_H_DP.dp),
                    ) {
                        items(count = ROW_COUNT, key = { index ->
                            keyCalls++
                            "k$index"
                        }) {
                            Box(Modifier.fillMaxWidth().height(ROW_H_DP.dp))
                        }
                    }
                }
            }
        }
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        label = "Back to latest"
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        assertTrue(keyCalls < MAX_KEY_CALLS, "expected fewer than $MAX_KEY_CALLS key calls, got $keyCalls")
    }

    private companion object {
        const val VIEWPORT_W_DP = 360
        const val VIEWPORT_H_DP = 800
        const val ROW_COUNT = 2000
        const val ROW_H_DP = 40
        const val SETTLE_FRAMES = 5
        const val MAX_KEY_CALLS = 400
    }
}
