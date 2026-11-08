package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChange
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

/** P12-SETTINGS: text size, language and the host's open places, over the real presenter. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsParityTest {
    private class Rig(val scope: TestScope, facts: SettingsFacts) {
        val choices = FakeChoices()
        val openers = RecordingOpeners()
        val p = SettingsPresenter(scope.backgroundScope, MutableStateFlow(facts), choices, openers)

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
    fun `02 3_7 a row leading to a place the host cannot open is absent, never dead`() {
        val facts = phoneFacts().copy(opens = setOf(SettingsOutside.Servers))
        val agent = SettingsStructure.rows(SettingsGroupId.Agent, facts, reasoning = false, timestamps = false)
        assertTrue(agent.isEmpty(), "model, providers, usage, tools and agents have no native page yet")
        assertFalse(SettingsGroupId.Agent in SettingsStructure.groups(facts))
        val server = SettingsStructure.rows(SettingsGroupId.Server, facts, reasoning = false, timestamps = false)
        // Server capabilities is a page this module draws itself, so the host's places never hide it.
        assertEquals(listOf(SettingsRowId.Servers, SettingsRowId.ServerCapabilities), server.map { it.id })
        // Pages this module draws itself are never hidden by the host's places.
        val thisApp = SettingsStructure.rows(SettingsGroupId.ThisApp, facts, reasoning = false, timestamps = false).map { it.id }
        assertTrue(SettingsRowId.Appearance in thisApp && SettingsRowId.Language in thisApp && SettingsRowId.Privacy in thisApp)
    }
}
