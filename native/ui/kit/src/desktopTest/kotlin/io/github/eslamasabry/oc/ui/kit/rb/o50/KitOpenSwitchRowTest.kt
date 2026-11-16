package io.github.eslamasabry.oc.ui.kit.rb.o50

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.row.KitOpenSwitchRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class KitOpenSwitchRowTest {
    @Test
    fun `B-KIT-027 opening a split switch row never toggles it`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            KitTheme(platform = FakeKitPlatform()) {
                KitOpenSwitchRow(
                    "Daily report",
                    true,
                    "Edit schedule",
                    { calls += "edit" },
                    { calls += "enabled:$it" },
                    supporting = "Every day at 09:00",
                )
            }
        }
        onNodeWithText("Daily report").performClick()
        assertEquals(listOf("edit"), calls)
        onNode(isToggleable()).assertIsOn().performClick()
        assertEquals(listOf("edit", "enabled:false"), calls)
    }

    @Test
    fun `COMPACT-028 open switch row supporting wraps without ellipsis at 360 dp for 1_0 and 1_3 text`() {
        for (scale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(360 * 3, 300 * 3) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(3f, scale)) {
                        KitTheme(platform = FakeKitPlatform(), stillFrame = true) {
                            Box(Modifier.width(360.dp)) {
                                KitOpenSwitchRow(
                                    "Check for expired coupons in demo-shop",
                                    true,
                                    "Edit schedule",
                                    {},
                                    {},
                                    supporting = "Every 12 hours · Last run Today 08:59: replied",
                                    supportingMaxLines = Int.MAX_VALUE,
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                val results = mutableListOf<TextLayoutResult>()
                onNodeWithTag("kit-row-supporting", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                val layout = results.single()
                assertFalse(layout.hasVisualOverflow, "scale $scale: visual overflow")
                assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) }, "scale $scale: ellipsized")
            }
        }
    }
}
