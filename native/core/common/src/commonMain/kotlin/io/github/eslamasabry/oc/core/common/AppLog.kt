package io.github.eslamasabry.oc.core.common

import kotlin.concurrent.atomics.AtomicReference

public enum class Severity { Debug, Info, Warn, Error }

/** A thrown error is logged by shape only: class name + up to 12 frames. Never its message. */
public data class ErrorShape(val type: String, val frames: List<String>)

public data class LogEntry(val severity: Severity, val tag: String, val message: String, val error: ErrorShape?)

public interface LogSink {
    public fun write(entry: LogEntry)
}

/** Marker for the root sink; only :core:security's RedactingWriter implements it. */
public interface RedactingLogSink : LogSink

/**
 * The one logging facade (plan 03 §18.1, S13). Entries logged before [install] wait in a 64-entry buffer and are
 * flushed THROUGH the redacting root at install; nothing is ever written unredacted. (Kermit joins underneath this
 * facade with the Logcat sink in slice 1; P1 writes to the redacting chain directly.)
 */
public object AppLog {
    private const val PRE_INSTALL_CAPACITY = 64
    private val state = AtomicReference<State>(State.Buffering(emptyList()))

    public fun install(root: RedactingLogSink, minSeverity: Severity) {
        while (true) {
            val current = state.load()
            val pending = (current as? State.Buffering)?.entries.orEmpty()
            if (state.compareAndSet(current, State.Installed(root, minSeverity))) {
                pending.filter { it.severity >= minSeverity }.forEach(root::write)
                return
            }
        }
    }

    public fun tagged(tag: String): Log = Log(tag)

    internal fun emit(entry: LogEntry) {
        while (true) {
            when (val current = state.load()) {
                is State.Installed -> {
                    if (entry.severity >= current.min) current.root.write(entry)
                    return
                }
                is State.Buffering -> {
                    val next = State.Buffering((current.entries + entry).takeLast(PRE_INSTALL_CAPACITY))
                    if (state.compareAndSet(current, next)) return
                }
            }
        }
    }

    internal fun shapeOf(error: Throwable): ErrorShape =
        ErrorShape(error::class.simpleName ?: "Throwable", stackFramesOf(error).take(12))

    private sealed interface State {
        data class Buffering(val entries: List<LogEntry>) : State
        data class Installed(val root: RedactingLogSink, val min: Severity) : State
    }

}

public class Log internal constructor(public val tag: String) {
    public fun d(message: () -> String): Unit = AppLog.emit(LogEntry(Severity.Debug, tag, message(), null))
    public fun i(message: () -> String): Unit = AppLog.emit(LogEntry(Severity.Info, tag, message(), null))
    public fun w(error: Throwable? = null, message: () -> String): Unit =
        AppLog.emit(LogEntry(Severity.Warn, tag, message(), error?.let(AppLog::shapeOf)))
    public fun e(error: Throwable? = null, message: () -> String): Unit =
        AppLog.emit(LogEntry(Severity.Error, tag, message(), error?.let(AppLog::shapeOf)))
}

internal expect fun stackFramesOf(error: Throwable): List<String>
