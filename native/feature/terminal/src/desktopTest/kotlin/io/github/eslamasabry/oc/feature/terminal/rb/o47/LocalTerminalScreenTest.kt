package io.github.eslamasabry.oc.feature.terminal.rb.o47

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
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
                    if (method.name != "linkTipMoment")
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
            assertEquals(listOf("select" to listOf(2)), calls)
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
                listOf("key" to listOf(TerminalKey.Control), "key" to listOf(TerminalKey.Tab)),
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
}
