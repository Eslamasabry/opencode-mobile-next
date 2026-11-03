package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-NAV-058: the search set is exactly the rows this device and server can open (amended row, scope.jsonl). */
class SettingsSearchScopeTest {
    @Test
    fun `B-NAV-058 in-app Linux has one This phone result and its agents`() {
        val rows = SettingsStructure.searchable(phoneFacts())
        assertEquals(1, rows.count { it == SettingsRowId.ThisPhone })
        assertTrue(SettingsRowId.Agents in rows)
    }

    @Test
    fun `B-NAV-058 a device without the phone features loses the phone-only results and gains release checks`() {
        val rows = SettingsStructure.searchable(SettingsFacts(platform = desktop, server = phoneFacts().server))
        for (phoneOnly in listOf(SettingsRowId.ThisPhone, SettingsRowId.Agents, SettingsRowId.Notifications, SettingsRowId.Background, SettingsRowId.Voice)) {
            assertFalse(phoneOnly in rows, "$phoneOnly must not be found")
        }
        val withReleaseCheck = SettingsStructure.searchable(SettingsFacts(platform = desktop.copy(desktopReleaseCheck = true)))
        assertTrue(SettingsRowId.Updates in withReleaseCheck)
    }

    @Test
    fun `B-NAV-058 without a server the server tools are not offered`() {
        val rows = SettingsStructure.searchable(phoneFacts(server = null))
        for (serverRow in listOf(SettingsRowId.ModelAndMode, SettingsRowId.Providers, SettingsRowId.Tools, SettingsRowId.DefaultShell)) {
            assertFalse(serverRow in rows, "$serverRow must not be found")
        }
        assertTrue(SettingsRowId.Servers in rows)
    }

    @Test
    fun `B-NAV-058 a place the host cannot open is absent from search, never a dead result`() {
        val opens = SettingsOutside.entries.toSet() - SettingsOutside.Tools
        val rows = SettingsStructure.searchable(phoneFacts().copy(opens = opens))
        assertFalse(SettingsRowId.Tools in rows)
        assertTrue(SettingsRowId.Providers in rows)
    }
}
