package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An owner of sign-ins outside secure storage names (plan 03 §13.5 `Set<SignInResetParticipant>`): `:engine:agents`
 * (agent host cleanup per `oc.agentHostSecret.<owner>`), `:engine:integrations` (BYO SSH identities in the Keystore).
 * Called after the active selection is gone and before the secret names are deleted.
 */
public interface SignInResetParticipant {
    public val owner: String

    /** [found] = the owned secret names enumerated for this reset (names only). */
    public suspend fun reset(found: Set<String>): Outcome<Unit>
}

/**
 * "Reset saved sign-ins" (Flutter `ProfileStore.resetSavedSignIns`, B-STATE-064). Removes only app-owned sign-ins
 * (the [ProfileKeys.SIGN_IN_PREFIXES] names, orphans included) and the active selection; never reads, parses or
 * rewrites `oc.profiles`, drafts, queues or settings. Cached profiles lose their secrets and are flagged for re-entry
 * first, whatever happens next. Failed enumeration, a refused selection removal, a refused or unconfirmed delete are
 * all failures, and a retry is safe. The failure never carries keyring text.
 */
public class SignInReset(
    private val store: ProfileStore,
    private val kv: KeyValueStore,
    private val secrets: SecretStore,
    private val lanes: WriterLanes,
    private val redactor: Redactor,
    private val participants: Set<SignInResetParticipant> = emptySet(),
) {
    private val log = AppLog.tagged("profiles.reset")
    private val mutex = Mutex()

    public suspend fun reset(): Outcome<Unit> = mutex.withLock {
        store.dropCachedSecrets()
        val owned = when (val found = enumerate()) {
            is Outcome.Failed -> return refused()
            is Outcome.Ok -> found.value
        }
        // Keep redaction for late diagnostics: register every value about to go.
        for (name in owned) (secrets.read(name) as? Outcome.Ok)?.value?.let(redactor::registerSecret)

        // Selection removal is confirmed BEFORE any secret goes: a refusal keeps every sign-in for a retry.
        if (kv.commit(lanes.app) { remove(ProfileKeys.ACTIVE) } is Outcome.Failed) return refused()
        store.activeCleared()

        for (participant in participants) {
            val done = attempt({ Failure.Unexpected(Redacted.shape(it)) }) { participant.reset(owned) }
            if (done is Outcome.Failed || (done as Outcome.Ok).value is Outcome.Failed) return refused()
        }
        for (name in owned) if (secrets.delete(name) is Outcome.Failed) return refused()

        // Verified absent, or it is not a reset.
        when (val left = enumerate()) {
            is Outcome.Failed -> return refused()
            is Outcome.Ok -> if (left.value.isNotEmpty()) return refused()
        }
        if (kv.get(ProfileKeys.ACTIVE) != null) return refused()
        return Unit.ok()
    }

    private suspend fun enumerate(): Outcome<Set<String>> {
        val names = LinkedHashSet<String>()
        for (prefix in ProfileKeys.SIGN_IN_PREFIXES) {
            when (val found = secrets.names(prefix)) {
                is Outcome.Failed -> return found
                // `names` is a prefix match: `pw` and `oc.codexToken` (no dot) are not ours.
                is Outcome.Ok -> found.value.filterTo(names) { it.startsWith(prefix) && it.length > prefix.length }
            }
        }
        return names.ok()
    }

    private fun refused(): Outcome<Nothing> {
        log.w { "saved sign-in reset did not finish; it can be retried" }
        return Failure.Storage(StorageProblem.KeystoreUnavailable).failed()
    }
}
