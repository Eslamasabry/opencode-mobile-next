package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActKind
import kotlin.time.Instant

/** Confirmed restarts use the same admitted history owner as the Inbox. */
@Inject
@SingleIn(AppScope::class)
class NativeRuntimeActs(
    private val stores: PreviewStores,
    private val activity: AppActivityHistory,
) {
    suspend fun recordRestart(profileId: String, eventId: String, atMillis: Long): Outcome<Unit> {
        if (eventId.isEmpty() || eventId.length > 256 || atMillis <= 0)
            return Outcome.Failed(Failure.StorageRefused())
        val id = ProfileId(profileId)
        val profile =
            stores.profiles.profile(id)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        val history =
            activity.forProfile(id)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        return if (
            history.record(
                eventId,
                DartJson.encode(listOf(profile.profile.baseUrl)),
                AutomaticActKind.Restart,
                profile.profile.name,
                Instant.fromEpochMilliseconds(atMillis),
            )
        )
            Unit.ok()
        else Outcome.Failed(Failure.StorageRefused())
    }
}
