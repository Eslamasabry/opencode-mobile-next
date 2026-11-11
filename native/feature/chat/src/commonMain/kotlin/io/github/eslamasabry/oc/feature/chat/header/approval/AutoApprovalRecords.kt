package io.github.eslamasabry.oc.feature.chat.header.approval

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.requests.consent.AutoApproved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What this phone approved automatically (Flutter `AutoApprovedPermission`, last five per conversation): the
 * transparency half of a setting that removes prompts. Two sweeps answer asks (the chip's own and the connection's), so
 * both write here and the approvals sheet reads one record. Memory only: it goes with the app process, like Flutter's.
 */
public object AutoApprovalRecords {
    private const val KEPT: Int = 5

    private data class Key(val profile: ProfileId, val session: SessionId)

    private val entries = MutableStateFlow<Map<Key, List<AutoApproved>>>(emptyMap())
    private val version = MutableStateFlow(0L)

    /** +1 per recorded approval, so a sheet that is open updates. */
    public val revision: StateFlow<Long> get() = version

    public fun add(profile: ProfileId, entry: AutoApproved) {
        val key = Key(profile, entry.sessionId)
        entries.update { all -> all + (key to ((all[key].orEmpty() + entry).takeLast(KEPT))) }
        version.update { it + 1 }
    }

    /** The latest approvals of [session], newest first. */
    public fun recent(profile: ProfileId, session: SessionId): List<AutoApproved> =
        entries.value[Key(profile, session)].orEmpty().asReversed()
}
