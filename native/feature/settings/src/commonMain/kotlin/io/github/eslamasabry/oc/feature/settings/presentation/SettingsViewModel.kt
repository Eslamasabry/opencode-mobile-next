package io.github.eslamasabry.oc.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideMemory
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the Settings pages read and write, bound once by the app (`SettingsWiring`): the
 * engines behind the ports are app-wide, so one host serves every visit. A port the platform lacks
 * is null; its row is already absent.
 */
public fun interface SettingsServerDoor {
    public fun open(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        section: SettingsOutside?,
    )
}

public interface SettingsHost {
    public val appearanceGuide: AppearanceGuideMemory?
        get() = null

    public val serverPage: SettingsServerDoor?
        get() = null

    public val facts: StateFlow<SettingsFacts>
    public val choices: PersonalChoices
    public val openers: SettingsOpeners
    public val analytics: AnalyticsChoices?
    public val voice: VoiceSettings?
    public val notifications: NotificationSettings?
    public val updates: UpdateSettings?
    public val about: AboutFacts
    public val configuration: ConfigurationInspection?
        get() = null

    public val crashes: CrashSettings?
        get() = null

    public val appearanceHelp: AppearanceHelp?
        get() = null

    public val localData: LocalDataSettings?
        get() = null

    public val phrases: PhraseSettings?
        get() = null

    public val diagnostics: DiagnosticsExport?
}

/**
 * The Settings ViewModel: one presenter for the hub and its pages (they share the back stack and
 * the personal choices). Leaving the hub closes the route.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class SettingsViewModel(
    host: SettingsHost,
    private val nav: AppNavigator,
    arrivals: io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTargets,
) : ViewModel() {
    private val presenter =
        SettingsPresenter(
            viewModelScope,
            host.facts,
            host.choices,
            host.openers,
            host.analytics,
            host.voice,
            host.notifications,
            host.updates,
            host.about,
            host.diagnostics,
            host.crashes,
            host.phrases,
            host.appearanceGuide,
            nav::back,
            host.appearanceHelp,
            host.serverPage,
            host.localData,
            arrivals,
        )

    public val state: StateFlow<SettingsUi> = presenter.state
    public val actions: SettingsActions = presenter

    /** System Back or the bar's Back: walk the pages first, then leave Settings. */
    public fun back() {
        if (!presenter.onBack()) nav.back()
    }

    public fun exit(): Unit = nav.back()

    override fun onCleared() {
        presenter.closeReport()
    }

    private var announced = false

    public fun showWhatsNew() {
        if (announced) return
        announced = true
        presenter.showWhatsNew(standalone = true)
    }
}
