package io.github.eslamasabry.oc.feature.projects.rb.o47

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
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
class PhoneFolderSheetTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(
            ProjectsPickerActions::class.java.classLoader,
            arrayOf(ProjectsPickerActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PhoneFolderActions"
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as ProjectsPickerActions

    @Composable
    private fun Page(state: ProjectsPickerUi) {
        KitTheme(
            KitAppearance(motion = MotionLevel.Off),
            DesktopKitPlatform(),
            localeTag = "en",
            stillFrame = true,
        ) {
            ProjectsPickerScreen(state, actions)
        }
    }

    @Test
    fun `B-ANDR-078 phone folder row and header retain their exact paths`() = runComposeUiTest {
        setContent {
            Page(
                ProjectsPickerUi(
                    phase = ProjectsPhase.Ready,
                    phonePath = "/storage/emulated/0/app",
                    phoneParent = "/storage/emulated/0",
                    phoneFolders =
                        persistentListOf(
                            PhoneFolderUi("src", "/storage/emulated/0/app/src", "main.kt")
                        ),
                )
            )
        }
        onNodeWithText("Open app").assertExists()
        onNodeWithTag("phone-folder-/storage/emulated/0/app/src").performClick()
        onNodeWithContentDescription("Up one folder").performClick()
        assertEquals(
            listOf(
                "enterPhoneFolder" to listOf("/storage/emulated/0/app/src"),
                "enterPhoneFolder" to listOf("/storage/emulated/0"),
            ),
            calls,
        )
    }

    @Test
    fun `B-ANDR-051 storage root has no open action and explains an empty listing`() =
        runComposeUiTest {
            setContent {
                Page(
                    ProjectsPickerUi(phase = ProjectsPhase.Ready, phonePath = "/storage/emulated/0")
                )
            }
            onNodeWithText("Internal storage").assertExists()
            onNodeWithText("No folders in here").assertExists()
            onNodeWithText("Open 0").assertDoesNotExist()
            onNode(hasContentDescription("Back") and hasAnyAncestor(hasTestTag("kit-sheet")))
                .performClick()
            assertEquals(listOf("closePhoneFolders" to emptyList<Any?>()), calls)
        }

    @Test
    fun `B-ANDR-078 failed folder shows specific words and retries only the named folder`() =
        runComposeUiTest {
            setContent {
                Page(
                    ProjectsPickerUi(
                        phase = ProjectsPhase.Ready,
                        phonePath = "/work/app",
                        phoneParent = "/work",
                        browsingBuiltin = true,
                        phoneFailure = Failure.Folder(FolderProblem.Missing),
                    )
                )
            }
            onNodeWithText("This folder can’t be shown").assertExists()
            onNodeWithText("It isn’t there any more.").assertExists()
            onNodeWithText("Open app").assertDoesNotExist()
            onNodeWithText("Try again").performClick()
            assertEquals(listOf("enterPhoneFolder" to listOf("/work/app")), calls)
        }
}
