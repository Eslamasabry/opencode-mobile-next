package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which conversations' newest turn the person stopped, kept on disk per server (`oc.stoppedTurns.<profileId>`).
 * OpenCode 2 reports a stopped run and a run lost while the app was away the same way ("interrupted"), so the
 * person's own Stop is the only thing that tells them apart; held in memory it was forgotten at every restart and a
 * stopped row came back reading "Interrupted".
 */
internal class StoppedTurnMemory(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val profile: ProfileId,
    private val scope: CoroutineScope,
) {
    private val key = PREFIX + profile.raw
    val refs = MutableStateFlow(read())

    fun add(ref: ConversationRef) = change { it + ref }

    fun remove(ref: ConversationRef) = change { it - ref }

    private fun change(edit: (Set<ConversationRef>) -> Set<ConversationRef>) {
        val before = refs.value
        refs.update(edit)
        val after = refs.value
        if (after == before) return
        val ids = after.filter { it.profileId == profile }.map { it.sessionId.raw }.takeLast(MAX)
        scope.launch {
            store.commit(lane) { if (ids.isEmpty()) remove(key) else putString(key, ids.joinToString(",")) }
        }
    }

    private fun read(): Set<ConversationRef> =
        ((store.get(key) as? PrefValue.Str)?.value ?: "")
            .split(',')
            .filter { it.isNotBlank() }
            .mapTo(linkedSetOf()) { ConversationRef(profile, SessionId(it)) }

    companion object {
        const val PREFIX = "oc.stoppedTurns."
        private const val MAX = 200
    }
}
