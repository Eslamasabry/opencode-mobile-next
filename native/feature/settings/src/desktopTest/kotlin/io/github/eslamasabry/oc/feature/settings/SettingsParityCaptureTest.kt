package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.feature.settings.presentation.AboutFacts
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.BackgroundState
import io.github.eslamasabry.oc.feature.settings.presentation.GroupUi
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test

/**
 * UI parity (P12 UI-SERVERS-SETTINGS): every Settings screen state the Flutter goldens show, in the five parity frames,
 * from canned state. Each scene asserts what the person must see (by text in English, by tag everywhere).
 * Captures: `./gradlew :feature:settings:desktopTest --tests '*SettingsParityCaptureTest*' -Poc.parityOut=<dir>`.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsParityCaptureTest {
    private val facts = phoneFacts()
    private val hub =
        HubUi(
            groups = SettingsGroupId.entries.toImmutableList(),
            server = ServerLine("Laptop", onPhone = false),
            searchable = SettingsStructure.searchable(facts),
        )

    private fun on(page: SettingsPage, ui: SettingsUi = SettingsUi()): SettingsUi =
        ui.copy(hub = hub, stack = persistentListOf(SettingsPage.Hub, page))

    private fun group(id: SettingsGroupId): SettingsUi =
        on(
            SettingsPage.Group(id),
            SettingsUi(
                group = GroupUi(id, SettingsStructure.rows(id, facts, reasoning = true, timestamps = false)),
                notifications = NotificationFacts(background = BackgroundState.Off),
            ),
        )

    private fun scene(screen: String, state: SettingsUi, english: List<String>, tags: List<String> = emptyList()) =
        ParityCapture.capture(
            "settings-$screen",
            check = { v ->
                tags.forEach { onNodeWithTag(it, useUnmergedTree = true).assertExists() }
                if (v.locale == "en") english.forEach { onNodeWithText(it, useUnmergedTree = true).assertExists() }
            },
        ) { SettingsScreen(state, RecordingActions()) }

    @Test
    fun `B-NAV-068 parity the hub shows search, the five groups and the server line`() =
        scene(
            "hub",
            SettingsUi(hub = hub),
            listOf("Settings", "Server", "Laptop · Connected", "Agent", "Conversations", "This app", "Help"),
            listOf("settings-search"),
        )

    @Test
    fun `B-NAV-068 parity group Server lists Servers and This phone`() =
        scene("group-server", group(SettingsGroupId.Server), listOf("Server"), listOf("settings-servers"))

    @Test
    fun `B-NAV-068 parity group Agent lists model, providers, usage, tools and agents`() =
        scene("group-agent", group(SettingsGroupId.Agent), listOf("Agent"), listOf("settings-model-mode", "settings-providers"))

    @Test
    fun `B-NAV-068 parity group Conversations lists the transcript switches`() =
        scene("group-conversations", group(SettingsGroupId.Conversations), listOf("Conversations"), listOf("settings-show-reasoning"))

    @Test
    fun `B-NAV-068 parity group This app lists notifications, appearance, language and privacy`() =
        scene(
            "group-this-app",
            group(SettingsGroupId.ThisApp),
            listOf("This app"),
            listOf("settings-notifications", "settings-appearance", "settings-language", "settings-privacy"),
        )

    @Test
    fun `B-NAV-068 parity group Help lists guide, tips, report and about`() =
        scene("group-help", group(SettingsGroupId.Help), listOf("Help"), listOf("settings-about"))

    @Test
    fun `SCREENS-NAV-91 parity appearance shows mode, colours, style and motion`() =
        scene(
            "appearance",
            on(
                SettingsPage.Appearance,
                SettingsUi(
                    appearance =
                        AppearanceUi(
                            mode = AppearanceMode.Light, pack = PackId.Opencode, packs = Packs.mostUsed.toImmutableList(),
                            seeAllCount = 31, style = SurfaceStyle.Solid, motion = MotionLevel.Full,
                        ),
                ),
            ),
            listOf("Appearance"),
        )

    @Test
    fun `B-L10N-001 parity language lists each language in its own name`() =
        scene("language", on(SettingsPage.Language, SettingsUi(language = AppLanguage.System)), listOf("English", "العربية"))

    @Test
    fun `plan 07 I05 parity notifications with the switches, quiet hours and background`() =
        scene(
            "notifications",
            on(
                SettingsPage.Notifications,
                SettingsUi(notifications = NotificationFacts(quietHours = null, background = BackgroundState.Running)),
            ),
            listOf("Notifications"),
        )

    @Test
    fun `B-SEC-191 parity privacy page`() =
        scene(
            "privacy",
            on(SettingsPage.Privacy, SettingsUi(privacy = PrivacyUi(WhatWeCollectSource.build(ConsentState(), RemoteSink)))),
            listOf("Privacy and data"),
        )

    @Test
    fun `plan 07 I21 parity about shows the version and the links`() =
        scene("about", on(SettingsPage.About, SettingsUi(about = AboutFacts(version = "3.0.0", build = "10001"))), listOf("About"))
}
