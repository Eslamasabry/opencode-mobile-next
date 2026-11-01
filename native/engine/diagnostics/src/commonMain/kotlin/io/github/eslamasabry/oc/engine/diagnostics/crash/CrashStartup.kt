package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
    private val generation = AtomicInt(0)
    private val opening = AtomicReference<Deferred<CrashDiagnostics?>?>(null)
    private val readiness = AtomicReference(CompletableDeferred<CrashDiagnostics?>())
    private val mutableCurrent = MutableStateFlow<CrashDiagnostics?>(null)

    /** The attached store, once (and while) this generation opened one. */
    public val current: StateFlow<CrashDiagnostics?> = mutableCurrent.asStateFlow()

    /** Actual readiness of this generation, independent of the bounded launch wait. Null on failure or reset. */
    public val ready: Deferred<CrashDiagnostics?> get() = readiness.load()

    /** The bounded launch wait; every call in one generation answers the same wait and opens the platform once. */
    public fun start(): Deferred<CrashDiagnostics?> {
        opening.load()?.let { return it }
        val gen = generation.load()
        val ready = readiness.load()
        val pending = scope.async(dispatchers.io) { openAndPublish(gen, ready) }
        val bounded = scope.async { withTimeoutOrNull(launchBudget) { pending.await() } }
        if (opening.compareAndSet(null, bounded)) return bounded
        pending.cancel()
        bounded.cancel()
        return opening.load() ?: bounded
    }

    /** A global error before the store attached keeps its category in the ring only, never its text or stack. */
    public suspend fun capture(error: Throwable, source: CrashSource = CrashSource.Platform) {
        val controller = mutableCurrent.value
        if (controller == null) {
            diagnostics.record(source.wire, CrashRecordCategory.of(error).wire)
        } else {
            controller.capture(error, source)
        }
    }

    /** A new generation: drops late replies, cancels replays, closes the current store. */
    public fun reset() {
        generation.addAndFetch(1)
        mutableCurrent.value?.close()
        mutableCurrent.value = null
        opening.store(null)
        readiness.exchange(CompletableDeferred()).complete(null)
    }

    private suspend fun openAndPublish(gen: Int, ready: CompletableDeferred<CrashDiagnostics?>): CrashDiagnostics? {
        fun canOpen() = generation.load() == gen
        val controller = (attempt({ Failure.Unexpected() }) { open(::canOpen) } as? Outcome.Ok)?.value
        if (!canOpen()) {
            controller?.close()
            return null
        }
        val restored = controller?.storedRecords().orEmpty()
        mutableCurrent.value = controller
        ready.complete(controller)
        // Readiness settles before the ring hears anything.
        if (controller != null && restored.isNotEmpty()) {
            scope.launch {
                for (entry in restored) {
                    // A ring listener may reset the startup mid-replay.
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
        val controller = CrashDiagnostics.open(evidence.store, diagnostics, clock, scope)
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
