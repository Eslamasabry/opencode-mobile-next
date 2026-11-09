package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import io.github.eslamasabry.oc.engine.analytics.health.ProblemReportContent
import io.github.eslamasabry.oc.feature.settings.presentation.ProblemReportSender
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The errors and timings this phone kept stay reachable inside Report a problem (parity gap #4): Copy error log, Clear
 * errors behind its question, and the timing report, only when there is something kept.
 */
@OptIn(ExperimentalTestApi::class)
class ReportKeptUiTest {
    private val choices = FakeChoices()
    private val openers = RecordingOpeners()
    private lateinit var presenter: SettingsPresenter

    private class Kept(var errors: Int, var timings: String) : ProblemReportSender {
        override suspend fun build() = ProblemReportContent()

        override fun words(text: String) = text

        override val canSend = true

        override suspend fun send(text: String): String? = null

        override suspend fun reportCode(): String? = null

        override fun errorsKept() = errors

        override fun errorLogText() = if (errors > 0) "Error" else ""

        override suspend fun clearErrors(): Boolean {
            errors = 0
            return true
        }

        override fun timingReport() = timings

        override fun clearTimings() {
            timings = ""
        }
    }

    @BeforeTest fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun androidx.compose.ui.test.ComposeUiTest.show(kept: Kept) {
        val facts = MutableStateFlow(phoneFacts().copy(diagnostics = true))
        setContent {
            SettingsThemed(locale = "en") {
                val scope = rememberCoroutineScope()
                val p = remember { SettingsPresenter(scope, facts, choices, openers, problemReport = kept).also { presenter = it } }
                val state by p.state.collectAsState()
                SettingsScreen(state, p)
            }
        }
        waitForIdle()
        presenter.onOpen(SettingsPage.ReportProblem)
        waitForIdle()
    }

    @Test
    fun `B-DIAG-REPORT-004 Report a problem lists Copy error log and Clear errors, which asks first`() =
        runComposeUiTest {
            val kept = Kept(errors = 2, timings = "")
            show(kept)
            onNodeWithText("2 recent errors").performScrollTo().assertExists()
            onNodeWithText("Copy error log").assertExists()
            onNodeWithTag("report-clear-errors").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Clear 2 errors?").assertExists()
            assertEquals(2, kept.errors, "the question clears nothing")
            onNodeWithText("Clear 2 errors").performClick()
            waitForIdle()
            assertEquals(0, kept.errors)
            onAllNodesWithTag("report-clear-errors").assertCountEquals(0)
        }

    @Test
    fun `B-DIAG-REPORT-005 with nothing kept the page shows no extra lines`() =
        runComposeUiTest {
            show(Kept(errors = 0, timings = ""))
            onAllNodesWithTag("report-clear-errors").assertCountEquals(0)
            onAllNodesWithTag("report-copy-timings").assertCountEquals(0)
            onAllNodesWithTag("report-copy-errors").assertCountEquals(0)
        }

    @Test
    fun `B-DIAG-REPORT-006 the timing report can be copied and cleared from the page`() =
        runComposeUiTest {
            val kept = Kept(errors = 0, timings = "connect 12 ms")
            show(kept)
            onNodeWithTag("report-copy-timings").performScrollTo().assertExists()
            onNodeWithTag("report-clear-timings").performScrollTo().performClick()
            waitForIdle()
            assertEquals("", kept.timings)
            onAllNodesWithTag("report-copy-timings").assertCountEquals(0)
        }
}
