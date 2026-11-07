package io.github.eslamasabry.oc.ui.kit.rb.o25

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostHeader
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostHeaderHost
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostTopClaim
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of lib/ui/kit/kit_frost_header.dart at 8302de537 (standard §6 step 8). */
@OptIn(ExperimentalTestApi::class)
class KitFrostHeaderParityTest {
    @Test fun `a body straight under the bar runs up by the distance between the tops`() {
        // Bar 0..56; body starts at 56: runs up 56.
        assertEquals(56f, KitFrostHeader.extendUp(barTop = 0f, barHeight = 56f, bodyTop = 56f, loadingBarPx = 2f))
        // The idle loading bar (2) may sit between them, with half a pixel of slack (kit_frost_header.dart:276).
        assertEquals(58.5f, KitFrostHeader.extendUp(0f, 56f, 58.5f, 2f))
        assertEquals(55.6f, KitFrostHeader.extendUp(0f, 56f, 55.6f, 2f))
        // Anything else between them (a status line) keeps the body below.
        assertEquals(0f, KitFrostHeader.extendUp(0f, 56f, 58.6f, 2f))
        assertEquals(0f, KitFrostHeader.extendUp(0f, 56f, 55.5f, 2f))
        assertEquals(2f, KitFrostHeader.loadingBarHeight.value)
    }

    @Test fun `the glass shows only while the body runs up and content is under it, and release clears both`() {
        val host = KitFrostHeaderHost()
        assertFalse(host.frosted)
        host.extendUp = 40f
        assertFalse(host.frosted)
        host.under = true
        assertTrue(host.frosted)
        host.release()
        assertEquals(0f, host.extendUp)
        assertFalse(host.under)
    }

    @Test fun `the frame lays the bar on top at its own height and the content below it, painted under the bar`() =
        runComposeUiTest {
            setContent {
                val host = KitFrostHeaderHost()
                KitFrostHeader.Frame(
                    host,
                    rememberKitFrostState(),
                    bar = { Box(Modifier.height(56.dp).testTag("bar")) },
                    modifier = Modifier.size(300.dp, 600.dp),
                ) {
                    Box(Modifier.testTag("content"))
                }
            }
            onNodeWithTag("bar").assertTopPositionInRootIsEqualTo(0.dp)
            onNodeWithTag("content").assertTopPositionInRootIsEqualTo(56.dp)
        }

    @Test fun `reading the clearance is the body's opt-in, and outside a clearance it is zero`() = runComposeUiTest {
        val claim = KitFrostTopClaim()
        var inside = (-1).dp
        var outside = (-1).dp
        setContent {
            outside = KitFrostHeader.topClearance()
            KitFrostHeader.Clearance(72.dp, claim) { inside = KitFrostHeader.topClearance() }
        }
        waitForIdle()
        assertEquals(0.dp, outside)
        assertEquals(72.dp, inside)
        assertTrue(KitFrostHeader.honoured(claim))
    }
}
