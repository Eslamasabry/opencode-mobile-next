package io.github.eslamasabry.oc.feature.projects.rb.o47

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.feature.projects.terminal.*
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class TerminalScreenTest {
    @Test
    fun `B-NAV-021 terminal has one bottom key bar and interrupt dispatches once`() =
        runComposeUiTest {
            val keys = mutableListOf<String>()
            val actions =
                Proxy.newProxyInstance(
                    TerminalActions::class.java.classLoader,
                    arrayOf(TerminalActions::class.java),
                ) { _, method, args ->
                    if (method.name == "key") keys += args[0] as String
                    null
                } as TerminalActions
            val process =
                TerminalProcess(
                    "term",
                    "build-server",
                    "bash",
                    persistentListOf(),
                    "/project",
                    true,
                    12,
                    null,
                )
            val state =
                TerminalUi(
                    "/project",
                    phase = TerminalPhase.Connected,
                    processes = persistentListOf(process),
                    selected = "term",
                    transcript = "$ git status",
                )
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    Box(Modifier.size(412.dp, 915.dp)) { TerminalScreen(state, actions) }
                }
            }
            onNodeWithText("build-server").assertExists()
            // The legacy readable switch and the extra Actions row no longer occupy the terminal.
            onNodeWithText("Readable mode").assertDoesNotExist()
            onNodeWithText("Terminal actions").assertDoesNotExist()
            onNodeWithText("^C").performClick()
            assertEquals(listOf("interrupt"), keys)
        }
}
