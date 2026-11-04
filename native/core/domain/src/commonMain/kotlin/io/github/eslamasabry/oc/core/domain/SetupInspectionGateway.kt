package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.serialization.json.JsonObject

/**
 * A read-only setup view pinned to the location selected when it was opened (PROTO), and STATE's safe summary of
 * the current location (inspection only: production servers have no verified atomic configuration restore
 * contract). One interface for both units.
 */
public interface SetupInspectionGateway {
    public fun setupInspection(location: ServerLocation): SetupInspector

    public suspend fun inspectSetup(): Outcome<SetupInspection> = unavailable("setupInspection")
}

/**
 * All display values have already passed through the shared Redactor; config secrets never enter
 * presentation.
 */
public data class SetupConfigSource(val type: String, val path: String?, val redactedInfo: String?)

public data class SetupConfigSnapshot(val sources: List<SetupConfigSource>)

public data class SetupMcpStatus(val name: String, val status: String)

public enum class SetupHistoryPhase {
    Required,
    Running,
    Completed,
    Error,
}

/** Server labels and error text are deliberately absent from the display contract. */
public data class SetupHistoryMigration(
    val phase: SetupHistoryPhase,
    val done: Long? = null,
    val total: Long? = null,
)

public interface SetupInspector {
    public suspend fun historyMigration(): Outcome<SetupHistoryMigration>

    public suspend fun readConfig(): Outcome<SetupConfigSnapshot>

    public suspend fun mcpInventory(): Outcome<List<SetupMcpStatus>>

    /**
     * No supported conditional restore contract exists; implementations refuse without making a
     * request.
     */
    public suspend fun patchConfig(patch: JsonObject): Outcome<Unit>
}
