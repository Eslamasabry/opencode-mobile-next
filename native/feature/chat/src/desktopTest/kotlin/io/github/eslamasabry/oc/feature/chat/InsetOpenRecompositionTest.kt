package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.feature.chat.ui.rememberBottomInsetOpen
import kotlin.test.Test
import kotlin.test.assertEquals

/** The keyboard inset animates every frame; a reader of "is it open" must recompose only when that flips. */
@OptIn(ExperimentalTestApi::class)
class InsetOpenRecompositionTest {
    private class MovingInsets : WindowInsets {
        var bottom by mutableIntStateOf(0)
        override fun getBottom(density: Density) = bottom
        override fun getTop(density: Density) = 0
        override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
    }

    @Test
    fun theReaderRecomposesWhenTheKeyboardOpensAndCloses_notOnEveryFrameBetween() = runDesktopComposeUiTest {
        val insets = MovingInsets()
        var runs = 0
        var open = false
        setContent {
            val keyboardOpen by rememberBottomInsetOpen(insets)
            open = keyboardOpen
            SideEffect { runs++ }
        }
        waitForIdle()
        val first = runs
        for (px in listOf(10, 40, 90, 200, 300, 300, 250, 120, 60, 10)) {
            insets.bottom = px
            waitForIdle()
        }
        assertEquals(true, open)
        assertEquals(1, runs - first, "opened once, no recomposition for the frames while it moved")
        insets.bottom = 0
        waitForIdle()
        assertEquals(false, open)
        assertEquals(2, runs - first)
    }
}
