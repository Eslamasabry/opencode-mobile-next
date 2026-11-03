package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.engine.profiles.ProfileKeys
import io.github.eslamasabry.oc.engine.profiles.StoredProfileJson

/**
 * Durable ownership, re-read before every step and after settlement (ports of Flutter `widgetActionIsCurrent`,
 * `widgetStatusIsCurrent` and `readWidgetActionTarget`). [read] returns the acknowledged value of one preference key.
 * A deleted profile, a foreground profile or scope switch, a metadata edit or a replaced row invalidates the tap.
 * Nothing here writes.
 */
public class WidgetRunGuards(private val read: (String) -> String?) {
    /** App-private routing only; never falls back to paths from the public snapshot. */
    public fun target(tap: WidgetTap, fingerprint: String): Map<String, Any?>? {
        val raw = read(TARGET_PREFIX + tap.profileId) ?: return null
        val target = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
        val directory = target["directory"]
        val workspace = target["workspace"]
        if (
            target["profileID"] != tap.profileId ||
                target["scopeToken"] != tap.scopeToken ||
                target["fingerprint"] != fingerprint ||
                (directory != null && directory !is String) ||
                (workspace != null && workspace !is String)
        )
            return null
        return target.entries.associate { (k, v) -> k.toString() to v }
    }

    public fun actionIsCurrent(tap: WidgetTap, fingerprint: String, expectedSnapshot: Map<*, *>? = null): Boolean {
        if (read(ProfileKeys.ACTIVE) != tap.profileId || target(tap, fingerprint) == null) return false
        if (!metadataMatches(tap.profileId, fingerprint)) return false
        val current = DartJson.decodeOrNull(read(SNAPSHOT) ?: return false) as? Map<*, *> ?: return false
        if (current["profileID"] != tap.profileId || current["scopeToken"] != tap.scopeToken) return false
        fun row(source: Map<*, *>): Map<*, *>? =
            source.rows().firstOrNull {
                if (tap.runId.isNotEmpty()) (it["team"] as? Map<*, *>)?.get("runID") == tap.runId
                else it["id"] == tap.sessionId
            }
        val row = row(current) ?: return false
        if (row.actions()?.contains(tap.action) != true) return false
        val expected = expectedSnapshot?.let(::row)
        return when (tap.action) {
            "allowOnce", "reject" -> row["requestID"] == tap.requestId
            "stop" ->
                row["busy"] == true &&
                    row["startedAt"].asLong() == tap.expectedStartedAt &&
                    (expected == null || row["runMarker"] == expected["runMarker"])
            else -> false // Team actions are not part of the OpenCode 2 native app.
        }
    }

    public fun statusIsCurrent(ticket: Map<String, Any?>): Boolean {
        val profile = ticket["profileID"] as? String ?: return false
        val fingerprint = ticket["fingerprint"] as? String ?: return false
        if (read(ProfileKeys.ACTIVE) != profile || !metadataMatches(profile, fingerprint)) return false
        val saved = DartJson.decodeOrNull(read(FOLLOWUP_PREFIX + profile) ?: "{}") as? Map<*, *> ?: return false
        if ((saved[ticket["sessionID"]] as? Map<*, *>)?.get("token") != ticket["token"]) return false
        val current = DartJson.decodeOrNull(read(SNAPSHOT) ?: "{}") as? Map<*, *> ?: return false
        if (
            current["version"].asLong() != 2L ||
                current["profileID"] != profile ||
                current["scopeToken"] != ticket["scopeToken"] ||
                (current["onDemand"] as? Map<*, *>)?.get("fingerprint") != fingerprint
        )
            return false
        val row = current.rows().firstOrNull { it["id"] == ticket["sessionID"] } ?: return false
        return row["busy"] == true &&
            (row["requestID"] as? String).isNullOrEmpty() &&
            row["runMarker"] == ticket["runMarker"] &&
            (row["startedAt"].asLong() ?: 0L) == (ticket["startedAt"].asLong() ?: 0L)
    }

    /** The saved profile metadata (`oc.profiles`) still has this fingerprint. */
    public fun metadataMatches(profile: String, fingerprint: String): Boolean {
        val stored = StoredProfileJson.decodeList(read(ProfileKeys.PROFILES) ?: "[]").firstOrNull { it.id.raw == profile }
        return stored != null && widgetProfileFingerprint(stored) == fingerprint
    }

    public companion object {
        public const val SNAPSHOT: String = "oc.widgetSessions"
        public const val TARGET_PREFIX: String = "oc.widgetTarget."
        public const val FOLLOWUP_PREFIX: String = "oc.widgetFollowup."
    }
}
