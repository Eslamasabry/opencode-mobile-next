package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText.int
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText.isTrue
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText.text
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Saved rows that paint first (AGENTS-BACKENDS-05; port of `_AgentFeedCache`,
 * `lib/state/connection/phone_agents_cache.dart`). Formats are the Flutter ones byte for byte:
 * a JSON list of at most [MAX_ROWS] `{sourceId, sessionID, title, directory, projectName, isGit, at, parentID,
 * agentId, agentLabel, sourceLabel, canReopen}` under `oc.agentFeed.<owner>` (sources `paseo:…`),
 * `oc.mainFeed.<profile>` (source `opencode`) and `oc.sideFeed.<serverId>` (sources `profile:…`).
 * A saved row never claims to be running. Reads never fail: an unreadable value is no rows.
 */
public class SavedChatFeed(private val prefs: AgentPrefs) {
    public fun agentRows(owner: OwnerId): ImmutableList<ChatFeedItem> = decode(prefs.string(AgentKeys.agentFeed(owner)), AGENT_PREFIX)

    public suspend fun saveAgentRows(owner: OwnerId, rows: List<ChatFeedItem>): Outcome<Unit> = write(AgentKeys.agentFeed(owner), rows, keepReopen = true)

    public fun mainRows(profile: ProfileId): ImmutableList<ChatFeedItem> = decode(prefs.string(AgentKeys.mainFeed(profile)), MAIN_SOURCE_ID)

    public suspend fun saveMainRows(profile: ProfileId, rows: List<ChatFeedItem>): Outcome<Unit> = write(AgentKeys.mainFeed(profile), rows, keepReopen = false)

    public fun sideRows(serverId: ProfileId): ImmutableList<ChatFeedItem> = decode(prefs.string(AgentKeys.sideFeed(serverId)), SIDE_PREFIX)

    public suspend fun saveSideRows(serverId: ProfileId, rows: List<ChatFeedItem>): Outcome<Unit> = write(AgentKeys.sideFeed(serverId), rows, keepReopen = false)

    /** `oc.phoneAgentsUsed.<profile>`: agents had conversations last time, so the first paint waits for them. */
    public fun usedBefore(profile: ProfileId): Boolean = prefs.bool(AgentKeys.phoneAgentsUsed(profile)) ?: false

    public suspend fun rememberUsed(profile: ProfileId, used: Boolean): Outcome<Unit> =
        if (used == usedBefore(profile)) Unit.ok() else prefs.commit { putBool(AgentKeys.phoneAgentsUsed(profile), used) }

    /** `oc.agentSignedOut.<owner>`: agents the last read found signed out (id → app name), present from first paint. */
    public fun signedOut(owner: OwnerId): ImmutableMap<AgentId, String> {
        val decoded = prefs.string(AgentKeys.agentSignedOut(owner))?.let(DartJsonText::decode) as? JsonObject
            ?: return persistentMapOf()
        return decoded.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.takeIf { it.isString }?.let { AgentId(k) to it.content }
        }.toMap().toImmutableMap()
    }

    public suspend fun saveSignedOut(owner: OwnerId, agents: Map<AgentId, String>): Outcome<Unit> {
        val key = AgentKeys.agentSignedOut(owner)
        if (agents.isEmpty()) return if (prefs.string(key) == null) Unit.ok() else prefs.commit { remove(key) }
        val encoded = DartJsonText.encode(agents.entries.associate { it.key.raw to it.value })
        return if (prefs.string(key) == encoded) Unit.ok() else prefs.commit { putString(key, encoded) }
    }

    private suspend fun write(key: String, rows: List<ChatFeedItem>, keepReopen: Boolean): Outcome<Unit> {
        val encoded = encode(rows, keepReopen)
        if (prefs.string(key) == encoded) return Unit.ok()
        return prefs.commit { putString(key, encoded) }
    }

    public companion object {
        public const val MAX_ROWS: Int = 200
        public const val MAIN_SOURCE_ID: String = "opencode"
        public const val AGENT_PREFIX: String = "paseo:"
        public const val SIDE_PREFIX: String = "profile:"

        /** Dart `_writeTo`: the first [MAX_ROWS] rows; main and side rows are always saved as reopenable. */
        internal fun encode(rows: List<ChatFeedItem>, keepReopen: Boolean): String =
            DartJsonText.encode(
                rows.take(MAX_ROWS).map { row ->
                    linkedMapOf(
                        "sourceId" to row.sourceId,
                        "sessionID" to row.sessionId,
                        "title" to row.title,
                        "directory" to row.directory,
                        "projectName" to row.projectName,
                        "isGit" to row.isGit,
                        "at" to row.lastActivityMillis,
                        "parentID" to row.parentId,
                        "agentId" to row.agentId.raw,
                        "agentLabel" to row.agentLabel,
                        "sourceLabel" to row.sourceLabel,
                        "canReopen" to (!keepReopen || row.canReopen),
                    )
                },
            )

        /** Dart `_decode`: rows whose sourceId starts with [prefix] and have sessionID, directory and an int `at`. */
        internal fun decode(raw: String?, prefix: String): ImmutableList<ChatFeedItem> {
            val list = raw?.let(DartJsonText::decode) as? JsonArray ?: return persistentListOf()
            return list.mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                val sourceId = entry.text("sourceId")?.takeIf { it.startsWith(prefix) } ?: return@mapNotNull null
                val sessionId = entry.text("sessionID") ?: return@mapNotNull null
                val directory = entry.text("directory") ?: return@mapNotNull null
                val at = entry.int("at") ?: return@mapNotNull null
                ChatFeedItem(
                    sessionId = sessionId,
                    title = entry.text("title"),
                    directory = directory,
                    projectName = entry.text("projectName") ?: directory.substringAfterLast('/'),
                    isGit = entry.isTrue("isGit"),
                    status = ChatStatus.Idle,
                    lastActivityMillis = at,
                    parentId = entry.text("parentID"),
                    agentId = AgentId(entry.text("agentId") ?: "paseo"),
                    agentLabel = entry.text("agentLabel"),
                    sourceId = sourceId,
                    sourceLabel = entry.text("sourceLabel"),
                    canReopen = entry.isTrue("canReopen"),
                )
            }.toImmutableList()
        }

        /** Saved rows of one source, for [NamedChatFeedSource.saved]. */
        public fun rowsFor(rows: List<ChatFeedItem>, sourceId: String): List<ChatFeedItem> = rows.filter { it.sourceId == sourceId }
    }
}
