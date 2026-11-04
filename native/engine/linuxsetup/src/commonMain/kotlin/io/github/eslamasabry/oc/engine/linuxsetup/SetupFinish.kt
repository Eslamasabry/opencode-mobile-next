package io.github.eslamasabry.oc.engine.linuxsetup

/**
 * The in-app server's profile as the finish step sees it (the fields of ServerProfile it reads and writes).
 * [orchestration] is the opaque team configuration ([teamEngineAuth] its engine credential).
 */
public class FinishProfile(
    public val id: String,
    public var name: String,
    public val baseUrl: String,
    public var password: String = "",
    public var username: String = "",
    public var requiresPasswordReentry: Boolean = false,
    public var requiresOpenCode2Migration: Boolean = false,
    public var openCode2HistoryPending: Boolean = false,
    public var orchestration: String? = null,
    public var teamEngineAuth: String = "",
    public var serverVersion: String? = null,
    public val openCode: Boolean = true,
)

/** The saved profiles, as a port (ProfileStore). */
public interface FinishProfiles {
    public val profiles: List<FinishProfile>
    public val activeId: String?

    /** False when storage refused the write. */
    public suspend fun upsert(profile: FinishProfile): Boolean
}

/** What stopped a start (BuiltinStartProblem). */
public enum class FinishStartProblem { Exited, TimedOut, Interrupted, PasswordMissing, PasswordNotSaved, ComponentRecovery, Other }

/** Starting and stopping the in-app server (BuiltinServerStarter + BuiltinLinux), as a port. */
public interface FinishServer {
    public suspend fun status(): HostReply<SetupLinuxStatus>

    public suspend fun stop()

    /** Null when OpenCode answers. */
    public suspend fun start(profile: FinishProfile, migrateLegacyHistory: Boolean): FinishStartProblem?
}

/** Why the finish did not connect. */
public sealed interface SetupFinishFailure {
    /** A Termux job never creates or starts the in-app Ubuntu profile. */
    public data object CannotStart : SetupFinishFailure

    /** The stable code `runtime_ownership_unknown`. */
    public data object RuntimeOwnershipUnknown : SetupFinishFailure

    /** A move to OpenCode 2 could not finish (setupSwitchFailed). */
    public data object SwitchFailed : SetupFinishFailure

    public data class Start(val problem: FinishStartProblem) : SetupFinishFailure

    public data class Connect(val reason: String) : SetupFinishFailure
}

public data class SetupFinishRequest(
    val openCodeChanged: Boolean,
    val host: SetupHostKind = SetupHostKind.Builtin,
    val version: String? = null,
)

/**
 * The last step of a setup job (port of BuiltinSetupFinisher, lib/builtin/setup/setup_finish.dart): start OpenCode
 * inside the app and connect. A server already running the right OpenCode, with the app connected, is left alone; a
 * replaced program stops the old process first. The migration source keeps its identity, credentials and team.
 */
public class SetupFinisher(
    private val store: FinishProfiles,
    private val server: FinishServer,
    private val connect: suspend (FinishProfile) -> String?,
    private val isConnectedTo: (FinishProfile) -> Boolean,
    private val serverUrl: String,
    private val managesServerUrl: (String) -> Boolean,
    private val newId: () -> String,
    private val newPassword: () -> String,
    private val profileName: String = "This phone",
    private val replaceNames: Set<String> = emptySet(),
) {
    public suspend fun finish(request: SetupFinishRequest): SetupFinishFailure? {
        if (request.host != SetupHostKind.Builtin) return SetupFinishFailure.CannotStart
        val profile = ensureProfile(request.version)
        teamFollowsRuntime(profile)
        val running =
            when (val status = server.status()) {
                is HostReply.Ok -> status.value.serverRunning
                is HostReply.Refused -> false
            }
        if (!request.openCodeChanged && running && isConnectedTo(profile)) return null
        if (request.openCodeChanged && running) server.stop()
        val migrating = profile.requiresOpenCode2Migration
        val failure = server.start(profile, migrateLegacyHistory = migrating)
        if (failure == FinishStartProblem.ComponentRecovery) return SetupFinishFailure.RuntimeOwnershipUnknown
        if (failure != null) return if (migrating) SetupFinishFailure.SwitchFailed else SetupFinishFailure.Start(failure)
        if (migrating) {
            profile.requiresOpenCode2Migration = false
            profile.openCode2HistoryPending = true
            if (!store.upsert(profile)) {
                profile.requiresOpenCode2Migration = true
                profile.openCode2HistoryPending = false
                return SetupFinishFailure.SwitchFailed
            }
        }
        return connect(profile)?.let(SetupFinishFailure::Connect)
    }

    /** The in-app profile: the active one, else a migration source with unfinished history, else the first. */
    public fun findProfile(): FinishProfile? {
        val candidates = store.profiles.filter { it.openCode && managesServerUrl(it.baseUrl) }
        return candidates.firstOrNull { it.id == store.activeId }
            ?: candidates.firstOrNull { it.requiresOpenCode2Migration || it.openCode2HistoryPending }
            ?: candidates.firstOrNull()
    }

    private suspend fun ensureProfile(version: String?): FinishProfile {
        val existing = findProfile()
        if (existing != null && existing.name in replaceNames) existing.name = profileName
        val profile = existing ?: FinishProfile(id = newId(), name = profileName, baseUrl = serverUrl)
        profile.username = "opencode"
        if (profile.password.isEmpty() || profile.requiresPasswordReentry) {
            profile.password = newPassword()
            profile.requiresPasswordReentry = false
        }
        if (version != null) profile.serverVersion = version
        store.upsert(profile)
        return profile
    }

    /** Only a target without its own team adopts an older alias's team, which then leaves the alias. */
    private suspend fun teamFollowsRuntime(target: FinishProfile) {
        if (target.orchestration != null) return
        for (from in store.profiles.toList()) {
            if (from.id == target.id || !(from.openCode && managesServerUrl(from.baseUrl))) continue
            val config = from.orchestration ?: continue
            target.orchestration = config
            target.teamEngineAuth = from.teamEngineAuth
            from.orchestration = null
            from.teamEngineAuth = ""
            store.upsert(target)
            store.upsert(from)
            return
        }
    }
}
