package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentProgress
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProblem
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupItemUi
import io.github.eslamasabry.oc.feature.phonesetup.ui.PhoneSetupScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** The real Set up page by visible text (phone_setup_start_screen / phone_setup_progress_screen widget tests). */
@OptIn(ExperimentalTestApi::class)
class PhoneSetupScreenTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class Recorder : PhoneSetupActions {
        val calls = mutableListOf<String>()

        override fun back() {
            calls += "back"
        }

        override fun setUp() {
            calls += "setUp"
        }

        override fun continueSetup() {
            calls += "continue"
        }

        override fun askStop() {
            calls += "askStop"
        }

        override fun keepGoing() {
            calls += "keepGoing"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun openStorage() {
            calls += "openStorage"
        }

        override fun done() {
            calls += "done"
        }
    }

    private val items =
        persistentListOf(SetupItemUi("linux", "Linux base"), SetupItemUi("node", "Node.js"), SetupItemUi("opencode", "OpenCode"))

    private fun show(state: PhoneSetupUi, actions: PhoneSetupActions, locale: String = "en") =
        runComposeUiTest {
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = locale,
                    stillFrame = true,
                ) {
                    Box(Modifier.width(412.dp)) { PhoneSetupScreen(state, actions) }
                }
            }
            check(this)
        }

    private var check: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {}

    @Test
    fun `B-LINUX-209 the start page lists what is installed, its size and offers Set up`() {
        val actions = Recorder()
        check = {
            onNodeWithText("Run a coding agent right here").assertExists()
            onNodeWithText("No computer and no other apps. ~350 MB to download the first time.").assertExists()
            onNodeWithText("Linux base").assertExists()
            onNodeWithText("Node.js").assertExists()
            onNodeWithText("Set up OpenCode on this phone").performClick()
        }
        show(PhoneSetupUi(checking = false, preflight = SetupPreflightResult.Ok, items = items, downloadBytes = 350_000_000), actions)
        assertEquals(listOf("setUp"), actions.calls)
    }

    @Test
    fun `B-LINUX-209 a 32-bit-only phone is told why and gets no Set up button`() {
        check = {
            onNodeWithText("This phone can't run it").assertExists()
            onNodeWithText("This app's Ubuntu only runs on a 64-bit Arm or Intel phone; this one reports armeabi-v7a.").assertExists()
            onAllNodesWithText("Set up OpenCode on this phone").assertCountEquals(0)
        }
        show(
            PhoneSetupUi(checking = false, preflight = SetupPreflightResult.UnsupportedAbi("armeabi-v7a"), items = items),
            Recorder(),
        )
    }

    @Test
    fun `B-LINUX-210 low memory names both numbers and a slow phone gets a note`() {
        check = {
            onNodeWithText("This phone doesn't have enough memory").assertExists()
            onNodeWithText(
                "OpenCode needs a phone with at least 1800 MB of memory; this one has 1024 MB. Run it on a computer instead and connect this phone to it.",
            ).assertExists()
        }
        show(PhoneSetupUi(checking = false, preflight = SetupPreflightResult.LowMemory(1024), items = items), Recorder())
        check = {
            onNodeWithText("It may be slow on this phone, which has 2048 MB of memory.").assertExists()
            onNodeWithText("Set up OpenCode on this phone").assertExists()
        }
        show(PhoneSetupUi(checking = false, preflight = SetupPreflightResult.MayBeSlow(2048), items = items), Recorder())
    }

    @Test
    fun `B-LINUX-211 low space says how much to free and opens Storage settings`() {
        val actions = Recorder()
        check = {
            onNodeWithText("Not enough free space").assertExists()
            onNodeWithText("Free about 1.2 GB on this phone, then come back to set this up.").assertExists()
            onNodeWithText("Open Storage settings").performClick()
        }
        show(PhoneSetupUi(checking = false, preflight = SetupPreflightResult.LowSpace(1_200_000_000), items = items), actions)
        assertEquals(listOf("openStorage"), actions.calls)
    }

    @Test
    fun `B-LINUX-201 a running job shows its stage, percent and Stop`() {
        val actions = Recorder()
        val running =
            SetupProgress(
                state = SetupState.Running,
                components =
                    listOf(
                        ComponentProgress("linux", ComponentState.Done),
                        ComponentProgress("node", ComponentState.Running, stage = "Installing Node.js"),
                    ),
                overall = 0.42,
                current = "node",
                jobId = "setup-1",
            )
        check = {
            onNodeWithText("Setting up OpenCode on this phone").assertExists()
            onNodeWithText("42%").assertExists()
            onAllNodesWithText("Installing Node.js", substring = true).assertCountEquals(1)
            onNodeWithText("Stop setup").performClick()
        }
        show(PhoneSetupUi(checking = false, items = items, progress = running), actions)
        assertEquals(listOf("askStop"), actions.calls)
    }

    @Test
    fun `B-LINUX-203 a failed job says what happened plainly and offers Continue setup`() {
        val actions = Recorder()
        val failed =
            SetupProgress(
                state = SetupState.Failed,
                components = listOf(ComponentProgress("opencode", ComponentState.Failed)),
                overall = 0.6,
                error = SetupProblem.Offline("OpenCode"),
                logTail = "curl: (6) Could not resolve host",
                jobId = "setup-1",
            )
        check = {
            onNodeWithText("Setup didn't finish").assertExists()
            onNodeWithText("Could not download OpenCode: no internet connection").assertExists()
            onAllNodesWithText("curl: (6) Could not resolve host").assertCountEquals(0)
            onNodeWithText("Continue setup").performClick()
        }
        show(PhoneSetupUi(checking = false, items = items, progress = failed), actions)
        assertEquals(listOf("continue"), actions.calls)
    }

    @Test
    fun `B-LINUX-201 a finished job says OpenCode is ready and opens it`() {
        val actions = Recorder()
        val done = SetupProgress(state = SetupState.Done, components = emptyList(), overall = 1.0, jobId = "setup-1")
        check = {
            onNodeWithText("OpenCode is ready").assertExists()
            onNodeWithText("100%").assertExists()
            onNodeWithText("Open").performClick()
        }
        show(PhoneSetupUi(checking = false, items = items, progress = done), actions)
        assertEquals(listOf("done"), actions.calls)
    }

    @Test
    fun `B-LINUX-209 the start page reads in Arabic`() {
        check = {
            onAllNodesWithText("Set up OpenCode on this phone").assertCountEquals(0)
            onAllNodesWithText("Run a coding agent right here").assertCountEquals(0)
        }
        Locale.setDefault(Locale.forLanguageTag("ar"))
        show(PhoneSetupUi(checking = false, preflight = SetupPreflightResult.Ok, items = items, downloadBytes = 350_000_000), Recorder(), "ar")
    }
}
