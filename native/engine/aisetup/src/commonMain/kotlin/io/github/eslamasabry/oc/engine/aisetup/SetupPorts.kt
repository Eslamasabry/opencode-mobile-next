package io.github.eslamasabry.oc.engine.aisetup

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Raw setup transport boundary, for [SetupController] only (Dart `SetupConfigGateway`, read side). Never persist, log
 * or display what it returns. Gated on `ServerCapabilities.setupConfigRead`, never on the backend kind. The legacy
 * PATCH write is deliberately absent: only [SetupTransactionalConfigGateway] can make a proposal writable.
 *
 * Android adapter (integrator): OpenCode 2 `GET /api/config` (answering `{sources: [...]}` as the server reports
 * them) and `GET /api/mcp`; expected failures as [io.github.eslamasabry.oc.core.common.Failure] values.
 */
public interface SetupConfigGateway {
    public val support: SetupSupport

    public suspend fun readConfig(): Outcome<JsonObject>

    public suspend fun listMcpServers(): Outcome<List<SetupMcpStatus>>
}

/**
 * Optional host transaction contract. Never emulate it with GET plus PATCH. The host binds immutable source identity
 * and atomically compares `expected` before committing all edits; a `Failure.Conflict` means no write occurred.
 * Snapshots carry exact source values (including absence), not merged effective config. Disabled MCP transactions must
 * not start connections or change authentication. [restore] consumes the host-owned handle and conditionally restores
 * the exact original source against `commit.after`; credentials never travel in edits. No production server offers
 * this yet, so production adapters implement only [SetupConfigGateway].
 */
public interface SetupTransactionalConfigGateway : SetupConfigGateway {
    public suspend fun readSnapshot(): Outcome<SetupConfigRevision>

    public suspend fun commit(
        expected: SetupConfigRevision,
        edits: List<SetupEdit>,
        operationId: String,
    ): Outcome<SetupConfigCommit>

    public suspend fun restore(commit: SetupConfigCommit, operationId: String): Outcome<SetupConfigCommit>
}

/**
 * The app redactor seen from this engine (Dart KitRedact; production adapter: `:core:security` `Redactor`).
 */
public interface SetupRedaction {
    /** Masks known secrets and credential shapes. */
    public fun text(value: String): String

    public fun containsSecret(value: String): Boolean = text(value) != value

    /** Registers every credential-named string of a config tree as a known secret. */
    public fun registerCredentialValues(config: JsonElement)

    public val mask: String get() = MASK

    public companion object {
        public const val MASK: String = "•••"
    }
}

public enum class SetupAuditAction(public val wire: String) {
    Proposed("proposed"), Pending("pending"), Accepted("accepted"), Verified("verified"), Uncertain("uncertain"),
    Rejected("rejected"),
}

public enum class SetupAuditOperation(public val wire: String) { Apply("apply"), Undo("undo"), Review("review") }

/** Metadata only: never configuration, credentials, server location or an Undo handle. */
public data class SetupAuditRecord(
    val id: String,
    val action: SetupAuditAction,
    val atMs: Long,
    val operation: SetupAuditOperation? = null,
)

/**
 * One metadata-only audit writer per profile, shared by every controller of that profile (Dart `SetupAuditStore`).
 *
 * Android adapter (integrator): key `oc.setupAudit.<profileId>` (registered with the profile deletion sweep), a JSON
 * list of `{id, action, at (UTC ISO-8601), operation?}`, at most the last 100 records, appends serialised. It is
 * unavailable when the profile is closed or no longer listed in `oc.profiles`, or when the stored history is
 * unreadable (an unreadable history is never overwritten). A refused commit returns `Failure.StorageRefused`.
 */
public interface SetupAuditLog {
    public val isAvailable: Boolean

    /** Oldest first; empty when unavailable. */
    public val records: List<SetupAuditRecord>

    public suspend fun append(
        id: String,
        action: SetupAuditAction,
        operation: SetupAuditOperation? = null,
    ): Outcome<Unit>
}
