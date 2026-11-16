package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.SettingsInside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsInsideEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
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

/** Small-faults 301: the rows at the bottom of Privacy and data are found from Settings search. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPrivacyRowsSearchTest {
    private val entries =
        listOf(
            SettingsInsideEntry(SettingsInside.PrivacyWhatWeCollect, "What we collect", "Usage and Crash reports are off until you turn them on."),
            SettingsInsideEntry(SettingsInside.PrivacyPolicy, "Privacy policy", "Servers, providers, voice, files, and updates"),
        )

    private fun TestScope.presenter(withCollect: Boolean) =
        SettingsPresenter(
            scope = backgroundScope,
            facts = MutableStateFlow(phoneFacts()),
            choices = FakeChoices(),
            openers = RecordingOpeners(),
            analytics = if (withCollect) FakeAnalytics() else null,
            notifications = FakeNotifications(),
            updates = FakeUpdates(io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts(current = "3.0.0", build = "10001")),
        )

    @Test
    fun `SMALL-301 collect and privacy policy find their rows and open the right page`() = runTest {
        val p = presenter(withCollect = true)
        runCurrent()
        p.onInsideEntries(entries)
        p.onQuery("collect"); runCurrent()
        assertEquals(listOf(SettingsInside.PrivacyWhatWeCollect), p.state.value.hub.insideResults)
        p.onInside(SettingsInside.PrivacyWhatWeCollect); runCurrent()
        assertEquals(SettingsPage.WhatWeCollect, p.state.value.stack.last())
        p.onQuery("what we collect"); runCurrent()
        assertTrue(SettingsInside.PrivacyWhatWeCollect in p.state.value.hub.insideResults)
        p.onQuery("privacy policy"); runCurrent()
        assertEquals(listOf(SettingsInside.PrivacyPolicy), p.state.value.hub.insideResults)
        p.onInside(SettingsInside.PrivacyPolicy); runCurrent()
        assertEquals(SettingsPage.PrivacyPolicy, p.state.value.stack.last())
    }

    @Test
    fun `SMALL-301 What we collect is not offered where the build has no collect list`() = runTest {
        val p = presenter(withCollect = false)
        runCurrent()
        p.onInsideEntries(entries)
        p.onQuery("collect"); runCurrent()
        assertEquals(emptyList(), p.state.value.hub.insideResults)
        p.onQuery("privacy policy"); runCurrent()
        assertEquals(listOf(SettingsInside.PrivacyPolicy), p.state.value.hub.insideResults)
    }
}
