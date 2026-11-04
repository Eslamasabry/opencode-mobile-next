package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the phrase chips switch knows about the active server (hidden chips can be shown again). */
public data class PhraseSettingsFacts(
    val enabled: Boolean,
    val hiddenCount: Int,
    val profile: ProfileId?,
    val available: Boolean,
)

/** Device-wide switch, shared with the composer's existing Flutter-compatible key. */
public interface PhraseSettings {
    public val enabled: StateFlow<Boolean>

    /** Hidden-chip facts for the active server; null when the host does not track them. */
    public val facts: StateFlow<PhraseSettingsFacts?>
        get() = MutableStateFlow(null)

    public suspend fun setEnabled(on: Boolean): Outcome<Unit>

    /** Shows the chips this person hid on [profile] again. */
    public suspend fun showHidden(profile: ProfileId): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phrases")))
}
