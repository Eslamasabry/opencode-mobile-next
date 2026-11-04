package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashRecordCategory

/**
 * What an app framework error callback hands on: the fixed category only. No cause, no caller metadata, no text the
 * throwable controlled (B-STATE-074).
 */
public class CategorizedAppError internal constructor(public val category: String, public val source: String) :
    RuntimeException(category) {
    override fun toString(): String = category
}

/**
 * The global error callbacks' half that feeds diagnostics (port of `installAppErrorCapture`; B-STATE-074). Pattern
 * redaction cannot prove arbitrary throwable-controlled metadata safe, so only a category chosen from the error's TYPE
 * is recorded: its message, `toString`, stack and cause are never read. The previous handler gets a
 * [CategorizedAppError] instead of the original.
 */
public class AppErrorCapture(private val diagnostics: Diagnostics) {
    /** Records [error]'s category under [source] and answers the scrubbed error to forward. Never throws. */
    public fun capture(error: Throwable, source: String = FRAMEWORK_SOURCE): CategorizedAppError {
        val category =
            try {
                CrashRecordCategory.of(error).wire
            } catch (_: Exception) {
                CrashRecordCategory.ApplicationError.wire
            }
        try {
            diagnostics.record(source, category)
        } catch (_: Exception) {
            // A failing diagnostics sink must never break the error path it reports on.
        }
        return CategorizedAppError(category, source)
    }

    /** An uncaught-error handler: captures, then hands the previous handler only the scrubbed error. */
    public fun handler(previous: ((Throwable) -> Unit)?, source: String = FRAMEWORK_SOURCE): (Throwable) -> Unit =
        { error ->
            val safe = capture(error, source)
            previous?.invoke(safe)
        }

    public companion object {
        public const val FRAMEWORK_SOURCE: String = "kotlin"
    }
}
