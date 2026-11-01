package io.github.eslamasabry.oc.engine.agents.approval

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText.isTrue
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText.text
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * How this phone answers permission requests for one session while connected (AGENTS-BACKENDS-42). [AutoOnce]
 * replies "once" to every incoming permission request, never "always": a saved rule would outlive the session.
 */
public enum class AutoApprovalMode(public val wire: String) {
    Ask("ask"),
    AutoOnce("autoOnce"),
}

/** One session's explicit setting. [inheritToChildren]: sub-agent sessions without their own setting follow it. */
public data class SessionAutoApproval(
    val mode: AutoApprovalMode = AutoApprovalMode.Ask,
    val inheritToChildren: Boolean = false,
) {
    public val automatic: Boolean get() = mode == AutoApprovalMode.AutoOnce

    public companion object {
        public val Ask: SessionAutoApproval = SessionAutoApproval()
        public val AutoInherit: SessionAutoApproval = SessionAutoApproval(AutoApprovalMode.AutoOnce, inheritToChildren = true)
    }
}

/** The setting that applies once inheritance is resolved. */
public data class EffectiveAutoApproval(
    val setting: SessionAutoApproval,
    /** The session carries its own stored setting. */
    val explicit: Boolean,
    /** The ancestor whose inheriting setting reached this session. */
    val inheritedFrom: SessionId? = null,
    /** Neither the session nor an ancestor decided; the server-wide "approve everything" did. */
    val serverWide: Boolean = false,
) {
    public val automatic: Boolean get() = setting.automatic
    public val inherited: Boolean get() = inheritedFrom != null

    public companion object {
        public val AskByDefault: EffectiveAutoApproval = EffectiveAutoApproval(SessionAutoApproval.Ask, explicit = false)
        internal val Everything: EffectiveAutoApproval =
            EffectiveAutoApproval(SessionAutoApproval.AutoInherit, explicit = false, serverWide = true)
    }
}

/**
 * Device-only, per-profile session approval choices (port of `SessionAutoApprovalStore`,
 * `lib/state/session_auto_approval.dart`). Stored as JSON `{"<sessionId>": {"mode","inheritToChildren"}, "*": …}` under
 * `oc.autoApprove.<profileId>`; the agents backend passes its pseudo-profile `<owner>.agents`, giving
 * `oc.autoApprove.<owner>.agents`. The reserved entry [SERVER_WIDE_KEY] holds "approve everything" in the same map, so
 * the profile sweep and [drain] cover it. Writes are serialized; the cache changes only after the commit is accepted.
 */
public class AutoApprovalStore(private val prefs: AgentPrefs) {
    private val cache = mutableMapOf<ProfileId, LinkedHashMap<String, SessionAutoApproval>>()
    private val writes = Mutex()

    private fun load(profile: ProfileId): LinkedHashMap<String, SessionAutoApproval> =
        cache.getOrPut(profile) {
            val decoded = prefs.string(AgentKeys.autoApprove(profile))?.let(DartJsonText::decode) as? JsonObject
            val out = LinkedHashMap<String, SessionAutoApproval>()
            decoded?.forEach { (key, value) ->
                if (key.isNotEmpty()) fromJson(value as? JsonObject)?.let { out[key] = it }
            }
            out
        }

    /** Whether every conversation is approved automatically unless it (or an inheriting ancestor) says otherwise. */
    public fun approvesEverything(profile: ProfileId): Boolean =
        profile.raw.isNotEmpty() && load(profile)[SERVER_WIDE_KEY]?.automatic == true

    public suspend fun setApprovesEverything(profile: ProfileId, value: Boolean): Outcome<Unit> =
        write(profile, SERVER_WIDE_KEY, if (value) SessionAutoApproval.AutoInherit else null)

    public fun explicitFor(profile: ProfileId, session: SessionId): SessionAutoApproval? =
        if (profile.raw.isEmpty()) null else load(profile)[session.raw]

    /** Every session with an explicit setting; "approve everything" is not a conversation. */
    public fun settingsFor(profile: ProfileId): ImmutableMap<SessionId, SessionAutoApproval> =
        if (profile.raw.isEmpty()) {
            persistentMapOf()
        } else {
            load(profile).filterKeys { it != SERVER_WIDE_KEY }.mapKeys { SessionId(it.key) }.toImmutableMap()
        }

    /**
     * The session's own setting, else the nearest ancestor (via [parentOf]) whose setting inherits, else the
     * server-wide choice, else ask. An ancestor with an explicit setting that does not inherit stops the walk (a
     * grandparent cannot reach past an opted-out child). A conversation set to ask keeps asking under "approve
     * everything". Cycles and unknown parents end the walk.
     */
    public fun effectiveFor(profile: ProfileId, session: SessionId, parentOf: (SessionId) -> SessionId?): EffectiveAutoApproval {
        if (profile.raw.isEmpty()) return EffectiveAutoApproval.AskByDefault
        val settings = load(profile)
        settings[session.raw]?.let { return EffectiveAutoApproval(it, explicit = true) }
        val visited = mutableSetOf(session)
        var parent = parentOf(session)
        while (parent != null && parent.raw.isNotEmpty() && visited.add(parent)) {
            val setting = settings[parent.raw]
            if (setting != null) {
                return if (setting.inheritToChildren) {
                    EffectiveAutoApproval(setting, explicit = false, inheritedFrom = parent)
                } else {
                    fallback(profile)
                }
            }
            parent = parentOf(parent)
        }
        return fallback(profile)
    }

    private fun fallback(profile: ProfileId) =
        if (approvesEverything(profile)) EffectiveAutoApproval.Everything else EffectiveAutoApproval.AskByDefault

    /**
     * Stores [setting] for [session], or removes its own entry (null) so it follows its ancestors again. An empty
     * profile or session is refused before anything is read or written; a refused commit keeps the last choice.
     */
    public suspend fun set(profile: ProfileId, session: SessionId, setting: SessionAutoApproval?): Outcome<Unit> =
        write(profile, session.raw, setting)

    private suspend fun write(profile: ProfileId, key: String, setting: SessionAutoApproval?): Outcome<Unit> {
        if (profile.raw.isEmpty() || key.isEmpty()) return Failure.Refused(RefusalReason.StaleScope).failed()
        return writes.withLock {
            val next = LinkedHashMap(load(profile))
            if (setting == null) next.remove(key) else next[key] = setting
            val json = DartJsonText.encode(next.mapValues { (_, v) -> toJson(v) })
            when (val result = prefs.commit { putString(AgentKeys.autoApprove(profile), json) }) {
                is Outcome.Ok -> {
                    cache[profile] = next
                    result
                }
                is Outcome.Failed -> result
            }
        }
    }

    /** Profile deletion waits for accepted writes before its sweep; then [forget] drops the cache. */
    public suspend fun drain(profile: ProfileId) {
        writes.withLock { }
    }

    public fun forget(profile: ProfileId) {
        cache.remove(profile)
    }

    public companion object {
        /** The entry holding the server-wide "approve everything"; no session id can be `*`. */
        public const val SERVER_WIDE_KEY: String = "*"

        internal fun toJson(value: SessionAutoApproval): Map<String, Any> =
            linkedMapOf("mode" to value.mode.wire, "inheritToChildren" to value.inheritToChildren)

        internal fun fromJson(value: JsonObject?): SessionAutoApproval? {
            if (value == null) return null
            val mode = AutoApprovalMode.entries.firstOrNull { it.wire == value.text("mode") } ?: return null
            return SessionAutoApproval(mode, value.isTrue("inheritToChildren"))
        }
    }
}
