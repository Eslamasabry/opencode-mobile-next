package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
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
            override suspend fun report(): Outcome<String> = Outcome.Ok("OpenCode Mobile 3.0.0+10002\nServers: 1 saved · 1 connected")

            override suspend fun copy(text: String): Outcome<Unit> {
                copied += text
                return Outcome.Ok(Unit)
            }

            override suspend fun share(text: String): Outcome<Unit> = Outcome.Ok(Unit)
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
            onNodeWithText("Text size").performScrollTo().assertExists()
            onNodeWithText("Default").assertExists()
            tap("Large")
            assertEquals(AppTextSize.Large, choices.value.value.textSize)
        }

    @Test
    fun `B-SEC-060 Help, Report a problem previews the report and Copy hands over exactly it`() =
        runComposeUiTest {
            show()
            tap("Help")
            tap("Report a problem")
            onNodeWithText("Passwords, keys and server addresses are taken out.").assertExists()
            onNodeWithText("Share report").assertExists()
            tap("Copy")
            assertEquals(listOf("OpenCode Mobile 3.0.0+10002\nServers: 1 saved · 1 connected"), copied)
            onNodeWithText("Report copied").assertExists()
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
