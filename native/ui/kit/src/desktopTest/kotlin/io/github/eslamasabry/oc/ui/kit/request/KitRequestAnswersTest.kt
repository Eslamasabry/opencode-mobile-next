package io.github.eslamasabry.oc.ui.kit.request

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.lamp.K2
import io.github.eslamasabry.oc.ui.kit.lamp.k2Host
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Answering in place (LAMP-PLAN §5, LAMP-CRAFT §4): two half-width 52 dp answers named by their target, Send, the held receipt. */
@OptIn(ExperimentalTestApi::class)
class KitRequestAnswersTest {
    private class Scene {
        var held by mutableStateOf<KitHeld?>(null)
        val events = mutableListOf<String>()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.host(scene: Scene, kind: KitRequestKind = KitRequestKind.Permission, platform: FakeKitPlatform = FakeKitPlatform()) =
        k2Host(platform = platform) {
            KitRequestAnswers(
                KitRequest("r", "Asks to edit: wait7.txt", kind),
                "create a file notes.txt",
                K2.request,
                onAllow = { scene.events += "allow" },
                onDeny = { scene.events += "deny" },
                onAnswer = { scene.events += "answer" },
                modifier = Modifier.width(336.dp),
                held = scene.held,
                onUndo = { scene.events += "undo" },
            )
        }

    @Test
    fun `B-LAMP-K2-040 Allow once and Deny are two half-width 52 dp answers named by their target, with Send`() =
        runDesktopComposeUiTest(412, 400) {
            val platform = FakeKitPlatform()
            val scene = Scene()
            host(scene, platform = platform)
            waitForIdle()
            val allow = onNodeWithContentDescription("Allow once: create a file notes.txt", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val deny = onNodeWithContentDescription("Deny: create a file notes.txt", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue((allow.bottom - allow.top).value >= 52f - 0.5f, "Allow once is at least 52 dp tall")
            assertTrue(abs((allow.right - allow.left).value - (deny.right - deny.left).value) < 1f, "the two answers are the same width")
            assertTrue((allow.right - allow.left).value > 140f, "each takes half the row")
            onNodeWithText("Asks to edit: wait7.txt").assertExists()
            onNodeWithContentDescription("Allow once: create a file notes.txt").performClick()
            assertEquals(listOf("allow"), scene.events)
            assertEquals(listOf(KitHaptic.Send), platform.haptics)
        }

    @Test
    fun `B-LAMP-K2-041 a held answer is the draining receipt with Undo, and a question is one Answer`() =
        runDesktopComposeUiTest(412, 400) {
            mainClock.autoAdvance = false
            val scene = Scene()
            host(scene)
            mainClock.advanceTimeBy(100)
            scene.held = KitHeld("r", "Allowed")
            mainClock.advanceTimeBy(400)
            onNode(hasContentDescription("Allowed", substring = true), useUnmergedTree = true).assertExists()
            onNodeWithText("Allow once").assertDoesNotExist()
            onNodeWithText("Undo").performClick()
            assertEquals(listOf("undo"), scene.events)
        }

    @Test
    fun `B-LAMP-K2-042 a question is answered with one full-width Answer`() =
        runDesktopComposeUiTest(412, 400) {
            val scene = Scene()
            host(scene, kind = KitRequestKind.Question)
            waitForIdle()
            val b = onNodeWithContentDescription("Answer: create a file notes.txt", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue((b.right - b.left).value > 300f, "Answer… takes the whole row")
            onNodeWithText("Answer…").performClick()
            assertEquals(listOf("answer"), scene.events)
        }
}
