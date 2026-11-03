package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.StartProblem
import io.github.eslamasabry.oc.engine.runtime.server.StartTarget

/** The phone's own server profile as setup's last step needs it. [password] never leaves the start path. */
public data class PhoneProfile(
    val id: ProfileId,
    val password: String,
    /** A legacy (OpenCode 1) profile whose history has not been moved yet. */
    val requiresOpenCode2Migration: Boolean = false,
    /** The move was admitted and the copy is still to be confirmed by the history screen. */
    val openCode2HistoryPending: Boolean = false,
) {
    override fun toString(): String = "PhoneProfile(id=${id.raw}, password=<hidden>)"
}

/** The profile store, as setup sees it (`ensureBuiltinProfile` and `upsert`). */
public interface PhoneSetupProfiles {
    /**
     * The phone's profile: the existing one (a legacy profile keeps its id, so its drafts, keys and team stay its own),
     * or a new one. Renaming the older setup screen's names is the binding's job.
     */
    public suspend fun ensureBuiltinProfile(version: String?): Outcome<PhoneProfile>

    public suspend fun save(profile: PhoneProfile): Outcome<Unit>
}

/** The two server facts the finisher reads besides the start itself. */
public interface PhoneSetupServer {
    /** An unreadable status reads as not running. */
    public suspend fun serverRunning(): Boolean

    public suspend fun stopServer(): Outcome<Unit>
}

/**
 * The last step of a setup job (port of setup_finish.dart `BuiltinSetupFinisher`; B-LINUX-015): start OpenCode inside
 * the app and connect, through the one [PhoneServerStarter] the opening card and healing use. It depends on objects,
 * never on a screen, so it keeps working after the screen that attached it is gone.
 *
 * A server that already runs the right OpenCode with the app connected is left alone (Add tools must not cut a running
 * session); one the job just replaced is stopped first. A legacy profile is started only through the history move
 * admission: refused, nothing launches, the profile stays as it was and still needs the move. Admitted, the profile
 * is saved as moved with its history copy pending, then connected; a save that fails leaves the profile unmoved.
 */
public class PhoneSetupFinisher(
    private val profiles: PhoneSetupProfiles,
    private val server: PhoneSetupServer,
    private val starter: PhoneServerStarter,
    private val connect: suspend (PhoneProfile) -> Outcome<Unit>,
    private val isConnectedTo: (PhoneProfile) -> Boolean,
) : SetupFinishStep {
    override suspend fun finish(request: SetupFinishRequest): Outcome<Unit> {
        // A Termux job must never create or start the in-app Ubuntu profile.
        if (request.host != SetupHost.Builtin) return Failure.Runtime(RuntimeProblem.ServerStartUnavailable).failed()
        val profile = when (val found = profiles.ensureBuiltinProfile(request.version)) {
            is Outcome.Failed -> return found
            is Outcome.Ok -> found.value
        }
        val running = server.serverRunning()
        if (!request.openCodeChanged && running && isConnectedTo(profile)) return Outcome.Ok(Unit)
        // The job replaced the OpenCode program: a server still running is the old process.
        if (request.openCodeChanged && running) server.stopServer()
        val migrating = profile.requiresOpenCode2Migration
        val failure = starter.start(
            StartTarget(profile.id, profile.password, requiresOpenCode2Migration = migrating),
            migrateLegacyHistory = migrating,
        )
        if (failure?.problem == StartProblem.ComponentRecovery) return Failure.Runtime(RuntimeProblem.OwnershipUnknown).failed()
        if (failure != null) return Failure.Runtime(RuntimeProblem.ServerStartUnavailable, failure.details).failed()
        var connected = profile
        if (migrating) {
            connected = profile.copy(requiresOpenCode2Migration = false, openCode2HistoryPending = true)
            if (profiles.save(connected) is Outcome.Failed) return Failure.Runtime(RuntimeProblem.SetupPersistence).failed()
        }
        return connect(connected)
    }
}
