@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o40

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitChecklistStep
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import kotlin.test.Test

class ChecklistStepTest {
    @Test
    fun `step semantics distinguish checking done failed and pending without inventing actions`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Column {
                        KitChecklistStep("Linux", KitStepState.Done, 1, 4, supporting = "24.04")
                        KitChecklistStep(
                            "Node.js",
                            KitStepState.Working,
                            2,
                            4,
                            supporting = "18 of 30 MB",
                        )
                        KitChecklistStep(
                            "Python",
                            KitStepState.Failed,
                            3,
                            4,
                            supporting = "Reconnect and continue",
                        )
                        KitChecklistStep("OpenCode", KitStepState.Waiting, 4, 4)
                    }
                }
            }
            onNodeWithContentDescription("Step 1 of 4, Linux, Done, 24.04").assertExists()
            onNodeWithContentDescription("Step 2 of 4, Node.js, Working, 18 of 30 MB")
                .assertExists()
            onNodeWithContentDescription("Step 3 of 4, Python, Failed, Reconnect and continue")
                .assertExists()
            onNodeWithContentDescription("Step 4 of 4, OpenCode, Waiting").assertExists()
            onAllNodes(hasClickAction()).assertCountEquals(0)
        }
}
