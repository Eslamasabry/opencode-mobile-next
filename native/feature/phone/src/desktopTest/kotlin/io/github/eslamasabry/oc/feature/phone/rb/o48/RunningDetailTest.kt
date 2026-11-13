@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phone.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.procs.CpuLevel
import io.github.eslamasabry.oc.engine.runtime.procs.CpuWords
import io.github.eslamasabry.oc.feature.phone.presentation.*
import io.github.eslamasabry.oc.feature.phone.ui.RunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.PhoneSizes
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.*
import kotlinx.collections.immutable.persistentListOf

class RunningDetailTest {
    private class Actions : RunningActions {
        var stopped: Int? = null

        override fun shown(visible: Boolean) {}

        override fun retry() {}

        override fun details(pid: Int) {}

        override fun askStop(pid: Int) {
            stopped = pid
        }

        override fun confirmStop() {}

        override fun dismiss() {}

        override fun openChildLimitSettings() {}

        override fun hideChildLimit() {}

        override fun back() {}
    }

    @Test
    fun `individual process reports the precise CPU reading once and caps the word at Busy`() =
        runComposeUiTest {
            val row =
                ProcessRowUi(
                    42,
                    "node",
                    ProcessKind.DevService,
                    92,
                    false,
                    null,
                    cpuWords = CpuWords(CpuLevel.VeryBusy, "91.8"),
                    memoryBytes = 43 * 1048576L,
                    elapsedSeconds = 720,
                )
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    RunningScreen(
                        RunningUi(loading = false, rows = persistentListOf(row)),
                        Actions(),
                    )
                }
            }
            onNodeWithText("Busy · 91.8% · ${PhoneSizes.memory(43 * 1048576L)} · running 12 min").assertExists()
            onNodeWithText("92 %", substring = true).assertDoesNotExist()
        }

    @Test
    fun `standalone Details shows measured facts and copy while a protected program has no Stop`() =
        runComposeUiTest {
            val row =
                ProcessRowUi(
                    42,
                    "OpenCode",
                    ProcessKind.OpenCode,
                    3,
                    false,
                    ProcessRole.ServerRoot,
                    command = Redacted.shape(null, 418),
                    cpuWords = CpuWords(CpuLevel.Idle, "3.0"),
                    memoryBytes = 43 * 1048576L,
                    elapsedSeconds = 720,
                )
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    RunningScreen(RunningUi(loading = false, selected = row), Actions())
                }
            }
            onNodeWithText("Idle · 3.0% · ${PhoneSizes.memory(43 * 1048576L)} · running 12 min").assertExists()
            onNodeWithText("Copy command").assertExists()
            onNodeWithText("Stop OpenCode").assertDoesNotExist()
        }

    @Test
    fun `one stop in flight disables another row's prominent stop`() = runComposeUiTest {
        val actions = Actions()
        val row = ProcessRowUi(42, "node", ProcessKind.DevService, 92, false, null, heavy = true)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                RunningScreen(
                    RunningUi(loading = false, rows = persistentListOf(row), stopping = 99),
                    actions,
                )
            }
        }
        onNodeWithContentDescription("Stop node").assertIsNotEnabled()
        assertNull(actions.stopped)
    }

    @Test
    fun `S7 one summary line over the list, not This phone's row line from another count`() =
        runComposeUiTest {
            val rows =
                persistentListOf(
                    ProcessRowUi(41, "Linux", ProcessKind.OpenCode, 0, false, null, memoryBytes = 4 * 1048576L),
                    ProcessRowUi(42, "OpenCode", ProcessKind.OpenCode, 4, false, null, memoryBytes = 268 * 1048576L),
                    ProcessRowUi(43, "This app", ProcessKind.Other, 46, false, null, memoryBytes = 307 * 1048576L),
                )
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    RunningScreen(
                        RunningUi(
                            loading = false,
                            rows = rows,
                            // This phone's row counts kernel processes (OpenCode runs two): "4 running".
                            status =
                                io.github.eslamasabry.oc.engine.runtime.procs.RunningRowStatus.Normal(
                                    4,
                                    582 * 1048576L,
                                ),
                        ),
                        Actions(),
                    )
                }
            }
            onNodeWithTag("running-summary").assertTextContains("3 running", substring = true)
            onNodeWithText("4 running", substring = true).assertDoesNotExist()
        }

    @Test
    fun `memory is whole MB below a GB and one decimal in GB, never rounded to tens`() {
        val mb = 1048576L
        assertEquals("\u2066997\u00a0MB\u2069", PhoneSizes.memory(997 * mb + 400_000))
        assertEquals("\u20664\u00a0MB\u2069", PhoneSizes.memory(4 * mb))
        assertEquals("\u20661.0\u00a0GB\u2069", PhoneSizes.memory(1000 * mb))
        assertEquals("\u20661.5\u00a0GB\u2069", PhoneSizes.memory(1610612736L))
    }
}
