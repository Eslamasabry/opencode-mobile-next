package io.github.eslamasabry.oc.protocol.codex.probe

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.protocol.codex.CodexProfileRules
import io.github.eslamasabry.oc.protocol.codex.CodexUrlProblem
import kotlinx.coroutines.CancellationException

/** The part of a Codex gateway the connection probe uses (the real one is `CodexGateway`). */
public interface CodexProbeGateway {
    public suspend fun health(): Outcome<Health>

    public suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>>

    public suspend fun close()
}

/** What the pairing check proved (Dart `CodexConnectionProbeResult`). Copy lives in `:ui:words`. */
public sealed interface CodexProbeResult {
    public val ok: Boolean get() = this is Verified

    /** Health and a scoped one-item thread read passed. An empty list does not prove the folder exists. */
    public data class Verified(val version: String?) : CodexProbeResult

    /** Refused before any socket opened; nothing typed is echoed. */
    public data class InvalidInput(val url: CodexUrlProblem?, val directory: Boolean, val token: Boolean) : CodexProbeResult

    /**
     * Not verified. A Codex failure keeps its kind; an unhealthy server or any unexpected error is the fixed
     * [Failure.Unknown] without details (“Could not verify the Codex connection.”).
     */
    public data class NotVerified(val failure: Failure) : CodexProbeResult
}

/**
 * Checks a Codex endpoint and project folder without creating a turn (port of lib/state/codex_connection_probe.dart,
 * B-AGENT-060/220). Validation runs before [open], so malformed input cannot open a socket; health proves the
 * authenticated handshake; a one-item `thread/list` in the folder proves a scoped read. The gateway is always closed.
 */
public suspend fun probeCodexConnection(
    baseUrl: String,
    token: String,
    directory: String,
    open: (url: String, token: String, directory: String) -> CodexProbeGateway,
): CodexProbeResult {
    val url = CodexProfileRules.normalizeUrl(baseUrl)
    val dir = directory.trim()
    val urlProblem = CodexProfileRules.validateUrl(url)
    val dirBad = !CodexProfileRules.directoryAcceptable(dir)
    val tokenBad = !CodexProfileRules.tokenAcceptable(token)
    if (urlProblem != null || dirBad || tokenBad) return CodexProbeResult.InvalidInput(urlProblem, dirBad, tokenBad)
    var gateway: CodexProbeGateway? = null
    try {
        val opened = open(url, token, dir)
        gateway = opened
        val health = when (val h = opened.health()) {
            is Outcome.Failed -> return CodexProbeResult.NotVerified(h.failure.withoutDetails())
            is Outcome.Ok -> h.value
        }
        if (!health.healthy) return CodexProbeResult.NotVerified(Failure.Unknown())
        val page = opened.sessionPage(null, 1)
        if (page is Outcome.Failed) return CodexProbeResult.NotVerified(page.failure.withoutDetails())
        return CodexProbeResult.Verified(health.version)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        // A bug path (or a fake that throws): fixed words, nothing of the error.
        return CodexProbeResult.NotVerified(Failure.Unknown())
    } finally {
        try {
            gateway?.close()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // A cleanup failure never replaces the verdict.
        }
    }
}

/** An unexpected failure becomes the fixed one; a typed failure is kept (Codex failures carry no details). */
private fun Failure.withoutDetails(): Failure = if (this is Failure.Unexpected || details != null) Failure.Unknown() else this
