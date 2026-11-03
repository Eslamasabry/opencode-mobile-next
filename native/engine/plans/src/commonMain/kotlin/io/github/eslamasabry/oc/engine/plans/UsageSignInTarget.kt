package io.github.eslamasabry.oc.engine.plans

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sign-in a Usage widget row asks for: which server (saved profile id, or `phone` for this phone's agents), which
 * provider, and whether it is a sign-in, a key, or reading usage from a server. IDs and fixed words only travel. Wire
 * value: `usage_target:<action>:<provider>:<profile>` (port of UsageSignInTarget).
 */
public data class UsageSignInTarget(val profileId: String, val providerId: String, val action: String) {
    val wire: String get() = "$PREFIX$action:$providerId:$profileId"

    public companion object {
        public const val PREFIX: String = "usage_target:"
        private val idPattern = Regex("^[A-Za-z0-9._-]{1,64}$")
        private val providerPattern = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")
        private val actions =
            setOf(UsageWidgetSnapshot.SIGN_IN, UsageWidgetSnapshot.ADD_KEY, UsageWidgetSnapshot.SET_UP)

        /** Null for anything malformed. */
        public fun fromWire(value: Any?): UsageSignInTarget? {
            if (value !is String || !value.startsWith(PREFIX)) return null
            val parts = value.substring(PREFIX.length).split(':')
            if (parts.size != 3) return null
            val (action, provider, profile) = parts
            if (action !in actions || !providerPattern.matches(provider) || !idPattern.matches(profile)) return null
            return UsageSignInTarget(profileId = profile, providerId = provider, action = action)
        }
    }
}

/**
 * Holds a widget tap handed over by the platform until the app shell takes it (the Usage part of LaunchShortcut). A
 * repeat tap on the same row still notifies; the target is taken once.
 */
public class UsageTargetInbox {
    private val _arrivals = MutableStateFlow(0L)

    /** Bumps on every accepted tap, including a repeat of the same target. */
    public val arrivals: StateFlow<Long> = _arrivals.asStateFlow()
    private var pending: UsageSignInTarget? = null

    /** A `launched` push from the platform; anything that is not a valid target is dropped. True when accepted. */
    public fun accept(wire: Any?): Boolean {
        val target = UsageSignInTarget.fromWire((wire as? String)?.trim()) ?: return false
        pending = target
        _arrivals.value += 1
        return true
    }

    public val hasPending: Boolean get() = pending != null

    /** The waiting target, once. */
    public fun take(): UsageSignInTarget? = pending.also { pending = null }
}

/** Where a Usage widget row's tap leads. */
public enum class UsageSignInKind { OpenCodeProvider, AgentSignIn, ReadUsageFrom, UsagePage }

public data class UsageSignInDestination(
    val kind: UsageSignInKind,
    val providerId: String? = null,
    val agentId: String? = null,
    val profileId: String? = null,
) {
    public companion object {
        public val Usage: UsageSignInDestination = UsageSignInDestination(UsageSignInKind.UsagePage)
    }
}

private val openCodeProviderIds = mapOf("codex" to "openai", "glm" to "zai-coding-plan", "minimax" to "minimax-coding-plan")

/**
 * Maps a widget row's [target] to a screen. [profileId]/[profileIsOpenCode] describe the saved server the target names
 * (null id when it is gone). [builtInAgent] resolves this phone's agent id for a provider (null when there is none).
 */
public fun usageSignInDestination(
    target: UsageSignInTarget,
    profileId: String?,
    profileIsOpenCode: Boolean,
    builtInAgent: (String) -> String?,
): UsageSignInDestination {
    if (target.action == UsageWidgetSnapshot.SET_UP) {
        return if (profileId == null) {
            UsageSignInDestination.Usage
        } else {
            UsageSignInDestination(UsageSignInKind.ReadUsageFrom, providerId = target.providerId, profileId = profileId)
        }
    }
    if (target.profileId == UsageConnection.PHONE_ID) {
        val agent = builtInAgent(target.providerId) ?: return UsageSignInDestination.Usage
        return UsageSignInDestination(UsageSignInKind.AgentSignIn, agentId = agent)
    }
    if (profileId == null || !profileIsOpenCode) return UsageSignInDestination.Usage
    val id = openCodeProviderIds[target.providerId] ?: return UsageSignInDestination.Usage
    return UsageSignInDestination(UsageSignInKind.OpenCodeProvider, providerId = id)
}

/** A tapped quota alert (`quota` or `quota_reset`): opaque scope only, never quick actions. */
public data class QuotaAlertOpen(val reset: Boolean, val profileId: String, val monitorToken: String)

public object QuotaAlertRoutes {
    private val token = Regex("^[a-f0-9]{64}$")

    /**
     * Parses the platform's open payload for a quota alert. A `quota` alert needs session id `quota`, a profile id and
     * a 64-hex monitor token. Null for any other kind or anything malformed.
     */
    public fun openFromPlatform(value: Map<String, Any?>): QuotaAlertOpen? {
        val kind = value["kind"]
        if (kind != "quota" && kind != "quota_reset") return null
        val session = value["sessionID"]?.toString()?.trim().orEmpty()
        val profile = value["profileID"]?.toString()?.trim().orEmpty()
        val monitorToken = value["monitorToken"]?.toString()?.trim().orEmpty()
        if (session.isEmpty()) return null
        if (kind == "quota" && (session != "quota" || profile.isEmpty() || !token.matches(monitorToken))) return null
        return QuotaAlertOpen(reset = kind == "quota_reset", profileId = profile, monitorToken = monitorToken)
    }

    /** Notification quick actions (allow, deny, reply) exist only for permission and question alerts, never quota. */
    public fun actionAllowed(kind: Any?): Boolean = kind == "permission" || kind == "question"
}
