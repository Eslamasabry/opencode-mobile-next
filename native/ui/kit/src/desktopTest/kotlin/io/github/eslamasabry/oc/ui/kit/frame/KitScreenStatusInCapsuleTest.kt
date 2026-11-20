@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F18 (build 10113, journey J16-kill): the chat reopened with "Connecting to OpenCode…" as a second capsule of its own
 * under the header capsule. A floating page has one capsule at the top: its status line is that capsule's second row.
 */
class KitScreenStatusInCapsuleTest {
    @Test
    fun `B-KIT-FLOAT-030 on a floating page the status line is a row inside the header capsule, not a capsule of its own`() =
        runComposeUiTest {
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                    Box(Modifier.size(360.dp, 720.dp)) {
                        val frost = rememberKitFrostState()
                        KitScreen(
                            page = true,
                            overlayChrome = true,
                            floating = true,
                            frost = frost,
                            topBar = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("bar")) },
                            status = KitStatus(KitStatusKind.Connection, "Connecting to OpenCode…", id = "link"),
                            bottom = { Box(Modifier.fillMaxWidth().height(56.dp)) },
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
            waitForIdle()
            assertEquals(1, onAllNodesWithTag("kit-header-capsule", useUnmergedTree = true).fetchSemanticsNodes().size)
            val capsule = onNodeWithTag("kit-header-capsule", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bar = onNodeWithTag("bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val line = onNodeWithTag("kit-screen-status", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(line.height > 0f, "the line is shown: $line")
            assertTrue(
                line.top >= bar.bottom - 0.5f && line.bottom <= capsule.bottom + 0.5f &&
                    line.left >= capsule.left - 0.5f && line.right <= capsule.right + 0.5f,
                "the status line $line lies inside the header capsule $capsule, under its bar $bar",
            )
        }
}
