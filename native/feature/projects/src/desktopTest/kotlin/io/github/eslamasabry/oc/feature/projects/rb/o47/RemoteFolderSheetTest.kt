package io.github.eslamasabry.oc.feature.projects.rb.o47

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.projects.presentation.*
import io.github.eslamasabry.oc.feature.projects.ui.ProjectsPickerScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class RemoteFolderSheetTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(
            ProjectsPickerActions::class.java.classLoader,
            arrayOf(ProjectsPickerActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FolderActions"
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as ProjectsPickerActions

    @Test
    fun `B-ANDR-078 recent folder keeps its named path and is not repeated in known projects`() =
        runComposeUiTest {
            val folder = FolderRowUi("/work/app", "app")
            val state =
                ProjectsPickerUi(
                    phase = ProjectsPhase.Ready,
                    askingPath = true,
                    openedBefore = persistentListOf(folder),
                    projects = persistentListOf(folder, FolderRowUi("/work/other", "other")),
                )
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    ProjectsPickerScreen(state, actions)
                }
            }
            onAllNodesWithTag("remote-folder-/work/app").assertCountEquals(1)
            onNodeWithTag("remote-folder-/work/app").performClick()
            assertEquals(listOf("finishPath" to listOf("/work/app")), calls)
        }

    @Test
    fun `B-ANDR-078 path validation holds the sheet and a valid path is trimmed`() =
        runComposeUiTest {
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    ProjectsPickerScreen(
                        ProjectsPickerUi(phase = ProjectsPhase.Ready, askingPath = true),
                        actions,
                    )
                }
            }
            val path = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("open-folder-path")))
            path.performTextInput("relative")
            path.performImeAction()
            assertEquals(emptyList(), calls)
            path.performTextReplacement(" /work/app ")
            path.performImeAction()
            assertEquals(listOf("finishPath" to listOf("/work/app")), calls)
        }

    @Test
    fun `B-ANDR-078 project creation submits the visible suggestion before starting the operation`() =
        runComposeUiTest {
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    ProjectsPickerScreen(
                        ProjectsPickerUi(
                            phase = ProjectsPhase.Ready,
                            askingCreate = true,
                            canCreate = true,
                            createName = "my-app",
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Enter a folder name.").assertDoesNotExist()
            onNodeWithText("Create").performClick()
            assertEquals(
                listOf(
                    "changeCreateName" to listOf("my-app"),
                    "createFolder" to emptyList(),
                ),
                calls,
            )
        }
}
