package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-LAMP-KC1-002: the message block tells the page where its top is, so the edge light can stand just above it. */
@OptIn(ExperimentalTestApi::class)
class KitMessageBlockTest {
    @Test
    fun `B-LAMP-KC1-002 the block reports its top, follows it when it moves, and clears when it leaves`() = runComposeUiTest {
        val block = KitMessageBlock()
        var above by mutableStateOf(300.dp)
        var shown by mutableStateOf(true)
        var density = 1f
        setContent {
            ChatTestTheme {
                density = LocalDensity.current.density
                KitProvideMessageBlock(block) {
                    Box(Modifier.size(412.dp, 800.dp)) {
                        Column {
                            Box(Modifier.height(above))
                            if (shown) Column(Modifier.kitReportMessageBlock()) { Box(Modifier.height(100.dp)) }
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertTrue(block.present)
        assertEquals(300f * density, block.topPx, 1f)
        assertEquals(300f, block.topDp(density)!!.value, 1f)
        assertFalse(block.keyboardVisible, "no keyboard in the test window: the kit's one value says so")
        // 800 dp root: the block stands 500 dp above the bottom.
        assertEquals(500f, block.heightAboveBottomDp(800f * density, density)!!.value, 1f)

        above = 340.dp // a phrase row arrives above the box: the block's top moves with it
        waitForIdle()
        assertEquals(340f * density, block.topPx, 1f)

        shown = false // the page without a message box (Files, Git, a form) reads none
        waitForIdle()
        assertFalse(block.present)
        assertNull(block.topDp(density))
        assertNull(block.heightAboveBottomDp(800f, density))
    }

    @Test
    fun `B-LAMP-KC1-002 without a holder the report is a no-op`() = runComposeUiTest {
        setContent { ChatTestTheme { Box(Modifier.fillMaxSize().kitReportMessageBlock()) } }
        waitForIdle()
    }
}
