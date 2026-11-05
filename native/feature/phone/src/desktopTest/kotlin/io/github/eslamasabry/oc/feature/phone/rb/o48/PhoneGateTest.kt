@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.phone.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.phone.presentation.*
import io.github.eslamasabry.oc.feature.phone.ui.ThisPhoneScreen
import io.github.eslamasabry.oc.feature.phone.ui.KeepRunningScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneGateTest {
    @Test fun `an installed-tools row with a leading glyph retains its visible expand action`() = runComposeUiTest {
        var expanded = false
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            io.github.eslamasabry.oc.ui.kit.row.KitExpandRow("Installed", false, onExpandedChange = { expanded = it },
                leading = io.github.eslamasabry.oc.ui.kit.icon.KitIcons.check) { }
        } }
        onNodeWithText("Installed").performClick()
        assertEquals(true, expanded)
    }

    private object PhoneActions : ThisPhoneActions {
        override fun act(act: PhoneAct) {}
        override fun dismissRecovery() {}
        override fun refreshStorage() {}
        override fun stopServer() {}
        override fun askRestart() {}
        override fun askUpdate() {}
        override fun confirm() {}
        override fun dismiss() {}
        override fun measureStorage() {}
        override fun openRunning() {}
        override fun openKeepRunning() {}
        override fun back() {}
        override fun shown(visible: Boolean) {}
    }
    @Test fun not_set_up() {
        FlutterGoldenGate.check("phone_this_phone_not_set_up", frame = FlutterGoldenGate.Frame.Screen) {
            ThisPhoneScreen(ThisPhoneUi(answer = ThisPhoneAnswer(AnswerTone.Off, AnswerHeadline.NotSetUp, primary = PhoneAct.SetUp)), PhoneActions)
        }
    }
    @Test fun running() {
        FlutterGoldenGate.check("phone_this_phone_running", frame = FlutterGoldenGate.Frame.Screen) {
            ThisPhoneScreen(ThisPhoneUi(answer = ThisPhoneAnswer(AnswerTone.Ok, AnswerHeadline.Running),
                details = PhoneDetailsUi(installed = true, installedNames = persistentListOf("Linux base", "Git and SSH", "Node.js", "OpenCode")),
                serverRows = persistentListOf(ServerRow.Restart, ServerRow.Stop)), PhoneActions)
        }
    }
    @Test fun `a completed battery step stays visible but cannot open settings again`() = runComposeUiTest {
        val opened = mutableListOf<KeepAliveStepKind>()
        val actions = object : KeepRunningActions {
            override fun resumed() {}
            override fun open(step: KeepAliveStepKind) { opened += step }
            override fun back() {}
        }
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            KeepRunningScreen(KeepRunningUi(loaded = true, steps = persistentListOf(
                KeepAliveStepUi(KeepAliveStepKind.Battery, true, KeepAliveSetting.Battery),
            )), actions)
        } }
        onNodeWithText("Allowed").assertExists()
        onNodeWithText("Open").assertDoesNotExist()
        assertEquals(emptyList(), opened)
    }
}
