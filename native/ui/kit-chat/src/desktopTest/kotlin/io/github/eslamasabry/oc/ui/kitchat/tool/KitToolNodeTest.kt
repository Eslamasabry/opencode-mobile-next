package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kitchat.ChatFakePlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitToolNodeTest {
    @Test
    fun `B-CHAT-565 the step mark keeps its identity from Running to Done`() =
        runComposeUiTest {
            var status by mutableStateOf(KitToolStatus.Running)
            mainClock.autoAdvance = false
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Full),
                    platform = ChatFakePlatform(),
                    localeTag = "en",
                    stillFrame = false,
                ) {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                        KitStepList {
                            KitToolRow(
                                KitToolView(
                                    id = "call-1",
                                    kind = KitToolKind.Shell,
                                    title = "Ran tests",
                                    subject = null,
                                    status = status,
                                    durationLabel = "1.2s",
                                    statusLabel = if (status == KitToolStatus.Running) "Running" else "Done",
                                ),
                                opened = false,
                                onToggle = {},
                            )
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(300)
            val before = onAllNodesWithTag("kit-step-node", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, before.size, "expected one kit-step-node before Done: $before")
            val runningId = before.single().id
            var prevTop = before.single().boundsInRoot.top
            var maxMove = 0f
            status = KitToolStatus.Done
            try {
                repeat(30) { frame ->
                    mainClock.advanceTimeByFrame()
                    val nodes = onAllNodesWithTag("kit-step-node", useUnmergedTree = true).fetchSemanticsNodes()
                    assertEquals(1, nodes.size, "frame $frame: expected one kit-step-node")
                    val node = nodes.single()
                    maxMove = maxOf(maxMove, abs(node.boundsInRoot.top - prevTop))
                    prevTop = node.boundsInRoot.top
                    assertEquals(runningId, node.id, "frame $frame: kit-step-node recreated across Running -> Done")
                }
            } finally {
                println("B-CHAT-565 maxMove=$maxMove")
            }
        }
}
