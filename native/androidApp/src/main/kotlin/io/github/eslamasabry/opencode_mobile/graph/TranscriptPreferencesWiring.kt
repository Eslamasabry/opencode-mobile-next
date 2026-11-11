package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayPreferences
import io.github.eslamasabry.oc.engine.settings.prefs.PreferenceChange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoSet(AppScope::class, binding = binding<TranscriptDisplayPreferences>())
@SingleIn(AppScope::class)
@Inject
class AndroidTranscriptPreferences(
    private val appPrefs: AppPreferences,
    private val appScope: AppCoroutineScope,
) : TranscriptDisplayPreferences {
    private val prefs = appPrefs
    private val scope = appScope

    override val canSetTimestamps: Boolean = true

    /** C10: the same device choice Settings writes; a failed save leaves the shown state as it was. */
    override fun setTimestamps(visible: Boolean) {
        scope.launch { appPrefs.preferences()?.set(PreferenceChange.TimestampsVisible(visible)) }
    }

    override val value =
        prefs.value
            .map { TranscriptDisplayChoices(it.reasoningExpanded, it.timestampsVisible) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                TranscriptDisplayChoices(
                    prefs.value.value.reasoningExpanded,
                    prefs.value.value.timestampsVisible,
                ),
            )
}
