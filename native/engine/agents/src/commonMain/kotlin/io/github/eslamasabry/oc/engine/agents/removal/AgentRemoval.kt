package io.github.eslamasabry.oc.engine.agents.removal

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes

/** What the fixed removal script reports. [receipt] is the bounded private receipt, read back once; null if unreadable. */
public data class RemovalRun(val exitCode: Int, val receipt: RemovalReceipt?)

public data class RemovalReceipt(val freedBytes: Long, val wasAbsent: Boolean)

/** Runs `AgentRemovalScripts.remove` / `readReceipt` (kept scripts, 11 S11-3) in the fixed oc setup view. */
public interface AgentRemovalRunner {
    public suspend fun remove(owner: OwnerId, agent: AgentId, receiptId: String): Outcome<RemovalRun>
}

public sealed interface RemovalResult {
    /** "freed 98 MB": measured; 0 when the payload was already absent. */
    public data class Removed(val freedBytes: Long) : RemovalResult

    public data class Refused(val reason: RemovalRefusal) : RemovalResult
}

public enum class RemovalRefusal {
    /** Claude cannot be removed here; non-catalog ids never dispatch. */
    NotRemovable,
    /** A setup, an install, a phone check or a live agent process runs (exit 16); no raw output. */
    Busy,
    /** Exit 17: the payload or its paths are not the app's. */
    Unsafe,
    /** Exit 18 or anything else. */
    Failed,
    /** The script said 0 but no receipt confirmed it: never reported as success. */
    Unconfirmed,
}

/**
 * Remove an agent (AGENTS-BACKENDS-12, plan 17 §1.5). Only the catalog executables `codex gemini qwen kilo cline goose
 * omp fx`; keeps accounts, chats, shared tools and the gate metadata; a partial payload stays removable.
 */
public class AgentRemoval(
    private val runner: AgentRemovalRunner,
    private val random: SecureBytes,
    private val catalog: AgentCatalog = AgentCatalog.builtIn,
) {
    public fun removable(agent: AgentId): Boolean =
        catalog.byId(agent)?.recipe?.executable?.let { it in REMOVABLE_EXECUTABLES } == true

    /** [busy]: setup running or unknown, an install, a pending preparation or a phone check. */
    public suspend fun remove(owner: OwnerId, agent: AgentId, busy: Boolean): Outcome<RemovalResult> {
        if (!removable(agent)) return RemovalResult.Refused(RemovalRefusal.NotRemovable).ok()
        if (busy) return RemovalResult.Refused(RemovalRefusal.Busy).ok()
        val receiptId = random.next(16).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        require(RECEIPT.matches(receiptId))
        val run = when (val r = runner.remove(owner, agent, receiptId)) {
            is Outcome.Failed -> return r
            is Outcome.Ok -> r.value
        }
        val result = when (run.exitCode) {
            0 -> run.receipt?.let { RemovalResult.Removed(if (it.wasAbsent) 0 else it.freedBytes.coerceAtLeast(0)) }
                ?: RemovalResult.Refused(RemovalRefusal.Unconfirmed)
            16 -> RemovalResult.Refused(RemovalRefusal.Busy)
            17 -> RemovalResult.Refused(RemovalRefusal.Unsafe)
            else -> RemovalResult.Refused(RemovalRefusal.Failed)
        }
        return result.ok()
    }

    public companion object {
        public val REMOVABLE_EXECUTABLES: Set<String> = setOf("codex", "gemini", "qwen", "kilo", "cline", "goose", "omp", "fx")
        public val RECEIPT: Regex = Regex("^[a-f0-9]{32}$")
    }
}
