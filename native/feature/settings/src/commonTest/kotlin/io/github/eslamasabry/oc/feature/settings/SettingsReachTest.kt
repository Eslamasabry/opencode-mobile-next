package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** E14-S01: the Settings places Flutter had open a real page, in this module or the host's. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsReachTest {
    private class Rig(val scope: TestScope, facts: SettingsFacts) {
        val openers = RecordingOpeners()
        val p =
            SettingsPresenter(
                scope.backgroundScope,
                MutableStateFlow(facts),
                FakeChoices(),
                openers,
                notifications = FakeNotifications(),
            )

        fun settle() = scope.runCurrent()
    }

    private fun rig(facts: SettingsFacts = phoneFacts(), body: suspend Rig.() -> Unit) =
        runTest { Rig(this, facts).apply { settle() }.body() }

    @Test
    fun `E14-S01 Notices opens the in-app notices page, never a dead row`() =
        rig {
            p.onOutside(SettingsOutside.Notices); settle()
            assertEquals(SettingsPage.Notices, p.state.value.page)
            assertTrue(openers.opened.isEmpty())
        }

    @Test
    fun `E14-S01 Quiet hours opens Notifications, where they are set`() =
        rig {
            p.onOutside(SettingsOutside.QuietHours); settle()
            assertEquals(SettingsPage.Notifications, p.state.value.page)
            assertTrue(openers.opened.isEmpty())
        }

    @Test
    fun `E14-S01 Usage alerts show on Notifications when the host opens them, and lead there`() {
        rig(phoneFacts().copy(opens = setOf(SettingsOutside.UsageAlerts))) {
            p.onOpen(SettingsPage.Notifications); settle()
            assertTrue(p.state.value.notifications!!.usageAlertsShown)
            p.onOutside(SettingsOutside.UsageAlerts); settle()
            assertEquals(listOf(SettingsOutside.UsageAlerts), openers.opened)
        }
        rig(phoneFacts().copy(opens = emptySet())) {
            p.onOpen(SettingsPage.Notifications); settle()
            assertFalse(p.state.value.notifications!!.usageAlertsShown, "absent, never dead")
        }
    }

    @Test
    fun `E14-S01 Default shell and Permissions open their place for the one server`() =
        rig {
            p.onRow(SettingsRowId.DefaultShell); settle()
            p.onRow(SettingsRowId.Permissions); settle()
            assertEquals(listOf(SettingsOutside.DefaultShell, SettingsOutside.Permissions), openers.opened)
        }

    @Test
    fun `E14-S01 Permissions belong to a server and are absent without one`() {
        val none = phoneFacts(server = null)
        val rows = SettingsStructure.rows(SettingsGroupId.Conversations, none, reasoning = false, timestamps = false)
        assertFalse(rows.any { it.id == SettingsRowId.Permissions })
        assertFalse(SettingsRowId.Permissions in SettingsStructure.searchable(none))
        assertTrue(SettingsRowId.Permissions in SettingsStructure.searchable(phoneFacts()))
    }

    @Test
    fun `E14-S01 with every place open, every row has a destination in the hub and in search`() {
        val all =
            phoneFacts().copy(
                diagnostics = true,
                configurationInspection = true,
                localModelStorage = true,
                phraseChips = true,
                transcriptDisplay = true,
                opens = null,
            )
        assertEquals(SettingsRowId.entries.toSet(), SettingsStructure.searchable(all).toSet())
    }

    @Test
    fun `ST1 the Storage row opens the Storage page, with or without the phone's Linux`() {
        for (linux in listOf(true, false)) {
            val facts = phoneFacts().let { it.copy(localModelStorage = true, platform = it.platform.copy(builtinLinux = linux), opens = null) }
            val row = SettingsStructure.rows(SettingsGroupId.ThisApp, facts, reasoning = false, timestamps = false)
                .first { it.id == SettingsRowId.Storage }
            assertEquals(io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor.Page(SettingsPage.Storage), row.door)
        }
    }
}
