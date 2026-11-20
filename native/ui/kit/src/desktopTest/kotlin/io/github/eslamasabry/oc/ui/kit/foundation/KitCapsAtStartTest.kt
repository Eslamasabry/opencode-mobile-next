package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-KIT-302 (big screens, decision (a)): inside a pane beside a list, a capped reading column sits at the pane's start
 * and the spare room goes to the end; centred parts stay centred; outside such a pane nothing moves.
 */
@OptIn(ExperimentalTestApi::class)
class KitCapsAtStartTest {
    private fun bounds(atStart: Boolean, rtl: Boolean = false): Map<String, Pair<Float, Float>> {
        var out: Map<String, Pair<Float, Float>> = emptyMap()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalKitCapsAtStart provides atStart,
                ) {
                    // Each part fills its slot, as a page's column does, so the cap decides where it sits.
                    Column(Modifier.width(1000.dp)) {
                        Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Conversation).height(10.dp).testTag("conversation"))
                        Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Reading).height(10.dp).testTag("reading"))
                        Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.State).height(10.dp).testTag("state"))
                        Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Dialog).height(10.dp).testTag("dialog"))
                    }
                }
            }
            out =
                listOf("conversation", "reading", "state", "dialog").associateWith {
                    val b = onNodeWithTag(it).getBoundsInRoot()
                    b.left.value to b.right.value
                }
        }
        return out
    }

    @Test
    fun `B-KIT-302 capped prose sits at the pane's start beside a list, mirrored in RTL, centred parts stay`() {
        val beside = bounds(atStart = true)
        assertEquals(0f to 700f, beside["conversation"], "the conversation column starts at the pane's start")
        assertEquals(0f to 720f, beside["reading"])
        assertEquals(280f to 720f, beside["state"], "a state stays centred")
        assertEquals(260f to 740f, beside["dialog"], "a dialog stays centred")
        val rtl = bounds(atStart = true, rtl = true)
        assertEquals(300f to 1000f, rtl["conversation"], "right to left: the start is the right side")
        val phone = bounds(atStart = false)
        assertEquals(150f to 850f, phone["conversation"], "one page (a phone, a window without panes): centred as before")
        assertEquals(140f to 860f, phone["reading"])
    }
}
