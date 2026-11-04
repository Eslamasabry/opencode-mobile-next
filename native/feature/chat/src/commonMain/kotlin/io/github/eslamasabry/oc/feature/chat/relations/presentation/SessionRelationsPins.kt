package io.github.eslamasabry.oc.feature.chat.relations.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.settings.pins.SessionPinStore

/** One store/cache and serial writer for every related-conversation page, over the adopted native preferences. */
@SingleIn(AppScope::class)
@Inject
public class SessionRelationsPins(stores: Set<DeliveryStorage>) {
    private val storage = stores.singleOrNull()
    private val store = storage?.let { SessionPinStore(it.store, it.lanes) }

    public fun available(profile: ProfileId): Boolean {
        val exists = storage?.profiles?.exists(profile) == true
        if (!exists) store?.forget(profile)
        return exists && store != null
    }

    public fun isPinned(profile: ProfileId, scope: String, id: String): Boolean =
        available(profile) && store?.ids(profile, scope)?.contains(id) == true

    public suspend fun setPinned(profile: ProfileId, scope: String, id: String, pinned: Boolean): Outcome<Unit> {
        if (!available(profile)) return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        return store!!.setPinned(profile, scope, id, pinned)
    }
}
