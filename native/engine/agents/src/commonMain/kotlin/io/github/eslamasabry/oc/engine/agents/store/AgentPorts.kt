package io.github.eslamasabry.oc.engine.agents.store

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The narrow preference port this engine reads and writes through (plan 05 §2.1 semantics). The adapter over
 * `:core:data` `KeyValueStore` (with the owning profile's `WriterLane`) binds above both modules; `:core:data` is not a
 * dependency while it does not compile on native/m0 (see the S3-AGENTSE report).
 *
 * Reads are in-memory and see only acknowledged values. [commit] is one atomic platform commit; on refusal nothing
 * changes and `Failure.Storage` comes back.
 */
public interface AgentPrefs {
    public fun string(key: String): String?

    public fun bool(key: String): Boolean?

    /** Both Flutter list encodings read as a list (05's list codec lives in the adapter). */
    public fun stringList(key: String): List<String>?

    public fun keys(prefix: String): Set<String>

    /** Keys changed by acknowledged writes. */
    public val changes: Flow<Set<String>>

    public suspend fun commit(edit: AgentPrefsEdit.() -> Unit): Outcome<Unit>
}

public interface AgentPrefsEdit {
    public fun putString(key: String, value: String)

    public fun putBool(key: String, value: Boolean)

    public fun putStringList(key: String, value: List<String>)

    public fun remove(key: String)
}

/** Secure storage (`SecretStore`): the helper password and nothing else from this engine. */
public interface AgentSecrets {
    public suspend fun read(name: String): Outcome<String?>

    public suspend fun write(name: String, value: String): Outcome<Unit>

    public suspend fun delete(name: String): Outcome<Unit>

    public suspend fun names(prefix: String): Outcome<Set<String>>
}

/** Random bytes from a secure source (`SecureRandom` on Android). Never `kotlin.random` (K-SEC-12). */
public fun interface SecureBytes {
    public fun next(count: Int): ByteArray
}
