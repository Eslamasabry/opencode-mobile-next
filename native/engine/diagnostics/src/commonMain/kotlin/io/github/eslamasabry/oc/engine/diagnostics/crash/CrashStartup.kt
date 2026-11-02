package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the platform hands the crash startup: the store, and this start's evidence (never trace files, 16 §1.10). */
public class CrashStartupEvidence(
    public val store: CrashStore,
    /** The JVM handler's last crash record (`NativeCrashStore`), if any. */
    public val platformCrash: NativeCrashRecord? = null,
    /** `ApplicationExitInfo` records seen at this start; only native crashes and ANRs are imported. */
    public val exits: List<ExitRecord> = emptyList(),
)

/** The platform side of the crash startup; may be slow, fail, or never answer. */
public fun interface CrashStartupSource {
    public suspend fun open(): Outcome<CrashStartupEvidence>
}

/**
 * Crash diagnostics startup that never blocks launch (port of `CrashDiagnosticsStartup`, B-SEC-045).
 *
 * [start] returns the launch wait, bounded by [launchBudget]; the real opening goes on in [scope] and resolves [ready]
 * later. [reset] starts a new generation: an old reply never attaches or touches evidence, queued or running replays
 * stop, and the old readiness resolves to null. Restored records reach the diagnostics ring once, after readiness.
 */
public class CrashStartup(
    private val source: CrashStartupSource,
    private val diagnostics: Diagnostics,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    public val launchBudget: Duration = LAUNCH_BUDGET,
) {
    private class Opening(val pending: Deferred<CrashDiagnostics?>, val bounded: Deferred<CrashDiagnostics?>)
    private data class Lifecycle(
        val token: Any = Any(),
        val ready: CompletableDeferred<CrashDiagnostics?> = CompletableDeferred(),
        val opening: Opening? = null,
        val controller: CrashDiagnostics? = null,
    )
    private val lifecycle = MutableStateFlow(Lifecycle())

    /** A synchronous view of the same atomically published generation used by start and reset. */
    @OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
    public val current: StateFlow<CrashDiagnostics?> = object : StateFlow<CrashDiagnostics?> {
        override val value: CrashDiagnostics? get() = lifecycle.value.controller
        override val replayCache: List<CrashDiagnostics?> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<CrashDiagnostics?>): Nothing {
            lifecycle.map { it.controller }.distinctUntilChanged().collect(collector)
            awaitCancellation()
        }
    }

    /** Actual readiness of this generation, independent of the bounded launch wait. Null on failure or reset. */
    public val ready: Deferred<CrashDiagnostics?> get() = lifecycle.value.ready

    /** Install the lazy operation before any source or file I/O can run (B-SEC-045 eng-8). */
    public fun start(): Deferred<CrashDiagnostics?> {
        val initial = lifecycle.value
        initial.opening?.let { return it.bounded }
        val pending = scope.async(dispatchers.io, start = CoroutineStart.LAZY) { openAndPublish(initial.token, initial.ready) }
        val bounded = scope.async(start = CoroutineStart.LAZY) {
            try {
                withTimeoutOrNull(launchBudget) { pending.await() }
            } catch (cancelled: CancellationException) {
                if (lifecycle.value.token === initial.token) throw cancelled
                null
            }
        }
        val candidate = Opening(pending, bounded)
        while (true) {
            val state = lifecycle.value
            if (state.token !== initial.token || state.opening != null) {
                pending.cancel()
                bounded.cancel()
                return state.opening?.bounded ?: start()
            }
            if (lifecycle.compareAndSet(state, state.copy(opening = candidate))) {
                pending.start()
                bounded.start()
                return bounded
            }
        }
    }

    /** A global error before the store attached keeps its category in the ring only, never its text or stack. */
    public suspend fun capture(error: Throwable, source: CrashSource = CrashSource.Platform) {
        val controller = lifecycle.value.controller
        if (controller == null) {
            diagnostics.record(source.wire, CrashRecordCategory.of(error).wire)
        } else {
            controller.capture(error, source)
        }
    }

    /** A new generation: drops late replies, cancels replays, closes the current store. */
    public fun reset() {
        while (true) {
            val previous = lifecycle.value
            if (lifecycle.compareAndSet(previous, Lifecycle())) {
                previous.controller?.close()
                previous.ready.complete(null)
                previous.opening?.pending?.cancel()
                return
            }
        }
    }

    private suspend fun openAndPublish(token: Any, ready: CompletableDeferred<CrashDiagnostics?>): CrashDiagnostics? {
        fun canOpen() = lifecycle.value.token === token
        if (!canOpen()) return null
        val controller = try {
            (attempt({ Failure.Unexpected() }) { open(::canOpen) } as? Outcome.Ok)?.value
        } catch (cancelled: CancellationException) {
            if (canOpen()) throw cancelled
            return null
        }
        while (true) {
            val state = lifecycle.value
            if (state.token !== token) {
                controller?.close()
                return null
            }
            if (lifecycle.compareAndSet(state, state.copy(controller = controller))) break
        }
        val restored = controller?.storedRecords().orEmpty()
        ready.complete(controller)
        if (!canOpen()) {
            controller?.close()
            return null
        }
        // Readiness settles before the ring hears anything.
        if (controller != null && restored.isNotEmpty()) {
            scope.launch {
                for (entry in restored) {
                    if (!canOpen() || controller.isClosed) break
                    if (entry !in controller.storedRecords()) continue
                    diagnostics.record("crash.${entry.source.wire}", entry.category.wire, at = entry.at)
                }
            }
        }
        return controller
    }

    private suspend fun open(canOpen: () -> Boolean): CrashDiagnostics? {
        val evidence =
            when (val reply = source.open()) {
                is Outcome.Ok -> reply.value
                is Outcome.Failed -> return null
            }
        // A reply from a reset generation never attaches or touches evidence.
        if (!canOpen()) return null
        val controller = CrashDiagnostics.open(GenerationCrashStore(evidence.store, canOpen), diagnostics, clock, scope)
        if (!canOpen()) {
            controller.close()
            return null
        }
        controller.importAtStart(evidence.platformCrash, evidence.exits)
        if (!canOpen()) {
            controller.close()
            return null
        }
        return controller
    }

    public companion object {
        public val LAUNCH_BUDGET: Duration = 300.milliseconds
    }
}

/** Fence both sides of every suspended file operation, including a platform read that ignores cancellation. */
private class GenerationCrashStore(private val delegate: CrashStore, private val isCurrent: () -> Boolean) : CrashStore {
    private fun check() { if (!isCurrent()) throw CancellationException("crash generation replaced") }
    override suspend fun read(file: CrashFile, maxBytes: Int): Outcome<String?> {
        check()
        val result = delegate.read(file, maxBytes)
        check()
        return result
    }
    override suspend fun write(file: CrashFile, text: String): Outcome<Unit> {
        check()
        val result = delegate.write(file, text)
        check()
        return result
    }
    override suspend fun delete(file: CrashFile): Outcome<Unit> {
        check()
        val result = delegate.delete(file)
        check()
        return result
    }
}
