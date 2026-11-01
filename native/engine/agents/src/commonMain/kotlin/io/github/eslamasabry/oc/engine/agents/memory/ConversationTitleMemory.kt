package io.github.eslamasabry.oc.engine.agents.memory

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText.text
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.serialization.json.JsonObject

/**
 * The title of a conversation whose older helper records were archived (plan 17 §1.8, port of
 * `lib/state/conversation_title_memory.dart`), per saved server, keyed by the runtime's session
 * (`provider:sessionId`), never by the helper's agent id. Titles stay app-side and are never written back to the
 * helper (`update_agent_request` bumps the row to "Just now").
 *
 * Stored as a Flutter StringList under `oc.conversationTitles.<profileId>`, one JSON entry `{"k":…,"t":…}` per
 * conversation, newest last, at most [MAX_ENTRIES]. The profile sweep removes it with the server.
 */
public class ConversationTitleMemory(private val prefs: AgentPrefs) {
    /** Readable entries as stored (line + parsed), or empty when any line is unreadable (Dart reads all or nothing). */
    private fun entries(profile: ProfileId): List<Pair<String, JsonObject>> =
        MemoryLines.read(prefs.stringList(AgentKeys.conversationTitles(profile)))

    /** The saved title of [sessionKey], or null when none or blank. */
    public fun read(profile: ProfileId, sessionKey: String): String? {
        val hit = entries(profile).lastOrNull { it.second.text("k") == sessionKey } ?: return null
        return hit.second.text("t")?.takeIf { it.isNotBlank() }
    }

    /** Saves [title] for [sessionKey]. `Ok(false)` for a blank title, as Dart returns false. */
    public suspend fun remember(profile: ProfileId, sessionKey: String, title: String): Outcome<Boolean> {
        if (title.isBlank()) return Outcome.Ok(false)
        val kept = entries(profile).filter { it.second.text("k") != sessionKey }.map { it.first } +
            DartJsonText.encode(linkedMapOf("k" to sessionKey, "t" to title))
        return MemoryLines.write(prefs, AgentKeys.conversationTitles(profile), kept.takeLast(MAX_ENTRIES))
    }

    public companion object {
        public const val MAX_ENTRIES: Int = 300
    }
}

/** The shared StringList-of-JSON-objects shape of the Flutter memories. */
internal object MemoryLines {
    fun read(raw: List<String>?): List<Pair<String, JsonObject>> {
        if (raw == null) return emptyList()
        val out = ArrayList<Pair<String, JsonObject>>(raw.size)
        for (line in raw) {
            val decoded = DartJsonText.decode(line) ?: return emptyList()
            if (decoded is JsonObject) out += line to decoded
        }
        return out
    }

    suspend fun write(prefs: AgentPrefs, key: String, lines: List<String>): Outcome<Boolean> =
        when (val result = prefs.commit { putStringList(key, lines) }) {
            is Outcome.Ok -> Outcome.Ok(true)
            is Outcome.Failed -> result
        }
}
