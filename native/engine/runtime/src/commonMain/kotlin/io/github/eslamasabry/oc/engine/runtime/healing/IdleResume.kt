package io.github.eslamasabry.oc.engine.runtime.healing

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Port of lib/builtin/phone_server_idle.dart: foreground restoration of a native idle stop, independent of a manual
 * Start. The idle receipt must be valid and its generation live at every step; the agent helper is resumed (when the
 * idle stop took it) before the native completion, and any doubt ends in [IdleFailure.Unavailable] instead of a guess.
 * One restore runs per owner; a second caller waits for the same one.
 */
public class IdleResume(
    private val host: HealingHost,
    private val isForeground: () -> Boolean,
    private val ownerId: () -> String?,
    private val isReadable: (String) -> Boolean,
    /** Resumes the local agents the idle stop took (BA); Failed (or absent) ends the restore as unavailable. */
    private val resumeAgents: suspend (profileId: String, expectedIdleGeneration: Long) -> Outcome<Unit>,
    private val scope: CoroutineScope,
) {
    private var pending: Deferred<Unit>? = null
    private var pendingToken: Any? = null
    private var pendingOwner: String? = null
    private var epoch = 0
    private var disposed = false

    public var running: Boolean = false
        private set

    /** Dart's `failure` string: `idle_resume_unavailable` is [IdleFailure.Unavailable]. */
    public var failure: IdleFailure? = null
        private set

    public suspend fun check() {
        if (disposed) return
        val owner = ownerId() ?: return
        if (!isForeground() || !isReadable(owner)) return
        val existing = pending
        if (existing != null && pendingOwner == owner) return existing.await()
        val token = Any()
        val at = epoch
        pendingToken = token
        pendingOwner = owner
        running = true
        failure = null
        val operation = scope.async(start = CoroutineStart.LAZY) { restore(owner, at, token) }
        pending = operation
        operation.await()
    }

    private fun current(owner: String, at: Int): Boolean =
        !disposed && epoch == at && isForeground() && ownerId() == owner && isReadable(owner)

    private suspend fun restore(owner: String, at: Int, token: Any) {
        val admitted = steps(owner, at)
        if (admitted == false && current(owner, at)) failure = IdleFailure.Unavailable
        if (pendingToken === token) {
            pending = null
            pendingToken = null
            pendingOwner = null
            running = false
        }
    }

    /** True when restored or nothing to do, false when unavailable, null when this restore went stale. */
    private suspend fun steps(owner: String, at: Int): Boolean? {
        if (!current(owner, at)) return null
        var status = host.statusOrNull() ?: return false
        if (!current(owner, at)) return null
        if (!status.serverIdlePolicySupported) return true
        if (!status.serverIdleReceiptValid) return false
        if (!status.serverRestartWanted || !(status.serverIdleStopped || status.serverIdleHelperStopped)) return true
        val expected = status.serverIdleGeneration
        if (expected == null || expected <= 0) return false
        val helperWasStopped = status.serverIdleHelperStopped
        if (status.serverIdleStopped) {
            status = (host.resumeIdleStopped(owner, expected) as? Outcome.Ok)?.value ?: return if (current(owner, at)) false else null
            if (!current(owner, at)) return null
        }
        if (!live(status, expected) || status.serverIdleHelperStopped != helperWasStopped) return false
        if (helperWasStopped) {
            val resumed = resumeAgents(owner, expected)
            if (!current(owner, at)) return null
            if (resumed is Outcome.Failed) return false
        }
        // A Stop or a new generation during helper readiness cannot be completed.
        status = host.statusOrNull() ?: return false
        if (!current(owner, at)) return null
        if (!live(status, expected)) return false
        val completed = (host.completeIdleResume(owner, expected) as? Outcome.Ok)?.value ?: return if (current(owner, at)) false else null
        if (!current(owner, at)) return null
        return completed.serverIdlePolicySupported &&
            completed.serverIdleReceiptValid &&
            completed.serverRestartWanted &&
            completed.serverRunning &&
            !completed.serverIdleStopped &&
            !completed.serverIdleHelperStopped &&
            completed.serverIdleGeneration == (if (helperWasStopped) 0L else expected)
    }

    private fun live(status: RuntimeStatus, generation: Long): Boolean =
        status.serverIdlePolicySupported &&
            status.serverIdleReceiptValid &&
            status.serverRestartWanted &&
            status.serverRunning &&
            !status.serverIdleStopped &&
            status.serverIdleGeneration == generation

    /** Any ownership, foreground or lifecycle change: the pending restore can no longer act or report. */
    public fun invalidate() {
        if (disposed) return
        epoch++
        pending = null
        pendingToken = null
        pendingOwner = null
        running = false
        failure = null
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        epoch++
        pending = null
        pendingToken = null
        pendingOwner = null
        running = false
    }
}

/** Why the idle restore did not complete; the card offers no launch fallback while it is set. */
public enum class IdleFailure {
    /** Dart `idle_resume_unavailable`. */
    Unavailable,
}
