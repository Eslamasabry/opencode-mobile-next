package io.github.eslamasabry.oc.feature.terminal.rb.o47

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.project.terminal.LocalShell
import io.github.eslamasabry.oc.engine.project.terminal.TerminalKey
import io.github.eslamasabry.oc.feature.terminal.presentation.*
import io.github.eslamasabry.oc.feature.terminal.ui.LocalTerminalScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LocalTerminalScreenTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(
            LocalTerminalActions::class.java.classLoader,
            arrayOf(LocalTerminalActions::class.java, LocalTerminalNativeActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "LocalTerminalActions"
                else -> {
                    if (method.name != "linkTipMoment" && method.name != "resumed")
                        calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as LocalTerminalActions

    private fun shell(id: Int, running: Boolean = true) =
        LocalShell(
            id,
            id,
            id,
            running,
            if (running) null else 0,
            "ready",
            object : PlatformViewHandle {
                override val native: Any = Unit
            },
        )

    @Test
    fun `B-NAV-040 one shell omits tabs and a second shell selects its named target`() =
        runComposeUiTest {
            val state =
                mutableStateOf(
                    LocalTerminalUi(shells = listOf(shell(1)), selected = 1, accessible = true)
                )
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    LocalTerminalScreen(state.value, actions)
                }
            }
            onNodeWithText("Shell 1").assertDoesNotExist()
            runOnIdle { state.value = state.value.copy(shells = listOf(shell(1), shell(2))) }
            onNodeWithContentDescription("Shell 2").performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("select" to listOf(2)), calls)
        }

    @Test
    fun `B-NAV-040 modifier reaches native grid immediately and named key keeps its target`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    LocalTerminalScreen(
                        LocalTerminalUi(shells = listOf(shell(1)), selected = 1, accessible = true),
                        actions,
                    )
                }
            }
            onNodeWithText("Ctrl").performClick()
            onNodeWithText("Tab").performClick()
            assertEquals(
                listOf<Pair<String, List<Any?>>>("key" to listOf(TerminalKey.Control), "key" to listOf(TerminalKey.Tab)),
                calls,
            )
        }

    @Test
    fun `B-NAV-040 ended shell keeps transcript and Restart instead of typing keys`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    LocalTerminalScreen(
                        LocalTerminalUi(
                            shells = listOf(shell(1, false)),
                            selected = 1,
                            accessible = true,
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("ready").assertExists()
            onNodeWithText("Ctrl").assertDoesNotExist()
            onNodeWithText("Restart").performClick()
            assertEquals(listOf("restart" to emptyList()), calls)
        }

    @Test
    fun `B-NAV-040 the menu pastes into the running shell it names`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                LocalTerminalScreen(
                    LocalTerminalUi(shells = listOf(shell(1)), selected = 1, accessible = true),
                    actions,
                )
            }
        }
        onNodeWithContentDescription("More").performClick()
        onNodeWithText("Paste into Shell 1").performClick()
        assertEquals(listOf("paste" to emptyList()), calls)
    }

    @Test
    fun `B-NAV-040 an ended shell offers no Paste`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                LocalTerminalScreen(
                    LocalTerminalUi(shells = listOf(shell(1, false)), selected = 1, accessible = true),
                    actions,
                )
            }
        }
        onNodeWithContentDescription("More").performClick()
        onNodeWithText("Close this shell").assertExists()
        onNodeWithText("Paste into Shell 1").assertDoesNotExist()
    }

    @Test
    fun `AUDIT-462 Linux not set up offers Set up Linux, which opens the phone setup`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                LocalTerminalScreen(LocalTerminalUi(failed = true, notInstalled = true), actions)
            }
        }
        onNodeWithText("Try again").assertDoesNotExist()
        onNodeWithText("Set up Linux on this phone").performClick()
        assertEquals(listOf("setUpLinux"), calls.map { it.first }.filter { it != "resumed" })
    }

    @Test
    fun `AUDIT-462 a terminal that failed for another reason still offers Try again`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                LocalTerminalScreen(LocalTerminalUi(failed = true), actions)
            }
        }
        onNodeWithText("Set up Linux on this phone").assertDoesNotExist()
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("create"), calls.map { it.first }.filter { it != "resumed" })
    }

    @Test
    fun `B-TERM-SRC-1 another server's terminal is offered with a verb, not a bare name`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    LocalTerminalScreen(
                        LocalTerminalUi(
                            shells = listOf(shell(1)),
                            selected = 1,
                            accessible = true,
                            servers = listOf(TerminalServerSource(ProfileId("p1"), "This phone")),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithContentDescription("More").performClick()
            onNodeWithText("Use the terminal on This phone").assertExists()
        }
}
