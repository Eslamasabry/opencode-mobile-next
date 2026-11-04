package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@ContributesIntoSet(AppScope::class, binding = binding<TranscriptDisplayPreferences>())
@SingleIn(AppScope::class)
@Inject
class AndroidTranscriptPreferences(prefs: AppPreferences, scope: AppCoroutineScope) :
    TranscriptDisplayPreferences {
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
