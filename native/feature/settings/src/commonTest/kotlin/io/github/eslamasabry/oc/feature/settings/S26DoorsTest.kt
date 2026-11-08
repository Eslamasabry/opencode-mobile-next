package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.MemoryPreviewSetting
import io.github.eslamasabry.oc.feature.settings.presentation.MemoryPreviewUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * s26: the doors Flutter 2.1.5 has to AI Team and FactsBook in Settings. Settings › Agents › AI Team
 * (agents_permissions_screens.dart:48, shown with a server) and About › Developer › Memory (preview) with
 * "Use memory on this phone" (about_screen.dart:515).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class S26DoorsTest {
    private class FakeMemory(var on: Boolean = false, override val phoneNode: Boolean = true) : MemoryPreviewSetting {
        var saves = true
        val written = mutableListOf<Boolean>()

        override fun enabled(): Boolean = on

        override suspend fun setEnabled(on: Boolean): Boolean {
            written += on
            if (saves) this.on = on
            return saves
        }
    }

    private fun agent(facts: io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts) =
        SettingsStructure.rows(SettingsGroupId.Agent, facts, reasoning = false, timestamps = false)

    @Test
    fun `B-SET-260 with a server the Agent group has AI Team, which opens the team page, and search lists it`() {
        val row = agent(phoneFacts()).single { it.id == SettingsRowId.AiTeam }
        assertEquals(SettingsDoor.Outside(SettingsOutside.AiTeam), row.door)
        assertEquals("settings-ai-team", SettingsRowId.AiTeam.tag)
        assertTrue(SettingsRowId.AiTeam in SettingsStructure.searchable(phoneFacts()))
    }

    @Test
    fun `B-SET-260 without a server there is no AI Team row`() {
        assertFalse(agent(phoneFacts(server = null)).any { it.id == SettingsRowId.AiTeam })
    }

    @Test
    fun `B-SET-261 About shows Memory (preview) off by default and the switch saves the answer`() = runTest {
        val memory = FakeMemory()
        val p =
            SettingsPresenter(
                scope = backgroundScope,
                facts = MutableStateFlow(phoneFacts()),
                choices = FakeChoices(),
                openers = RecordingOpeners(),
                memoryPreview = memory,
            ).also { runCurrent() }
        assertEquals(MemoryPreviewUi(on = false, phoneNode = true), p.state.value.memoryPreview)
        p.onMemoryPreview(true); runCurrent()
        assertEquals(listOf(true), memory.written)
        assertEquals(true, p.state.value.memoryPreview?.on)
        memory.saves = false
        p.onMemoryPreview(false); runCurrent()
        assertEquals(true, p.state.value.memoryPreview?.on, "an answer that cannot be saved goes back")
        assertEquals(SettingsNote.ChoiceNotSaved, p.state.value.note)
    }

    @Test
    fun `B-SET-261 a build without FactsBook has no Developer group`() = runTest {
        val p =
            SettingsPresenter(
                scope = backgroundScope,
                facts = MutableStateFlow(phoneFacts()),
                choices = FakeChoices(),
                openers = RecordingOpeners(),
            ).also { runCurrent() }
        assertNull(p.state.value.memoryPreview)
    }
}
