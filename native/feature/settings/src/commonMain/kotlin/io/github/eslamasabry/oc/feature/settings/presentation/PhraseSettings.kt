package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.StateFlow

/** What the Phrase suggestions row shows: the switch, and the active profile's hidden phrases. */
public data class PhraseSettingsFacts(
    val enabled: Boolean,
    val hiddenCount: Int,
    val profile: ProfileId?,
    val available: Boolean,
)

/**
 * Device-wide switch, shared with the composer's existing Flutter-compatible key
 * (`oc.phraseChips.enabled`), plus the active profile's hidden phrases.
 */
public interface PhraseSettings {
    /** Null until the store is open. */
    public val facts: StateFlow<PhraseSettingsFacts?>

    /** The acknowledged switch alone (true until the store says otherwise). */
    public val enabled: StateFlow<Boolean>

    public suspend fun setEnabled(on: Boolean): Outcome<Unit>

    public suspend fun showHidden(profile: ProfileId): Outcome<Unit>
}
