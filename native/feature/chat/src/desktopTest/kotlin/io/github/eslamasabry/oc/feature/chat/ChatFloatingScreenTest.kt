@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
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
        kitUiTest(412, 915) {
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

    /** The words inside the capsule, top to bottom and start to end, as one string. */
    private fun androidx.compose.ui.test.ComposeUiTest.capsuleWords(): String {
        val capsule = onNodeWithTag("kit-header-capsule", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.let { b -> b.left >= capsule.left && b.right <= capsule.right && b.top >= capsule.top && b.bottom <= capsule.bottom } }
            .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
            .joinToString(" ") { n -> n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text } }
    }

    @Test
    fun `B-CHAT-EDGE-021 the capsule's second line carries the project and the place, as the live header builds it`() {
        for (place in listOf("This phone", "Home server")) {
            kitUiTest(412, 915) {
                val scene = ChatScene()
                setContent { E20Scene { scene.Screen(scene.live(), canGoBack = true, projectName = "IPTV_King", serverName = place) } }
                waitForIdle()
                val words = capsuleWords()
                assertTrue("IPTV_King" in words, "the project is missing from the capsule: $words")
                assertTrue(place in words, "the place ($place) is missing from the capsule: $words")
                assertTrue(words.indexOf("IPTV_King") < words.indexOf(place), "the project comes before the place: $words")
            }
        }
    }

    @Test
    fun `B-CHAT-EDGE-022 a header with no place set shows no place, so the 021 check does tell the two apart`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            setContent { E20Scene { scene.Screen(scene.live(), canGoBack = true, projectName = "IPTV_King", serverName = null) } }
            waitForIdle()
            val words = capsuleWords()
            assertTrue("IPTV_King" in words, "the project is missing: $words")
            assertTrue("This phone" !in words && "Home server" !in words, "a place shows with none set: $words")
        }
}
