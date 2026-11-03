package io.github.eslamasabry.oc.feature.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.feature.review.presentation.ChangeRowUi
import io.github.eslamasabry.oc.feature.review.presentation.ChangesActions
import io.github.eslamasabry.oc.feature.review.presentation.ChangesUi
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLoad
import io.github.eslamasabry.oc.feature.review.presentation.StagedActions
import io.github.eslamasabry.oc.feature.review.presentation.StagedConfirm
import io.github.eslamasabry.oc.feature.review.presentation.StagedPhase
import io.github.eslamasabry.oc.feature.review.presentation.StagedUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoAllUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereActions
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereUi
import io.github.eslamasabry.oc.feature.review.ui.ChangesScreen
import io.github.eslamasabry.oc.feature.review.ui.StagedScreen
import io.github.eslamasabry.oc.feature.review.ui.UndoFromHereScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/**
 * The real review screens, found by the words a person sees (plan 14: tests assert behaviour, not widgets).
 * File-name rows are found by their spoken name: the kit draws a file name with a zero-width break before the dot
 * (`titleIsFileName`), so its drawn text is not the plain name.
 */
@OptIn(ExperimentalTestApi::class)
class ReviewScreensTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previous)

    @Composable
    private fun Themed(locale: String = "en", content: @Composable () -> Unit) {
        KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = locale, stillFrame = true) {
            Box(Modifier.size(412.dp, 900.dp)) { content() }
        }
    }

    private class Changes : ChangesActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun retry() { calls += "retry" }

        override fun openFile(path: String) { calls += "file $path" }

        override fun keepAll() { calls += "keep" }

        override fun undoAll() { calls += "undo" }

        override fun confirmUndoAll() { calls += "confirm" }

        override fun dismissConfirm() { calls += "dismiss" }

        override fun openStaged() { calls += "staged" }
    }

    private val files =
        persistentListOf(
            ChangeRowUi("src/app/Main.kt", "Main.kt", "src/app", 3, 1, hasDiff = true),
            ChangeRowUi("README.md", "README.md", null, null, null, hasDiff = false),
        )

    @Test
    fun `B-CHAT-068 a turn's changed files show by name with Looks good and Undo all`() = runComposeUiTest {
        val actions = Changes()
        setContent { Themed { ChangesScreen(ChangesUi(turn = true, load = ReviewLoad.Ready, files = files, undoAll = UndoAllUi.Offered), actions) } }
        onNodeWithText("Changes in this turn").assertIsDisplayed()
        onNodeWithContentDescription("Main.kt").assertIsDisplayed()
        onNodeWithContentDescription("README.md").assertIsDisplayed()
        onNodeWithContentDescription("Main.kt").performClick()
        onNodeWithText("Undo all").performClick()
        onNodeWithText("Looks good").performClick()
        assertEquals(listOf("file src/app/Main.kt", "undo", "keep"), actions.calls)
        // Per-file undo is stated plainly, never offered as a dead control.
        onNodeWithText("Undoing one file at a time isn't available yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `B-CHAT-105 Undo all asks first, naming how many files go back`() = runComposeUiTest {
        val actions = Changes()
        setContent { Themed { ChangesScreen(ChangesUi(turn = false, load = ReviewLoad.Ready, files = files, undoAll = UndoAllUi.Offered, confirming = true), actions) } }
        onNodeWithText("Undo 2 changed files?").assertIsDisplayed()
    }

    @Test
    fun `B-CHAT-120 a server without staged undo gets no Undo all and says why`() = runComposeUiTest {
        setContent { Themed { ChangesScreen(ChangesUi(turn = false, load = ReviewLoad.Ready, files = files, undoAll = UndoAllUi.Unavailable), Changes()) } }
        onNodeWithText("This server can't undo from a prompt.").assertIsDisplayed()
        assertEquals(0, onAllNodesCount("Undo all"))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    private class Undo : UndoFromHereActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun putFilesBack(apply: Boolean) { calls += "files $apply" }

        override fun confirm() { calls += "confirm" }
    }

    @Test
    fun `B-CHAT-120 undo from here quotes the prompt and offers putting files back`() = runComposeUiTest {
        val actions = Undo()
        setContent { Themed { UndoFromHereScreen(UndoFromHereUi(load = ReviewLoad.Ready, staged = true, prompt = "make it blue"), actions) } }
        onNodeWithText("Undo from this prompt?").assertIsDisplayed()
        onNodeWithText("make it blue").assertIsDisplayed()
        onNodeWithText("Put files back too").assertIsDisplayed()
        onNodeWithText("Undo and review").performClick()
        assertEquals(listOf("confirm"), actions.calls)
    }

    @Test
    fun `B-CHAT-120 one-step servers list the edited files and undo now`() = runComposeUiTest {
        setContent {
            Themed {
                UndoFromHereScreen(UndoFromHereUi(load = ReviewLoad.Ready, staged = false, prompt = "go", messagesAfter = 2, edited = persistentListOf("lib/a.dart")), Undo())
            }
        }
        onNodeWithText("Files the agent edited after it").assertIsDisplayed()
        onNodeWithContentDescription("a.dart").assertIsDisplayed()
        onNodeWithText("Undo now").assertIsDisplayed()
    }

    private class Staged : StagedActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun retry() { calls += "retry" }

        override fun openFile(path: String) { calls += "file $path" }

        override fun reviewLatest() { calls += "latest" }

        override fun askKeep() { calls += "keep" }

        override fun askPutBack() { calls += "putBack" }

        override fun confirm() { calls += "confirm" }

        override fun dismissConfirm() { calls += "dismiss" }
    }

    @Test
    fun `B-CHAT-103 the staged review shows the exact count, its files and both decisions`() = runComposeUiTest {
        val actions = Staged()
        setContent {
            Themed {
                StagedScreen(
                    StagedUi(phase = StagedPhase.Review, prompt = "make it blue", messagesAfter = 3, files = persistentListOf(FileDiff("src/a.kt", additions = 2, deletions = 1))),
                    actions,
                )
            }
        }
        onNodeWithText("This prompt and the 3 messages after it are hidden", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("a.kt").performClick()
        onNodeWithText("Put everything back").performClick()
        onNodeWithText("Delete the hidden messages").performClick()
        assertEquals(listOf("file src/a.kt", "putBack", "keep"), actions.calls)
    }

    @Test
    fun `B-CHAT-105 keeping the undo asks first and names what is lost`() = runComposeUiTest {
        setContent { Themed { StagedScreen(StagedUi(phase = StagedPhase.Review, title = "Blue app", messagesAfter = 1, confirm = StagedConfirm.Keep), Staged()) } }
        onNodeWithText("Delete messages in “Blue app”?").assertIsDisplayed()
        onNodeWithText("Files stay as they are now").assertIsDisplayed()
    }

    @Test
    fun `B-CHAT-105 a stale review says so and offers the latest state`() = runComposeUiTest {
        val actions = Staged()
        setContent { Themed { StagedScreen(StagedUi(phase = StagedPhase.Stale), actions) } }
        onNodeWithText("The undo changed").assertIsDisplayed()
        onNodeWithText("Review latest state").performClick()
        assertEquals(listOf("latest"), actions.calls)
    }

    @Test
    fun `B-L10N-010 the review reads in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Themed("ar") { ChangesScreen(ChangesUi(turn = true, load = ReviewLoad.Ready, files = files, undoAll = UndoAllUi.Offered), Changes()) } }
        onNodeWithText("تغييرات هذه الجولة").assertIsDisplayed()
        onNodeWithText("تراجع عن الكل").assertIsDisplayed()
    }

}
