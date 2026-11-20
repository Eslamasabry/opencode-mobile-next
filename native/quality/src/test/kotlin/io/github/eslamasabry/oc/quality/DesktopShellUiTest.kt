package io.github.eslamasabry.oc.quality

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.zacsweers.metro.createGraphFactory
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.desktop.DesktopAppGraph
import io.github.eslamasabry.oc.desktop.desktopBuildInfo
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.shell.App
import kotlin.test.Test

/**
 * P1 runtime proof on the desktop JVM, headless: the real graph, App(), Nav3 NavDisplay, a plain Metro ViewModel on
 * the start entry, a route-keyed (assisted) ViewModel on the pushed entry, CMP string resources, and back.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopShellUiTest {
    @Test
    // A phone-sized window: from 840 dp the home and New conversation stand side by side (big screens, slice 3) and
    // the rail is gone, so the push-and-back path is the phone's.
    fun `shell opens on the conversations home, pushes an assisted entry and goes back`() =
        runDesktopComposeUiTest(412, 915) {
        val graph = createGraphFactory<DesktopAppGraph.Factory>().create(desktopBuildInfo, Redactor())
        // Main.kt wraps App in the graph's chat environment (redactor and the rest); the host does the same.
        setContent { graph.kitChatEnvironment.Provide { App(graph.shell, DesktopKitPlatform(), graph.metroViewModelFactory) } }

        // The home's title.
        onAllNodesWithText("Conversations")[0].assertExists()
        onNodeWithText("New conversation").performClick()
        // The route-keyed (assisted) entry: no server is connected, so it says so and offers to add one.
        onNodeWithText("Add an OpenCode 2 server to start a conversation.").assertExists()
        onNodeWithContentDescription("Close").performClick()
        onAllNodesWithText("Conversations")[0].assertExists()
    }
}
