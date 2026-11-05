package io.github.eslamasabry.oc.feature.diagnostics.rb.o51

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.diagnostics.presentation.*
import io.github.eslamasabry.oc.feature.diagnostics.ui.KeptScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KeptPageTest {
    private val calls = mutableListOf<String>()
    private val actions =
        Proxy.newProxyInstance(
            KeptActions::class.java.classLoader,
            arrayOf(KeptActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "KeptActions"
                else -> {
                    calls += method.name
                    null
                }
            }
        } as KeptActions

    @Composable
    private fun Page(state: KeptUi) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KeptScreen(state, actions)
        }
    }

    @Test
    fun `B-SEC-047 exact crash text is previewed in a sheet before explicit sharing`() =
        runComposeUiTest {
            val text = "OpenCode Mobile crash report\nCaptured error categories and times only."
            setContent { Page(KeptUi(preview = KeptPreview(text, 2, 256))) }
            onNodeWithText(text).assertExists()
            onNodeWithText("2 reports · 256 B").assertExists()
            assertEquals(emptyList(), calls)
            onNodeWithText("Share report").performClick()
            assertEquals(listOf("onShareCrashReport"), calls)
        }

    @Test
    fun `B-SEC-054 sharing holds repeat taps`() = runComposeUiTest {
        setContent { Page(KeptUi(preview = KeptPreview("Safe report", 1, 2048), sharing = true)) }
        onNodeWithText("1 report · 2.0 KB").assertExists()
        onNodeWithText("Share report").performClick()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `B-SEC-059 routine app exits are folded while problem exits remain visible`() =
        runComposeUiTest {
            setContent {
                Page(
                    KeptUi(
                        crashAvailable = true,
                        exits =
                            KeptExits.Ready(
                                listOf(
                                    KeptExitRow(ExitKind.Update, "Today 09:00"),
                                    KeptExitRow(ExitKind.ForceStop, "Today 08:00"),
                                    KeptExitRow(ExitKind.Crash, "Yesterday 21:42"),
                                ),
                                0,
                            ),
                    )
                )
            }
            onNodeWithText("App stopped unexpectedly").assertExists()
            onNodeWithText("App updated").assertDoesNotExist()
            onNodeWithTag("exit-history-routine").performScrollTo().performClick()
            onNodeWithText("App updated").assertExists()
            onNodeWithText("App stopped").assertExists()
            assertEquals(emptyList(), calls)
        }
}
