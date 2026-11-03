package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.model.StreamStatus

/** One handled error for Report a problem: where it happened and a plain, already-redacted line. */
public data class HandledError(val source: String, val message: String)

/**
 * What feeds Report a problem's error list (plan 07 I18; Flutter feeds handled errors into its report evidence): the
 * app's own warnings and errors (already through the redacting log root) and a connection to a saved server dropping
 * or stopping. Lines carry the failure's kind only, never an address, a credential or server text (rule 7).
 */
public object HandledErrors {
    /** A warning or an error the app logged; info and debug lines are not errors. */
    public fun fromLog(entry: LogEntry): HandledError? {
        if (entry.severity < Severity.Warn) return null
        val shape = entry.error?.type?.let { " ($it)" }.orEmpty()
        return HandledError(entry.tag, entry.message + shape)
    }

    /**
     * A server's event stream moving from [previous] to [next]: a stop (sign-in needed, incompatible server) and the
     * first drop into Disconnected are kept; reconnect attempts and recoveries are not errors.
     */
    public fun fromStream(previous: StreamStatus?, next: StreamStatus): HandledError? =
        when {
            next is StreamStatus.Stopped && previous !is StreamStatus.Stopped ->
                HandledError(SOURCE, "connection stopped: ${next.failure::class.simpleName ?: "Failure"}")
            next is StreamStatus.Disconnected && previous !is StreamStatus.Disconnected ->
                HandledError(SOURCE, "connection lost after ${next.attempt} tries")
            else -> null
        }

    private const val SOURCE = "connection"
}
