package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The undo bar takes its own touches (device QA 2026-11-11, run 5: in a chat a tap meant for the bar opened the newest
 * reply's menu, which lies exactly under it). A tap anywhere on the bar never reaches what is under it; Undo still
 * runs; a tap beside the bar still reaches the page.
 */
@OptIn(ExperimentalTestApi::class)
class KitUndoBarTouchTest {
    private class Counts {
        var under = 0
        var undo = 0
    }

    private fun ComposeUiTest.host(counts: Counts) {
        setContent {
            KitTheme(KitAppearance(), FakeKitPlatform(), "en") {
                Box(Modifier.fillMaxSize()) {
                    // What lies under the bar: a control as large as the page.
                    Box(Modifier.fillMaxSize().clickable { counts.under++ })
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        KitUndoBar(OFFER, onUndo = { counts.undo++ }, onTimeout = {})
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `a tap on the bar's words does not reach what lies under the bar`() =
        runDesktopComposeUiTest(W, H) {
            val counts = Counts()
            host(counts)
            onNodeWithText(OFFER.message).performTouchInput { click() }
            waitForIdle()
            assertEquals(0, counts.under, "the control under the bar")
            assertEquals(0, counts.undo, "Undo")
        }

    @Test
    fun `a tap on the bar's empty part does not reach what lies under the bar`() =
        runDesktopComposeUiTest(W, H) {
            val counts = Counts()
            host(counts)
            // Just inside the bar's start edge, beside its words.
            onNodeWithTag("kit-undo").performTouchInput { click(Offset(4f, height / 2f)) }
            waitForIdle()
            assertEquals(0, counts.under, "the control under the bar")
        }

    @Test
    fun `Undo still runs and nothing under it fires`() =
        runDesktopComposeUiTest(W, H) {
            val counts = Counts()
            host(counts)
            onNodeWithText("Undo").performTouchInput { click() }
            waitForIdle()
            assertEquals(1, counts.undo, "Undo")
            assertEquals(0, counts.under, "the control under the bar")
        }

    @Test
    fun `a tap beside the bar still reaches the page`() =
        runDesktopComposeUiTest(W, H) {
            val counts = Counts()
            host(counts)
            onRoot().performTouchInput { click(Offset(width / 2f, 40f)) }
            waitForIdle()
            assertEquals(1, counts.under, "the control under the bar")
        }

    private companion object {
        const val W = 412
        const val H = 860
        val OFFER = KitUndoOffer("u1", "Alpha left the dock")
    }
}
