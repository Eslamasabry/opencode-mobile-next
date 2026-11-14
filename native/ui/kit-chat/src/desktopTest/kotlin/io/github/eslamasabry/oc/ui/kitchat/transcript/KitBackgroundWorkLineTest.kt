@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owner 2026-11-14 ("where is the background activity?"): the line under the newest turn while work goes on in the
 * background is the way into that work: one tap target with its words, where the conversation can list the work.
 */
class KitBackgroundWorkLineTest {
    private val words = "Still running in the background · build.sh · 2 min"

    @Test
    fun `B-CHAT-TASKS-013 the background line is one tap target that opens the running work`() = runComposeUiTest {
        var opened = 0
        setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.size(412.dp, 200.dp)) {
                    KitBackgroundWorkLine(words, onOpen = { opened++ }, openLabel = "Open running work")
                }
            }
        }
        onNodeWithText(words, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("kit-background-work-line").assertHasClickAction().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `B-CHAT-TASKS-013 without a place to open the line stays quiet text`() = runComposeUiTest {
        setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.size(412.dp, 200.dp)) { KitBackgroundWorkLine(words) }
            }
        }
        onNodeWithText(words).assertIsDisplayed()
        onNodeWithTag("kit-background-work-line").assertDoesNotExist()
    }
}
