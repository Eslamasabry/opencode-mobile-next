@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.linux

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.manager.LinuxCleanupId
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPackage
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPort
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageCategory
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageReport
import io.github.eslamasabry.oc.feature.linux.manager.PortOwner
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPackagesActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPackagesUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPage
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxStorageActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxStorageUi
import io.github.eslamasabry.oc.feature.linux.ui.LinuxHomeScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxPackagesScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxPortsScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxStorageScreen
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Linux pages say the Flutter words for their states and route taps to their intents. */
class LinuxScreensTest {
    private object TestPlatform : KitPlatform {
        override val name: String = "test"
        override val family: KitPlatformFamily = KitPlatformFamily.Desktop
        override val signals: StateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())

        override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

        override fun haptic(kind: KitHaptic) = Unit

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) = KitTheme(platform = TestPlatform, localeTag = "en", stillFrame = true, content = content)

    private class Calls : LinuxHomeActions, LinuxPortsActions, LinuxStorageActions, LinuxPackagesActions {
        val seen = mutableListOf<String>()

        override fun shown(visible: Boolean) = Unit
        override fun open(page: LinuxPage) { seen += "open $page" }
        override fun askRepair() { seen += "askRepair" }
        override fun askReset() { seen += "askReset" }
        override fun confirm() { seen += "confirm" }
        override fun dismiss() { seen += "dismiss" }
        override fun back() { seen += "back" }
        override fun askStopJob() { seen += "askStopJob" }
        override fun dismissJob() { seen += "dismissJob" }
        override fun retry() { seen += "retry" }
        override fun preview(port: Int) { seen += "preview $port" }
        override fun askCleanup(id: LinuxCleanupId) { seen += "askCleanup $id" }
        override fun search(text: String) = Unit
        override fun checkInstall(typed: String) { seen += "check $typed" }
        override fun askRemove(name: String) { seen += "askRemove $name" }
        override fun askUpdate() { seen += "askUpdate" }
        override fun askRemove(projects: Boolean) {
            seen += "askRemove $projects"
        }
        override fun startSetup() {
            seen += "startSetup"
        }
        override fun addPython() {
            seen += "addPython"
        }
        override fun continueSetup() {
            seen += "continueSetup"
        }
        override fun askStopSetup() {
            seen += "askStopSetup"
        }
        override fun setIdleEnabled(enabled: Boolean) {
            seen += "setIdleEnabled $enabled"
        }
        override fun askIdleMinutes() {
            seen += "askIdleMinutes"
        }
        override fun finishIdleMinutes(value: String?) {
            seen += "finishIdleMinutes $value"
        }
        override fun resumeIdle() {
            seen += "resumeIdle"
        }
        override fun refreshLifetimes() {
            seen += "refreshLifetimes"
        }
        override fun askCreateProject() {
            seen += "askCreateProject"
        }
        override fun finishCreateProject(path: String?) {
            seen += "finishCreateProject $path"
        }
        override fun browse(path: String) {
            seen += "browse $path"
        }
        override fun closeFolders() {
            seen += "closeFolders"
        }
        override fun measureRemoval() {
            seen += "measureRemoval"
        }
        override fun readProgramLog(pid: Int) {
            seen += "readProgramLog $pid"
        }
        override fun askStopProgram(pid: Int) {
            seen += "askStopProgram $pid"
        }
        override fun confirmStopProgram() {
            seen += "confirmStopProgram"
        }
    }

    @Test
    fun `B-LINUX-092 the manager lists its pages and opens them`() =
        runComposeUiTest {
            val calls = Calls()
            setContent { Themed { LinuxHomeScreen(LinuxHomeUi(presence = LinuxPresence.Ready), calls) } }
            onNodeWithText("Linux on this phone").assertExists()
            onNodeWithText("Ports").performClick()
            onNodeWithText("Storage").performClick()
            onNodeWithText("Packages").performClick()
            onNodeWithText("Running").performClick()
            onNodeWithText("Repair Linux").performClick()
            onNodeWithText("Reset Linux").performClick()
            assertEquals(
                listOf("open Ports", "open Storage", "open Packages", "open Running", "askRepair", "askReset"),
                calls.seen,
            )
        }

    @Test
    fun `B-LINUX-092 Linux that is not here is said plainly and no page is offered`() =
        runComposeUiTest {
            setContent { Themed { LinuxHomeScreen(LinuxHomeUi(presence = LinuxPresence.NotOnThisBuild), Calls()) } }
            onNodeWithText("Could not read Linux").assertExists()
            onNodeWithText("Linux is not set up or is not answering. Open This phone to set it up.").assertExists()
            onNodeWithText("Ports").assertDoesNotExist()
            onNodeWithText("Reset Linux").assertDoesNotExist()
        }

    @Test
    fun `B-LINUX-085 a dev server's port opens its preview and the server's port is not a page`() =
        runComposeUiTest {
            val calls = Calls()
            val ui =
                LinuxPortsUi(
                    loading = false,
                    ports =
                        persistentListOf(
                            LinuxPort(4097),
                            LinuxPort(5173, PortOwner(51, "node /app/node_modules/.bin/vite --port 5173"), allInterfaces = false),
                        ),
                )
            setContent { Themed { LinuxPortsScreen(ui, calls) } }
            onNodeWithText("Port 4097").assertExists()
            onNodeWithText("Port 5173").assertExists()
            onNodeWithText("Open preview").performClick()
            assertEquals(listOf("preview 5173"), calls.seen)
        }

    @Test
    fun `B-LINUX-089 storage names each category and asks before a cleanup`() =
        runComposeUiTest {
            val calls = Calls()
            val ui =
                LinuxStorageUi(
                    loading = false,
                    report = LinuxStorageReport(LinuxStorageCategory.entries.associateWith { 1024L * 1024 }),
                    cleanable = persistentMapOf(LinuxCleanupId.AptCache to 2048L * 1024),
                )
            setContent { Themed { LinuxStorageScreen(ui, calls) } }
            onNodeWithText("Linux itself").assertExists()
            onNodeWithText("Your work. Never cleared from here.").assertExists()
            onNodeWithText("Clear package downloads").performClick()
            assertEquals(listOf("askCleanup AptCache"), calls.seen)
        }

    @Test
    fun `B-LINUX-090 a package Linux needs offers no Remove and another does`() =
        runComposeUiTest {
            val calls = Calls()
            val ui =
                LinuxPackagesUi(
                    loading = false,
                    packages =
                        persistentListOf(
                            LinuxPackage("bash", "5.2", 1400, essential = true, required = true),
                            LinuxPackage("htop", "3.3", 300),
                        ),
                )
            setContent { Themed { LinuxPackagesScreen(ui, calls) } }
            onNodeWithText("Remove bash").assertDoesNotExist()
            onNodeWithText("Remove htop").performClick()
            onNodeWithText("Update system").performClick()
            assertEquals(listOf("askRemove htop", "askUpdate"), calls.seen)
        }
}
