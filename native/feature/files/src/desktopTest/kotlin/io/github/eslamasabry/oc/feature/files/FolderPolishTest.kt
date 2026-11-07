package io.github.eslamasabry.oc.feature.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.*
import io.github.eslamasabry.oc.feature.files.ui.FolderScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** P-025: the Files page names the project and offers each row's acts behind a quiet ⋯. */
@OptIn(ExperimentalTestApi::class)
class FolderPolishTest {
    private val calls = mutableListOf<String>()

    private val actions =
        Proxy.newProxyInstance(FolderActions::class.java.classLoader, arrayOf(FolderActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FolderCalls"
                else -> {
                    calls += method.name + (args?.getOrNull(0)?.let { a -> (a as? FileNode)?.let { " ${it.path}" } } ?: "")
                    null
                }
            }
        } as FolderActions

    private val ui =
        FolderUi(
            path = "",
            phase = FolderPhase.Ready,
            entries = persistentListOf(FileNode("lib", "lib", true), FileNode("README.md", "README.md", false)),
            projectName = "my-app",
            canTerminal = true,
        )

    @Test
    fun `P-025 the root is titled with the project's name and rows offer their acts behind a glyph`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.size(412.dp, 915.dp)) { FolderScreen(ui, actions) }
            }
        }
        onNodeWithText("my-app").assertExists()
        onAllNodesWithText("Project").assertCountEquals(0)
        // No green word on every row: a labelled ⋯ that names its file for TalkBack.
        onAllNodesWithText("Actions").assertCountEquals(0)
        onNodeWithContentDescription("Actions for README.md").performClick()
        assertEquals(listOf("showActions README.md"), calls.filter { it.startsWith("showActions") })
    }

    @Test
    fun `P-025 before the server names the project the root still says Project`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.size(412.dp, 915.dp)) { FolderScreen(ui.copy(projectName = null), actions) }
            }
        }
        onNodeWithText("Project").assertExists()
    }

    @Test
    fun looks() {
        for ((suffix, scale) in listOf("" to 1f, "-text13" to 1.3f))
            Looks.shoot("p025-files-root$suffix", fontScale = scale) { FolderScreen(ui, actions) }
    }
}
