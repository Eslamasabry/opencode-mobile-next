@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KIT_ACTION_STACK_MAX_TERTIARY
import io.github.eslamasabry.oc.ui.kit.action.KitActionStack
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The rules of lib/ui/kit/kit_action_stack.dart: at most two tertiary actions; a disabled action states its reason. */
class KitActionStackRulesTest {
    @Test
    fun `B-KIT-ACTIONSTACK-001 at most two tertiary actions`() {
        assertEquals(2, KIT_ACTION_STACK_MAX_TERTIARY)
        assertFailsWith<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                        KitActionStack(tertiary = List(3) { KitAction("Act $it", {}) })
                    }
                }
                waitForIdle()
            }
        }
    }

    @Test
    fun `B-KIT-ACTIONSTACK-002 a disabled action shows its reason under it, an enabled one none`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitActionStack(
                    secondary = KitAction("Check now", {}),
                    tertiary = listOf(KitAction("Restart", null, enabled = KitEnabled.Disabled("Not connected right now."))),
                )
            }
        }
        onNodeWithText("Not connected right now.").assertExists()
        onAllNodesWithTag("kit-action-reason").assertCountEquals(1)
    }
}
