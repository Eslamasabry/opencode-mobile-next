package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Navigation metadata only: never prompts, transcripts or credentials (Flutter `HandoffLink`). */
public data class HandoffLink(
    val id: String,
    val sourceProfileId: String,
    val sourceSessionId: String,
    val sourceOrigin: HandoffOrigin = HandoffOrigin.Server,
    val sourceTitle: String,
    val targetProfileId: String,
    val targetSessionId: String,
    val targetOrigin: HandoffOrigin = HandoffOrigin.Server,
    val targetAgent: String,
    val projectDirectory: String,
    val handoffPath: String,
)

public sealed interface HandoffLinkSave {
    public data object Saved : HandoffLinkSave

    public data class Refused(val reason: HandoffLinkRefusal) : HandoffLinkSave
}

public enum class HandoffLinkRefusal {
    /** A malformed or secret-bearing identifier (Flutter FormatException). */
    Invalid,
    /** Either profile is unknown, closing or was deleted since admission. */
    ProfileUnavailable,
    /** The same link id already holds a different record. */
    Conflict,
    /** The stored links could not be read; never replaced. */
    Unreadable,
    /** The encoded links exceed the storage bound. */
    Full,
}

/**
 * Port of `AgentHandoffLinks` (lib/state/agent_handoff_links.dart). One canonical copy per link
 * under its source profile's key `oc.agentHandoffs.<profileId>`; both conversations find it through
 * [forSession]. One serial lane per store (one store per [AgentPrefs]). Removing a profile removes
 * its references from every surviving owner.
 *
 * Profile membership is read from `oc.profiles` (the profile list's stored JSON), like Flutter.
 */
public class HandoffLinks(
    private val prefs: AgentPrefs,
    private val redactor: Redactor = Redactor(),
) {
    private val lane = Mutex()
    private val closed = mutableSetOf<String>()
    private val epochs = mutableMapOf<String, Int>()

    private fun hasProfile(profileId: String): Boolean {
        val rows =
            AgentJson.parseOrNull(prefs.string(PROFILES_KEY) ?: "[]") as? JsonArray ?: return false
        return rows.any { row ->
            ((row as? JsonObject)?.get("id") as? JsonPrimitive)?.let {
                it.isString && it.content == profileId
            } == true
        }
    }

    private fun admits(profileId: String, epoch: Int? = null): Boolean =
        profileId !in closed &&
            hasProfile(profileId) &&
            (epoch == null || epoch == (epochs[profileId] ?: 0))

    /** Synchronous admission check for the final dispatch fence; closed during deletion. */
    public fun admitsProfiles(sourceProfileId: String, targetProfileId: String): Boolean =
        admits(sourceProfileId) && admits(targetProfileId)

    /**
     * Outgoing and incoming links, in insertion order within an owner. [profileId] is always a
     * durable parent. Pass [origin] when an agent and a server conversation can share one session
     * id. Null: the profile is unavailable or the stored links cannot be read.
     */
    public fun forSession(
        profileId: String,
        sessionId: String,
        origin: HandoffOrigin? = null,
    ): ImmutableList<HandoffLink>? {
        if (!admits(profileId)) return null
        val result = mutableListOf<HandoffLink>()
        for (key in prefs.keys(PREFIX).sorted()) {
            val links = read(key) ?: return null
            for (link in links) {
                if (link.sourceProfileId in closed || link.targetProfileId in closed) continue
                if (!hasProfile(link.sourceProfileId) || !hasProfile(link.targetProfileId)) continue
                val outgoing =
                    link.sourceProfileId == profileId &&
                        link.sourceSessionId == sessionId &&
                        (origin == null || link.sourceOrigin == origin)
                val incoming =
                    link.targetProfileId == profileId &&
                        link.targetSessionId == sessionId &&
                        (origin == null || link.targetOrigin == origin)
                if (outgoing || incoming) result += link
            }
        }
        return result.toImmutableList()
    }

    /**
     * Idempotent for an identical id and record; a conflicting id is refused. Only call after the
     * new conversation exists; a storage failure is reported apart from creation so a retry never
     * creates another chat.
     */
    public suspend fun save(link: HandoffLink): Outcome<HandoffLinkSave> {
        // Validate the original ids before redaction could turn an unsafe id into a safe-looking
        // one.
        val checked = decode(json(link)) ?: return refused(HandoffLinkRefusal.Invalid)
        if (!admits(checked.sourceProfileId) || !admits(checked.targetProfileId)) {
            return refused(HandoffLinkRefusal.ProfileUnavailable)
        }
        val sourceEpoch = epochs[checked.sourceProfileId] ?: 0
        val targetEpoch = epochs[checked.targetProfileId] ?: 0
        return lane.withLock {
            if (
                !admits(checked.sourceProfileId, sourceEpoch) ||
                    !admits(checked.targetProfileId, targetEpoch)
            ) {
                return@withLock refused(HandoffLinkRefusal.ProfileUnavailable)
            }
            val key = keyFor(checked.sourceProfileId)
            val links = read(key) ?: return@withLock refused(HandoffLinkRefusal.Unreadable)
            val existing = links.firstOrNull { it.id == checked.id }
            if (existing != null) {
                return@withLock if (encode(existing) == encode(checked)) HandoffLinkSave.Saved.ok()
                else refused(HandoffLinkRefusal.Conflict)
            }
            when (val written = write(key, links + checked)) {
                is Outcome.Failed -> return@withLock written
                is Outcome.Ok ->
                    written.value?.let {
                        return@withLock refused(it)
                    }
            }
            if (
                !admits(checked.sourceProfileId, sourceEpoch) ||
                    !admits(checked.targetProfileId, targetEpoch)
            ) {
                refused(HandoffLinkRefusal.ProfileUnavailable)
            } else {
                HandoffLinkSave.Saved.ok()
            }
        }
    }

    /** ProfileCloser step 1: synchronous fence before any asynchronous deletion step. */
    public fun stopAdmission(profileId: String) {
        close(profileId)
    }

    /** Called only after a failed deletion drained the lane and kept the saved profile. */
    public fun reopenProfile(profileId: String) {
        if (hasProfile(profileId)) closed -= profileId
    }

    /**
     * Closes admission synchronously, then drains the lane; run before the profile preference
     * sweep.
     */
    public suspend fun closeProfile(profileId: String) {
        close(profileId)
        lane.withLock {}
    }

    private fun close(profileId: String) {
        closed += profileId
        epochs[profileId] = (epochs[profileId] ?: 0) + 1
    }

    /**
     * May remove links partially on failure but never claims cleanup it did not finish; retry is
     * idempotent. No new link involving the closing profile is admitted. Unreadable surviving
     * owners report [HandoffLinkRefusal.Unreadable].
     */
    public suspend fun removeProfile(profileId: String): Outcome<HandoffLinkSave> {
        close(profileId)
        return lane.withLock {
            for (key in prefs.keys(PREFIX).sorted()) {
                if (key == keyFor(profileId)) {
                    val removed = write(key, emptyList())
                    if (removed is Outcome.Failed) return@withLock removed
                    continue
                }
                val links = read(key) ?: return@withLock refused(HandoffLinkRefusal.Unreadable)
                val kept = links.filter { it.targetProfileId != profileId }
                if (kept.size != links.size) {
                    val written = write(key, kept)
                    if (written is Outcome.Failed) return@withLock written
                }
            }
            HandoffLinkSave.Saved.ok()
        }
    }

    /** Only when deletion was aborted and the profile still exists. */
    public suspend fun cancelDeletion(profileId: String) {
        val epoch = epochs[profileId]
        lane.withLock {
            if (epochs[profileId] == epoch && hasProfile(profileId)) closed -= profileId
        }
    }

    private fun refused(reason: HandoffLinkRefusal): Outcome<HandoffLinkSave> =
        HandoffLinkSave.Refused(reason).ok()

    private fun read(key: String): List<HandoffLink>? {
        val raw = prefs.string(key) ?: return emptyList()
        if (raw.length > MAX_STORED) return null
        val envelope = AgentJson.parseOrNull(raw) as? JsonObject ?: return null
        if ((envelope["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull != 1)
            return null
        val rows = envelope["links"] as? JsonArray ?: return null
        val links = rows.map { decode(it) ?: return null }
        if (links.any { keyFor(it.sourceProfileId) != key }) return null
        return links
    }

    /** Null: written. Otherwise the refusal (nothing written). */
    private suspend fun write(key: String, links: List<HandoffLink>): Outcome<HandoffLinkRefusal?> {
        val encoded =
            if (links.isEmpty()) {
                null
            } else {
                AgentJson.encode(
                    JsonObject(
                        mapOf(
                            "version" to JsonPrimitive(1),
                            "links" to JsonArray(links.map(::json)),
                        )
                    )
                )
            }
        if (encoded != null && encoded.length > MAX_STORED) return HandoffLinkRefusal.Full.ok()
        return when (
            val committed = prefs.commit {
                if (encoded == null) remove(key) else putString(key, encoded)
            }
        ) {
            is Outcome.Failed -> committed
            is Outcome.Ok -> null.ok()
        }
    }

    /** Stored form: display metadata masked, identifiers as validated. */
    private fun json(link: HandoffLink): JsonObject =
        JsonObject(
            linkedMapOf(
                "id" to JsonPrimitive(link.id),
                "sourceProfileId" to JsonPrimitive(link.sourceProfileId),
                "sourceSessionId" to JsonPrimitive(link.sourceSessionId),
                "sourceOrigin" to JsonPrimitive(link.sourceOrigin.wire),
                "sourceTitle" to JsonPrimitive(redactor.redact(link.sourceTitle)),
                "targetProfileId" to JsonPrimitive(link.targetProfileId),
                "targetSessionId" to JsonPrimitive(link.targetSessionId),
                "targetOrigin" to JsonPrimitive(link.targetOrigin.wire),
                "targetAgent" to JsonPrimitive(redactor.redact(link.targetAgent)),
                "projectDirectory" to JsonPrimitive(redactor.redact(link.projectDirectory)),
                "handoffPath" to JsonPrimitive(redactor.redact(link.handoffPath)),
            )
        )

    private fun encode(link: HandoffLink): String = AgentJson.encode(json(link))

    private fun decode(value: Any?): HandoffLink? {
        val map = value as? JsonObject ?: return null
        fun raw(name: String): String? {
            val p = map[name] as? JsonPrimitive ?: return null
            return if (p.isString && p.content.length <= MAX_FIELD) p.content else null
        }
        fun field(name: String): String? = raw(name)?.let(redactor::redact)
        fun origin(name: String): HandoffOrigin? {
            val p = map[name] ?: return HandoffOrigin.Server // written before origins were explicit
            val text = (p as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            return HandoffOrigin.fromWire(text)
        }
        for (name in ID_FIELDS) {
            val original = raw(name) ?: return null
            if (
                original.isEmpty() ||
                    original.length > MAX_ID ||
                    redactor.redact(original) != original ||
                    original.any { it.code < 0x20 || it.code == 0x7f }
            ) {
                return null
            }
        }
        return HandoffLink(
            id = field("id") ?: return null,
            sourceProfileId = field("sourceProfileId") ?: return null,
            sourceSessionId = field("sourceSessionId") ?: return null,
            sourceOrigin = origin("sourceOrigin") ?: return null,
            sourceTitle = field("sourceTitle") ?: return null,
            targetProfileId = field("targetProfileId") ?: return null,
            targetSessionId = field("targetSessionId") ?: return null,
            targetOrigin = origin("targetOrigin") ?: return null,
            targetAgent = field("targetAgent") ?: return null,
            projectDirectory = field("projectDirectory") ?: return null,
            handoffPath = field("handoffPath") ?: return null,
        )
    }

    public companion object {
        public const val PREFIX: String = "oc.agentHandoffs."
        public const val PROFILES_KEY: String = "oc.profiles"

        /** `oc.agentHandoffs.<profileId>` (the ProfileScope sweep finds it by prefix). */
        public fun keyFor(profileId: String): String = "$PREFIX$profileId"

        private const val MAX_STORED = 4 * 1024 * 1024
        private const val MAX_FIELD = 4096
        private const val MAX_ID = 256
        private val ID_FIELDS =
            listOf("id", "sourceProfileId", "sourceSessionId", "targetProfileId", "targetSessionId")
    }
}
