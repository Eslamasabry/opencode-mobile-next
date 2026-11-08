package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** E14-S01: every Settings row Flutter had is reachable on Android, in the hub and in search. */
class SettingsRowsWiringTest {
    /** Android Settings as the host builds it, with a connected server and every page on. */
    private fun androidFacts() =
        SettingsFacts(
            platform = settingsShippedPlatform(PlatformCapabilities.android(), voiceOffered = true),
            server = ServerLine("laptop", onPhone = false),
            diagnostics = true,
            configurationInspection = true,
            localModelStorage = true,
            opens = SETTINGS_OPENS,
            phraseChips = true,
            transcriptDisplay = true,
        )

    private fun rows(facts: SettingsFacts) =
        SettingsGroupId.entries.flatMap {
            SettingsStructure.rows(it, facts, reasoning = false, timestamps = false)
        }

    @Test
    fun `E14-S01 every SettingsStructure row resolves to a destination on Android`() {
        val shown = rows(androidFacts())
        val missing =
            SettingsRowId.entries.filter { id -> shown.none { it.id == id } } -
                notNativeRows()
        assertEquals(emptyList<SettingsRowId>(), missing, "rows with no destination on Android")
        for (row in shown) {
            val door = row.door
            if (door is SettingsDoor.Outside)
                assertTrue(door.place in SETTINGS_OPENS, "${row.id} leads to ${door.place}")
        }
    }

    @Test
    fun `E14-S01 the hub's outside places Flutter had are all opened`() {
        val flutterPlaces =
            listOf(
                SettingsOutside.ThisPhone,
                SettingsOutside.DefaultShell,
                SettingsOutside.Permissions,
                SettingsOutside.UsageAlerts,
                SettingsOutside.QuietHours,
                SettingsOutside.Notices,
                // s26: Settings › Agents › AI Team and About › Developer › Use memory on this phone.
                SettingsOutside.AiTeam,
                SettingsOutside.MemoryOnPhone,
            )
        assertEquals(emptyList<SettingsOutside>(), flutterPlaces.filterNot { it in SETTINGS_OPENS })
        assertTrue(SETTINGS_OPENS.none { it in SETTINGS_NOT_NATIVE_YET })
    }

    @Test
    fun `E14-S01 search finds This phone, Default shell and Permissions on Android`() {
        val search = SettingsStructure.searchable(androidFacts())
        assertTrue(SettingsRowId.ThisPhone in search)
        assertTrue(SettingsRowId.DefaultShell in search)
        assertTrue(SettingsRowId.Permissions in search)
        assertEquals(search.distinct(), search)
    }

    @Test
    fun `E14-S01 This phone opens the phone's page once set up, else its setup`() {
        assertEquals(PhoneRoute.ThisPhone, thisPhoneRoute(setUp = true))
        assertEquals(PhoneSetupRoute.Setup, thisPhoneRoute(setUp = false))
    }

    @Test
    fun `B-NAV-090 Help Guide is a page and its doors are opened on Android`() {
        val guide = rows(androidFacts()).single { it.id == SettingsRowId.Guide }
        assertEquals(SettingsDoor.Page(io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage.Guide), guide.door)
        for (door in listOf(SettingsOutside.AddServer, SettingsOutside.PhoneSetup, SettingsOutside.Demo))
            assertTrue(door in SETTINGS_OPENS, "Guide's $door leads nowhere")
    }

    @Test
    fun `B-NAV-093 the signer reads as Flutter prints it, upper-case hex without separators`() {
        val print = certificateFingerprint(byteArrayOf(1, 2, 3))
        assertEquals(64, print.length)
        assertEquals("039058C6F2C0CB492C533B0A4D14EF77CC0F78ABCCCED5287D84A1A2011CFB81", print)
    }

    /** Rows whose Flutter page has no native page yet (reported, never faked). */
    private fun notNativeRows(): Set<SettingsRowId> =
        buildSet {
            if (SettingsOutside.Guide in SETTINGS_NOT_NATIVE_YET) add(SettingsRowId.Guide)
        }

    /**
     * S31 story 6: Settings › Conversations lacked Flutter's "Show reasoning" and "Show time, tokens and cost"
     * switches because the host built its facts with `transcriptDisplay = false`.
     */
    @Test
    fun `B-SET-270 Conversations shows Flutter's Show reasoning and cost switches on Android`() {
        val facts =
            androidSettingsFacts(
                settingsShippedPlatform(PlatformCapabilities.android(), voiceOffered = true),
                dynamicColour = true,
            )
        val conversations =
            SettingsStructure.rows(SettingsGroupId.Conversations, facts, reasoning = true, timestamps = false)
        val ids = conversations.map { it.id }
        assertEquals(
            listOf(SettingsRowId.ShowReasoning, SettingsRowId.ShowTimestamps),
            ids.take(2),
            "Flutter's order: Show reasoning, then Show time, tokens and cost",
        )
        assertEquals(SettingsDoor.Switch(true), conversations[0].door)
        assertEquals(SettingsDoor.Switch(false), conversations[1].door)
    }
}
