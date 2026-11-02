package io.github.eslamasabry.oc.feature.prompts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandItem
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandsActions
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandsUi
import io.github.eslamasabry.oc.feature.prompts.presentation.ListPhase
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptItem
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsActions
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsIssue
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsUi
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskRules
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksActions
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksUi
import io.github.eslamasabry.oc.feature.prompts.ui.CommandsScreen
import io.github.eslamasabry.oc.feature.prompts.ui.SavedPromptsScreen
import io.github.eslamasabry.oc.feature.prompts.ui.TasksScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** The real prompts screens, found by their visible words. */
@OptIn(ExperimentalTestApi::class)
class PromptsScreensTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class Taps : SavedPromptsActions, CommandsActions, TasksActions {
        val log = mutableListOf<String>()

        override fun back() {
            log += "back"
        }

        override fun retry() {
            log += "retry"
        }

        override fun search(query: String) {}

        override fun newPrompt() {
            log += "new"
        }

        override fun editPrompt(id: String) {
            log += "edit:$id"
        }

        override fun cancelEdit() {}

        override fun submitEdit(text: String) {
            log += "submit:$text"
        }

        override fun insert(id: String) {
            log += "insert:$id"
        }

        override fun delete(id: String) {
            log += "delete:$id"
        }

        override fun undo() {
            log += "undo"
        }

        override fun undoTimedOut() {}

        override fun openCommands() {
            log += "commands"
        }

        override fun openTasks() {
            log += "tasks"
        }

        override fun choose(name: String) {
            log += "choose:$name"
        }

        override fun toggleUnfinished() {
            log += "unfinished"
        }
    }

    @Composable
    private fun Frame(locale: String = "en", content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) { content() }
            }
        }
    }

    private val prompts =
        persistentListOf(
            SavedPromptItem("b", "Review the open pull request", 2),
            SavedPromptItem("a", "Explain the failing test", 1, attachments = 2),
        )

    @Test
    fun `B-STATE-140 saved prompts list by their words, restore and delete act on that prompt`() = runComposeUiTest {
        val taps = Taps()
        setContent { Frame { SavedPromptsScreen(SavedPromptsUi(loading = false, prompts = prompts), taps) } }
        onNodeWithText("Saved prompts").assertExists()
        onNodeWithText("Review the open pull request").assertExists()
        onNodeWithText("2 attachments").assertExists()
        onNodeWithText("Explain the failing test").performClick()
        onNodeWithTag("prompt-delete-b").performClick()
        onNodeWithText("Save prompt for later").performClick()
        onNodeWithText("Slash commands and agents").performClick()
        onNodeWithText("Tasks").performClick()
        assertEquals(listOf("insert:a", "delete:b", "new", "commands", "tasks"), taps.log)
    }

    @Test
    fun `B-STATE-085 a deleted prompt offers Undo`() = runComposeUiTest {
        val taps = Taps()
        setContent {
            Frame {
                SavedPromptsScreen(
                    SavedPromptsUi(
                        loading = false,
                        prompts = persistentListOf(prompts.last()),
                        undo = io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsUndo("b"),
                    ),
                    taps,
                )
            }
        }
        onNodeWithText("Saved prompt deleted").assertExists()
        onNodeWithText("Undo").performClick()
        assertEquals(listOf("undo"), taps.log)
    }

    @Test
    fun `B-CHAT-088 empty, full and unreadable shelves say so in plain words`() = runComposeUiTest {
        val taps = Taps()
        var ui by mutableStateOf(SavedPromptsUi(loading = false))
        setContent { Frame { SavedPromptsScreen(ui, taps) } }
        onNodeWithText("No saved prompts yet").assertExists()
        ui = SavedPromptsUi(loading = false, issue = SavedPromptsIssue.ReadFailed)
        waitForIdle()
        onNodeWithText("Could not read saved prompts. Their stored data has been kept.").assertExists()
        onNodeWithText("No saved prompts yet").assertDoesNotExist()
    }

    @Test
    fun `B-STATE-140 the Arabic page reads in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Frame("ar") { SavedPromptsScreen(SavedPromptsUi(loading = false, prompts = prompts), Taps()) } }
        onNodeWithText("Saved prompts").assertDoesNotExist()
        onNodeWithText("Review the open pull request").assertExists()
    }

    @Test
    fun `B-NAV-051 commands list with slash names and a server without them says so`() = runComposeUiTest {
        val taps = Taps()
        val ui = CommandsUi(ListPhase.Shown(listOf(CommandItem("review", "Review changes")), "Home"))
        setContent { Frame { CommandsScreen(ui, taps) } }
        onNodeWithText("/review").performClick()
        onNodeWithText("Review changes").assertExists()
        assertEquals(listOf("choose:review"), taps.log)
    }

    @Test
    fun `B-NAV-051 a server without slash commands names itself`() = runComposeUiTest {
        setContent { Frame { CommandsScreen(CommandsUi(ListPhase.NotOnServer("Home")), Taps()) } }
        onNodeWithText("Slash commands and agents isn't available on Home").assertExists()
    }

    @Test
    fun `B-CHAT-065 tasks show progress, states and the unfinished filter`() = runComposeUiTest {
        val taps = Taps()
        val tasks =
            listOf(
                TaskRules.item("Write tests", "completed", "high"),
                TaskRules.item("Fix build", "in_progress", null),
                TaskRules.item("Old idea", "cancelled", null),
            )
        setContent { Frame { TasksScreen(TasksUi(ListPhase.Shown(tasks, "Home")), taps) } }
        onNodeWithText("1 of 2 done").assertExists()
        onNodeWithText("Write tests").assertExists()
        onNodeWithText("Completed · High priority").assertExists()
        onNodeWithText("Show unfinished only").performClick()
        assertEquals(listOf("unfinished"), taps.log)
    }
}
