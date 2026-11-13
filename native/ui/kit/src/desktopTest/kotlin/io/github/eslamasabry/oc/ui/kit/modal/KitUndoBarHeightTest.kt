package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The undo bar says how much room it takes, so a page that scrolls under it can keep its last row clear (device QA
 * 2026-11-11, run 6: the bar covered "Continue this reply").
 */
@OptIn(ExperimentalTestApi::class)
class KitUndoBarHeightTest {
    @Test
    fun `B-285 the bar reports its height while it is on screen, leaving included, and nothing once it is gone`() =
        runDesktopComposeUiTest(412, 860) {
            var offer by mutableStateOf<KitUndoOffer?>(KitUndoOffer("u1", "Alpha left the dock"))
            var reported = -1
            setContent {
                KitTheme(KitAppearance(), FakeKitPlatform(), "en") {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        KitUndoBar(offer, onUndo = {}, onTimeout = {}, onHeight = { reported = it })
                    }
                }
            }
            waitForIdle()
            assertTrue(reported >= 48, "the bar's height while offered, px: $reported")
            val offered = reported
            // B-285: while the bar leaves (its fade and guard) the room stays, so nothing slides under it.
            mainClock.autoAdvance = false
            offer = null
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(100)
            assertEquals(offered, reported, "the leaving bar keeps its room")
            mainClock.advanceTimeBy(5_000)
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(0, reported, "no room once the bar is gone")
        }

    @Test
    fun `a bar that never had an offer reports no room`() =
        runDesktopComposeUiTest(412, 860) {
            var reported = -1
            setContent {
                KitTheme(KitAppearance(), FakeKitPlatform(), "en") {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        KitUndoBar(null, onUndo = {}, onTimeout = {}, onHeight = { reported = it })
                    }
                }
            }
            waitForIdle()
            assertEquals(0, reported)
        }
}
