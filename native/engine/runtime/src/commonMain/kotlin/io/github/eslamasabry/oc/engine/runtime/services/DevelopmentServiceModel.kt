package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.Outcome

/*
 * Port of lib/domain/development_service.dart and lib/domain/managed_shell.dart (the project service records, the
 * server-owned shell and the gateway that controls it) plus the port shape of lib/state/development_service_store.dart.
 */

/** Written BEFORE the create request: a lost response never silently authorizes a duplicate start. */
public data class DevelopmentServiceRun(
    val ownerToken: String,
    val shellId: String? = null,
    /** The shell's start, epoch milliseconds, as the server reported it. */
    val startedAt: Long? = null,
    val stopped: Boolean = false,
) {
    override fun toString(): String = "DevelopmentServiceRun(shellId=$shellId, startedAt=$startedAt, stopped=$stopped)"
}

/** A user-reviewed command and destination, scoped to one saved server and project directory. */
public data class DevelopmentService(
    val id: String,
    val name: String,
    val command: String,
    val directory: String,
    val url: String,
    val workspace: String? = null,
    val run: DevelopmentServiceRun? = null,
) {
    public fun withRun(value: DevelopmentServiceRun?): DevelopmentService = copy(run = value)
}

public enum class DevelopmentServiceStatus { NotStarted, Running, Stopped, Unknown }

public enum class ManagedShellStatus { Running, Exited, Timeout, Killed, Unknown }

/** A server-owned, non-interactive command. Host paths and process IDs are deliberately absent. */
public data class ManagedShell(
    val id: String,
    val command: String,
    val status: ManagedShellStatus,
    /** Epoch milliseconds. */
    val startedAt: Long,
    val exitCode: Int? = null,
    val directory: String? = null,
    /** Only this feature's ownership field, never arbitrary server metadata. */
    val ownerToken: String? = null,
) {
    val running: Boolean get() = status == ManagedShellStatus.Running
}

/** [cursor] and [size] are absolute byte offsets supplied by the server, never string lengths. */
public data class ManagedShellOutput(val text: String, val cursor: Long, val size: Long, val truncated: Boolean)

/** The server operations the panel needs (Dart `ManagedShellGateway`). */
public interface ManagedShellGateway {
    public suspend fun startManagedShell(command: String, directory: String, ownerToken: String): Outcome<ManagedShell>

    public suspend fun loadRunningShells(): Outcome<List<ManagedShell>>

    public suspend fun getManagedShell(id: String): Outcome<ManagedShell?>

    public suspend fun readManagedShellOutput(id: String, cursor: Long, limit: Int = 65536): Outcome<ManagedShellOutput>

    public suspend fun stopManagedShell(id: String): Outcome<Unit>
}

/**
 * Configurations and ownership receipts only (Dart `DevelopmentServiceStore`). Log output and credentials are never
 * persisted. Saved under [key] = `oc.developmentServices.<profileId>`, so the profile sweep deletes it.
 */
public interface DevelopmentServiceStore {
    public val key: String

    /** Failed when the saved data cannot be read. */
    public fun load(): Outcome<List<DevelopmentService>>

    public suspend fun save(service: DevelopmentService): Outcome<Unit>

    public suspend fun remove(id: String): Outcome<Unit>

    /** Refused when the service is gone or (with [expectedOwner]) its receipt changed. */
    public suspend fun updateRun(id: String, run: DevelopmentServiceRun?, expectedOwner: String? = null): Outcome<Unit>

    public companion object {
        public const val MAX_SERVICES: Int = 20

        public fun keyFor(profileId: String): String = "oc.developmentServices.$profileId"
    }
}
