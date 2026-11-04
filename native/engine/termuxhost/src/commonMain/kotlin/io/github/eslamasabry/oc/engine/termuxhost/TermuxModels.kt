package io.github.eslamasabry.oc.engine.termuxhost

/**
 * Port of lib/termux/bridge_models.dart. Termux is migrate-out only in the native app (plan 01 D7): these are the
 * parsing and decision rules the move-out and the phone checks still need, over the `oc/termux` channel port.
 */

/** A failure the Termux bridge reports: a stable [code] and Termux's own [message] (classified, never shown raw). */
public data class TermuxBridgeError(val message: String, val code: String = "termux_error")

/** An answer from the bridge: a value or a [TermuxBridgeError]. */
public sealed interface TermuxAnswer<out T> {
    public data class Done<out T>(val value: T) : TermuxAnswer<T>

    public data class Failed(val error: TermuxBridgeError) : TermuxAnswer<Nothing>
}

public fun <T> TermuxAnswer<T>.valueOrNull(): T? = (this as? TermuxAnswer.Done)?.value

public fun TermuxAnswer<*>.errorOrNull(): TermuxBridgeError? = (this as? TermuxAnswer.Failed)?.error

/** The runtime selected for the one app-managed Ubuntu server. */
public enum class TermuxRuntime(public val wireName: String, public val pinnedVersion: String) {
    /** Detection only: an old installation must migrate before it can start. */
    OpenCode1("opencode1", ""),
    OpenCode2("opencode2", "2.2.5"),
    ;

    /** Null when this runtime may start; the refusal otherwise. */
    public fun supportProblem(): TermuxBridgeError? =
        if (this == OpenCode2) {
            null
        } else {
            TermuxBridgeError("OpenCode 1 must move to OpenCode 2 before starting.", "opencode1_migration_required")
        }

    public companion object {
        /** Fresh setup only. Missing runtime records still describe older installs. */
        public val DefaultForNewInstall: TermuxRuntime = OpenCode2

        /** Null for an unsupported selection (Dart throws `invalid_runtime`). */
        public fun parse(value: String?): TermuxRuntime? =
            when (value?.trim()) {
                null, "", "opencode1" -> OpenCode1
                "opencode2" -> OpenCode2
                else -> null
            }

        public val InvalidRuntime: TermuxBridgeError =
            TermuxBridgeError("The managed server has an unsupported runtime selection.", "invalid_runtime")
    }
}

public data class TermuxCapabilities(
    val installed: Boolean,
    val version: String?,
    val serviceAvailable: Boolean,
    val protocolSupported: Boolean,
    val permissionGranted: Boolean,
    /** False when the running platform has no Termux bridge at all. */
    val platformSupported: Boolean = true,
) {
    public companion object {
        /** What a platform without a Termux bridge reports. */
        public val Unavailable: TermuxCapabilities =
            TermuxCapabilities(
                installed = false,
                version = null,
                serviceAvailable = false,
                protocolSupported = false,
                permissionGranted = false,
                platformSupported = false,
            )

        public fun fromMap(map: Map<String, Any?>): TermuxCapabilities =
            TermuxCapabilities(
                installed = map["installed"] == true,
                version = map["version"]?.toString(),
                serviceAvailable = map["serviceAvailable"] == true,
                protocolSupported = map["protocolSupported"] == true,
                permissionGranted = map["permissionGranted"] == true,
            )
    }
}

public data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errorCode: Int,
    val errorMessage: String,
) {
    /** Android RESULT_OK (-1) plus shell exit zero. */
    val successful: Boolean get() = errorCode == -1 && exitCode == 0

    /** Termux's own wording when it SIGKILLs an execution because its service is stopping. */
    val killedByServiceShutdown: Boolean
        get() = !successful && errorMessage.contains("android is killing the execution service")

    val failureMessage: String
        get() {
            val details = listOf(errorMessage.trim(), stderr.trim()).filter { it.isNotEmpty() }.joinToString("\n")
            return details.ifEmpty { "Termux command failed (error $errorCode, exit $exitCode)." }
        }

    public companion object {
        public fun fromMap(map: Map<String, Any?>): TermuxCommandResult =
            TermuxCommandResult(
                stdout = map["stdout"]?.toString() ?: "",
                stderr = map["stderr"]?.toString() ?: "",
                exitCode = (map["exitCode"] as? Number)?.toInt() ?: -1,
                errorCode = (map["err"] as? Number)?.toInt() ?: -1,
                errorMessage = map["errorMessage"]?.toString() ?: "",
            )
    }
}

public data class TermuxSetupStatus(
    val phase: String,
    val message: String,
    val port: Int,
    val runner: String,
    val version: String,
    val pid: Int?,
    val startedAtEpochSeconds: Long? = null,
    val operationId: String = "",
    val operationResult: String = "",
    val failureKind: String = "",
    val runtime: TermuxRuntime = TermuxRuntime.OpenCode1,
    val runtimeSelected: Boolean = false,
    val switchPrevious: TermuxRuntime? = null,
    val switchTarget: TermuxRuntime? = null,
    val switchPhase: String = "",
    val switchReturnAvailable: Boolean = false,
) {
    val switchPending: Boolean get() = switchPrevious != null && switchTarget != null
    val isRunning: Boolean get() = phase in RUNNING
    val isReady: Boolean get() = phase == "ready"
    val isFailed: Boolean get() = phase == "failed"
    val canRecover: Boolean
        get() = isFailed && !switchPending && runner == "proot" && port == TermuxScripts.MANAGED_SERVER_PORT &&
            (failureKind == "crash" || failureKind == "recovery")

    public companion object {
        private val RUNNING =
            setOf(
                "queued", "preparing", "installing_dependencies", "installing_ubuntu", "installing_opencode",
                "refreshing_models", "restarting", "starting_server",
            )
        private val DIGITS = Regex("^[0-9]+$")

        /** Parses `key=value` lines. Null only for an unsupported runtime record (Dart `invalid_runtime`). */
        public fun parse(output: String): TermuxSetupStatus? {
            val values = mutableMapOf<String, String>()
            for (line in output.split('\n')) {
                val separator = line.indexOf('=')
                if (separator <= 0) continue
                values[line.substring(0, separator)] = line.substring(separator + 1)
            }
            val rawStartedAt = values["started_at"] ?: ""
            // Dart int.tryParse is 64-bit: an overflowing value is not a clock.
            val startedAt = if (DIGITS.matches(rawStartedAt)) rawStartedAt.toLongOrNull() else null
            val runtime = TermuxRuntime.parse(values["runtime"]) ?: return null
            val previous = values["switch_previous"]?.let { TermuxRuntime.parse(it) ?: return null }
            val target = values["switch_target"]?.let { TermuxRuntime.parse(it) ?: return null }
            return TermuxSetupStatus(
                phase = values["phase"] ?: "unknown",
                message = values["message"] ?: "Unknown setup state",
                port = values["port"]?.toIntOrNull() ?: 4096,
                runner = values["runner"] ?: "",
                version = values["version"] ?: "",
                pid = values["pid"]?.toIntOrNull(),
                startedAtEpochSeconds = startedAt?.takeIf { it >= 0 },
                operationId = values["operation"] ?: "",
                operationResult = values["operation_result"] ?: "",
                failureKind = values["failure_kind"] ?: "",
                runtime = runtime,
                runtimeSelected = values["runtime"]?.trim()?.isNotEmpty() == true,
                switchPrevious = previous,
                switchTarget = target,
                switchPhase = values["switch_phase"] ?: "",
                switchReturnAvailable = values["switch_return"] == "opencode1",
            )
        }
    }
}

/** Status plus the terminal output after `__OC_SETUP_OUTPUT__`, kept apart. */
public data class TermuxSetupSnapshot(val status: TermuxSetupStatus, val output: String) {
    public companion object {
        private const val MARKER = "__OC_SETUP_OUTPUT__"

        public fun parse(raw: String): TermuxSetupSnapshot? {
            val markerIndex = raw.indexOf(MARKER)
            if (markerIndex < 0) return TermuxSetupStatus.parse(raw)?.let { TermuxSetupSnapshot(it, "") }
            val status = TermuxSetupStatus.parse(raw.substring(0, markerIndex)) ?: return null
            return TermuxSetupSnapshot(status, raw.substring(markerIndex + MARKER.length).trim())
        }
    }
}

/** The installed app-owned environment, independent of server running state. */
public data class TermuxInstallation(
    val ubuntuInstalled: Boolean,
    val openCodeVersion: String? = null,
    val runtime: TermuxRuntime = TermuxRuntime.OpenCode1,
    val runtimeSelected: Boolean = false,
) {
    public companion object {
        private val SHAPE =
            Regex("^ubuntu=(absent|installed)\\r?\\nversion=([^\\r\\n]*)(?:\\r?\\nruntime=(opencode1|opencode2))?\\r?\\n?$")
        private val VERSION = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$")

        public val Unreadable: TermuxBridgeError =
            TermuxBridgeError("Could not read the installed Ubuntu and OpenCode versions.", "invalid_installation_probe")

        /** Null when the probe answer is malformed: an unreadable inventory is never a fresh install. */
        public fun parse(output: String): TermuxInstallation? {
            val match = SHAPE.find(output.trim()) ?: return null
            val installed = match.groupValues[1] == "installed"
            val version = match.groupValues[2]
            if (version.isNotEmpty() && (!installed || !VERSION.matches(version))) return null
            val recorded = match.groups[3]?.value
            return TermuxInstallation(
                ubuntuInstalled = installed,
                openCodeVersion = version.ifEmpty { null },
                runtime = TermuxRuntime.parse(recorded) ?: return null,
                runtimeSelected = recorded != null,
            )
        }
    }
}

/** Termux's home filesystem in bytes, read from checked `df` counts. */
public data class TermuxStorageSnapshot(val totalBytes: Long, val availableBytes: Long) {
    public companion object {
        private val LINE = Regex("^(total_kib|available_kib)=([0-9]{1,13})$")

        public val Unreadable: TermuxBridgeError = TermuxBridgeError("Could not read Termux storage.", "invalid_storage")

        /** Null for a malformed or impossible answer (Dart throws `invalid_storage`). */
        public fun parse(output: String): TermuxStorageSnapshot? {
            val values = mutableMapOf<String, Long>()
            for (line in output.trim().split('\n')) {
                val match = LINE.find(line) ?: return null
                if (match.groupValues[1] in values) return null
                values[match.groupValues[1]] = match.groupValues[2].toLong()
            }
            val total = values["total_kib"] ?: return null
            val available = values["available_kib"] ?: return null
            if (total <= 0 || available > total) return null
            return TermuxStorageSnapshot(total * 1024, available * 1024)
        }
    }
}
