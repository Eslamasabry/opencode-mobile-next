package io.github.eslamasabry.oc.engine.runtime.history

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Port of lib/domain/opencode_history_copy.dart `OpenCodeHistorySwitchAdmission` and lib/state/phone_host.dart
 * `_checkHistorySwitch`: the read-only answer of [HistoryCopyScripts.SWITCH_CHECK], asked immediately before an
 * explicit move from OpenCode 1 to OpenCode 2. Unknown or failed checks never authorize a promise that chats will move.
 */
public enum class HistorySwitchAdmission {
    /** Nothing stands in the way: the move may stop OpenCode 1 and start OpenCode 2. */
    Allowed,

    /** OpenCode 2 already has its own separate history; both stay untouched (l10n: the switch's separate-history line). */
    SeparateHistory,

    /** The check could not answer (any other exit, a transport failure, an unreadable folder). */
    Unavailable,
    ;

    public companion object {
        /** Flutter `fromResult`: exit 0 allows; exit 74 with the blocked text is a separate history; all else unavailable. */
        public fun fromResult(exitCode: Int, output: String): HistorySwitchAdmission =
            when {
                exitCode == 0 -> Allowed
                exitCode == HistoryCopyScripts.FAILED_EXIT && output.contains(HistoryCopyScripts.SWITCH_BLOCKED_TEXT) ->
                    SeparateHistory
                else -> Unavailable
            }
    }
}

/** One finished run inside Ubuntu: its exit code and its folded stdout+stderr. */
public data class LinuxRunResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}

/** The port that runs one script as root inside the phone's Ubuntu (in-app proot or Termux), within [timeout]. */
public fun interface HistoryShellPort {
    public suspend fun run(script: String, timeout: Duration): Outcome<LinuxRunResult>
}

/** Flutter `_checkHistorySwitch`: runs [HistoryCopyScripts.admissionScript] for 20 s; a failed run is [Unavailable]. */
public class HistorySwitchCheck(private val shell: HistoryShellPort, private val timeout: Duration = 20.seconds) {
    public suspend fun admission(): HistorySwitchAdmission =
        when (val ran = shell.run(HistoryCopyScripts.admissionScript, timeout)) {
            is Outcome.Ok -> HistorySwitchAdmission.fromResult(ran.value.exitCode, ran.value.output)
            is Outcome.Failed -> HistorySwitchAdmission.Unavailable
        }
}
