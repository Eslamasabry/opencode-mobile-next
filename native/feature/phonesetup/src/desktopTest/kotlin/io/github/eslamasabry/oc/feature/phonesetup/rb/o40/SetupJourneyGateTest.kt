@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phonesetup.rb.o40

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
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

        var connected = 0

        override fun connectComputer() {
            connected++
        }
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
    fun `C12 the checking page says what it is doing instead of drawing nothing`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(SetupJourneyUi(checking = true), Actions())
            }
        }
        onNodeWithText("Checking what's installed…").assertExists()
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
            onNodeWithText("About 165 MB to download").assertExists()
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

    /**
     * test/support/phone_setup_scenes.dart `setup_progress_failed`: Node.js failed downloading because the phone is
     * offline. Rendered here, on the live PhoneSetupRoute.Setup screen; :feature:setup's SetupRoute.Phone is not opened
     * by any route and no longer renders this golden.
     */
    @Test
    fun failed() {
        val offline = SetupError.Offline("Node.js")
        val progress =
            SetupProgress(
                SetupState.Failed,
                listOf(
                    ComponentProgress("linux", ComponentState.Done, version = "24.04"),
                    ComponentProgress("essentials", ComponentState.Done),
                    ComponentProgress("python", ComponentState.Done, version = "3.12.3"),
                    ComponentProgress(
                        "node",
                        ComponentState.Failed,
                        stage = "Downloading Node.js 24",
                        error = offline,
                    ),
                    ComponentProgress("opencode", ComponentState.Pending),
                ),
                .55,
                current = "node",
                error = offline,
                logTail = "curl: (6) Could not resolve host: nodejs.org\n",
                jobId = "scene",
                firstSetup = true,
            )
        FlutterGoldenGate.check("setup_progress_failed", frame = FlutterGoldenGate.Frame.Screen) {
            SetupJourneyScreen(state.copy(progress = progress), Actions())
        }
    }

    @Test
    fun `a stopped job's Continue fills the bottom like Pause does`() = runComposeUiTest {
        val stopped =
            SetupProgress(
                SetupState.Interrupted,
                listOf(
                    ComponentProgress("linux", ComponentState.Done),
                    ComponentProgress("essentials", ComponentState.Pending),
                ),
                .5,
                jobId = "stopped",
            )
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(state.copy(progress = stopped), Actions())
            }
        }
        // F: phone_setup_progress_screen.dart:351: KitScreen's bottom stretches the button to the control cap.
        val width = onNodeWithTag("setup-progress-continue").getUnclippedBoundsInRoot().width
        assertTrue(width >= 380.dp, "Continue is $width wide")
    }

    @Test
    fun `other ways unfolds in place and offers connecting a computer by address`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(state, actions)
            }
        }
        onNodeWithText("Connect to a computer by address").assertDoesNotExist()
        onNodeWithText("Other ways").performScrollTo().performClick()
        onNodeWithText("Connect to a computer by address").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, actions.connected)
        assertEquals(0, actions.started)
    }

    @Test
    fun `customize Done spans the sheet like Flutter's action block`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                SetupJourneyScreen(state.copy(customizing = true), Actions())
            }
        }
        val done = onNodeWithTag("phone-setup-customize-done").getUnclippedBoundsInRoot()
        val python = onNodeWithTag("phone-setup-customize-python").getUnclippedBoundsInRoot()
        // F: phone_setup_customize_sheet.dart:465: KitActionBlock(primary:) fills the sheet's content width, up to the
        // 400 dp control cap.
        val expected = minOf(python.width, 400.dp)
        assertTrue(done.width >= expected - 1.dp, "Done is ${done.width} wide; expected $expected")
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
