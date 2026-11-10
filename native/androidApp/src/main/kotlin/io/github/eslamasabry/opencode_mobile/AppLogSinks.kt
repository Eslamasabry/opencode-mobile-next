package io.github.eslamasabry.opencode_mobile

import android.util.Log
import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.LogSink
import io.github.eslamasabry.oc.core.common.Severity

/**
 * The sinks behind the redacting writer. A debuggable build also writes every line to logcat so a tester can read the
 * app's own lines (`adb logcat -s <Tag>`). A release build writes a tag's lines there only while a tester has asked
 * for that tag (`adb shell setprop log.tag.<Tag> DEBUG`, gone at the next restart of the phone), so a phone nobody is
 * testing on gets exactly [base].
 */
internal fun appLogSinks(
    debuggable: Boolean,
    base: List<LogSink>,
    logcat: LogSink,
    asked: (String) -> Boolean,
): List<LogSink> = base + if (debuggable) logcat else OnRequestLogSink(logcat, asked)

/** Passes on only the lines whose tag a tester asked for. */
internal class OnRequestLogSink(private val to: LogSink, private val asked: (String) -> Boolean) : LogSink {
    override fun write(entry: LogEntry) {
        if (asked(entry.tag)) to.write(entry)
    }
}

/** Android's own per-tag switch: off unless `log.tag.<Tag>` was set to DEBUG or VERBOSE. */
internal fun logTagAsked(tag: String): Boolean =
    try {
        Log.isLoggable(tag, Log.DEBUG)
    } catch (_: IllegalArgumentException) {
        false
    }

/** Logcat with the entry's own tag. Only ever a delegate of the redacting writer, so the message is already clean. */
internal class LogcatLogSink : LogSink {
    override fun write(entry: LogEntry) {
        val priority =
            when (entry.severity) {
                Severity.Debug -> Log.DEBUG
                Severity.Info -> Log.INFO
                Severity.Warn -> Log.WARN
                Severity.Error -> Log.ERROR
            }
        Log.println(priority, entry.tag, entry.message)
    }
}
