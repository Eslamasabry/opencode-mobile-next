@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-438 on the real chat screen (the live header and the live composer): the header capsule keeps the same side margin on
 * both sides, and its second line (the project and the place) stays in it.
 */
class ChatFloatingScreenTest {
    @Test
    fun `B-CHAT-EDGE-015 on the real chat the capsule keeps the same margin on both sides and holds the project line`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            setContent { E20Scene { scene.Screen(scene.live(), canGoBack = true, projectName = "IPTV_King") } }
            waitForIdle()
            val capsule = onNodeWithTag("kit-header-capsule", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(capsule.left, 412f - capsule.right, 1f, "the capsule's side margins differ: $capsule")
            assertTrue(capsule.left in 10f..18f, "the capsule's margin is ${capsule.left}")
            onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).assertExists()
            val line = onNodeWithText("IPTV_King", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(line.top >= capsule.top && line.bottom <= capsule.bottom, "the project line lies inside the capsule: $line in $capsule")
        }

    @Test
    fun `B-CHAT-EDGE-021 the capsule's second line carries the project and the place, as the live header builds it`() {
        for (place in listOf("This phone", "Home server")) {
            runDesktopComposeUiTest(412, 915) {
                val scene = ChatScene()
                setContent { E20Scene { scene.Screen(scene.live(), canGoBack = true, projectName = "IPTV_King", serverName = place) } }
                waitForIdle()
                val capsule = onNodeWithTag("kit-header-capsule", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val project = onNodeWithText("IPTV_King", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val where = onNodeWithText(place, substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(project.left >= capsule.left && where.right <= capsule.right && where.bottom <= capsule.bottom, "the project and the place ($place) lie inside the capsule")
                // One line (or two parts of it): the project starts the line and the place follows or shares it.
                assertTrue(project.left <= where.left + 1f, "the project comes before the place ($place)")
            }
        }
    }
}
