package io.github.eslamasabry.oc.feature.linux.manager

/** How the one long job stands (port of `LinuxJobStatus`). */
public enum class LinuxJobStatus {
    None,
    Running,
    Done,
    Failed,
    Interrupted,
    TimedOut,
}

/** A package (or backup) job as its log tells it (port of `LinuxJobState.fromLog`, B-LINUX-091). */
public data class LinuxJobState(
    val kind: LinuxJobKind? = null,
    val names: List<String> = emptyList(),
    val status: LinuxJobStatus = LinuxJobStatus.None,
    /** 0–100 from apt's own status stream, null while unknown. */
    val percent: Int? = null,
    /** The script's latest stage text: technical, for Details only. */
    val stage: String? = null,
    val exitCode: Int? = null,
    /** The end of the log, for Details. */
    val tail: String = "",
    val details: io.github.eslamasabry.oc.core.common.Redacted? = null,
) {
    val running: Boolean
        get() = status == LinuxJobStatus.Running

    /** Another task held the slot when the job began. */
    val wasBusy: Boolean
        get() = exitCode == 75

    public companion object {
        public val None: LinuxJobState = LinuxJobState()

        public fun fromLog(log: String, running: Boolean): LinuxJobState {
            if (log.isBlank() && !running) return None
            var kind: LinuxJobKind? = null
            var names = emptyList<String>()
            var percent: Int? = null
            var stage: String? = null
            var exitCode: Int? = null
            for (line in log.split('\n')) {
                when {
                    line.startsWith("::oc job-exit ") ->
                        exitCode =
                            line
                                .substring(LinuxPackages.JOB_EXIT_PREFIX.length)
                                .trim()
                                .toIntOrNull()
                    line.startsWith("::oc job ") -> kind = LinuxJobKind.of(line.substring(9).trim())
                    line.startsWith("::oc names") ->
                        names = line.substring(10).trim().split(' ').filter { it.isNotEmpty() }
                    line.startsWith("::oc percent ") ->
                        line.substring(13).trim().toDoubleOrNull()?.let {
                            percent = kotlin.math.round(it).toInt().coerceIn(0, 100)
                        }
                    line.startsWith("::oc stage ") -> stage = line.substring(11).trim()
                }
            }
            if (kind == null && !running) return None
            val status =
                when {
                    running && exitCode == null -> LinuxJobStatus.Running
                    exitCode == 0 -> LinuxJobStatus.Done
                    exitCode == 124 -> LinuxJobStatus.TimedOut
                    exitCode != null -> LinuxJobStatus.Failed
                    else -> LinuxJobStatus.Interrupted
                }
            val lines = log.trimEnd().split('\n')
            return LinuxJobState(
                kind,
                names,
                status,
                percent,
                stage,
                exitCode,
                lines.takeLast(40).joinToString("\n"),
            )
        }
    }
}
