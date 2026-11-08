package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.HealthAnswers
import io.github.eslamasabry.oc.feature.settings.presentation.HealthConsentSetting
import io.github.eslamasabry.oc.feature.settings.presentation.ProblemReportSender
import io.github.eslamasabry.oc.feature.settings.presentation.SendReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearch
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * B-190: Settings › Help › Send a problem report (Flutter 2.1.5 section E, and the Help-row fix ed13c31dd): the row
 * sits between Report a problem and About only where the build can send; opening the page sends nothing; only Send
 * sends exactly the previewed text, once; the code then shows, and About shows it too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendProblemReportTest {
    private class FakeSender(var answer: String? = "abcdef01-1") : ProblemReportSender {
        val sent = mutableListOf<String>()
        var builds = 0

        override suspend fun build(): String {
            builds++
            return "OpenCode Mobile problem report\n  build_kind=sideload\n  [path] [url]"
        }

        override suspend fun send(text: String): String? {
            sent += text
            return answer
        }

        override suspend fun reportCode(): String? = "abcdef01-0"
    }

    private fun TestScope.presenter(sender: FakeSender?, available: Boolean = true) =
        SettingsPresenter(
            scope = backgroundScope,
            facts = MutableStateFlow(phoneFacts().copy(problemReport = sender != null)),
            choices = FakeChoices(),
            openers = RecordingOpeners(),
            healthConsent =
                object : HealthConsentSetting {
                    override val available: Boolean = available

                    override fun read() = HealthAnswers()

                    override suspend fun write(answers: HealthAnswers) = true
                },
            problemReport = sender,
        ).also { runCurrent() }

    @Test
    fun `B-190-050 the Help row sits between Report a problem and About, only in a build that can send`() {
        fun help(problemReport: Boolean) =
            SettingsStructure.rows(SettingsGroupId.Help, phoneFacts().copy(problemReport = problemReport), false, false)
                .map { it.id }
        assertEquals(
            listOf(
                SettingsRowId.Guide, SettingsRowId.Tips, SettingsRowId.Capabilities, SettingsRowId.ReportProblem,
                SettingsRowId.SendProblemReport, SettingsRowId.About,
            ),
            help(problemReport = true),
        )
        assertTrue(SettingsRowId.SendProblemReport !in help(problemReport = false))
        assertEquals("settings-send-problem-report", SettingsRowId.SendProblemReport.tag)
        assertEquals(
            SettingsDoor.Page(SettingsPage.SendProblemReport),
            SettingsStructure.rows(SettingsGroupId.Help, phoneFacts().copy(problemReport = true), false, false)
                .single { it.id == SettingsRowId.SendProblemReport }
                .door,
        )
    }

    @Test
    fun `B-190-051 search finds it by its title and its line`() {
        val entries =
            listOf(
                SettingsSearchEntry(SettingsRowId.SendProblemReport, "Send a problem report", "bug crash Review a redacted log, then send it"),
                SettingsSearchEntry(SettingsRowId.About, "About", "version licences"),
            )
        assertEquals(SettingsRowId.SendProblemReport, SettingsSearch.find("problem report", entries).first())
        assertEquals(SettingsRowId.SendProblemReport, SettingsSearch.find("redacted log", entries).first())
    }

    @Test
    fun `B-190-052 opening collects and previews, sends nothing, and Send sends exactly the preview once`() = runTest {
        val sender = FakeSender()
        val p = presenter(sender)
        p.onOpen(SettingsPage.SendProblemReport); runCurrent()
        val preview = p.state.value.sendReport
        assertTrue(preview is SendReportUi.Preview)
        assertTrue(sender.sent.isEmpty(), "opening the page sends nothing")
        p.onSendProblemReport(); runCurrent()
        assertEquals(listOf(preview.text), sender.sent)
        assertEquals(SendReportUi.Sent(preview.text, "abcdef01-1"), p.state.value.sendReport)
        p.onSendProblemReport(); runCurrent()
        assertEquals(1, sender.sent.size, "a sent report is not sent again")
        assertEquals("abcdef01-1", p.state.value.reportCode)
        // Back closes it; nothing more is sent.
        p.onBack(); runCurrent()
        assertNull(p.state.value.sendReport)
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `B-190-053 a failed send keeps the preview and can be sent again`() = runTest {
        val sender = FakeSender(answer = null)
        val p = presenter(sender)
        p.onOpen(SettingsPage.SendProblemReport); runCurrent()
        p.onSendProblemReport(); runCurrent()
        val failed = p.state.value.sendReport
        assertTrue(failed is SendReportUi.Failed)
        sender.answer = "abcdef01-2"
        p.onSendProblemReport(); runCurrent()
        assertEquals(SendReportUi.Sent(failed.text, "abcdef01-2"), p.state.value.sendReport)
    }

    @Test
    fun `B-190-054 About shows the report code, and nothing without a vendor`() = runTest {
        val p = presenter(FakeSender())
        p.onOpen(SettingsPage.About); runCurrent()
        assertEquals("abcdef01-0", p.state.value.reportCode)
        val none = presenter(null, available = false)
        none.onOpen(SettingsPage.About); runCurrent()
        assertNull(none.state.value.reportCode)
        none.onOpen(SettingsPage.SendProblemReport); runCurrent()
        assertNull(none.state.value.sendReport)
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
