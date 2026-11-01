package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStructureTest {
    private fun ids(group: SettingsGroupId, facts: SettingsFacts) =
        SettingsStructure.rows(group, facts, reasoning = false, timestamps = false).map { it.id }

    @Test
    fun `B-NAV-068 one server shows the five groups in order with their rows`() {
        val facts = phoneFacts()
        assertEquals<List<SettingsGroupId>>(SettingsGroupId.entries.toList(), SettingsStructure.groups(facts))
        assertEquals(listOf(SettingsRowId.Servers, SettingsRowId.ThisPhone), ids(SettingsGroupId.Server, facts))
        assertEquals(
            listOf(
                SettingsRowId.Notifications, SettingsRowId.Background, SettingsRowId.Appearance, SettingsRowId.Language,
                SettingsRowId.Privacy, SettingsRowId.Updates,
            ),
            ids(SettingsGroupId.ThisApp, facts),
        )
        assertEquals(
            listOf(SettingsRowId.Guide, SettingsRowId.Tips, SettingsRowId.ReportProblem, SettingsRowId.About),
            ids(SettingsGroupId.Help, facts),
        )
    }

    @Test
    fun `B-NAV-081 rows the server cannot serve are absent without a server`() {
        val none = phoneFacts(server = null)
        val agent = ids(SettingsGroupId.Agent, none)
        assertFalse(SettingsRowId.ModelAndMode in agent)
        assertFalse(SettingsRowId.Providers in agent)
        assertFalse(SettingsRowId.Tools in agent)
        assertFalse(SettingsRowId.DefaultShell in ids(SettingsGroupId.Conversations, none))
        assertTrue(SettingsRowId.Usage in agent)
    }

    @Test
    fun `B-XPLAT-003 desktop has no Android-only rows`() {
        val facts = SettingsFacts(platform = desktop)
        assertEquals(listOf(SettingsRowId.Servers), ids(SettingsGroupId.Server, facts))
        val thisApp = ids(SettingsGroupId.ThisApp, facts)
        assertFalse(SettingsRowId.Background in thisApp)
        assertFalse(SettingsRowId.Updates in thisApp)
        assertFalse(SettingsRowId.Notifications in thisApp)
        assertFalse(SettingsRowId.Voice in ids(SettingsGroupId.Conversations, facts))
    }

    @Test
    fun `SCREENS-NAV-90 rows open their page in this module or name the place another feature owns`() {
        val rows = SettingsStructure.rows(SettingsGroupId.ThisApp, phoneFacts(), reasoning = false, timestamps = false)
        assertEquals(SettingsDoor.Page(SettingsPage.Appearance), rows.first { it.id == SettingsRowId.Appearance }.door)
        assertEquals(SettingsDoor.Outside(SettingsOutside.KeepRunning), rows.first { it.id == SettingsRowId.Background }.door)
        val conv = SettingsStructure.rows(SettingsGroupId.Conversations, phoneFacts(), reasoning = true, timestamps = false)
        assertEquals(SettingsDoor.Switch(true), conv.first { it.id == SettingsRowId.ShowReasoning }.door)
        assertEquals(SettingsDoor.Switch(false), conv.first { it.id == SettingsRowId.ShowTimestamps }.door)
    }

    @Test
    fun `B-NAV-072 the search set holds only rows that exist`() {
        val desktopRows = SettingsStructure.searchable(SettingsFacts(platform = desktop))
        assertFalse(SettingsRowId.ThisPhone in desktopRows)
        assertFalse(SettingsRowId.Updates in desktopRows)
        assertTrue(SettingsRowId.Appearance in desktopRows)
        assertEquals(desktopRows.distinct(), desktopRows)
    }

    @Test
    fun `plan 07 10_2 every row has its own stable test tag`() {
        val tags = SettingsRowId.entries.map { it.tag }
        assertEquals(tags.distinct(), tags)
        assertTrue(tags.all { it.startsWith("settings-") })
    }
}
