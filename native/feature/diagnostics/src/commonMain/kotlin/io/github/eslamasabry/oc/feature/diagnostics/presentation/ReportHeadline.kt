package io.github.eslamasabry.oc.feature.diagnostics.presentation

import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.engine.diagnostics.problem.ProblemReportEvent

/**
 * The plain first line of Report a problem when the person has described nothing (E19-S04; Flutter
 * `recentErrorTitle`). It names what the person noticed; the technical record ("ForceStop reason=10 …") stays only in
 * the report's Diagnostics section.
 */
public enum class ReportHeadline {
    /** A crash or an "isn't responding" recorded by Android. */
    AppClosedUnexpectedly,
    AppNotResponding,

    /** Android reclaimed the app's memory, or its Memory Limiter capped it. */
    AndroidFreedMemory,

    /** Any other kill by the system. */
    AndroidClosedApp,
    Connection,
    Temperature,
    Screen,
    Startup,
    ReportStore,
    Generic,
}

public object ReportHeadlines {
    /** The source Android's exit records carry (`ReportProblem.ANDROID_EXIT_SOURCE`). */
    public const val ANDROID_EXIT_SOURCE: String = "android.exit"

    /**
     * The headline of the newest event that is a problem; [events] are newest first, as the report lists them. A
     * force-stop from Android settings, an update and a normal exit are not problems: they are skipped, so an older
     * real error can still name the report, and null when nothing is left.
     */
    public fun newest(events: List<ProblemReportEvent>): ReportHeadline? =
        events.asSequence().filter { it.isError }.firstNotNullOfOrNull(::of)

    /** One event's headline; null when it is not a problem (see [newest]). */
    public fun of(event: ProblemReportEvent): ReportHeadline? =
        if (event.source == ANDROID_EXIT_SOURCE) exit(exitKind(event.message)) else ofSource(event.source)

    /** By what ended the process; null for the person's own stop, an update or a normal end. */
    public fun exit(kind: ExitKind?): ReportHeadline? =
        when (kind) {
            ExitKind.Normal,
            ExitKind.Update,
            ExitKind.ForceStop,
            // Possibly a battery manager, possibly the person: a later real error names the report instead.
            ExitKind.BackgroundStop -> null
            ExitKind.LowMemory,
            ExitKind.MemoryLimit -> ReportHeadline.AndroidFreedMemory
            ExitKind.Crash -> ReportHeadline.AppClosedUnexpectedly
            ExitKind.Killed,
            null -> ReportHeadline.AndroidClosedApp
        }

    /** By where a kept error came from (Flutter `recentErrorTitle`). */
    public fun ofSource(source: String): ReportHeadline =
        when {
            source == "sse" || source.startsWith("sse.") || source == "events" -> ReportHeadline.Connection
            source == "thermal" || source.startsWith("thermal.") || source == "android.thermal" ->
                ReportHeadline.Temperature
            source == "flutter" || source == "widget" || source == "crash.widget" -> ReportHeadline.Screen
            source == "crash.last" || source == "crash.native" -> ReportHeadline.AppClosedUnexpectedly
            source == "crash.anr" -> ReportHeadline.AppNotResponding
            source == ANDROID_EXIT_SOURCE -> ReportHeadline.AndroidClosedApp
            source == "bootstrap" || source == "bootstrap-reset" || source == "notify-migration" ->
                ReportHeadline.Startup
            source == "report-problem" -> ReportHeadline.ReportStore
            else -> ReportHeadline.Generic
        }

    /** The kind the exit record starts with (`ReportProblem.recordAndroidExit` writes "<Kind> reason=…"). */
    internal fun exitKind(message: String): ExitKind? {
        val word = message.trim().substringBefore(' ')
        return ExitKind.entries.firstOrNull { it.name == word }
    }
}
