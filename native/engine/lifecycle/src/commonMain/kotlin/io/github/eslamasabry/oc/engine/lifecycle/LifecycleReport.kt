package io.github.eslamasabry.oc.engine.lifecycle

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of lib/state/lifecycle_report.dart (LifecycleReportController). Readiness observed now, never inferred from
// Android's historical exit record. A process or PID alone is never evidence of recovery: the in-app server must
// answer an authenticated health check, and a team needs its process, supervisor and city health.

/** Readiness observed now. */
public enum class LifecycleReadiness { Unchecked, Checking, Unavailable, Stopped, Unreachable, Partial, Ready }

/**
 * Presentation-safe facts for a one-line recovery notice. Holds no native description, server reply, profile or
 * credential. [everythingBack] means the server and the services that ran before answer health checks; it does not
 * promise interrupted agent tasks resumed.
 */
public data class LifecycleReport(
    val readiness: LifecycleReadiness = LifecycleReadiness.Unchecked,
    val exitKind: AppExitKind? = null,
    val stoppedAtMillis: Long? = null,
    val hadServices: Boolean = false,
    val dismissed: Boolean = false,
) {
    val showNotice: Boolean get() = !dismissed && hadServices && (exitKind?.notable ?: false)
    val everythingBack: Boolean get() = showNotice && readiness == LifecycleReadiness.Ready
}

/** The saved profile facts the check needs. The password is read once, before any wait, and never published. */
public class LifecycleProfile(
    public val id: String,
    public val baseUrl: String,
    public val backend: PhoneServerBackend = PhoneServerBackend.OpenCode,
    public val username: String = "",
    public val password: String = "",
    public val requiresPasswordReentry: Boolean = false,
    /** The profile's orchestration is the built-in team (BuiltinTeam.isBuiltinConfig). */
    public val teamConfigured: Boolean = false,
) {
    override fun toString(): String = "LifecycleProfile(id=$id)"
}

/** The in-app Linux as the lifecycle check sees it. */
public data class PhoneLinuxSnapshot(
    val installed: Boolean,
    val serverRunning: Boolean,
    val services: List<String> = emptyList(),
) {
    public fun serviceRunning(name: String): Boolean = name in services
}

public fun interface LaunchHistorySource {
    public suspend fun launchReport(): Outcome<AppLaunchReport>
}

public fun interface PhoneLinuxStatusSource {
    public suspend fun status(): Outcome<PhoneLinuxSnapshot>
}

/** An authenticated health probe of the in-app server: true only when it answers OK. */
public fun interface PhoneServerHealthProbe {
    public suspend fun answers(baseUrl: String, username: String, password: String): Boolean
}

/** The phone team's health (BuiltinTeam.supervisorAnswers / cityAnswers). */
public interface PhoneTeamHealth {
    public suspend fun supervisorAnswers(): Boolean

    public suspend fun cityAnswers(): Boolean
}

/** BuiltinTeam's health over a plain GET: `{"status":"ok"}` at /health and /v0/city/<city>/health. */
public class HttpPhoneTeamHealth(
    private val get: suspend (path: String) -> String?,
    private val city: String = CITY,
) : PhoneTeamHealth {
    override suspend fun supervisorAnswers(): Boolean = answersOk("/health")

    override suspend fun cityAnswers(): Boolean = answersOk("/v0/city/$city/health")

    private suspend fun answersOk(path: String): Boolean {
        val body = get(path) ?: return false
        val decoded =
            try {
                Json.parseToJsonElement(body)
            } catch (_: SerializationException) {
                return false
            } catch (_: IllegalArgumentException) {
                return false
            }
        return ((decoded as? JsonObject)?.get("status") as? JsonPrimitive)?.let { it.isString && it.content == "ok" } ?: false
    }

    public companion object {
        public const val CITY: String = "phone"
        public const val PORT: Int = 8472
    }
}

/**
 * Foreground, read-only companion to the phone server's start path. Call [refresh] after the start/reconnect path
 * completes and [invalidate] before a start, on backgrounding, or on profile change or deletion. It never starts a
 * process, schedules retries, writes preferences or extends a background budget.
 */
public class LifecycleReportController(
    private val linux: PhoneLinuxStatusSource,
    private val history: LaunchHistorySource,
    private val probe: PhoneServerHealthProbe,
    private val team: PhoneTeamHealth,
    private val support: PhoneHostSupport = PhoneHostSupport.Android,
) {
    private val state = MutableStateFlow(LifecycleReport())
    public val value: StateFlow<LifecycleReport> = state.asStateFlow()

    private var launch: AppLaunchReport? = null
    private var epoch = 0
    private var disposed = false
    private var dismissed = false

    /** Removes previous health evidence now and rejects in-flight results. */
    public fun invalidate() {
        if (disposed) return
        epoch++
        publish(LifecycleReadiness.Unchecked)
    }

    /** Dismissal lasts for this app process; nothing is persisted. */
    public fun dismiss() {
        if (disposed || dismissed) return
        dismissed = true
        publish(state.value.readiness)
    }

    /** Checks only the in-app phone server, with its saved sign-in. Remote and Termux profiles are never probed. */
    public suspend fun refresh(profile: LifecycleProfile?) {
        if (disposed) return
        val mine = ++epoch
        fun current() = !disposed && mine == epoch
        val candidate = profile?.let { PhoneServerCandidate(it.id, it.baseUrl, it.backend) }
        if (profile == null || !PhoneOwnServers.looksLikeInAppServer(candidate, support)) {
            publish(LifecycleReadiness.Unavailable)
            return
        }
        val username = profile.username
        val password = profile.password
        val reentry = profile.requiresPasswordReentry
        val teamConfigured = profile.teamConfigured
        publish(LifecycleReadiness.Checking)
        val report =
            launch ?: when (val read = history.launchReport()) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> {
                    if (current()) publish(LifecycleReadiness.Unavailable)
                    return
                }
            }
        if (!current()) return
        launch = report
        val status =
            when (val read = linux.status()) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> {
                    if (current()) publish(LifecycleReadiness.Unavailable)
                    return
                }
            }
        if (!current()) return
        if (!status.installed) return publish(LifecycleReadiness.Unavailable)
        if (!status.serverRunning) return publish(LifecycleReadiness.Stopped)
        if (password.isEmpty() || reentry) return publish(LifecycleReadiness.Unreachable)
        val healthy = probe.answers(PhoneOwnServers.IN_APP_URL, username, password)
        if (!current()) return
        if (!healthy) return publish(LifecycleReadiness.Unreachable)
        // Unknown services cannot silently count as recovered.
        if (report.previousServices.any { it != SERVER_SERVICE && it != TEAM_SERVICE }) {
            return publish(LifecycleReadiness.Partial)
        }
        val teamWasRunning = TEAM_SERVICE in report.previousServices
        if (teamWasRunning || teamConfigured) {
            if (!teamConfigured || !status.serviceRunning(TEAM_SERVICE)) return publish(LifecycleReadiness.Partial)
            val supervisor = team.supervisorAnswers()
            if (!current()) return
            val city = supervisor && team.cityAnswers()
            if (!current()) return
            if (!city) return publish(LifecycleReadiness.Partial)
        }
        publish(LifecycleReadiness.Ready)
    }

    /** After disposal no in-flight check publishes. */
    public fun dispose() {
        disposed = true
        epoch++
    }

    private fun publish(readiness: LifecycleReadiness) {
        val history = launch
        state.value =
            LifecycleReport(
                readiness = readiness,
                exitKind = history?.exit?.kind,
                stoppedAtMillis = history?.exit?.timestampMillis,
                hadServices = history?.previousServices?.isNotEmpty() ?: false,
                dismissed = dismissed,
            )
    }

    public companion object {
        /** BuiltinLinux.serverServiceName. */
        public const val SERVER_SERVICE: String = "server"

        /** BuiltinTeam.serviceName. */
        public const val TEAM_SERVICE: String = "aiteam"
    }
}
