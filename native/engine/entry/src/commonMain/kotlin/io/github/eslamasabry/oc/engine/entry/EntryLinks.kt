package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * The validators 07 §7.1 reuses unchanged, ported from the Flutter app:
 * - `SessionLinkIngress.accepts` (android/…/SessionLinkIngress.kt);
 * - `SessionResumeCommand.isSafeIdentifier` and `SessionLink.parse` (lib/domain/session_handoff.dart);
 * - `TeamLink.isSafeId` and `TeamLink.parse` (lib/domain/team_link.dart);
 * - `ScheduledPromptPolicy.validId` (android/…/ScheduledPromptPolicy.kt);
 * - the `LaunchShortcutChannel` whitelist and `usage_target` regex.
 * Nothing here throws, decodes percent-escapes into ids, or logs.
 */
public object EntryValidators {
    private val safeIdentifier = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val teamId = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val scheduleId = Regex("^[A-Za-z0-9_.-]{1,128}$")
    private val monitorToken = Regex("^[a-f0-9]{64}$")

    public val usageTarget: Regex =
        Regex("^usage_target:(sign_in|add_key|set_up):([a-z0-9][a-z0-9_-]{0,31}):([A-Za-z0-9._-]{1,64})$")

    public val launchActions: Map<String, LaunchAction> =
        mapOf(
            "connect" to LaunchAction.Connect,
            "new_task" to LaunchAction.NewTask,
            "activity" to LaunchAction.Activity,
            "usage" to LaunchAction.Usage,
            "usage_settings" to LaunchAction.UsageSettings,
            "background_settings" to LaunchAction.BackgroundSettings,
            "phone_setup" to LaunchAction.PhoneSetup,
            "phone_setup_done" to LaunchAction.PhoneSetupDone,
            "linux" to LaunchAction.Linux,
        )

    /** `SessionLinkIngress.accepts`: non-empty, ≤ 1,024 chars, every char `0x21..0x7e`. */
    public fun ingressAccepts(value: String): Boolean =
        value.isNotEmpty() && value.length <= EntryLimits.LINK_MAX && value.all { it.code in 0x21..0x7e }

    public fun isSafeIdentifier(value: String): Boolean = safeIdentifier.matches(value)

    public fun isTeamId(value: String): Boolean = teamId.matches(value) && !value.endsWith(":")

    public fun isScheduleId(value: String): Boolean = scheduleId.matches(value)

    public fun isMonitorToken(value: String): Boolean = monitorToken.matches(value)

    /** The `oc.shortcut` value → action, or null (unknown values are consumed and do nothing, B-ANDR-028). */
    public fun launchAction(value: String): LaunchAction? {
        val trimmed = value.trim()
        launchActions[trimmed]?.let { return it }
        val match = usageTarget.matchEntire(trimmed) ?: return null
        val kind = UsageTargetKind.entries.first { it.wire == match.groupValues[1] }
        return LaunchAction.UsageTarget(kind, match.groupValues[2], ProfileId(match.groupValues[3]))
    }
}

/** A parsed `opencode-mobile://` link (07 §7.6). Address links (`session/v2`) are out of S2-ENTRY (N4-O4). */
public sealed interface ParsedLink {
    public data class Session(val profileId: ProfileId, val sessionId: SessionId) : ParsedLink

    public data class TeamGate(val profileId: ProfileId, val gateId: String) : ParsedLink

    public data class TeamRun(val profileId: ProfileId, val runId: String) : ParsedLink
}

/**
 * Exact-shape parsers (B-NAV-063). Every rejection returns null, never throws. Like Dart's `Uri`, scheme and host
 * compare case-insensitively; query values are taken literally (a `%` makes an id unsafe, so nothing is decoded).
 */
public object EntryLinkParser {
    private const val SCHEME = "opencode-mobile"

    public fun parse(raw: String): ParsedLink? {
        val text = raw.trim()
        if (text.isEmpty() || text.length > EntryLimits.LINK_MAX) return null
        val parts = split(text) ?: return null
        return when (parts.host.lowercase()) {
            "session" -> session(parts)
            "team" -> team(parts)
            else -> null
        }
    }

    /** `opencode-mobile://session?profile=<P>&session=<S>`, round-trips with [parse]. Null for unsafe ids. */
    public fun sessionLink(profileId: ProfileId, sessionId: SessionId): String? {
        if (!EntryValidators.isSafeIdentifier(profileId.raw) || !EntryValidators.isSafeIdentifier(sessionId.raw)) {
            return null
        }
        return "$SCHEME://session?profile=${profileId.raw}&session=${sessionId.raw}"
    }

    private class Parts(val host: String, val path: String, val query: String?)

    private fun split(text: String): Parts? {
        val schemeEnd = text.indexOf("://")
        if (schemeEnd <= 0 || !text.substring(0, schemeEnd).equals(SCHEME, ignoreCase = true)) return null
        val rest = text.substring(schemeEnd + 3)
        if ('#' in rest) return null // fragment
        val queryStart = rest.indexOf('?')
        val beforeQuery = if (queryStart >= 0) rest.substring(0, queryStart) else rest
        val query = if (queryStart >= 0) rest.substring(queryStart + 1) else null
        val slash = beforeQuery.indexOf('/')
        val authority = if (slash >= 0) beforeQuery.substring(0, slash) else beforeQuery
        val path = if (slash >= 0) beforeQuery.substring(slash) else ""
        if ('@' in authority || ':' in authority || authority.isEmpty()) return null // user info or port
        return Parts(authority, path, query)
    }

    private fun session(parts: Parts): ParsedLink? {
        if (parts.path.isNotEmpty() && parts.path != "/") return null
        val query = parts.query ?: return null
        val values = mutableMapOf<String, MutableList<String>>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = if (eq >= 0) pair.substring(0, eq) else pair
            val value = if (eq >= 0) pair.substring(eq + 1) else ""
            values.getOrPut(key) { mutableListOf() } += value
        }
        val profile = values["profile"]?.singleOrNull() ?: return null
        val session = values["session"]?.singleOrNull() ?: return null
        if (!EntryValidators.isSafeIdentifier(profile) || !EntryValidators.isSafeIdentifier(session)) return null
        return ParsedLink.Session(ProfileId(profile), SessionId(session))
    }

    private fun team(parts: Parts): ParsedLink? {
        if (parts.query != null) return null
        val segments = parts.path.removePrefix("/").split('/')
        if (segments.size != 3) return null
        val (kind, profile, id) = segments
        if (!EntryValidators.isSafeIdentifier(profile) || !EntryValidators.isTeamId(id)) return null
        return when (kind) {
            "gate" -> ParsedLink.TeamGate(ProfileId(profile), id)
            "run" -> ParsedLink.TeamRun(ProfileId(profile), id)
            else -> null
        }
    }
}
