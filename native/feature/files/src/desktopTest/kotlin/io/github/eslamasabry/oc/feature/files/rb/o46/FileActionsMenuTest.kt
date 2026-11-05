package io.github.eslamasabry.oc.feature.files.rb.o46

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.feature.files.presentation.*
import io.github.eslamasabry.oc.feature.files.ui.FolderScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.*

@OptIn(ExperimentalTestApi::class)
class FileActionsMenuTest {
    @Test
    fun `B-NAV-021 file menu consumes its target before dismissal clears it`() = runComposeUiTest {
        val node = FileNode("main.dart", "lib/main.dart", false)
        var ui by
            mutableStateOf(
                FolderUi(
                    "lib",
                    phase = FolderPhase.Ready,
                    entries = persistentListOf(node),
                    actionsFor = node,
                    canAttach = true,
                    changes =
                        persistentMapOf(
                            node.path to VersionControlFile(node.path, "modified", 1, 0)
                        ),
                )
            )
        val copied = mutableListOf<String?>()
        val actions =
            Proxy.newProxyInstance(
                FolderActions::class.java.classLoader,
                arrayOf(FolderActions::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "copyPath" -> copied += ui.actionsFor?.path
                    "closeActions" -> ui = ui.copy(actionsFor = null)
                }
                null
            } as FolderActions
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.size(412.dp, 915.dp)) { FolderScreen(ui, actions) }
            }
        }
        onNodeWithText("Ask about this file").assertExists()
        onNodeWithText("Attach to prompt").assertExists()
        onNodeWithText("Open in Review").assertExists()
        onNodeWithText("Copy path").performClick()
        assertEquals(listOf("lib/main.dart"), copied)
        onNodeWithText("Copy name").assertDoesNotExist()
    }
}
