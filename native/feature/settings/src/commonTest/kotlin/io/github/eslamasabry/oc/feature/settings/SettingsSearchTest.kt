package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearch
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchTest {
    private val entries =
        listOf(
            SettingsSearchEntry(SettingsRowId.Notifications, "Notifications", "alerts quiet hours wi-fi battery"),
            SettingsSearchEntry(SettingsRowId.Background, "Background", "keep running battery live connection"),
            SettingsSearchEntry(SettingsRowId.Appearance, "Appearance", "theme dark light colours مظهر سمة"),
            SettingsSearchEntry(SettingsRowId.Privacy, "Privacy and data", "drafts analytics what we collect"),
            SettingsSearchEntry(SettingsRowId.About, "About", "version licenses"),
        )

    @Test
    fun `B-NAV-071 a typo or a swap still finds the row`() {
        assertEquals(SettingsRowId.Appearance, SettingsSearch.find("apperance", entries).first())
        assertEquals(SettingsRowId.Appearance, SettingsSearch.find("appaerance", entries).first())
        assertTrue(SettingsRowId.Notifications in SettingsSearch.find("batery", entries))
    }

    @Test
    fun `B-NAV-071 prefixes, aliases and Arabic aliases match`() {
        assertEquals(listOf(SettingsRowId.Notifications), SettingsSearch.find("notif", entries))
        assertEquals(listOf(SettingsRowId.Privacy), SettingsSearch.find("what we collect", entries))
        assertEquals(listOf(SettingsRowId.Appearance), SettingsSearch.find("مظهر", entries))
    }

    @Test
    fun `B-NAV-071 literal matches rank above fuzzy ones and ties keep input order`() {
        // "battery" is literal in Notifications and Background (input order), and fuzzy nowhere else.
        assertEquals(listOf(SettingsRowId.Notifications, SettingsRowId.Background), SettingsSearch.find("battery", entries))
        val extra = entries + SettingsSearchEntry(SettingsRowId.Language, "Language", "batterx")
        assertEquals(
            listOf(SettingsRowId.Notifications, SettingsRowId.Background, SettingsRowId.Language),
            SettingsSearch.find("battery", extra),
        )
    }

    @Test
    fun `B-NAV-072 a word no row carries finds nothing and an empty query finds nothing`() {
        assertEquals(emptyList(), SettingsSearch.find("termux", entries))
        assertEquals(emptyList(), SettingsSearch.find("   ", entries))
    }
}
