@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phonesetup.rb.o40

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.runtime.setup.*
import io.github.eslamasabry.oc.feature.phonesetup.presentation.*
import io.github.eslamasabry.oc.feature.phonesetup.ui.JourneyProgressMemory
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import io.github.eslamasabry.oc.feature.phonesetup.ui.setupPromiseMinutes
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitLockedRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupJourneyGateTest {
    private class Actions : SetupJourneyActions {
        var started = 0
        val toggles = mutableListOf<Pair<String, Boolean>>()
        var accepted = 0
        var resumed = 0
        val stopCalls = mutableListOf<String>()

        override fun back() {}

        override fun retry() {}

        override fun toggle(id: String, checked: Boolean) {
            toggles += id to checked
        }

        override fun accept() {
            accepted++
        }

        override fun start() {
            started++
        }

        override fun customize() {}

        override fun continueSetup() {
            resumed++
        }

        override fun askStop() {
            stopCalls += "ask"
        }

        override fun cancelConfirm() {}

        override fun confirmStop() {
            stopCalls += "confirm"
        }

        override fun askRemoveVoice() {}

        override fun confirmRemoveVoice() {}

        override fun openStorage() {}

        override fun done() {}
    }

    // test/support/fake_setup_engine.dart: each total and estimate is the Flutter scene's registry.
    private val registry =
        listOf(
            SetupComponent(
                "linux",
                "Linux base",
                "Linux base",
                "true",
                "",
                required = true,
                estimatedSeconds = 20,
                downloadBytes = 30000000,
            ),
            SetupComponent(
                "essentials",
                "Git, SSH and certificates",
                "Git and SSH",
                "true",
                "true",
                dependsOn = listOf("linux"),
                required = true,
                estimatedSeconds = 40,
                downloadBytes = 15000000,
            ),
            SetupComponent(
                "python",
                "Python",
                "Python",
                "true",
                "true",
                dependsOn = listOf("essentials"),
                defaultOn = true,
                estimatedSeconds = 50,
                downloadBytes = 30000000,
            ),
            SetupComponent(
                "node",
                "Node.js",
                "Node.js",
                "true",
                "true",
                dependsOn = listOf("essentials"),
                required = true,
                estimatedSeconds = 40,
                downloadBytes = 30000000,
            ),
            SetupComponent(
                "opencode",
                "OpenCode",
                "OpenCode",
                "true",
                "true",
                dependsOn = listOf("node"),
                required = true,
                estimatedSeconds = 90,
                downloadBytes = 60000000,
            ),
        )
    private val state =
        SetupJourneyUi(
            checking = false,
            chosen = setOf("python"),
            options =
                SetupOptions(
                    registry,
                    emptySet(),
                    SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10000000000),
                ),
        )

    @Test
    fun `measured stage keeps its furthest share but a new stage starts a new arc`() {
        val memory = JourneyProgressMemory()
        val row =
            ComponentProgress("node", ComponentState.Running, stage = "Downloading", percent = 60.0)
        assertEquals(.6f, memory.measured(row))
        assertEquals(.6f, memory.measured(row.copy(percent = 30.0)))
        assertEquals(.1f, memory.measured(row.copy(stage = "Installing", percent = 10.0)))
        assertNull(memory.measured(row.copy(state = ComponentState.Checking)))
        assertNull(JourneyProgressMemory().measured(row.copy(percent = null)))
    }

    @Test
    fun `remaining time accounts for pending steps and never rises within a job`() {
        val memory = JourneyProgressMemory()
        val progress =
            SetupProgress(
                SetupState.Running,
                listOf(ComponentProgress("opencode", ComponentState.Pending)),
                0.0,
                etaSeconds = 15,
            )
        assertEquals(90, memory.eta(progress, registry))
        assertEquals(90, memory.eta(progress.copy(etaSeconds = 200), registry))
        assertEquals(
            30,
            memory.eta(progress.copy(components = emptyList(), etaSeconds = 30), registry),
        )
        assertNull(memory.eta(progress.copy(state = SetupState.Cancelled), registry))
    }

    @Test
    fun `setup promises round to the nearest minute and never promise zero`() {
        assertEquals(1, setupPromiseMinutes(0))
        assertEquals(1, setupPromiseMinutes(89))
        assertEquals(2, setupPromiseMinutes(90))
        assertEquals(4, setupPromiseMinutes(240))
    }

    @Test
    fun `restored add job names its known tools in job order and resumes the job`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SetupJourneyScreen(
                        state.copy(
                            progress =
                                SetupProgress(
                                    SetupState.Cancelled,
                                    listOf(ComponentProgress("python", ComponentState.Pending)),
                                    0.0,
                                    adding = listOf("node", "removed-tool", "python"),
                                )
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Adding Node.js and Python").assertExists()
            onNodeWithText("Continue setup").performClick()
            assertEquals(1, actions.resumed)
            assertEquals(0, actions.started)
        }

    @Test
    fun `pause dispatches the named stop flow without starting a new job`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(
                    state.copy(
                        progress =
                            SetupProgress(
                                SetupState.Running,
                                listOf(ComponentProgress("node", ComponentState.Running)),
                                .5,
                                jobId = "current-job",
                            )
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Pause setup").performClick()
        assertEquals(listOf("ask", "confirm"), actions.stopCalls)
        assertEquals(0, actions.started)
    }

    @Test
    fun `continue failure is visible while the persisted job stays paused`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(
                    state.copy(
                        progress = SetupProgress(SetupState.Cancelled, emptyList(), 0.0),
                        failure = io.github.eslamasabry.oc.core.common.Failure.TimedOut(),
                    ),
                    Actions(),
                )
            }
        }
        onNodeWithText("That did not finish. Try again.").assertExists()
        onNodeWithText("Continue setup").assertIsEnabled()
    }

    @Test
    fun `customization keeps optional facts and uses the actual duration and size`() =
        runComposeUiTest {
            val actions = Actions()
            val options =
                state.options!!.copy(
                    components =
                        registry.map {
                            if (it.id == "python") it.copy(summary = "Run Python scripts") else it
                        }
                )
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SetupJourneyScreen(state.copy(customizing = true, options = options), actions)
                }
            }
            onNodeWithText("Run Python scripts · ~30\u00a0MB").assertExists()
            onNodeWithText("About 4 minutes · ~165 MB").assertExists()
            onNodeWithText("Python").performClick()
            assertEquals(listOf("python" to false), actions.toggles)
            onNodeWithTag("phone-setup-customize-done").performClick()
            assertEquals(1, actions.accepted)
        }

    @Test
    fun `all optional tools installed says so and cannot add an empty job`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(
                    state.copy(
                        customizing = true,
                        adding = true,
                        options = state.options!!.copy(installed = setOf("python")),
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Every optional tool is already on this phone.").assertExists()
        onNodeWithTag("phone-setup-customize-done").assertIsNotEnabled()
        assertEquals(0, actions.accepted)
    }

    @Test
    fun `failure names its current component when no failed row was saved`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(
                    state.copy(
                        progress =
                            SetupProgress(SetupState.Failed, emptyList(), .5, current = "python")
                    ),
                    Actions(),
                )
            }
        }
        onNodeWithText("Stopped at Python. What went wrong is under Details.").assertExists()
        onNodeWithText("Continue setup").assertIsEnabled()
    }

    @Test
    fun `offline failure gives the same recovery sentence as Flutter`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(
                    state.copy(
                        progress =
                            SetupProgress(
                                SetupState.Failed,
                                listOf(
                                    ComponentProgress(
                                        "node",
                                        ComponentState.Failed,
                                        error = SetupError.Offline("Node.js"),
                                    )
                                ),
                                .5,
                            )
                    ),
                    Actions(),
                )
            }
        }
        onNodeWithText("No internet connection — Continue when you're back online").assertExists()
    }

    @Test
    fun `failed step keeps its known stage when its reason is technical`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(
                    state.copy(
                        progress =
                            SetupProgress(
                                SetupState.Failed,
                                listOf(
                                    ComponentProgress(
                                        "node",
                                        ComponentState.Failed,
                                        stage = "Installing Node.js",
                                        error = SetupError.Unexpected,
                                    )
                                ),
                                .5,
                            )
                    ),
                    Actions(),
                )
            }
        }
        onNodeWithText("Stopped during: Installing Node.js. What went wrong is under Details.")
            .assertExists()
    }

    @Test
    fun `progress body rests above the middle of the viewport and details stays reachable`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SetupJourneyScreen(
                        state.copy(
                            progress =
                                SetupProgress(
                                    SetupState.Running,
                                    listOf(
                                        ComponentProgress(
                                            "node",
                                            ComponentState.Running,
                                            percent = 60.0,
                                        )
                                    ),
                                    .6,
                                    current = "node",
                                )
                        ),
                        Actions(),
                    )
                }
            }
            val viewport =
                onNodeWithTag("setup-progress-viewport").fetchSemanticsNode().boundsInRoot
            val body = onNodeWithTag("setup-progress-body").fetchSemanticsNode().boundsInRoot
            val room = viewport.height - body.height
            assertTrue(room > 0f, "The fixture must leave room to detect top-pinned content")
            assertEquals(viewport.top + room * .35f, body.top, absoluteTolerance = 1f)
            onNodeWithText("Details").assertIsDisplayed().performClick()
            onNodeWithText("Setup details").assertIsDisplayed()
        }

    @Test
    fun running() {
        val progress =
            SetupProgress(
                SetupState.Running,
                listOf(
                    ComponentProgress("linux", ComponentState.Done),
                    ComponentProgress("essentials", ComponentState.Done),
                    ComponentProgress("python", ComponentState.Done),
                    ComponentProgress(
                        "node",
                        ComponentState.Running,
                        stage = "Downloading Node.js 24",
                        bytesDone = 18000000,
                        bytesTotal = 30000000,
                    ),
                    ComponentProgress("opencode", ComponentState.Pending),
                ),
                .5,
                current = "node",
                etaSeconds = 130,
                jobId = "running",
            )
        FlutterGoldenGate.check("setup_progress_running", frame = FlutterGoldenGate.Frame.Screen) {
            SetupJourneyScreen(state.copy(progress = progress), Actions())
        }
    }

    @Test
    fun fresh() {
        FlutterGoldenGate.check("setup_start", frame = FlutterGoldenGate.Frame.Screen) {
            SetupJourneyScreen(state, Actions())
        }
    }

    @Test
    fun customize() {
        FlutterGoldenGate.check("setup_customize", frame = FlutterGoldenGate.Frame.Screen) {
            SetupJourneyScreen(state.copy(customizing = true), Actions())
        }
    }

    @Test
    fun `an unsupported phone explains why and cannot dispatch setup`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(
                    state.copy(
                        options =
                            state.options!!.copy(
                                device = SetupDeviceInfo(listOf("armeabi-v7a"), 4096, 10000000000)
                            )
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("This phone can't run it").assertExists()
        onNodeWithText("Set up OpenCode on this phone").assertIsNotEnabled()
        assertEquals(0, actions.started)
    }

    @Test
    fun `a required component exposes its lock reason and no toggle action`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitLockedRow("Linux base", "Required")
            }
        }
        onNodeWithText("Required").assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
