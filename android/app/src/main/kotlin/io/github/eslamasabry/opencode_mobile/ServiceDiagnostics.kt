package io.github.eslamasabry.opencode_mobile

/** Service history independent of its process and storage implementation. */
data class ServiceDiagnostics(
    val lastExitCode: Int? = null,
    val lastUptimeMs: Long? = null,
    val lastExitAtMs: Long? = null,
    val lastStopRequested: Boolean? = null,
    val restartCount: Int = 0,
    val hasLaunched: Boolean = false,
) {
    fun launched(): ServiceDiagnostics = copy(
        restartCount = restartCount + if (hasLaunched) 1 else 0,
        hasLaunched = true,
    )

    fun exited(exitCode: Int?, uptimeMs: Long, atMs: Long? = null,
        stopRequested: Boolean? = null): ServiceDiagnostics = copy(
        lastExitCode = exitCode,
        lastUptimeMs = uptimeMs.coerceAtLeast(0L),
        lastExitAtMs = atMs?.takeIf { it > 0 },
        lastStopRequested = stopRequested,
    )

    fun snapshot(running: Boolean, uptimeMs: Long?): Map<String, Any?> = mapOf(
        "running" to running,
        "lastExitCode" to lastExitCode,
        "lastUptimeMs" to lastUptimeMs,
        "lastExitAtMs" to lastExitAtMs,
        "lastStopRequested" to lastStopRequested,
        "uptimeMs" to uptimeMs,
        "restartCount" to restartCount,
        // Exit 137 indicates SIGKILL; it cannot distinguish memory pressure
        // from Android's phantom-process policy (or another external kill).
        "exitReason" to when (lastExitCode) {
            SIGKILL_EXIT_CODE -> "memory_or_phantom_kill"
            null -> "unknown"
            else -> "exited"
        },
    )

    private companion object {
        const val SIGKILL_EXIT_CODE = 137
    }
}
