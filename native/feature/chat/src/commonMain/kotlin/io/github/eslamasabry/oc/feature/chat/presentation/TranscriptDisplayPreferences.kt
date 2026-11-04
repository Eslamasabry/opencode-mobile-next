package io.github.eslamasabry.oc.feature.chat.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlinx.coroutines.flow.StateFlow

/** Acknowledged device choices only; the chat never depends on another feature's Settings facade. */
public data class TranscriptDisplayChoices(
    val reasoningExpanded: Boolean = false,
    val timestampsVisible: Boolean = false,
)

public interface TranscriptDisplayPreferences {
    public val value: StateFlow<TranscriptDisplayChoices>
}

@ContributesTo(AppScope::class)
public interface TranscriptDisplayBindings {
    @Multibinds(allowEmpty = true)
    public val transcriptDisplayPreferences: Set<TranscriptDisplayPreferences>
}
