package io.github.eslamasabry.oc.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.projects.presentation.OpenFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectActions
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectStep
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenRecentUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenServerStatus
import io.github.eslamasabry.oc.feature.projects.presentation.OpenServerUi
import io.github.eslamasabry.oc.feature.projects.ui.OpenProjectScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.lang.reflect.Proxy
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * B-182 Open a project, by visible text, in Flutter's golden frame (412 x 915, dark): each test also writes its render
 * to build/s27-open-project/ for the side-by-side with test/goldens/folder_browser_*_dark.png.
 */
@OptIn(ExperimentalTestApi::class)
class OpenProjectScreenTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(OpenProjectActions::class.java.classLoader, arrayOf(OpenProjectActions::class.java)) {
            proxy,
            method,
            args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "OpenProjectActions"
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as OpenProjectActions

    private val out = File("build/s27-open-project").also { it.mkdirs() }

    private fun shoot(name: String, state: OpenProjectUi, advanceMs: Long = 1_000, check: ComposeUiTest.() -> Unit) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        // The same page in the light appearance too (parity-1109 renders); the checks run on the dark frame.
        runDesktopComposeUiTest(412, 915) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    Frame(AppearanceMode.Light) { OpenProjectScreen(state, actions) }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(advanceMs)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name-light.png"))
        }
        runDesktopComposeUiTest(412, 915) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    Frame(AppearanceMode.Dark) { OpenProjectScreen(state, actions) }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(advanceMs)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name-dark.png"))
            check()
        }
    }

    @Composable
    private fun Frame(mode: AppearanceMode, content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
            platform = DesktopKitPlatform(),
            localeTag = "en",
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
            }
        }
    }

    private val pc = ProfileId("pc")
    private val projects =
        listOf(
            OpenFolderUi("demo", "/root/projects/demo", known = true),
            OpenFolderUi("landing-page", "/root/projects/landing-page", git = true, known = true),
            OpenFolderUi("notes", "/root/projects/notes"),
            OpenFolderUi("opencode-mobile", "/root/projects/opencode-mobile", git = true, known = true),
            OpenFolderUi("scratch", "/root/projects/scratch"),
        )
    private val projectsPage =
        OpenProjectUi(
            step = OpenProjectStep.Projects,
            serversLoading = false,
            serverName = "This phone",
            onThisPhone = true,
            folders = projects.toImmutableList(),
            makeIn = "/root/projects",
            canMake = true,
            canClone = true,
        )
    private val inside =
        projectsPage.copy(
            step = OpenProjectStep.Folder,
            path = "/root/projects/opencode-mobile",
            makeIn = "/root/projects/opencode-mobile",
            folders =
                listOf("android", "assets", "docs", "lib", "packages", "test")
                    .map { OpenFolderUi(it, "/root/projects/opencode-mobile/$it") }
                    .toImmutableList(),
        )

    @Test
    fun `B-182 Where is the project names each server with its state, and a tap opens its projects`() =
        shoot(
            "open_project_servers",
            OpenProjectUi(
                serversLoading = false,
                servers =
                    persistentListOf(
                        OpenServerUi(ProfileId("phone"), "This phone", true, OpenServerStatus.Connected),
                        OpenServerUi(pc, "studio-pc", false, OpenServerStatus.Connecting),
                        OpenServerUi(ProfileId("vps"), "vps", false, OpenServerStatus.NotConnected),
                    ),
            ),
        ) {
            onNodeWithText("Where is the project?").assertExists()
            onNodeWithText("Connected").assertExists()
            onNodeWithText("Connecting").assertExists()
            onNodeWithText("Not connected").assertExists()
            onNodeWithTag("open-server-pc").performClick()
            // ProfileId is a value class: the proxy sees the mangled method and the raw id.
            assertEquals(listOf("chooseServer" to listOf<Any?>("pc")), calls.map { (m, a) -> m.substringBefore('-') to a })
        }

    @Test
    fun `WALK-1108-P1 Where is the project lists the projects opened before above the servers`() =
        shoot(
            "open_project_recent",
            OpenProjectUi(
                serversLoading = false,
                servers = persistentListOf(OpenServerUi(pc, "studio-pc", false, OpenServerStatus.Connected)),
                recent =
                    persistentListOf(
                        OpenRecentUi(pc, "app", "/srv/app"),
                        OpenRecentUi(pc, "old", "/srv/old"),
                    ),
            ),
        ) {
            onNodeWithText("Opened before").assertExists()
            val recentTop = onNodeWithText("app").fetchSemanticsNode().boundsInRoot.top
            val serverTop = onNodeWithText("studio-pc").fetchSemanticsNode().boundsInRoot.top
            kotlin.test.assertTrue(recentTop < serverTop, "recent projects come first")
            onNodeWithTag("open-recent-pc-/srv/app").performClick()
            assertEquals(
                listOf("openRecent" to listOf<Any?>("pc", "/srv/app")),
                calls.map { (m, a) -> m.substringBefore('-') to a },
            )
        }

    @Test
    fun `B-182 the projects page marks Git repositories and OpenCode projects and keeps the other ways in view`() =
        shoot("open_project_projects", projectsPage) {
            onNodeWithText("Projects on This phone").assertExists()
            onNodeWithText("Git repository · /root/projects/landing-page").assertExists()
            onNodeWithText("OpenCode project · /root/projects/demo").assertExists()
            onNodeWithText("New folder").assertExists()
            onNodeWithText("Clone a repository").assertExists()
            onNodeWithText("Type a path").assertExists()
            onNodeWithTag("open-folder-/root/projects/notes").performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("enterFolder" to listOf("/root/projects/notes")), calls)
        }

    @Test
    fun `B-182 inside a folder its folders are listed with Open name as the primary, and Back goes up`() =
        shoot("open_project_inside", inside) {
            onNodeWithText("Open opencode-mobile").performClick()
            onNodeWithTag("open-folder-/root/projects/opencode-mobile/lib").performClick()
            onNodeWithContentDescription("Up one folder").performClick()
            assertEquals(
                listOf<Pair<String, List<Any?>>>(
                    "openShown" to emptyList(),
                    "enterFolder" to listOf("/root/projects/opencode-mobile/lib"),
                    "back" to emptyList(),
                ),
                calls,
            )
        }

    @Test
    fun `B-182 inside a folder on this phone Show hidden folders is offered, and says Hide once on`() {
        shoot("open_project_inside_hidden_off", inside.copy(canShowHidden = true)) {
            onNodeWithContentDescription("Show hidden folders").performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("toggleHidden" to emptyList()), calls)
        }
        shoot("open_project_inside_hidden_on", inside.copy(canShowHidden = true, showHidden = true)) {
            onNodeWithContentDescription("Hide hidden folders").assertExists()
        }
        shoot("open_project_inside_no_hidden", inside) {
            onNodeWithContentDescription("Show hidden folders").assertDoesNotExist()
        }
    }

    @Test
    fun `B-182 loading shows placeholder rows`() =
        shoot("open_project_loading", inside.copy(folders = persistentListOf(), loading = true)) {
            onNodeWithText("Still reading this folder").assertDoesNotExist()
        }

    @Test
    fun `B-182 a slow listing says it is still reading`() =
        shoot("open_project_slow", inside.copy(folders = persistentListOf(), loading = true, slow = true)) {
            onNodeWithText("Still reading this folder").assertExists()
        }

    @Test
    fun `B-182 an empty project space offers its first steps`() =
        shoot("open_project_empty", projectsPage.copy(folders = persistentListOf())) {
            onNodeWithText("No projects yet").assertExists()
            onNodeWithText("New folder").performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("askNewFolder" to emptyList()), calls)
        }

    @Test
    fun `B-182 an error says why in plain words with Try again, and no Open`() =
        shoot(
            "open_project_error",
            inside.copy(folders = persistentListOf(), failure = Failure.Folder(FolderProblem.Denied)),
        ) {
            onNodeWithText("This folder can’t be shown").assertExists()
            onNodeWithText("The app isn’t allowed to read it.").assertExists()
            onNodeWithText("Open opencode-mobile").assertDoesNotExist()
            onNodeWithText("Try again").performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("retry" to emptyList()), calls)
        }
}
