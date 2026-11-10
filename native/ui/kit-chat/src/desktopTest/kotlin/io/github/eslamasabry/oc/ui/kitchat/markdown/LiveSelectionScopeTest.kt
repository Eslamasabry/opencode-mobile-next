package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A reply block that stops being the tail (live turns false) keeps its state: it is not torn down and drawn again. */
@OptIn(ExperimentalTestApi::class)
class LiveSelectionScopeTest {
    @Test
    fun `the block keeps its state when live flips both ways`() = runComposeUiTest {
        var live by mutableStateOf(true)
        var created = 0
        var disposed = 0
        setContent {
            KitLiveSelectionScope(live) {
                remember { created++ }
                DisposableEffect(Unit) { onDispose { disposed++ } }
                Box {}
            }
        }
        waitForIdle()
        live = false
        waitForIdle()
        live = true
        waitForIdle()
        assertEquals(1, created, "the block's state was created again after a flip")
        assertEquals(0, disposed, "the block was torn down by a flip")
    }
}
