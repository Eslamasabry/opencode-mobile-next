package io.github.eslamasabry.oc.engine.agents.runtime

// Port of the typed half of lib/termux/local_agent_runtime.dart: `claude.sh status` read back as a LocalAgentStatus.
// `absent` and `needs_ubuntu` are never written to the state file; status derives them. `ready` is only ever reported
// after a live health probe.

public enum class LocalAgentPhase {
    /** Ubuntu is there; Claude Code is not installed. */
    Absent,

    /** The app-managed Ubuntu is missing: the normal phone setup comes first. */
    NeedsUbuntu,
    Installing,

    /** Installed and not running. */
    Installed,
    Starting,

    /** The daemon answered its health check when status was read. */
    Ready,
    Stopping,
    Removing,
    Failed,

    /** A phase this build does not know (a newer script) or unreadable output. */
    Unknown,
    ;

    public val isTransient: Boolean get() = this == Installing || this == Starting || this == Stopping || this == Removing

    public companion object {
        public fun parse(raw: String?): LocalAgentPhase = when (raw?.trim()) {
            "absent" -> Absent
            "needs_ubuntu" -> NeedsUbuntu
            "installing" -> Installing
            "installed" -> Installed
            "starting" -> Starting
            "ready" -> Ready
            "stopping" -> Stopping
            "removing" -> Removing
            "failed" -> Failed
            else -> Unknown
        }
    }
}

/** Why a verb failed, from the script's `failure_kind`. */
public enum class LocalAgentFailureKind {
    NeedsUbuntu,
    NoSpace,
    Download,
    Checksum,
    NativeBuild,
    PortInUse,
    Timeout,

    /** The bridge itself failed (Termux missing, permission, dispatch). */
    Bridge,
    NotInstalled,
    UnsupportedArch,

    /** A verb stopped without finishing (Android killed it). */
    Interrupted,

    /** The daemon process exited or stopped answering. */
    Daemon,

    /** npm failed for a reason that is not a native build (usually network). */
    Packages,

    /** The daemon password on this phone is missing or malformed. */
    PasswordMissing,

    /** `ensure-project` did not report the folder. */
    ProjectNotCreated,
    Other,
    ;

    public companion object {
        public fun parse(raw: String?): LocalAgentFailureKind? = when (raw?.trim()) {
            null, "" -> null
            "needs_ubuntu" -> NeedsUbuntu
            "no_space" -> NoSpace
            "download" -> Download
            "checksum" -> Checksum
            "native_build" -> NativeBuild
            "port_in_use" -> PortInUse
            "timeout" -> Timeout
            "not_installed" -> NotInstalled
            "unsupported_arch" -> UnsupportedArch
            "interrupted" -> Interrupted
            "daemon_exited", "unhealthy" -> Daemon
            "npm" -> Packages
            else -> Other
        }
    }
}

/** The install step a status belongs to. */
public enum class LocalAgentStep {
    Node,
    Paseo,
    Claude,
    ;

    public companion object {
        public fun parse(raw: String?): LocalAgentStep? = when (raw?.trim()) {
            "node" -> Node
            "paseo" -> Paseo
            "claude" -> Claude
            else -> null
        }
    }
}

public enum class LocalAgentSignIn {
    Yes,
    No,
    Unknown,
    ;

    public companion object {
        public fun parse(raw: String?): LocalAgentSignIn = when (raw?.trim()) {
            "yes" -> Yes
            "no" -> No
            else -> Unknown
        }
    }
}

/**
 * A verb that failed, typed for the UI. [scriptMessage] is the script's own `message=` line (a status answer), shown
 * only in a Details fold; app-authored failures carry none and the UI words them from [kind].
 */
public data class LocalAgentFailure(val kind: LocalAgentFailureKind, val scriptMessage: String = "")

/** One `claude.sh status` answer. */
public data class LocalAgentStatus(
    val phase: LocalAgentPhase,
    val message: String = "",
    /** A verb is running right now (poll again). */
    val busy: Boolean = false,
    val verb: String = "",
    val step: LocalAgentStep? = null,
    /** Node, Paseo and Claude Code are all present in Ubuntu. */
    val installed: Boolean = false,
    val nodeVersion: String = "",
    val paseoVersion: String = "",
    val claudeVersion: String = "",
    /** The daemon runner's pid, when alive. */
    val pid: Int? = null,
    val port: Int = LocalAgentsScript.PORT,
    val signedIn: LocalAgentSignIn = LocalAgentSignIn.Unknown,
    val failureKind: LocalAgentFailureKind? = null,
    /** The state said `ready` but the daemon was gone: Android stopped it while the app was away. */
    val killedByAndroid: Boolean = false,
    /** Epoch milliseconds (UTC) of the state file's `updated_at`. */
    val updatedAtMillis: Long? = null,
) {
    val isReady: Boolean get() = phase == LocalAgentPhase.Ready

    /** The typed failure of a `failed` status, or null. */
    val failure: LocalAgentFailure?
        get() = if (phase == LocalAgentPhase.Failed) LocalAgentFailure(failureKind ?: LocalAgentFailureKind.Other, message) else null

    public companion object {
        public val Unreadable: LocalAgentStatus = LocalAgentStatus(phase = LocalAgentPhase.Unknown)

        /**
         * Parses `key=value` lines; unknown keys are ignored and a missing `phase` reads as [Unreadable], so a UI
         * always has a phase to show.
         */
        public fun parse(raw: String): LocalAgentStatus {
            val values = mutableMapOf<String, String>()
            for (line in raw.split('\n')) {
                val separator = line.indexOf('=')
                if (separator <= 0) continue
                values[line.substring(0, separator).trim()] = line.substring(separator + 1).trimEnd()
            }
            if ("phase" !in values) return Unreadable
            val phase = LocalAgentPhase.parse(values["phase"])
            return LocalAgentStatus(
                phase = phase,
                message = values["message"] ?: "",
                busy = values["busy"] == "yes",
                verb = values["verb"] ?: "",
                step = LocalAgentStep.parse(values["step"]),
                installed = values["installed"] == "yes",
                nodeVersion = values["node_version"] ?: "",
                paseoVersion = values["paseo_version"] ?: "",
                claudeVersion = values["claude_version"] ?: "",
                pid = values["pid"]?.trim()?.toIntOrNull(),
                port = values["port"]?.trim()?.toIntOrNull() ?: LocalAgentsScript.PORT,
                signedIn = LocalAgentSignIn.parse(values["signed_in"]),
                failureKind = if (phase == LocalAgentPhase.Failed) {
                    LocalAgentFailureKind.parse(values["failure_kind"]) ?: LocalAgentFailureKind.Other
                } else {
                    null
                },
                killedByAndroid = values["killed"] == "yes",
                updatedAtMillis = values["updated_at"]?.trim()?.toLongOrNull()?.let { it * 1000 },
            )
        }
    }
}
