package io.github.eslamasabry.oc.feature.linux.host

import io.github.eslamasabry.oc.core.common.Outcome

/** What one short script printed and how it ended (`BuiltinLinux.run` → `Result`). */
public data class ShellRun(val exitCode: Int, val output: String)

/** Whether the phone's own Linux can be asked anything at all. */
public enum class LinuxPresence {
    /** Installed and answering: the manager's pages work. */
    Ready,

    /** This phone can run Linux but it is not set up yet ("Open This phone to set it up"). */
    NotInstalled,

    /** This build or device has no built-in Linux (desktop, or an app without the runtime). */
    NotOnThisBuild,
}

/**
 * The narrow face of the built-in Linux the manager needs (plan 11 §5.4 `GuestLauncher` + the
 * service table), shaped after `BuiltinLinux.run / startService / stopService / serviceRunning /
 * serviceLogTail / setupStatus`. Each call runs off the main thread in the binding. Failures are
 * flat [io.github.eslamasabry.oc.core.common.Failure] values:
 *
 * - `Failure.Runtime(OwnershipUnknown)` or `Failure.Runtime(SlotBusy)`: a passing state (another
 *   phone task holds the installer slot, or a program is starting or ending); the manager tries
 *   twice more before it says "busy".
 * - `Failure.Runtime(NotInstalled)` / anything else: Linux is not available right now.
 *
 * No guest output reaches a Failure's main text; `details` carries only what the Redactor allowed.
 */
public interface LinuxHost {
    public suspend fun presence(): LinuxPresence

    /**
     * Runs [script] with `/bin/sh` inside Linux as root, in the installer slot, for at most
     * [timeoutSeconds].
     */
    public suspend fun run(script: String, timeoutSeconds: Long): Outcome<ShellRun>

    /** True while phone setup (or repair) runs: the setup journal says `running`. */
    public suspend fun setupRunning(): Boolean

    public suspend fun serviceRunning(name: String): Boolean

    /**
     * Starts [script] as the long-lived service [name] with the foreground notice text [notice].
     */
    public suspend fun startService(name: String, script: String, notice: String): Outcome<Unit>

    public suspend fun stopService(name: String): Outcome<Unit>

    /** The last [tailBytes] of [name]'s log, empty when there is none. */
    public suspend fun serviceLog(name: String, tailBytes: Int): String
}

/** Redaction stays in the application binding; a feature cannot construct technical copy. */
public fun interface LinuxLogDetails {
    public fun redact(text: String): io.github.eslamasabry.oc.core.common.Redacted
}

/**
 * The honest binding where there is no built-in Linux: every page says so, nothing is pretended.
 */
public class AbsentLinuxHost(private val why: LinuxPresence = LinuxPresence.NotOnThisBuild) :
    LinuxHost {
    override suspend fun presence(): LinuxPresence = why

    override suspend fun run(script: String, timeoutSeconds: Long): Outcome<ShellRun> = absent()

    override suspend fun setupRunning(): Boolean = false

    override suspend fun serviceRunning(name: String): Boolean = false

    override suspend fun startService(name: String, script: String, notice: String): Outcome<Unit> =
        absent()

    override suspend fun stopService(name: String): Outcome<Unit> = absent()

    override suspend fun serviceLog(name: String, tailBytes: Int): String = ""

    private fun absent(): Outcome<Nothing> =
        Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.Runtime(
                io.github.eslamasabry.oc.core.common.RuntimeProblem.NotInstalled
            )
        )
}
