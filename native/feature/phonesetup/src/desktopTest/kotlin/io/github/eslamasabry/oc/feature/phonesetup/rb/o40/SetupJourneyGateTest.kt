@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.phonesetup.rb.o40

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.runtime.setup.*
import io.github.eslamasabry.oc.feature.phonesetup.presentation.*
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import io.github.eslamasabry.oc.feature.phonesetup.ui.JourneyProgressMemory
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitLockedRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetupJourneyGateTest {
    private class Actions : SetupJourneyActions {
        var started = 0
        override fun back() {}
        override fun retry() {}
        override fun toggle(id: String, checked: Boolean) {}
        override fun accept() {}
        override fun start() { started++ }
        override fun customize() {}
        override fun continueSetup() {}
        override fun askStop() {}
        override fun cancelConfirm() {}
        override fun confirmStop() {}
        override fun askRemoveVoice() {}
        override fun confirmRemoveVoice() {}
        override fun openStorage() {}
        override fun done() {}
    }
    // test/support/fake_setup_engine.dart: each total and estimate is the Flutter scene's registry.
    private val registry = listOf(
        SetupComponent("linux", "Linux base", "Linux base", "true", "", required = true, estimatedSeconds = 20, downloadBytes = 30000000),
        SetupComponent("essentials", "Git, SSH and certificates", "Git and SSH", "true", "true", dependsOn = listOf("linux"), required = true, estimatedSeconds = 40, downloadBytes = 15000000),
        SetupComponent("python", "Python", "Python", "true", "true", dependsOn = listOf("essentials"), defaultOn = true, estimatedSeconds = 50, downloadBytes = 30000000),
        SetupComponent("node", "Node.js", "Node.js", "true", "true", dependsOn = listOf("essentials"), required = true, estimatedSeconds = 40, downloadBytes = 30000000),
        SetupComponent("opencode", "OpenCode", "OpenCode", "true", "true", dependsOn = listOf("node"), required = true, estimatedSeconds = 90, downloadBytes = 60000000),
    )
    private val state = SetupJourneyUi(checking = false, chosen = setOf("python"), options = SetupOptions(registry, emptySet(), SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10000000000)))
    @Test fun `measured stage keeps its furthest share but a new stage starts a new arc`() {
        val memory = JourneyProgressMemory()
        val row = ComponentProgress("node", ComponentState.Running, stage = "Downloading", percent = 60.0)
        assertEquals(.6f, memory.measured(row))
        assertEquals(.6f, memory.measured(row.copy(percent = 30.0)))
        assertEquals(.1f, memory.measured(row.copy(stage = "Installing", percent = 10.0)))
        assertNull(memory.measured(row.copy(state = ComponentState.Checking)))
        assertNull(JourneyProgressMemory().measured(row.copy(percent = null)))
    }
    @Test fun `remaining time accounts for pending steps and never rises within a job`() {
        val memory = JourneyProgressMemory()
        val progress = SetupProgress(SetupState.Running, listOf(ComponentProgress("opencode", ComponentState.Pending)), 0.0, etaSeconds = 15)
        assertEquals(90, memory.eta(progress, registry))
        assertEquals(90, memory.eta(progress.copy(etaSeconds = 200), registry))
        assertEquals(30, memory.eta(progress.copy(components = emptyList(), etaSeconds = 30), registry))
        assertNull(memory.eta(progress.copy(state = SetupState.Cancelled), registry))
    }
    @Test fun running() {
        val progress = SetupProgress(SetupState.Running, listOf(
            ComponentProgress("linux", ComponentState.Done), ComponentProgress("essentials", ComponentState.Done),
            ComponentProgress("python", ComponentState.Done),
            ComponentProgress("node", ComponentState.Running, stage = "Downloading Node.js 24", bytesDone = 18000000, bytesTotal = 30000000),
            ComponentProgress("opencode", ComponentState.Pending),
        ), .5, current = "node", etaSeconds = 130, jobId = "running")
        FlutterGoldenGate.check("setup_progress_running", frame = FlutterGoldenGate.Frame.Screen) {
            SetupJourneyScreen(state.copy(progress = progress), Actions())
        }
    }
    @Test fun fresh() {
        FlutterGoldenGate.check("setup_start", frame = FlutterGoldenGate.Frame.Screen) { SetupJourneyScreen(state, Actions()) }
    }
    @Test fun customize() {
        FlutterGoldenGate.check("setup_customize", frame = FlutterGoldenGate.Frame.Screen) { SetupJourneyScreen(state.copy(customizing = true), Actions()) }
    }
    @Test fun `an unsupported phone explains why and cannot dispatch setup`() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            SetupJourneyScreen(state.copy(options = state.options!!.copy(device = SetupDeviceInfo(listOf("armeabi-v7a"), 4096, 10000000000))), actions)
        } }
        onNodeWithText("This phone can't run it").assertExists()
        onNodeWithText("Set up OpenCode on this phone").assertIsNotEnabled()
        assertEquals(0, actions.started)
    }
    @Test fun `a required component exposes its lock reason and no toggle action`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) { KitLockedRow("Linux base", "Required") } }
        onNodeWithText("Required").assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
