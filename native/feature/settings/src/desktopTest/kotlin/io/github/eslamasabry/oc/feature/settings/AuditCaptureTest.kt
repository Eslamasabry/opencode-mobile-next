package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow

/** Audit capture only: every Settings page through the real presenter on the phone-shaped fakes. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private fun page(name: String, page: SettingsPage?) = Audit.guard("settings__${name}__build") {
        var presenter: SettingsPresenter? = null
        Audit.shoot(
            "settings", name, "default",
            setup = {
                if (page != null) {
                    presenter!!.onOpen(page)
                    waitForIdle()
                    mainClock.advanceTimeBy(800)
                }
            },
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                val scope = rememberCoroutineScope()
                val p = remember {
                    SettingsPresenter(scope, MutableStateFlow(phoneFacts().copy(phraseChips = true, transcriptDisplay = true)), FakeChoices(), RecordingOpeners())
                        .also { presenter = it }
                }
                val state by p.state.collectAsState()
                SettingsScreen(state, p)
            }
        }
    }

    @Test fun hub() = page("hub", null)

    @Test fun groups() {
        for (g in SettingsGroupId.entries) page("group-${g.name.lowercase()}", SettingsPage.Group(g))
    }

    @Test fun appearance() = page("appearance", SettingsPage.Appearance)
    @Test fun voice() = page("voice", SettingsPage.Voice)
    @Test fun notifications() = page("notifications", SettingsPage.Notifications)
    @Test fun updates() = page("updates", SettingsPage.Updates)
    @Test fun about() = page("about", SettingsPage.About)
    @Test fun notices() = page("notices", SettingsPage.Notices)
    @Test fun phraseChips() = page("phrase-chips", SettingsPage.PhraseChips)
    @Test fun whatsNew() = page("whats-new", SettingsPage.WhatsNew)
    @Test fun privacy() = page("privacy", SettingsPage.Privacy)
    @Test fun privacyPolicy() = page("privacy-policy", SettingsPage.PrivacyPolicy)
    @Test fun whatWeCollect() = page("what-we-collect", SettingsPage.WhatWeCollect)
    @Test fun language() = page("language", SettingsPage.Language)
    @Test fun reportProblem() = page("report-problem", SettingsPage.ReportProblem)
    @Test fun storage() = page("storage", SettingsPage.Storage)
    @Test fun guide() = page("guide", SettingsPage.Guide)
    @Test fun shortcuts() = page("keyboard-shortcuts", SettingsPage.KeyboardShortcuts)
    @Test fun serverCapabilities() = page("server-capabilities", SettingsPage.ServerCapabilities)
    @Test fun serverConfiguration() = page("server-configuration", SettingsPage.ServerConfiguration)
}
