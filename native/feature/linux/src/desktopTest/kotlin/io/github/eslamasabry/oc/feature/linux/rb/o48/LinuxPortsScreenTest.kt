@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.linux.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPort
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProblem
import io.github.eslamasabry.oc.feature.linux.manager.PortOwner
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsUi
import io.github.eslamasabry.oc.feature.linux.ui.LinuxPortsScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

class LinuxPortsScreenTest {
    private class Actions : LinuxPortsActions {
        var copied: Int? = null
        var previewed: Int? = null
        var stopAsked: Int? = null
        var retried = 0

        override fun selectPort(port: Int) {}

        override fun copyAddress(port: Int) {
            copied = port
        }

        override fun askStopOwner(port: Int) {
            stopAsked = port
        }

        override fun confirmStopOwner() {}

        override fun shown(visible: Boolean) {}

        override fun retry() {
            retried++
        }

        override fun preview(port: Int) {
            previewed = port
        }

        override fun readProgramLog(pid: Int) {}

        override fun askStopProgram(pid: Int) {}

        override fun confirmStopProgram() {}

        override fun dismiss() {}

        override fun back() {}
    }

    @Test
    fun `an OpenCode port is not a web page and its menu can copy but never stop it`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    LinuxPortsScreen(
                        LinuxPortsUi(
                            loading = false,
                            ports =
                                persistentListOf(
                                    LinuxPort(
                                        4097,
                                        PortOwner(91, "opencode serve --port 4097"),
                                        allInterfaces = true,
                                    )
                                ),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Used by OpenCode server · Not a web page").assertExists()
            onNodeWithText("Open preview").assertDoesNotExist()
            onNodeWithContentDescription("Port actions").performClick()
            onNodeWithText("Copy address").performClick()
            assertEquals(4097, actions.copied)
            assertEquals(null, actions.stopAsked)
            assertEquals(null, actions.previewed)
        }

    @Test
    fun `a web port opens its own preview and stops only through the named owner action`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    LinuxPortsScreen(
                        LinuxPortsUi(
                            loading = false,
                            ports =
                                persistentListOf(
                                    LinuxPort(
                                        5173,
                                        PortOwner(92, "node /project/vite.js --port 5173"),
                                        allInterfaces = false,
                                    )
                                ),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Used by ⁨vite⁩ · Only on this phone").assertExists()
            onNodeWithText("Port 5173").performClick()
            assertEquals(5173, actions.previewed)
            assertEquals(null, actions.stopAsked)
            onNodeWithContentDescription("Port actions").performClick()
            onNodeWithText("Stop vite").performClick()
            assertEquals(5173, actions.stopAsked)
        }

    @Test
    fun `a failed first read offers a page recovery with retry`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                LinuxPortsScreen(
                    LinuxPortsUi(loading = false, problem = LinuxProblem.Failed),
                    actions,
                )
            }
        }
        onNodeWithText("Try again").performClick()
        assertEquals(1, actions.retried)
    }
}
