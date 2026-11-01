package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Counterexamples from the KITA2 review; all input values are synthetic. */
@OptIn(ExperimentalTestApi::class)
class Kita2ActionsReviewTest {
    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(motion = MotionLevel.Off),
            platform = FakeKitPlatform(),
            localeTag = "en",
            content = content,
        )
    }

    @Test
    fun `B-KIT-011 KITA2-6 a working action stays inert in More and as a button`() =
        runComposeUiTest {
            var calls = 0
            setContent {
                Theme {
                    KitActionBlock(
                        primary = KitAction("Visible work", { calls++ }, working = true),
                        tertiary =
                            listOf(
                                KitAction("First", {}),
                                KitAction("Second", {}),
                                KitAction("Overflow work", { calls++ }, working = true),
                            ),
                    )
                }
            }
            onNodeWithText("Visible work").assertIsNotEnabled().performClick()
            onNodeWithTag("kit-actions-more").performClick()
            onNodeWithText("Overflow work").assertIsNotEnabled().performClick()
            assertEquals(0, calls)
        }

    @Test
    fun `B-KIT-027 KITA2-9 disabling an open risk fold prevents confirmation`() = runComposeUiTest {
        var enabled by mutableStateOf<KitEnabled>(KitEnabled.Enabled)
        val calls = mutableListOf<Boolean>()
        setContent {
            Theme {
                KitSwitchRow(
                    "Risky switch",
                    checked = false,
                    risk = "Risk",
                    enabled = enabled,
                    onCheckedChange = { calls += it },
                )
            }
        }
        onNodeWithText("Risky switch").performClick()
        onNodeWithText("Turn on").assertExists()
        runOnIdle { enabled = KitEnabled.Disabled("Unavailable now") }
        onNodeWithText("Turn on").assertIsNotEnabled().performClick()
        assertTrue(calls.isEmpty())
    }
}
