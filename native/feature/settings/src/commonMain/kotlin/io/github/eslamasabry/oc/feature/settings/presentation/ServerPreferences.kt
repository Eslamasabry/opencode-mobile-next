package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ModelRef
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

public data class ServerModelChoice(
    val model: ModelRef,
    val name: String,
    val provider: String,
    val variants: ImmutableList<String>,
)

public data class ServerLocalFacts(
    val model: ModelRef?,
    val watching: Boolean,
    val notifications: Boolean,
    val monitorSupported: Boolean,
    val sharingRead: Boolean,
    val sharingDefault: Boolean,
    val sharingSaving: Boolean,
    val variant: String = "",
) {
    public val hasOverrides: Boolean
        get() = model != null || (watching && !notifications) || sharingRead != sharingDefault
}

public interface ServerPreferencesSource {
    public fun watch(profile: ProfileId): Flow<Outcome<ServerLocalFacts>>

    public suspend fun models(profile: ProfileId): Outcome<ImmutableList<ServerModelChoice>>

    public suspend fun selectModel(
        profile: ProfileId,
        model: ModelRef?,
        variant: String,
    ): Outcome<Unit>

    public suspend fun watching(profile: ProfileId, enabled: Boolean): Outcome<Unit>

    public suspend fun notifications(profile: ProfileId, enabled: Boolean): Outcome<Unit>

    public suspend fun shareRead(profile: ProfileId, enabled: Boolean): Outcome<Unit>

    public suspend fun reset(profile: ProfileId): Outcome<Unit>
}
