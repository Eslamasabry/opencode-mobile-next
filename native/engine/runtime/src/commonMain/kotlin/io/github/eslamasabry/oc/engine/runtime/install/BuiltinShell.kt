package io.github.eslamasabry.oc.engine.runtime.install

import io.github.eslamasabry.oc.core.common.Redacted
import kotlin.time.Duration

/**
 * Port of lib/builtin/git_shell.dart `BuiltinGitShell`: one command in the app's own Ubuntu (`BuiltinLinux.run`), its
 * output returned, every bridge failure mapped to a stable kind (a timeout stays a timeout; everything else is
 * "unavailable"). The phone's own failure text is kept for Details only.
 */
public class BuiltinShell(private val linux: BuiltinLinuxPort) {
    public suspend fun run(script: String, timeout: Duration): ShellAnswer =
        when (val answer = linux.run(script, timeout)) {
            is LinuxBridgeAnswer.Ran -> ShellAnswer.Output(answer.output)
            is LinuxBridgeAnswer.BridgeFailed ->
                ShellAnswer.Failed(
                    if (answer.code == TIMEOUT_CODE) ShellFailure.TimedOut else ShellFailure.Unavailable,
                    answer.details,
                )
            LinuxBridgeAnswer.TimedOut -> ShellAnswer.Failed(ShellFailure.TimedOut)
            LinuxBridgeAnswer.Crashed -> ShellAnswer.Failed(ShellFailure.Unavailable)
        }

    public companion object {
        /** The bridge's code for a run that hit its time limit (Flutter `BuiltinLinuxException.code == 'timeout'`). */
        public const val TIMEOUT_CODE: String = "timeout"
    }
}

/** What the native bridge answered for one run (Flutter `BuiltinLinux.run` and the exceptions it throws). */
public sealed interface LinuxBridgeAnswer {
    /** The script ran; [output] is the last 64 KB of stdout and stderr together. */
    public data class Ran(val exitCode: Int, val output: String) : LinuxBridgeAnswer

    /** The bridge refused (`BuiltinLinuxException`), with its stable [code] and the phone's own Details text. */
    public data class BridgeFailed(val code: String?, val details: Redacted? = null) : LinuxBridgeAnswer

    /** The caller's own wait ran out (`TimeoutException`). */
    public data object TimedOut : LinuxBridgeAnswer

    /** Anything else went wrong. */
    public data object Crashed : LinuxBridgeAnswer
}

/** The port onto the app's own Ubuntu. */
public fun interface BuiltinLinuxPort {
    public suspend fun run(script: String, timeout: Duration): LinuxBridgeAnswer
}

/** Flutter `GitShellFailure`. */
public enum class ShellFailure { TimedOut, Unavailable }

/** The shell's answer: the run's output, or a mapped failure (Flutter `GitShellException`). */
public sealed interface ShellAnswer {
    public data class Output(val text: String) : ShellAnswer

    public data class Failed(val failure: ShellFailure, val details: Redacted? = null) : ShellAnswer
}
