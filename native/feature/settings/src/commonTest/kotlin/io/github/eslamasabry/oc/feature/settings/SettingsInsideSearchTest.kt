package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.SettingsInside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsInsideEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** S6: Settings search reaches the pages inside the rows ("ports" -> the Linux Ports page), gated like their rows. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsInsideSearchTest {
    private val entries =
        listOf(
            SettingsInsideEntry(SettingsInside.LinuxPorts, "Ports in Linux", "Linux; open ports; listening; server port"),
            SettingsInsideEntry(SettingsInside.LinuxPackages, "Packages", "Linux; software; apt; install; programs"),
            SettingsInsideEntry(SettingsInside.UsageBudgets, "Budgets", "budget budgets spending limit"),
            SettingsInsideEntry(SettingsInside.UsageAlerts, "Alerts", "usage alerts quota"),
        )

    @Test
    fun `ports and port and packages find their pages`() {
        assertEquals(listOf(SettingsInside.LinuxPorts), find("ports"))
        assertEquals(listOf(SettingsInside.LinuxPorts), find("port"))
        assertEquals(listOf(SettingsInside.LinuxPackages), find("package"))
        assertEquals(listOf(SettingsInside.UsageBudgets), find("budget"))
    }

    private fun find(q: String) = io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearch.findInside(q, entries)

    @Test
    fun `every inside page opens a real place`() {
        for (i in SettingsInside.entries) assertTrue(i.door() != null)
        assertEquals(
            io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor.Outside(SettingsOutside.LinuxPorts),
            SettingsInside.LinuxPorts.door(),
        )
    }

    @Test
    fun `Linux pages are offered only with the built-in Linux`() {
        val withLinux = phoneFacts().copy(platform = phoneFacts().platform.copy(builtinLinux = true))
        val without = phoneFacts().copy(platform = phoneFacts().platform.copy(builtinLinux = false))
        assertTrue(SettingsInside.LinuxPorts in SettingsInside.searchable(withLinux))
        assertFalse(SettingsInside.LinuxPorts in SettingsInside.searchable(without))
        assertFalse(SettingsInside.LinuxPackages in SettingsInside.searchable(without))
        assertTrue(SettingsInside.UsageSpent in SettingsInside.searchable(without))
    }

    @Test
    fun `typing ports in Settings lists the Ports page and opening it hands the host the place`() = runTest {
        val facts = phoneFacts().let { it.copy(platform = it.platform.copy(builtinLinux = true)) }
        val openers = RecordingOpeners()
        val p =
            SettingsPresenter(
                scope = backgroundScope,
                facts = MutableStateFlow(facts),
                choices = FakeChoices(),
                openers = openers,
                analytics = FakeAnalytics(),
                notifications = FakeNotifications(),
                updates = FakeUpdates(io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts(current = "3.0.0", build = "10001")),
            )
        runCurrent()
        p.onInsideEntries(entries)
        p.onQuery("ports"); runCurrent()
        assertEquals(listOf(SettingsInside.LinuxPorts), p.state.value.hub.insideResults)
        p.onInside(SettingsInside.LinuxPorts); runCurrent()
        assertEquals(listOf(SettingsOutside.LinuxPorts), openers.opened)
    }
}
