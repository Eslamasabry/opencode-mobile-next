@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryCard
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryFacts
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryOpen
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupMaker
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SystemKill
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue #138 on the screen: "not answering" becomes "Your phone closed OpenCode", with Continue and the maker's steps. */
class SetupKilledScreenTest {
    private class Actions : SetupJourneyActions {
        val calls = mutableListOf<String>()
        override fun back() {}
        override fun retry() {}
        override fun toggle(id: String, checked: Boolean) {}
        override fun accept() {}
        override fun start() {}
        override fun customize() {}
        override fun continueSetup() { calls += "continue" }
        override fun askStop() {}
        override fun cancelConfirm() {}
        override fun confirmStop() {}
        override fun askRemoveVoice() {}
        override fun confirmRemoveVoice() {}
        override fun openStorage() {}
        override fun done() {}
        override fun openBatterySettings(open: BatteryOpen) { calls += "open-$open" }
    }

    private val notAnswering =
        SetupProgress(
            SetupState.Failed,
            listOf(
                ComponentProgress("linux", ComponentState.Done),
                ComponentProgress("start", ComponentState.Failed, error = SetupError.CannotStart),
            ),
            0.9,
            current = "start",
            error = SetupError.CannotStart,
            jobId = "setup-1",
        )

    private fun ui(kill: SystemKill?, card: BatteryCard?, open: Boolean = false) =
        SetupJourneyUi(
            checking = false,
            progress = notAnswering,
            options = SetupOptions(emptyList(), emptySet(), SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10_000_000_000)),
            systemKill = kill,
            batteryCard = card,
            batteryOpenFailed = open,
        )

    private fun run(state: SetupJourneyUi, actions: Actions, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) { SetupJourneyScreen(state, actions) }
            }
            block()
        }

    private val killedWords =
        "Your phone closed OpenCode while it was setting up. Finished steps stay. Continue to pick up where it stopped."

    @Test
    fun `OEM-KILL-010 a killed setup says the phone closed OpenCode and offers Continue`() {
        val actions = Actions()
        val card = BatteryCard.decide(BatteryFacts(SetupMaker.Xiaomi, false, false), afterKill = true)
        run(ui(SystemKill.Other, card), actions) {
            onNodeWithText(killedWords).assertExists()
            onNodeWithText("Continue setup").performClick()
        }
        assertEquals(listOf("continue"), actions.calls)
    }

    @Test
    fun `OEM-KILL-011 the card shows the maker's steps and its buttons open settings`() {
        val actions = Actions()
        val card = BatteryCard.decide(BatteryFacts(SetupMaker.Xiaomi, false, false), afterKill = true)
        run(ui(SystemKill.Other, card, open = true), actions) {
            onNodeWithText("Let OpenCode keep running").assertExists()
            onNodeWithText("Turn on Autostart for OpenCode.").assertExists()
            onNodeWithText("This phone would not open that screen. Follow the steps in Settings by hand.").assertExists()
            onNodeWithText("Open app settings").performClick()
            onNodeWithText("Open Autostart").performClick()
        }
        assertEquals(listOf("open-App", "open-Maker"), actions.calls)
    }

    @Test
    fun `OEM-KILL-012 no card when the battery is unrestricted, and no kill words when nothing killed it`() {
        run(ui(null, null), Actions()) {
            onAllNodesWithText("Let OpenCode keep running").assertCountEquals(0)
            onAllNodesWithText(killedWords).assertCountEquals(0)
        }
    }

    @Test
    fun `OEM-KILL-013 Samsung gets Never sleeping apps and a plain phone only the first step`() {
        val samsung = BatteryCard.decide(BatteryFacts(SetupMaker.Samsung, false, false), afterKill = true)
        run(ui(SystemKill.Other, samsung), Actions()) {
            onNodeWithText("Add OpenCode to Never sleeping apps.").assertExists()
            onNodeWithText("Open Battery").assertExists()
            onAllNodesWithText("Turn on Autostart for OpenCode.").assertCountEquals(0)
        }
        val plain = BatteryCard.decide(BatteryFacts(SetupMaker.Other, false, false), afterKill = true)
        run(ui(SystemKill.Other, plain), Actions()) {
            onAllNodesWithText("Open Autostart").assertCountEquals(0)
            onNodeWithText("Open app settings").assertExists()
        }
    }
}
