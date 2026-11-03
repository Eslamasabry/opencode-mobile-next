package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Port of `Api2OperationsGateway.exportSession` (lib/api2/gateway_operations/ops_extras.dart) behind an HTTP-free port.

/**
 * The one request the export port receives. The route names its session globally, so it never carries a location
 * (Flutter: "never attach _loc()"). The port adds the connection's own auth and returns the body byte for byte.
 */
public data class SessionExportRequest(val sessionId: SessionId, val sanitize: Boolean) {
    /** Relative to the server's API prefix. */
    public val path: String get() = "/session/${DartJson.encodeComponent(sessionId.raw)}/export"

    public val query: Map<String, String> get() = mapOf("sanitize" to sanitize.toString())
}

/** The transport side: the complete response bytes, or the transport's Failure (tags kept). */
public fun interface SessionExportPort {
    public suspend fun download(request: SessionExportRequest): Outcome<ByteArray>
}

/**
 * Session export with the absent-endpoint latch: a 405/501, or a 404 that is not `SessionNotFoundError`, means the
 * server has no export route; it is remembered and never asked again ([supported] turns false). Every other failure
 * (401, a missing session, 5xx) is returned unchanged and leaves export available.
 */
public class SessionExporter(private val port: SessionExportPort) {
    private val mutableSupported = MutableStateFlow(true)
    public val supported: StateFlow<Boolean> = mutableSupported.asStateFlow()

    /** Redaction is on unless the person turned it off. The returned array is the caller's own copy. */
    public suspend fun export(sessionId: SessionId, sanitize: Boolean = true): Outcome<ByteArray> {
        if (!mutableSupported.value) return Outcome.Failed(Failure.Unavailable(FEATURE))
        return when (val result = port.download(SessionExportRequest(sessionId, sanitize))) {
            is Outcome.Ok -> Outcome.Ok(result.value.copyOf())
            is Outcome.Failed ->
                if (marksAbsent(result.failure)) {
                    mutableSupported.value = false
                    Outcome.Failed(Failure.Unavailable(FEATURE))
                } else {
                    result
                }
        }
    }

    public companion object {
        public val FEATURE: FeatureId = FeatureId("sessionExport")
    }
}

/** A missing session is a 404 too; only another 404 (or 405/501, or the latch itself) means the route is absent. */
internal fun marksAbsent(failure: Failure): Boolean =
    when (failure) {
        is Failure.Unavailable -> true
        is Failure.NotFound -> failure.tag?.raw != SESSION_NOT_FOUND
        is Failure.Rejected -> failure.status == 405
        is Failure.Server -> failure.status == 501
        else -> false
    }

internal const val SESSION_NOT_FOUND: String = "SessionNotFoundError"
