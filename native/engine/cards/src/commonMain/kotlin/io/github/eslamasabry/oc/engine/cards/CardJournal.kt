package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome

/** Persistence for the card dispatch journal (`oc.genui.journal.<profileId>`): one JSON string per profile. */
public interface CardJournalStore {
    /** The stored text; throws nothing (an unopened store reads as null). */
    public fun read(profileId: String): String?

    public suspend fun write(profileId: String, json: String?): Outcome<Unit>
}

/**
 * B-CHAT-051 (Flutter `gen_ui_journal.dart`): the cards whose answer may have left the phone. A card in the journal is
 * "not confirmed" until the conversation shows the answer, also after the chat is reopened or the app restarts, so a
 * second send is refused. Only routing identities are kept, never card or answer content. A corrupt record fails
 * closed (every card of the profile reads as dispatched); a closed profile accepts no late write.
 */
public interface CardJournal {
    /** The dispatched identities of [profileId]; null = the saved record is unreadable (fail closed). */
    public fun dispatched(profileId: String): Set<String>?

    public suspend fun put(profileId: String, identity: String): Outcome<Unit>

    public suspend fun remove(profileId: String, identity: String): Outcome<Unit>
}

/** A journal that forgets on restart (tests, previews and graphs without storage). */
public class MemoryCardJournal : CardJournal {
    private val entries = HashMap<String, LinkedHashSet<String>>()

    override fun dispatched(profileId: String): Set<String> = entries[profileId].orEmpty().toSet()

    override suspend fun put(profileId: String, identity: String): Outcome<Unit> {
        entries.getOrPut(profileId) { LinkedHashSet() } += identity
        return Outcome.Ok(Unit)
    }

    override suspend fun remove(profileId: String, identity: String): Outcome<Unit> {
        entries[profileId]?.remove(identity)
        return Outcome.Ok(Unit)
    }
}

/**
 * The journal over a [CardJournalStore], in the Flutter v1 shape `{"v":1,"entries":[{profile, source, directory,
 * workspace, session, message, call, revision, endpoint, dispatch}]}` so a migrated install keeps its guard. Only
 * entries with `dispatch` block a resend. At most [MAX] entries and [MAX_BYTES] bytes.
 */
public class StoredCardJournal(private val store: CardJournalStore) : CardJournal {
    private val closed = HashSet<String>()

    public companion object {
        public const val MAX: Int = 100
        public const val MAX_BYTES: Int = 65_536
        private val FIELDS = listOf("source", "directory", "session", "message", "call")
    }

    /** Entry maps, or null when the record is corrupt. */
    private fun entries(profileId: String): List<Map<String, Any?>>? {
        val raw = store.read(profileId) ?: return emptyList()
        return try {
            if (raw.encodeToByteArray().size > MAX_BYTES) return null
            val root = CardJson.parse(raw) as? Map<*, *> ?: return null
            if (root["v"] != 1L) return null
            val list = root["entries"] as? List<*> ?: return null
            if (list.size > MAX) return null
            list.map { item ->
                val m = item as? Map<*, *> ?: return null
                if (m["profile"] != profileId) return null
                for (f in FIELDS) if ((m[f] as? String).isNullOrEmpty()) return null
                val ws = m["workspace"]
                if (ws != null && ws !is String) return null
                if (m.containsKey("dispatch") && (m["dispatch"] as? String).isNullOrEmpty()) return null
                @Suppress("UNCHECKED_CAST")
                (m as Map<String, Any?>)
            }
        } catch (e: IllegalArgumentException) {
            null // malformed JSON (SerializationException is one) or too deep
        }
    }

    private fun identityOf(e: Map<String, Any?>): String =
        CardJson.encode(listOf(e["profile"], e["source"], e["directory"], e["workspace"], e["session"], e["message"], e["call"]))

    override fun dispatched(profileId: String): Set<String>? =
        entries(profileId)?.filter { it.containsKey("dispatch") }?.map(::identityOf)?.toSet()

    override suspend fun put(profileId: String, identity: String): Outcome<Unit> {
        val parts = parts(identity) ?: return Outcome.Failed(Failure.StorageRefused())
        return update(profileId) { list ->
            val entry =
                linkedMapOf<String, Any?>(
                    "profile" to parts[0], "source" to parts[1], "directory" to parts[2], "workspace" to parts[3],
                    "session" to parts[4], "message" to parts[5], "call" to parts[6],
                    // The native board compares revisions against the transcript itself; the fields stay for the shape.
                    "revision" to "native", "endpoint" to parts[1], "dispatch" to "card",
                )
            if (entry["workspace"] == null) entry.remove("workspace")
            list.filterNot { identityOf(it) == identity } + entry
        }
    }

    override suspend fun remove(profileId: String, identity: String): Outcome<Unit> =
        update(profileId) { list -> list.filterNot { identityOf(it) == identity } }

    private suspend fun update(profileId: String, change: (List<Map<String, Any?>>) -> List<Map<String, Any?>>): Outcome<Unit> {
        if (profileId in closed) return Outcome.Failed(Failure.StorageRefused())
        // A corrupt record is never rewritten from scratch: that would turn "needs review" into permission to resend.
        val previous = entries(profileId) ?: return Outcome.Failed(Failure.StorageRefused())
        val next = change(previous)
        if (next.size > MAX) return Outcome.Failed(Failure.StorageRefused())
        val text = if (next.isEmpty()) null else CardJson.encode(linkedMapOf("v" to 1L, "entries" to next))
        if (text != null && text.encodeToByteArray().size > MAX_BYTES) {
            return Outcome.Failed(Failure.StorageRefused())
        }
        if (profileId in closed) return Outcome.Failed(Failure.StorageRefused())
        return store.write(profileId, text)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parts(identity: String): List<String?>? =
        try {
            val list = CardJson.parse(identity) as? List<Any?> ?: return null
            if (list.size != 7) null else list.map { it as String? }
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: ClassCastException) {
            null
        }

    /** Fences late writes while the profile closes or is deleted; the stored text is left to profile deletion. */
    public fun closeProfile(profileId: String) {
        closed += profileId
    }

    public fun reopenProfile(profileId: String) {
        closed -= profileId
    }
}

/**
 * B-CHAT-039/050/052: the fresh check right before an answer leaves (Flutter `_send`: history recovery, then an explicit
 * idle check). The chat re-reads the conversation (the board sees it through observe) and proves the session idle;
 * a non-null refusal sends nothing.
 */
public fun interface CardDispatchCheck {
    public suspend fun verify(card: AgentCard): CardRefusal?
}
