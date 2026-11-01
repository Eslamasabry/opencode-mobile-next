package io.github.eslamasabry.oc.engine.diagnostics

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure

/** What kind of crash was seen (names == `:engine:analytics` `CrashCategory`; plan 18 `crash_seen`). */
public enum class CrashCategory { Kotlin, Native, OutOfMemory, StartupLoop, Other }

/** What kind of freeze was seen (names == `:engine:analytics` `FreezeKind`; plan 18 `freeze_seen`). */
public enum class FreezeKind { MainThreadStall, NotResponding }

/**
 * The port diagnostics reports through (COMMON.md, frozen): categories only, never a stack, class name or message.
 * `:engine:analytics` adapts it; this module may not depend on analytics (module order, A18-1).
 * Implementations never throw or block.
 */
public interface DiagnosticsSignals {
    public fun crash(category: CrashCategory, fatal: Boolean)

    public fun freeze(kind: FreezeKind, durationMs: Long, foreground: Boolean)

    public fun error(failure: Failure)

    public companion object {
        public val None: DiagnosticsSignals =
            object : DiagnosticsSignals {
                override fun crash(category: CrashCategory, fatal: Boolean) = Unit

                override fun freeze(kind: FreezeKind, durationMs: Long, foreground: Boolean) = Unit

                override fun error(failure: Failure) = Unit
            }
    }
}

private val signalLog = AppLog.tagged("diagnostics")

/** A broken signal adapter must never break the diagnostics path that called it. */
internal inline fun DiagnosticsSignals.safely(block: DiagnosticsSignals.() -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        signalLog.w(e) { "diagnostics signal adapter failed" }
    }
}
