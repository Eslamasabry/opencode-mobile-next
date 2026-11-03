package io.github.eslamasabry.oc.engine.linuxsetup

/**
 * The shared contract of the phone setup engine (port of lib/builtin/setup/setup_contract.dart). Nothing here knows
 * about any particular component, so adding one never changes this file.
 */

/** Where a setup job installs and runs OpenCode: the app's own Linux, or Termux's. */
public enum class SetupHostKind { Builtin, Termux }

/**
 * One installable piece: a check that says whether it is there, and an install that puts it there.
 * [app] marks a component the app installs into its own storage (voice typing) instead of a script inside Linux.
 */
public data class SetupComponent(
    val id: String,
    val title: String = id,
    val shortTitle: String = title,
    /** One plain sentence shown when a required component cannot be switched off. */
    val why: String? = null,
    /** What an optional component does, in one line, beside its switch. */
    val summary: String? = null,
    val checkScript: String = "",
    val installScript: String = "",
    val removeScript: String? = null,
    /** Exit 0 present (even partial), 1 absent, anything else unknown. */
    val presenceScript: String? = null,
    /** Prints the kilobytes a removal would give back on its last line. */
    val sizeScript: String? = null,
    val dependsOn: List<String> = emptyList(),
    val required: Boolean = false,
    val defaultOn: Boolean = false,
    /** Measured on a mid-range phone; weights the overall bar and the ETA. */
    val estimatedSeconds: Int = 60,
    /** Display/preflight estimate only. Never download-consent evidence. */
    val downloadBytes: Long? = null,
    /** Conservative known extracted payload bytes for peak storage admission. */
    val installedBytes: Long? = null,
    val native: Boolean = false,
    val agentUser: Boolean = false,
    /** Not installed: a step the engine itself runs at the end of every job (starting OpenCode). */
    val jobStep: Boolean = false,
    val app: Boolean = false,
)

public enum class SetupState { Idle, Running, Done, Failed, Interrupted, Cancelled }

public enum class ComponentState { Pending, Checking, Running, Done, Failed, Skipped }

public data class ComponentProgress(
    val id: String,
    val state: ComponentState,
    val stage: String? = null,
    val bytesDone: Long? = null,
    /** Null or 0 when the total is unknown. */
    val bytesTotal: Long? = null,
    val percent: Double? = null,
    val version: String? = null,
    val error: SetupProblem? = null,
) {
    /** This component's own fraction from its real signal, or null when it only reports stages. */
    val fraction: Double?
        get() {
            if (state == ComponentState.Done || state == ComponentState.Skipped) return 1.0
            val total = bytesTotal
            val done = bytesDone
            if (total != null && total > 0 && done != null) return (done.toDouble() / total).coerceIn(0.0, 1.0)
            val p = percent ?: return null
            return (p / 100).coerceIn(0.0, 1.0)
        }
}

public data class SetupProgress(
    val state: SetupState,
    val components: List<ComponentProgress>,
    /** 0..1, weighted by each component's estimate. */
    val overall: Double,
    val current: String? = null,
    /** Null until there is enough real progress to estimate honestly. */
    val etaSeconds: Int? = null,
    val error: SetupProblem? = null,
    /** Details text: redacted, without OCTRACE timing lines. */
    val logTail: String = "",
    val jobId: String? = null,
    val firstSetup: Boolean = false,
    val adding: List<String> = emptyList(),
) {
    val canContinue: Boolean
        get() = state == SetupState.Failed || state == SetupState.Interrupted || state == SetupState.Cancelled

    val ended: Boolean get() = canContinue || state == SetupState.Done

    public companion object {
        public val Idle: SetupProgress = SetupProgress(state = SetupState.Idle, components = emptyList(), overall = 0.0)
    }
}

/** Facts about a job rather than a component, kept in the job's params under [KEY]. */
public object SetupJobParams {
    public const val KEY: String = "_job"

    public val firstSetup: Map<String, Map<String, String>> = mapOf(KEY to mapOf("first" to "1"))

    public fun isFirstSetup(params: Map<String, Map<String, String>>): Boolean = params[KEY]?.get("first") == "1"

    public fun adding(ids: Iterable<String>): Map<String, Map<String, String>> =
        mapOf(KEY to mapOf("adding" to ids.joinToString(",")))

    public fun addingIds(params: Map<String, Map<String, String>>): List<String> =
        (params[KEY]?.get("adding") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * A job's log as people see it: the scripts' own lines, never the app's `OCTRACE` timing lines, which belong in the
 * device log and diagnostics only.
 */
public fun setupLogForPeople(log: String): String {
    if (!log.contains("OCTRACE")) return log
    return log.split('\n').filterNot { it.contains("OCTRACE") }.joinToString("\n")
}
