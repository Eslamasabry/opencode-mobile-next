package io.github.eslamasabry.oc.engine.agents.host

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.store.AgentSecrets
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The helper password (plan 17 §1.12, 05 Appendix B): `oc.agentHostSecret.<owner>` in secure storage, 32 secure random
 * bytes as 64 lowercase hex, committed BEFORE the helper starts and registered with the redactor ([registerSecret]).
 * Paseo reads it from stdin at every start and stores none on disk, so rotation is a restart.
 */
public class AgentHostSecret(
    private val secrets: AgentSecrets,
    private val random: SecureBytes,
    private val registerSecret: (String) -> Unit,
) {
    private val lane = Mutex()

    public suspend fun get(owner: OwnerId): Outcome<Secret> =
        lane.withLock {
            when (val read = secrets.read(AgentKeys.agentHostSecret(owner))) {
                is Outcome.Failed -> return@withLock read
                is Outcome.Ok -> read.value?.takeIf { FORMAT.matches(it) }?.let {
                    registerSecret(it)
                    return@withLock Secret(it).ok()
                }
            }
            createLocked(owner)
        }

    /** A fresh value, committed. The caller restarts the helper with it ([AgentHelper.rotate]). */
    public suspend fun replace(owner: OwnerId): Outcome<Secret> = lane.withLock { createLocked(owner) }

    public suspend fun delete(owner: OwnerId): Outcome<Unit> = secrets.delete(AgentKeys.agentHostSecret(owner))

    /** Sign-in reset: every owner's slot, including orphans whose profile is gone (B-STATE-056). */
    public suspend fun deleteAll(): Outcome<Set<String>> {
        val names = when (val n = secrets.names(AgentKeys.AGENT_HOST_SECRET_PREFIX)) {
            is Outcome.Failed -> return n
            is Outcome.Ok -> n.value
        }
        for (name in names) {
            when (val d = secrets.delete(name)) {
                is Outcome.Failed -> return d
                is Outcome.Ok -> Unit
            }
        }
        return names.ok()
    }

    private suspend fun createLocked(owner: OwnerId): Outcome<Secret> {
        val bytes = random.next(32)
        require(bytes.size == 32) { "SecureBytes returned ${bytes.size} bytes." }
        val value = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        registerSecret(value)
        return when (val w = secrets.write(AgentKeys.agentHostSecret(owner), value)) {
            is Outcome.Failed -> w
            is Outcome.Ok -> Secret(value).ok()
        }
    }

    public companion object {
        public val FORMAT: Regex = Regex("^[a-f0-9]{64}$")
    }
}
