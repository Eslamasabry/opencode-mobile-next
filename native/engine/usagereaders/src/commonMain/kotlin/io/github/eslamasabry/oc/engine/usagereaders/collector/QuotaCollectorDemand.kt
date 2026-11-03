package io.github.eslamasabry.oc.engine.usagereaders.collector

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.usagereaders.UsageServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the quota collector running only while something reads usage (Flutter `QuotaCollectorDemand`, B-LINUX-177
 * amended): the first read starts it ([use]), reads close together keep it, and it is stopped [idleForMs] after the
 * last one; the next read starts it again. A collector that already answers is left alone. Nothing else starts it:
 * starting the phone's OpenCode does not. Where there is no in-app Linux ([enabled] false) it does nothing.
 *
 * [ensure] is normally [InAppQuotaCollector.ensureRunning] (which also refuses when never set up or turned off);
 * [answers] probes the collector with the token; [stop] stops the service. [scope] owns the idle timer.
 */
@Suppress("LongParameterList")
public class QuotaCollectorDemand(
    private val scope: CoroutineScope,
    private val enabled: Boolean,
    private val ensure: suspend (UsageServer) -> Boolean,
    private val answers: suspend (token: String) -> Boolean,
    private val stop: suspend () -> Unit,
    private val idleForMs: Long = 2 * 60_000L,
    private val answerWithinMs: Long = 20_000L,
    private val answerEveryMs: Long = 500L,
) {
    private var idle: Job? = null
    private var waking: CompletableDeferred<Unit>? = null

    /** Whether a stop is pending (the collector runs, or was just asked to). */
    public val running: Boolean get() = idle != null

    /** Before a read: makes sure the collector runs and answers, then keeps it for [idleForMs] more. Quiet. */
    public suspend fun use(server: UsageServer, token: String) {
        if (!enabled) return
        touch()
        waking?.let { return it.await() }
        val done = CompletableDeferred<Unit>()
        waking = done
        try {
            wake(server, token)
        } finally {
            waking = null
            done.complete(Unit)
            touch()
        }
    }

    private suspend fun wake(server: UsageServer, token: String) {
        // A collector that cannot start leaves the read to fail as it always did.
        attempt({ Failure.Unexpected() }) {
            if (answers(token)) return
            if (!ensure(server)) return
            var waited = 0L
            while (waited < answerWithinMs) {
                if (answers(token)) return
                delay(answerEveryMs)
                waited += answerEveryMs
            }
        }
    }

    /** (Re)arms the stop [idleForMs] from now. */
    public fun touch() {
        if (!enabled) return
        idle?.cancel()
        idle = scope.launch {
            delay(idleForMs)
            idle = null
            if (waking != null) {
                touch()
                return@launch
            }
            attempt({ Failure.Unexpected() }) { stop() }
        }
    }

    /** Forgets a pending stop (the collector was turned off). */
    public fun cancel() {
        idle?.cancel()
        idle = null
    }
}
