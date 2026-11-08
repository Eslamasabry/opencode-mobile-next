package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.DiagnosticsExport
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** P12-SETTINGS screens over the real presenter, found by visible text. */
@OptIn(ExperimentalTestApi::class)
class SettingsParityUiTest {
    private val choices = FakeChoices()
    private val openers = RecordingOpeners()
    private val copied = mutableListOf<String>()
    private val export =
        object : DiagnosticsExport {
            override suspend fun copy(text: String): Outcome<Unit> {
                copied += text
                return Outcome.Ok(Unit)
            }
        }
    private lateinit var presenter: SettingsPresenter

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.show(locale: String = "en") {
        val facts = MutableStateFlow(phoneFacts().copy(diagnostics = true, opens = setOf(SettingsOutside.Servers)))
        setContent {
            SettingsThemed(locale = locale) {
                val scope = rememberCoroutineScope()
                val p = remember { SettingsPresenter(scope, facts, choices, openers, diagnostics = export).also { presenter = it } }
                val state by p.state.collectAsState()
                SettingsScreen(state, p)
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.tap(text: String) {
        onNodeWithText(text).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `B-L10N-001 This app, Language lists each language in its own name and a tap stores it`() =
        runComposeUiTest {
            show()
            tap("This app")
            tap("Language")
            onNodeWithText("Use system language").assertExists()
            onNodeWithText("English").assertExists()
            tap("العربية")
            assertEquals(AppLanguage.Arabic, choices.value.value.language)
        }

    @Test
    fun `B-KIT-059 Appearance ends with Text size, Default until chosen, and Large applies`() =
        runComposeUiTest {
            show()
            presenter.onOpen(SettingsPage.Appearance); waitForIdle()
            // Flutter's text-size step slider (94d86c48f): asked as "How big should text be?", the slider reads
            // "Text size" with its step as its state, and a step is chosen by moving it.
            onNodeWithText("How big should text be?").performScrollTo().assertExists()
            onNodeWithContentDescription("Text size")
                .performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Default"))
                .performSemanticsAction(SemanticsActions.SetProgress) { it(AppTextSize.Large.ordinal.toFloat()) }
            waitForIdle()
            assertEquals(AppTextSize.Large, choices.value.value.textSize)
        }

    @Test
    fun `R1-010 Help, Report a problem is the one R1 page; the nine old controls are gone`() =
        runComposeUiTest {
            show()
            tap("Help")
            onNodeWithText("Send a problem report").assertDoesNotExist()
            tap("Report a problem")
            onNodeWithText("What happened?").assertExists()
            onNodeWithText("Optional").assertExists()
            onNodeWithText("What's included").assertExists()
            onNodeWithText("App and phone details. Passwords, keys and server addresses are taken out.").assertExists()
            onNodeWithText("Post publicly on GitHub").assertExists()
            for (gone in
                listOf(
                    "Share report", "Copy", "Send as a GitHub issue", "Everything this phone kept", "Details",
                    "Keep crash categories on this phone", "Preview crash report", "Clear saved evidence",
                    "Refresh exit history",
                )) {
                onAllNodesWithText(gone).assertCountEquals(0)
            }
        }

    @Test
    fun `02 3_7 rows the app has no page for yet are absent, not dead`() =
        runComposeUiTest {
            show()
            onNodeWithText("Agent").assertDoesNotExist()
            tap("Server")
            onNodeWithText("Servers").assertExists()
        }
}
