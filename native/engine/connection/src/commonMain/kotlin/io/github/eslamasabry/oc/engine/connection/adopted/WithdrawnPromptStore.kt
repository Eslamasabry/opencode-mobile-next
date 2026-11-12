package io.github.eslamasabry.oc.engine.connection.adopted

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The copies of messages the chat stopped and sent again ("Stop this step" on the steer note), kept so the words show
 * once after a restart too (Flutter `WithdrawnPromptMemory`). Stored under `oc.withdrawnPrompts.<profileId>` as
 * `<sessionID>|<messageID>` entries, newest last, at most [MAX_ENTRIES]; the profile sweep removes the key with the
 * server and [forget] removes one conversation's entries when it is deleted.
 */
public class WithdrawnPromptStore(private val store: KeyValueStore, private val lanes: WriterLanes) {
    private val writes = Mutex()
    private val keys = AdoptedChatKeys(store)

    /** The withdrawn message ids of [session]. */
    public fun read(profile: ProfileId, session: String): Set<String> = keys.withdrawnPrompts(profile, session)

    /** Saves that [messageId] was withdrawn; a refused write leaves the old list. */
    public suspend fun remember(profile: ProfileId, session: String, messageId: String): Outcome<Unit> =
        writes.withLock {
            val entry = "$session|$messageId"
            val kept = (current(profile) - entry + entry).takeLast(MAX_ENTRIES)
            store.commit(lanes.forProfile(profile)) { putStringList(AdoptedChatKeys.withdrawnPromptsKey(profile), kept) }
        }

    /** Drops every entry of [session] (the conversation was deleted). */
    public suspend fun forget(profile: ProfileId, session: String): Outcome<Unit> =
        writes.withLock {
            val all = current(profile)
            val kept = all.filterNot { it.startsWith("$session|") }
            if (kept.size == all.size) return@withLock Outcome.Ok(Unit)
            val key = AdoptedChatKeys.withdrawnPromptsKey(profile)
            store.commit(lanes.forProfile(profile)) { if (kept.isEmpty()) remove(key) else putStringList(key, kept) }
        }

    private fun current(profile: ProfileId): List<String> =
        (store.get(AdoptedChatKeys.withdrawnPromptsKey(profile)) as? PrefValue.StrList)?.value.orEmpty()

    public companion object {
        /** Flutter `WithdrawnPromptMemory.maxEntries`. */
        public const val MAX_ENTRIES: Int = 300
    }
}
