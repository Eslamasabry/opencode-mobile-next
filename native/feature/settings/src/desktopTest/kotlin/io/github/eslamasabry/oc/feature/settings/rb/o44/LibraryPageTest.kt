package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.LibraryScreen
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.*

@OptIn(ExperimentalTestApi::class)
class LibraryPageTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()

    private fun actions(): LibraryActions =
        Proxy.newProxyInstance(
            LibraryActions::class.java.classLoader,
            arrayOf(LibraryActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestActions"
                else -> {

                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as LibraryActions

    @Test
    fun `B-NAV-068 command description opens the command rather than dispatching it`() =
        runComposeUiTest {
            val command = CommandInfo("review", "Review the working tree", "plan", false)
            val state =
                LibraryUi(
                    tab = LibraryTab.Commands,
                    available = true,
                    inventory =
                        LibraryInventory(loading = false, commands = persistentListOf(command)),
                )
            val actions = actions()
            setContent { SettingsThemed { LibraryScreen(state, actions) } }
            onNodeWithText("Pick a command, then the conversation it runs in").assertExists()
            onNodeWithText("/review · Runs with plan").assertExists()
            onNodeWithTag("library-command-review").performClick()
            assertEquals(listOf("show" to listOf(LibrarySheet.Command(command))), calls)
        }

    @Test
    fun `B-NAV-068 reference picker adds the named reference in one tap`() = runComposeUiTest {
        val reference = ReferenceInfo("docs", "/project/docs", "Project documentation")
        val state =
            LibraryUi(
                tab = LibraryTab.References,
                available = true,
                ref = ConversationRef(ProfileId("laptop"), SessionId("conversation")),
                inventory =
                    LibraryInventory(loading = false, references = persistentListOf(reference)),
            )
        val actions = actions()
        setContent { SettingsThemed { LibraryScreen(state, actions) } }
        onNodeWithTag("library-reference-docs").performClick()
        assertEquals(listOf("addReference" to listOf(reference)), calls)
    }

    @Test
    fun `B-NAV-068 failed first load does not also say there are no skills`() = runComposeUiTest {
        val state =
            LibraryUi(
                tab = LibraryTab.Skills,
                available = true,
                inventory =
                    LibraryInventory(loading = false, failedSections = persistentSetOf("skills")),
            )
        val actions = actions()
        setContent { SettingsThemed { LibraryScreen(state, actions) } }
        onNodeWithText("No skills available").assertDoesNotExist()
        onNodeWithText("Couldn’t load skills").assertExists()
        onNodeWithText("Retry").performClick()
        assertEquals(listOf("refresh" to emptyList()), calls)
    }

    @Test
    fun `B-NAV-068 skill preview copies its named slash command`() = runComposeUiTest {
        val skill =
            SkillInfo(
                "focused-review",
                "focused-review",
                "Review a change for correctness",
                "/skills/review/SKILL.md",
                "# focused-review\n\nKeep the patch focused.",
                true,
            )
        val state =
            LibraryUi(
                tab = LibraryTab.Skills,
                available = true,
                inventory = LibraryInventory(loading = false, skills = persistentListOf(skill)),
                sheet = LibrarySheet.Skill(skill),
            )
        val actions = actions()
        setContent { SettingsThemed { LibraryScreen(state, actions) } }
        onNodeWithText("Review a change for correctness").assertExists()
        onNodeWithText("Copy /focused-review").performClick()
        assertEquals(listOf("copy" to listOf("/focused-review")), calls)
        onNodeWithText("Copy path").assertDoesNotExist()
    }

    @Test
    fun `B-NAV-068 reference details copy the mention without changing the target`() =
        runComposeUiTest {
            val reference =
                ReferenceInfo(
                    "platform-docs",
                    "/work/references/platform-docs",
                    "Android platform guidance",
                )
            val state =
                LibraryUi(
                    tab = LibraryTab.References,
                    available = true,
                    inventory =
                        LibraryInventory(loading = false, references = persistentListOf(reference)),
                    sheet = LibrarySheet.Reference(reference),
                )
            val actions = actions()
            setContent { SettingsThemed { LibraryScreen(state, actions) } }
            onNodeWithText(
                    "Write @platform-docs in a prompt and the agent reads this folder for that reply."
                )
                .assertExists()
            onNodeWithText("Copy @platform-docs").performClick()
            assertEquals(listOf("copy" to listOf("@platform-docs")), calls)
        }
}
