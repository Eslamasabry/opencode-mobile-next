package io.github.eslamasabry.oc.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The Settings ViewModel: one presenter for the hub and its pages (they share the back stack and the personal
 * choices). Not yet contributed to Metro: the ports' adapters need `:engine:settings` to compile and the voice,
 * notification and updater engines to exist (report §6).
 */
public class SettingsViewModel(
    facts: StateFlow<SettingsFacts>,
    choices: PersonalChoices,
    openers: SettingsOpeners,
    analytics: AnalyticsChoices? = null,
    voice: VoiceSettings? = null,
    notifications: NotificationSettings? = null,
    updates: UpdateSettings? = null,
    about: AboutFacts = AboutFacts(),
) : ViewModel() {
    private val presenter =
        SettingsPresenter(viewModelScope, facts, choices, openers, analytics, voice, notifications, updates, about)

    public val state: StateFlow<SettingsUi> = presenter.state
    public val actions: SettingsActions = presenter
}
