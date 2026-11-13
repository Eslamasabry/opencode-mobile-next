@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phone.rb.o48

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.runtime.procs.RunningRowStatus
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerLine
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessKind
import io.github.eslamasabry.oc.feature.phone.presentation.RunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRowUi
import io.github.eslamasabry.oc.feature.phone.presentation.RunningUi
import io.github.eslamasabry.oc.feature.phone.ui.RunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.words
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** One reading of 997.4 MB reads "997 MB" in This phone's headline and row and on the Running page. */
class OneMemoryFigureTest {
    private val bytes = 1048576L * 997 + 400_000

    @Test
    fun `the headline, the row line and the Running summary write one reading the same way`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Column {
                        KitText(AnswerLine.Memory(bytes).words(), Modifier.testTag("headline"))
                        KitText(RunningRowStatus.Normal(8, bytes).words(), Modifier.testTag("row"))
                    }
                }
            }
            onNodeWithTag("headline").assertTextContains("997 MB", substring = true)
            onNodeWithTag("row").assertTextContains("8 running", substring = true)
            onNodeWithTag("row").assertTextContains("997 MB", substring = true)
        }

    @Test
    fun `the Running summary and its rows use the same unit rule`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                RunningScreen(
                    RunningUi(
                        loading = false,
                        rows =
                            persistentListOf(
                                ProcessRowUi(1, "Linux", ProcessKind.OpenCode, 0, false, null, memoryBytes = 4 * 1048576L),
                            ),
                        runningCount = 8,
                        runningMemoryBytes = bytes,
                    ),
                    NoActions(),
                )
            }
        }
        onNodeWithTag("running-summary").assertTextContains("8 running", substring = true)
        onNodeWithTag("running-summary").assertTextContains("997 MB", substring = true)
    }

    private class NoActions : RunningActions {
        override fun shown(visible: Boolean) {}

        override fun retry() {}

        override fun details(pid: Int) {}

        override fun askStop(pid: Int) {}

        override fun confirmStop() {}

        override fun dismiss() {}

        override fun openChildLimitSettings() {}

        override fun hideChildLimit() {}

        override fun back() {}
    }
}
