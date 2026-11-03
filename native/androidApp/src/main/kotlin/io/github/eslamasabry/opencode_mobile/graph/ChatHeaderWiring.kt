package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.requests.consent.SessionApproval
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalCodec
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalStore
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeModel
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeNoteStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * P12 CHAT-HEADER Android bindings: the approval settings live in the adopted Flutter preferences file under
 * `oc.autoApprove.<profileId>` (format unchanged). The profile deletion sweep (`ProfileKeyRule`) owns the key, so a
 * removed server takes its settings with it.
 */
internal class PrefsApprovalStore(private val stores: PreviewStores) : ApprovalStore {
    private val revisions = MutableStateFlow(0L)
    private val writes = Mutex()
    override val revision: StateFlow<Long> = revisions.asStateFlow()

    override fun settings(profile: ProfileId): Map<String, SessionApproval> {
        val stored =
            try {
                stores.requirePrepared()
                stores.prefs.get(ApprovalCodec.key(profile))
            } catch (_: IllegalStateException) {
                null // storage not ready yet: ask first, the safe default
            }
        return ApprovalCodec.decode((stored as? PrefValue.Str)?.value)
    }

    override suspend fun set(profile: ProfileId, session: String, setting: SessionApproval?): Outcome<Unit> =
        writes.withLock {
            if (session.isEmpty()) return@withLock Outcome.Failed(Failure.StorageRefused())
            // Codex chat-header #5: a write after the server was removed must not bring its key back.
            if (stores.profiles.profile(profile) == null)
                return@withLock Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
            val next = settings(profile).toMutableMap()
            if (setting == null) next.remove(session) else next[session] = setting
            val key = ApprovalCodec.key(profile)
            val result =
                stores.prefs.commit(stores.lanes.forProfile(profile)) {
                    if (next.isEmpty()) remove(key) else putString(key, ApprovalCodec.encode(next))
                }
            if (result is Outcome.Ok) revisions.value += 1
            result
        }
}

@BindingContainer
@ContributesTo(AppScope::class)
object ChatHeaderBindings {
    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun approvals(stores: PreviewStores): ApprovalStore = PrefsApprovalStore(stores)

    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun freeNotes(stores: PreviewStores): FreeNoteStore = PrefsFreeNoteStore(stores)
}

/** The free-model note's dismissals (`oc.freeModelNoteDismissed.<profileId>`, a string list, newest last, bounded). */
internal class PrefsFreeNoteStore(private val stores: PreviewStores) : FreeNoteStore {
    private val writes = Mutex()

    private fun kept(profile: ProfileId): List<String> =
        try {
            stores.requirePrepared()
            (stores.prefs.get(FreeModel.key(profile)) as? PrefValue.StrList)?.value.orEmpty()
        } catch (_: IllegalStateException) {
            emptyList()
        }

    override fun dismissed(profile: ProfileId, session: String): Boolean = session in kept(profile)

    override suspend fun dismiss(profile: ProfileId, session: String): Outcome<Unit> =
        writes.withLock {
            if (stores.profiles.profile(profile) == null)
                return@withLock Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
            val next = FreeModel.dismissed(kept(profile), session)
            stores.prefs.commit(stores.lanes.forProfile(profile)) { putStringList(FreeModel.key(profile), next) }
        }
}
