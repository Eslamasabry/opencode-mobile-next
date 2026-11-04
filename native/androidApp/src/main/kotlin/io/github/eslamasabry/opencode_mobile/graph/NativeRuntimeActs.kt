package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActKind
import kotlin.time.Instant
import kotlinx.coroutines.withContext

/**
 * Confirmed restarts use the same admitted history owner as the Inbox (Activity's durable writer);
 * archived receipts never revive an inverse.
 */
@Inject
@SingleIn(AppScope::class)
class NativeRuntimeActs(
    private val stores: PreviewStores,
    private val activities: AppActivityHistory,
    private val dispatchers: AppDispatchers,
) {
    suspend fun recordRestart(
        profileId: String,
        eventId: String,
        atMillis: Long,
        undo: (suspend () -> Boolean)? = null,
    ): Outcome<Unit> =
        withContext(dispatchers.main) {
            if (
                !PROFILE.matches(profileId) ||
                    eventId.isEmpty() ||
                    eventId.length > 256 ||
                    atMillis <= 0
            )
                return@withContext refused()
            val id = ProfileId(profileId)
            val profile =
                stores.profiles.profile(id)
                    ?: return@withContext Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
            val activity =
                activities.forProfile(id)
                    ?: return@withContext Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
            val recorded =
                activity.record(
                    eventId = eventId,
                    location = DartJson.encode(listOf(profile.profile.baseUrl)),
                    kind = AutomaticActKind.Restart,
                    summary = profile.profile.name,
                    occurredAt = Instant.fromEpochMilliseconds(atMillis),
                    undo = undo,
                )
            if (recorded) Outcome.Ok(Unit) else refused()
        }

    private fun refused(): Outcome.Failed = Outcome.Failed(Failure.StorageRefused())

    private companion object {
        val PROFILE = Regex("[A-Za-z0-9_-]{1,128}")
    }
}
