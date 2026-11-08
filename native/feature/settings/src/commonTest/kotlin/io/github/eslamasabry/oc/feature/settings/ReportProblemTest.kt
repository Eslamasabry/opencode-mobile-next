package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.analytics.health.ProblemReportContent
import io.github.eslamasabry.oc.engine.analytics.health.ReportClose
import io.github.eslamasabry.oc.engine.analytics.health.ReportConnection
import io.github.eslamasabry.oc.engine.analytics.health.ReportProblem
import io.github.eslamasabry.oc.engine.analytics.health.ReportRedaction
import io.github.eslamasabry.oc.feature.settings.presentation.DiagnosticsExport
import io.github.eslamasabry.oc.feature.settings.presentation.HealthAnswers
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentSetting
import io.github.eslamasabry.oc.feature.settings.presentation.ProblemReportSender
import io.github.eslamasabry.oc.feature.settings.presentation.ReportStage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * R1 Report a problem (owner's pick, 2026-11-09): one flow for the old Report a problem and B-190's Send a problem
 * report. Help has one row; opening the page and typing send nothing; Send report sends exactly the redacted text
 * "What's included" lists, once, and shows its code; a build without the vendor never sends, and Copy text leads;
 * GitHub opens only after the public question.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportProblemTest {
    private class FakeSender(var answer: String? = "abcdef01-1", override val canSend: Boolean = true) :
        ProblemReportSender {
        val sent = mutableListOf<String>()
        private val redaction = ReportRedaction(Redactor())

        override suspend fun build(): ProblemReportContent =
            ProblemReportContent(
                lastClose = ReportClose("crash", 1_000),
                appVersion = "3.0.0",
                appBuild = "57",
                android = "15",
                abi = "arm64",
                ramRange = "8-12",
                connection = ReportConnection("OpenCode", "2.1.1"),
                problems = listOf(ReportProblem("timeout", 2)),
                madeMs = 60_000,
            )

        override fun words(text: String): String = redaction.redact(text)

        override suspend fun send(text: String): String? {
            sent += text
            return answer
        }

        override suspend fun reportCode(): String? = "abcdef01-0"
    }

    private class GitHub : DiagnosticsExport {
        val opened = mutableListOf<String>()
        var works = true

        override suspend fun send(description: String): Outcome<Unit> {
            opened += description
            return if (works) Outcome.Ok(Unit)
            else Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Device())
        }

        override suspend fun copy(text: String): Outcome<Unit> = Outcome.Ok(Unit)
    }

    private fun TestScope.presenter(sender: FakeSender?, github: GitHub = GitHub(), available: Boolean = true) =
        SettingsPresenter(
            scope = backgroundScope,
            facts = MutableStateFlow(phoneFacts().copy(diagnostics = true)),
            choices = FakeChoices(),
            openers = RecordingOpeners(),
            diagnostics = github,
            healthConsent =
                object : HealthConsentSetting {
                    override val available: Boolean = available

                    override fun read() = HealthAnswers()

                    override suspend fun write(answers: HealthAnswers) = true
                },
            problemReport = sender,
        ).also { runCurrent() }

    @Test
    fun `R1-001 Help has one Report a problem row, the separate Send a problem report row is gone`() {
        val help = SettingsStructure.rows(SettingsGroupId.Help, phoneFacts().copy(diagnostics = true), false, false)
        assertEquals(
            listOf(
                SettingsRowId.Guide, SettingsRowId.Tips, SettingsRowId.Capabilities, SettingsRowId.ReportProblem,
                SettingsRowId.About,
            ),
            help.map { it.id },
        )
        assertEquals(SettingsDoor.Page(SettingsPage.ReportProblem), help.single { it.id == SettingsRowId.ReportProblem }.door)
        assertTrue(SettingsRowId.entries.none { it.tag == "settings-send-problem-report" })
    }

    @Test
    fun `R1-002 opening, typing and opening What's included send nothing`() = runTest {
        val sender = FakeSender()
        val p = presenter(sender)
        p.onOpen(SettingsPage.ReportProblem); runCurrent()
        val report = p.state.value.report!!
        assertTrue(report.canSend)
        assertEquals(ReportStage.Writing, report.stage)
        assertEquals("3.0.0", report.content!!.appVersion)
        p.onReportDescription("It closed when I sent a photo"); runCurrent()
        p.onShowIncluded(true); runCurrent()
        assertTrue(p.state.value.report!!.showIncluded)
        p.onShowIncluded(false); runCurrent()
        p.onBack(); runCurrent()
        assertTrue(sender.sent.isEmpty(), "only Send report sends")
        assertNull(p.state.value.report, "leaving drops the report")
    }

    @Test
    fun `R1-003 Send report sends exactly the redacted text What's included lists, once, and shows the code`() = runTest {
        val sender = FakeSender()
        val p = presenter(sender)
        p.onOpen(SettingsPage.ReportProblem); runCurrent()
        p.onReportDescription("Sent a photo to https://admin:hunter22@my-box.tail1234.ts.net:4096 and it closed"); runCurrent()
        val listed = p.state.value.report!!.content!!
        assertFalse("hunter22" in listed.words || "my-box" in listed.words, listed.words)
        assertTrue("and it closed" in listed.words)
        p.onSendProblemReport(); runCurrent()
        assertEquals(listOf(listed.text), sender.sent, "the listed text, nothing else")
        assertFalse(sender.sent.single().contains("hunter22"))
        assertEquals(ReportStage.Sent("abcdef01-1"), p.state.value.report!!.stage)
        assertEquals("abcdef01-1", p.state.value.reportCode)
        p.onSendProblemReport(); runCurrent()
        assertEquals(1, sender.sent.size, "a sent report is not sent again")
    }

    @Test
    fun `R1-004 a failed send says so, and Send report tries the same report again`() = runTest {
        val sender = FakeSender(answer = null)
        val p = presenter(sender)
        p.onOpen(SettingsPage.ReportProblem); runCurrent()
        p.onSendProblemReport(); runCurrent()
        assertEquals(ReportStage.Failed, p.state.value.report!!.stage)
        sender.answer = "abcdef01-2"
        p.onSendProblemReport(); runCurrent()
        assertEquals(ReportStage.Sent("abcdef01-2"), p.state.value.report!!.stage)
        assertEquals(2, sender.sent.size)
        assertEquals(sender.sent[0], sender.sent[1])
    }

    @Test
    fun `R1-005 a build without the vendor never sends, the report is still there to copy`() = runTest {
        val sender = FakeSender(canSend = false)
        val p = presenter(sender, available = false)
        p.onOpen(SettingsPage.ReportProblem); runCurrent()
        val report = p.state.value.report!!
        assertFalse(report.canSend)
        assertTrue(report.content != null, "Copy text has the report")
        p.onSendProblemReport(); runCurrent()
        assertTrue(sender.sent.isEmpty())
        assertEquals(ReportStage.Writing, p.state.value.report!!.stage)
        p.onOpen(SettingsPage.About); runCurrent()
    }

    @Test
    fun `R1-006 Post publicly on GitHub asks first, only yes opens the form with the words`() = runTest {
        val github = GitHub()
        val p = presenter(FakeSender(), github)
        p.onOpen(SettingsPage.ReportProblem); runCurrent()
        p.onReportDescription("the app closed"); runCurrent()
        p.onAskPostPublicly(); runCurrent()
        assertTrue(p.state.value.report!!.confirmPublic)
        assertTrue(github.opened.isEmpty(), "asking opens nothing")
        p.onPostPublicly(false); runCurrent()
        assertFalse(p.state.value.report!!.confirmPublic)
        assertTrue(github.opened.isEmpty())
        p.onAskPostPublicly(); runCurrent()
        github.works = false
        p.onPostPublicly(true); runCurrent()
        assertEquals(listOf("the app closed"), github.opened)
        assertEquals(SettingsNote.GitHubFailed, p.state.value.note)
    }

    @Test
    fun `B-190-054 About shows the report code, and nothing without a vendor`() = runTest {
        val p = presenter(FakeSender())
        p.onOpen(SettingsPage.About); runCurrent()
        assertEquals("abcdef01-0", p.state.value.reportCode)
        val none = presenter(null, available = false)
        none.onOpen(SettingsPage.About); runCurrent()
        assertNull(none.state.value.reportCode)
    }

    @Test
    fun `B-180-055 What we collect says nothing is sent only in a build without the vendor`() = runTest {
        val p = presenter(FakeSender())
        p.onOpen(SettingsPage.Privacy); runCurrent()
        assertTrue(p.state.value.privacy!!.vendorSends)
        val none = presenter(null, available = false)
        none.onOpen(SettingsPage.Privacy); runCurrent()
        assertNull(none.state.value.privacy, "no vendor, no switches")
    }
}
