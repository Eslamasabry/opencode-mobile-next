package io.github.eslamasabry.opencode_mobile

import android.util.Log
import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.LogSink
import io.github.eslamasabry.oc.core.common.Severity

/**
 * The sinks behind the redacting writer. A debuggable build also writes to logcat so a tester can read the app's own
 * lines (`adb logcat -s <Tag>`); a release build keeps exactly [base].
 */
internal fun appLogSinks(debuggable: Boolean, base: List<LogSink>, logcat: LogSink): List<LogSink> =
    if (debuggable) base + logcat else base

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
