@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phone.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.phone.presentation.*
import io.github.eslamasabry.oc.feature.phone.ui.KeepRunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.ThisPhoneScreen
import io.github.eslamasabry.oc.feature.phone.ui.offersInlineStop
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

class PhoneGateTest {
    @Test
    fun `an installed-tools row with a leading glyph retains its visible expand action`() =
        runComposeUiTest {
            var expanded = false
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    io.github.eslamasabry.oc.ui.kit.row.KitExpandRow(
                        "Installed",
                        false,
                        onExpandedChange = { expanded = it },
                        leading = io.github.eslamasabry.oc.ui.kit.icon.KitIcons.check,
                    ) {}
                }
            }
            onNodeWithText("Installed").performClick()
            assertEquals(true, expanded)
        }

    @Test
    fun `only stoppable work needing attention gets a prominent Stop`() {
        val row = ProcessRowUi(42, "node", ProcessKind.DevService, 2, false, null)
        assertFalse(offersInlineStop(row))
        assertTrue(offersInlineStop(row.copy(heavy = true)))
        assertTrue(offersInlineStop(row.copy(leftBehind = true)))
        assertFalse(offersInlineStop(row.copy(heavy = true, protectedAs = ProcessRole.ServerRoot)))
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

    @Test
    fun `stopping the phone server asks first and cancel keeps it running`() = runComposeUiTest {
        var stopped = 0
        val actions =
            object : ThisPhoneActions by PhoneActions {
                override fun stopServer() {
                    stopped++
                }
            }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                ThisPhoneScreen(
                    ThisPhoneUi(
                        answer = ThisPhoneAnswer(AnswerTone.Ok, AnswerHeadline.Running),
                        details = PhoneDetailsUi(installed = true),
                        serverRows = persistentListOf(ServerRow.Restart, ServerRow.Stop),
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Stop OpenCode on this phone").performClick()
        assertEquals(0, stopped)
        onNodeWithText("Stop the local server?").assertIsDisplayed()
        onNodeWithText("Keep running", useUnmergedTree = true).performClick()
        assertEquals(0, stopped)
        onNodeWithText("Stop OpenCode on this phone").performClick()
        onNodeWithText("Stop local server").performClick()
        assertEquals(1, stopped)
    }

    @Test
    fun `restart confirmation belongs to the standalone phone screen`() = runComposeUiTest {
        var confirmed = 0
        val actions =
            object : ThisPhoneActions by PhoneActions {
                override fun confirm() {
                    confirmed++
                }
            }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                ThisPhoneScreen(ThisPhoneUi(confirm = ThisPhoneConfirm.Restart(false)), actions)
            }
        }
        onNodeWithText("Restart the server?").assertIsDisplayed()
        onNodeWithText("Restart OpenCode on this phone").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun not_set_up() {
        FlutterGoldenGate.check(
            "phone_this_phone_not_set_up",
            frame = FlutterGoldenGate.Frame.Screen,
        ) {
            ThisPhoneScreen(
                ThisPhoneUi(
                    answer =
                        ThisPhoneAnswer(
                            AnswerTone.Off,
                            AnswerHeadline.NotSetUp,
                            primary = PhoneAct.SetUp,
                        )
                ),
                PhoneActions,
            )
        }
    }

    private val runningState =
        ThisPhoneUi(
            answer = ThisPhoneAnswer(AnswerTone.Ok, AnswerHeadline.Running),
            details =
                PhoneDetailsUi(
                    installed = true,
                    installedNames = persistentListOf("Linux base", "Git and SSH", "Node.js", "OpenCode"),
                ),
            version = VersionRowUi("1.18.29", UpdateRow.Available("2.1.1")),
            serverRows = persistentListOf(ServerRow.Restart, ServerRow.Stop),
        )

    @Test
    fun `installed tools read as one sentence like Flutter's joinSetupNames`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                ThisPhoneScreen(runningState, PhoneActions)
            }
        }
        onNodeWithText("Linux base, Git and SSH, Node.js and OpenCode").assertExists()
    }

    @Test
    fun `the update row leads the tools list, a section below the server rows`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                ThisPhoneScreen(runningState, PhoneActions)
            }
        }
        val stop = onNodeWithText("Stop OpenCode on this phone").getUnclippedBoundsInRoot()
        val update = onNodeWithText("Update OpenCode").getUnclippedBoundsInRoot()
        // F: this_phone_screen.dart:1009-1135: the server group, a section gap, then the list the update row leads.
        assertTrue(update.top - stop.bottom >= 16.dp, "Update sits ${update.top - stop.bottom} under Stop")
    }

    @Test
    fun running() {
        FlutterGoldenGate.check(
            "phone_this_phone_running",
            frame = FlutterGoldenGate.Frame.Screen,
        ) {
            ThisPhoneScreen(
                // test/revamp/screen_phone_1_golden_test.dart `phone_this_phone_running`: PhoneLinux(running: true)
                // uses 700 MB, the in-app profile is saved but not connected (Use this phone's server), and the
                // pinned OpenCode 2.1.1 is offered.
                ThisPhoneUi(
                    answer =
                        ThisPhoneAnswer(
                            AnswerTone.Ok,
                            AnswerHeadline.Running,
                            line = AnswerLine.Storage(700L * 1024 * 1024),
                        ),
                    details =
                        PhoneDetailsUi(
                            installed = true,
                            canConnect = true,
                            installedNames =
                                persistentListOf("Linux base", "Git and SSH", "Node.js", "OpenCode"),
                        ),
                    version = VersionRowUi("1.18.29", UpdateRow.Available("2.1.1")),
                    serverRows = persistentListOf(ServerRow.Restart, ServerRow.Stop),
                ),
                PhoneActions,
            )
        }
    }

    @Test
    fun `a completed battery step stays visible but cannot open settings again`() =
        runComposeUiTest {
            val opened = mutableListOf<KeepAliveStepKind>()
            val actions =
                object : KeepRunningActions {
                    override fun resumed() {}

                    override fun open(step: KeepAliveStepKind) {
                        opened += step
                    }

                    override fun back() {}
                }
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    KeepRunningScreen(
                        KeepRunningUi(
                            loaded = true,
                            steps =
                                persistentListOf(
                                    KeepAliveStepUi(
                                        KeepAliveStepKind.Battery,
                                        true,
                                        KeepAliveSetting.Battery,
                                    )
                                ),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Allowed").assertExists()
            onNodeWithText("Open").assertDoesNotExist()
            assertEquals(emptyList(), opened)
        }
}
