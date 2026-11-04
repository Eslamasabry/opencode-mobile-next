package io.github.eslamasabry.oc.engine.cards

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** A configuration outcome only (Flutter `GenUiInstallOutcome`): it never proves a live tool. */
public enum class CardsInstallOutcome {
    Registered, RemovalPending, NotInstalled, RuntimeMissing, NameCollision, UnsafePath, Failed;

    public companion object {
        /** The install script's exit status is the whole result protocol; output is never trusted. */
        public fun ofExitCode(code: Int): CardsInstallOutcome =
            when (code) {
                0 -> Registered
                10 -> RemovalPending
                11 -> NotInstalled
                20 -> RuntimeMissing
                21 -> NameCollision
                22 -> UnsafePath
                else -> Failed
            }
    }
}

/** Runs the fixed install (or removal) script for one agent in the built-in Linux. Raw output stays inside. */
public fun interface CardsSetupRunner {
    public suspend fun run(profileId: String, agent: CardAgent, enabled: Boolean): CardsInstallOutcome
}

/** Re-checks the qualified runtime and this profile's registered tool (Flutter `GenUiSetupVerifier`). */
public fun interface CardsSetupVerifier {
    public suspend fun verify(profileId: String, agent: CardAgent): Boolean
}

/**
 * Stages the card tools for every requested agent but reports ready only on verified evidence (Flutter
 * `ManagedGenUiInstaller`; B-CHAT-040). Registration alone is never ready; only evidence-backed agents ([qualified],
 * today Claude Code) are verified at all, so a permissive verifier cannot promote OpenCode 2; a missing verifier is
 * "not qualified"; a failed verification is "verification failed", never "restart". A name collision or a missing
 * runtime on one agent gives Partial while a verified agent stays on. A missing installation disables without
 * verifying. An invalid profile id is refused before any script runs. Every installer, in every instance, runs
 * in one serial lane.
 */
public class ManagedCardsInstaller(
    private val runner: CardsSetupRunner,
    private val verifier: CardsSetupVerifier? = null,
    private val qualified: Set<CardAgent> = setOf(CardAgent.Claude),
) {
    public suspend fun setEnabled(profileId: String, agents: List<CardAgent>, enabled: Boolean): CardsStatus {
        if (!PROFILE.matches(profileId)) return CardsStatus.Failed(CardsProblem.StorageFailed)
        val requested = agents.distinct()
        return lane.withLock {
            try {
                apply(profileId, requested, enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CardsStatus.Failed(CardsProblem.InstallationFailed)
            }
        }
    }

    private suspend fun apply(profileId: String, agents: List<CardAgent>, enabled: Boolean): CardsStatus {
        if (agents.isEmpty()) return if (enabled) CardsStatus.Unavailable(CardsProblem.NotQualified) else CardsStatus.Off
        var failure: CardsProblem? = null
        var changed = false
        val ready = mutableListOf<CardAgent>()
        val problems = LinkedHashMap<CardAgent, CardsProblem>()
        for (agent in agents) {
            var problem: CardsProblem? = null
            when (runner.run(profileId, agent, enabled)) {
                CardsInstallOutcome.Registered -> {
                    changed = true
                    if (enabled) problem = verify(profileId, agent, ready)
                }
                CardsInstallOutcome.RemovalPending -> changed = true
                CardsInstallOutcome.NotInstalled -> Unit
                CardsInstallOutcome.RuntimeMissing -> problem = CardsProblem.RuntimeMissing
                CardsInstallOutcome.NameCollision -> problem = CardsProblem.Conflict
                CardsInstallOutcome.UnsafePath -> problem = CardsProblem.PermissionDenied
                CardsInstallOutcome.Failed -> problem = if (enabled) CardsProblem.RegistrationFailed else CardsProblem.RemovalFailed
            }
            if (problem != null) {
                if (failure == null) failure = problem
                problems[agent] = problem
            }
        }
        val reason = failure
        if (reason != null) {
            val affected = problems.filterValues { it == reason }.keys.toList()
            if (enabled && ready.isEmpty()) {
                return if (reason == CardsProblem.NotQualified) CardsStatus.Unavailable(reason, affected) else CardsStatus.Failed(reason, affected)
            }
            return if (changed) CardsStatus.Partial(ready.toList(), reason, affected) else CardsStatus.Failed(reason, affected)
        }
        if (!enabled && !changed) return CardsStatus.Off
        if (enabled && ready.size == agents.size) return CardsStatus.On(ready.toList())
        if (enabled) return CardsStatus.Failed(CardsProblem.VerificationFailed)
        // Removing persisted config does not prove a running catalogue dropped it.
        return CardsStatus.RestartRequired(emptyList())
    }

    private suspend fun verify(profileId: String, agent: CardAgent, ready: MutableList<CardAgent>): CardsProblem? {
        if (agent !in qualified) return CardsProblem.NotQualified
        val check = verifier ?: return CardsProblem.NotQualified
        val ok =
            try {
                check.verify(profileId, agent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        if (!ok) return CardsProblem.VerificationFailed
        ready += agent
        return null
    }

    public companion object {
        private val PROFILE = Regex("^[a-zA-Z0-9_-]{1,96}$")

        /** One lane across every installer instance (the scripts also take a file lock in the Linux root). */
        private val lane = Mutex()
    }
}
