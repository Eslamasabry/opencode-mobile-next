package io.github.eslamasabry.opencode_mobile

/** Service history independent of its process and storage implementation. */
data class ServiceDiagnostics(
    val lastExitCode: Int? = null,
    val lastUptimeMs: Long? = null,
    val restartCount: Int = 0,
    val hasLaunched: Boolean = false,
) {
    fun launched(): ServiceDiagnostics = copy(
        restartCount = restartCount + if (hasLaunched) 1 else 0,
        hasLaunched = true,
    )

    fun exited(exitCode: Int?, uptimeMs: Long): ServiceDiagnostics = copy(
        lastExitCode = exitCode,
        lastUptimeMs = uptimeMs.coerceAtLeast(0L),
    )

    fun snapshot(running: Boolean, uptimeMs: Long?): Map<String, Any?> = mapOf(
        "running" to running,
        "lastExitCode" to lastExitCode,
        "lastUptimeMs" to lastUptimeMs,
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
