package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome

/** What the app needs from the phone to turn the team on (Dart `PhoneTeamSetupPorts`). */
public interface PhoneTeamPorts {
    /** Stops the phone's OpenCode so the engine can prove the phone safe (it restarts it protected afterwards). */
    public suspend fun stopServer(): Outcome<Unit>

    /** Closes the terminals: a terminal opened before protection cannot be vouched for. */
    public suspend fun closeTerminals(): Outcome<Unit>

    /** Starts the bundled engine, which proves the boundary; its loopback address and bearer token. */
    public suspend fun startEngine(): Outcome<PhoneEngineAddress>

    public suspend fun stopEngine()

    /** Starts OpenCode again, now protected. */
    public suspend fun startServer(): Outcome<Unit>

    /** Keeps the token in the Keystore and the team config on the profile. */
    public suspend fun saveTeam(address: PhoneEngineAddress): Outcome<Unit>
}

/** The engine's loopback [baseUrl] and its bearer token (never logged). */
public class PhoneEngineAddress(public val baseUrl: String, public val token: String) {
    override fun toString(): String = "PhoneEngineAddress($baseUrl)"
}

/**
 * Flutter's one-tap "Turn on AI Team on this phone" (PhoneTeamSetupController.run): stop OpenCode and the terminals, let
 * the engine prove this phone safe, bring OpenCode back protected, then keep the credential and the team config.
 * A failure after OpenCode was stopped brings it back and stops the engine, so a failed turn-on leaves the phone as it was.
 */
public class PhoneTeamTurnOn(private val ports: PhoneTeamPorts) {
    public suspend fun run(): Outcome<Unit> {
        ports.stopServer().let { if (it is Outcome.Failed) return it }
        val closed = ports.closeTerminals()
        if (closed is Outcome.Failed) return undo(closed)
        val address =
            when (val started = ports.startEngine()) {
                is Outcome.Ok -> started.value
                is Outcome.Failed -> return undo(started)
            }
        if (TeamConfig.loopback(address.baseUrl) == null) return undo(Outcome.Failed(Failure.Unavailable(FeatureId("team.on"))))
        val server = ports.startServer()
        if (server is Outcome.Failed) return undo(server)
        val saved = ports.saveTeam(address)
        if (saved is Outcome.Failed) return undo(saved)
        return Outcome.Ok(Unit)
    }

    private suspend fun undo(failure: Outcome.Failed): Outcome<Unit> {
        ports.stopEngine()
        ports.startServer()
        return failure
    }
}
