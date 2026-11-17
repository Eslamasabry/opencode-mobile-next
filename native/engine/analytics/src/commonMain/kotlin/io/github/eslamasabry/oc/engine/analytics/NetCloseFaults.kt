package io.github.eslamasabry.oc.engine.analytics

/**
 * Releasing a closed connection's engine failed (core/net `NetClient.close()`, which every disconnect, server switch,
 * removal and probe ends in). One non-fatal, behind the crash-report switch like every other: only the fixed name and
 * the error's class leave the phone, never a host, a URL or the error's message.
 */
public fun Reporting.reportNetClientCloseFailed(error: Throwable) {
    val kind = (error::class.simpleName ?: "unknown").replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
    reportFailure("net_client_close_failed", mapOf("failure" to kind))
}
