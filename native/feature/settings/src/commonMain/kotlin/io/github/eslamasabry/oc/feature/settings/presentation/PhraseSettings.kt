package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

/** Device-wide switch, shared with the composer's existing Flutter-compatible key. */
public interface PhraseSettings {
    public val enabled: StateFlow<Boolean>

    public suspend fun setEnabled(on: Boolean): Outcome<Unit>
}
