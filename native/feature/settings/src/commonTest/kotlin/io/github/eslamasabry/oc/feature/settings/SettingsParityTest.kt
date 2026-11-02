package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.DiagnosticsExport
import io.github.eslamasabry.oc.feature.settings.presentation.DiagnosticsReport
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChange
import io.github.eslamasabry.oc.feature.settings.presentation.ReportError
import io.github.eslamasabry.oc.feature.settings.presentation.ReportFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P12-SETTINGS: text size, language, Report a problem and the host's open places, over the real presenter. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsParityTest {
    private class Export : DiagnosticsExport {
        var text: Outcome<String> = Outcome.Ok("OpenCode Mobile 3.0.0+1")
        val copied = mutableListOf<String>()
        val shared = mutableListOf<String>()
        var shareWorks = true

        override suspend fun report(): Outcome<String> = text

        override suspend fun copy(text: String): Outcome<Unit> {
            copied += text
            return Outcome.Ok(Unit)
        }

        override suspend fun share(text: String): Outcome<Unit> {
            shared += text
            return if (shareWorks) Outcome.Ok(Unit) else Outcome.Failed(Failure.StorageRefused())
        }
    }

    private class Rig(val scope: TestScope, facts: SettingsFacts) {
        val choices = FakeChoices()
        val export = Export()
        val openers = RecordingOpeners()
        val p = SettingsPresenter(scope.backgroundScope, MutableStateFlow(facts), choices, openers, diagnostics = export)

        fun settle() = scope.runCurrent()

        val ui get() = p.state.value
    }

    private fun rig(facts: SettingsFacts = phoneFacts().copy(diagnostics = true), body: suspend Rig.() -> Unit) =
        runTest { Rig(this, facts).apply { settle() }.body() }

    @Test
    fun `B-KIT-059 Text size applies through the personal choices and shows the acknowledged size`() =
        rig {
            p.onOpen(SettingsPage.Appearance); settle()
            assertEquals(AppTextSize.Normal, ui.appearance.textSize)
            p.onTextSize(AppTextSize.Large); settle()
            assertEquals(listOf<PersonalChange>(PersonalChange.TextSize(AppTextSize.Large)), choices.writes)
            assertEquals(AppTextSize.Large, ui.appearance.textSize)
            p.onTextSize(AppTextSize.Large); settle()
            assertEquals(1, choices.writes.size, "choosing the size in use writes nothing")
        }

    @Test
    fun `B-KIT-059 a refused text size keeps the old size and says so once`() =
        rig {
            choices.refuseNext = true
            p.onTextSize(AppTextSize.Smallest); settle()
            assertEquals(AppTextSize.Normal, ui.appearance.textSize)
            assertEquals(SettingsNote.AppearanceNotSaved, ui.note)
        }

    @Test
    fun `B-L10N-001 the Language row opens its page here and a tap stores the language`() =
        rig {
            p.onOpen(SettingsPage.Group(SettingsGroupId.ThisApp)); settle()
            p.onRow(SettingsRowId.Language); settle()
            assertEquals(SettingsPage.Language, ui.page)
            assertEquals(AppLanguage.System, ui.language)
            p.onLanguage(AppLanguage.Arabic); settle()
            assertEquals(AppLanguage.Arabic, ui.language)
            assertEquals(SettingsPage.Language, ui.page, "the page stays, now in the new language")
            assertTrue(openers.opened.isEmpty())
        }

    @Test
    fun `B-SEC-060 Report a problem previews the exact text that Copy and Share hand over`() =
        rig {
            p.onOpen(SettingsPage.Group(SettingsGroupId.Help)); settle()
            p.onRow(SettingsRowId.ReportProblem); settle()
            assertEquals(SettingsPage.ReportProblem, ui.page)
            assertEquals(ReportUi.Ready("OpenCode Mobile 3.0.0+1"), ui.report)
            p.onCopyReport(); settle()
            p.onShareReport(); settle()
            assertEquals(listOf("OpenCode Mobile 3.0.0+1"), export.copied)
            assertEquals(listOf("OpenCode Mobile 3.0.0+1"), export.shared)
            assertEquals(SettingsNote.ReportCopied, ui.note)
        }

    @Test
    fun `B-STATE-078 a report that cannot be built is one plain state with Try again, and sharing that fails says so`() =
        rig {
            export.text = Outcome.Failed(Failure.StorageRefused())
            p.onOpen(SettingsPage.ReportProblem); settle()
            assertEquals(ReportUi.Failed, ui.report)
            p.onShareReport(); settle()
            assertTrue(export.shared.isEmpty(), "nothing is shared without a previewed report")

            export.text = Outcome.Ok("again")
            p.onRetryReport(); settle()
            assertEquals(ReportUi.Ready("again"), ui.report)
            export.shareWorks = false
            p.onShareReport(); settle()
            assertEquals(SettingsNote.ShareFailed, ui.note)
        }

    @Test
    fun `02 3_7 a row leading to a place the host cannot open is absent, never dead`() {
        val facts = phoneFacts().copy(opens = setOf(SettingsOutside.Servers))
        val agent = SettingsStructure.rows(SettingsGroupId.Agent, facts, reasoning = false, timestamps = false)
        assertTrue(agent.isEmpty(), "model, providers, usage, tools and agents have no native page yet")
        assertFalse(SettingsGroupId.Agent in SettingsStructure.groups(facts))
        val server = SettingsStructure.rows(SettingsGroupId.Server, facts, reasoning = false, timestamps = false)
        assertEquals(listOf(SettingsRowId.Servers), server.map { it.id })
        // Pages this module draws itself are never hidden by the host's places.
        val thisApp = SettingsStructure.rows(SettingsGroupId.ThisApp, facts, reasoning = false, timestamps = false).map { it.id }
        assertTrue(SettingsRowId.Appearance in thisApp && SettingsRowId.Language in thisApp && SettingsRowId.Privacy in thisApp)
    }

    @Test
    fun `B-SEC-041 the report carries generated facts and redacted errors only, newest first and bounded`() {
        val errors = (1..25).map { ReportError("2026-11-02T10:00:${it.toString().padStart(2, '0')}Z", "chat", "failed $it") }
        val text =
            DiagnosticsReport.text(
                ReportFacts(
                    appVersion = "3.0.0-dev", build = "10002", platform = "Android 15 (API 35)", language = "ar",
                    textSize = "large", motion = "calm", savedServers = 2, connectedServers = 1, errors = errors,
                ),
            )
        val lines = text.lines()
        assertEquals("OpenCode Mobile 3.0.0-dev+10002", lines[0])
        assertTrue("Servers: 2 saved · 1 connected" in lines)
        assertTrue(lines.first { it.startsWith("- ") }.endsWith("failed 25"), "newest first")
        assertEquals(DiagnosticsReport.MAX_ERRORS, lines.count { it.startsWith("- ") })
        assertTrue(lines.last().contains("5 older not shown"))
        assertFalse(text.contains("http"), "no addresses")
    }

    @Test
    fun `B-SEC-041 one runaway error line is cut and keeps only its first line`() {
        val long = ReportError("t", "net", "x".repeat(1_000) + "\nsecond line")
        val text =
            DiagnosticsReport.text(
                ReportFacts("3.0.0", "1", "Android", "system", "normal", "full", 0, 0, listOf(long)),
            )
        val line = text.lines().first { it.startsWith("- ") }
        assertTrue(line.length <= DiagnosticsReport.MAX_LINE + 2)
        assertFalse(text.contains("second line"))
    }
}
