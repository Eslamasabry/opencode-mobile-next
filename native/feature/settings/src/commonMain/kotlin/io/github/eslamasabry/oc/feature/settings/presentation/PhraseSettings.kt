package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.StateFlow

public data class PhraseSettingsFacts(
    val enabled: Boolean,
    val hiddenCount: Int,
    val profile: ProfileId?,
    val available: Boolean,
)

public interface PhraseSettings {
    public val facts: StateFlow<PhraseSettingsFacts?>

    public suspend fun setEnabled(on: Boolean): Outcome<Unit>

    public suspend fun showHidden(profile: ProfileId): Outcome<Unit>
}
