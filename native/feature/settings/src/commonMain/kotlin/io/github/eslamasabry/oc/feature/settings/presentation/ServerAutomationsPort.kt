package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerAutomation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Live server automation reads and controls for a saved server profile (B-605). */
public interface ServerAutomationsSource {
    public suspend fun automations(profile: ProfileId): Outcome<ImmutableList<ServerAutomation>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("serverAutomations")))

    public suspend fun pauseAutomation(profile: ProfileId, id: String): Outcome<ServerAutomation> =
        Outcome.Failed(Failure.Unavailable(FeatureId("serverAutomations")))

    public suspend fun resumeAutomation(profile: ProfileId, id: String): Outcome<ServerAutomation> =
        Outcome.Failed(Failure.Unavailable(FeatureId("serverAutomations")))

    public suspend fun deleteAutomation(profile: ProfileId, id: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("serverAutomations")))
}

/** Feedback banner for a server automations action (B-605). */
public sealed interface AutomationsNotice {
    public data class StoppedAll(val stopped: Int) : AutomationsNotice

    public data class StoppedPartial(val stopped: Int, val total: Int) : AutomationsNotice

    public data object ActionFailed : AutomationsNotice
}

internal fun sortAutomations(items: List<ServerAutomation>): ImmutableList<ServerAutomation> =
    items
        .sortedWith(
            compareBy<ServerAutomation>(
                { it.status.ordinal },
                { it.nextDueAtMs ?: Long.MAX_VALUE },
                { it.name.lowercase() },
            )
        )
        .toImmutableList()
