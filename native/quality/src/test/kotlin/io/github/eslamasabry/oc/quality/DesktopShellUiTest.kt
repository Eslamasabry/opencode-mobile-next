package io.github.eslamasabry.oc.quality

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
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
    fun `shell opens on the placeholder screen, pushes an assisted entry and goes back`() = runComposeUiTest {
        val graph = createGraphFactory<DesktopAppGraph.Factory>().create(desktopBuildInfo, Redactor())
        setContent { App(graph.shell, DesktopKitPlatform(), graph.metroViewModelFactory) }

        onNodeWithText("Conversations").assertExists()
        onNodeWithText("Show all conversations").performClick()
        onNodeWithText("All conversations").assertExists()
        onNodeWithText("Filter: No filter").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Conversations").assertExists()
    }
}
