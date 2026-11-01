package io.github.eslamasabry.oc.engine.runtime.budget

import io.github.eslamasabry.oc.engine.runtime.heat.BatteryReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * What the guard reads before it admits heavy work. The Android binding answers from `ProcessHost.appProcessCount()`,
 * `ProcessHost.childProcessLimit()` and `DeviceFacts`; every reader answers a safe value instead of throwing.
 */
public interface BudgetReadings {
    /** The processes Android counts against its limit (the app's own excepted); null when it cannot be read. */
    public suspend fun processCount(): Int?

    /** Whether the person turned Android's limit off; false (the limit applies) when unknown. */
    public suspend fun limitLifted(): Boolean

    public suspend fun thermal(): ThermalReading

    public suspend fun battery(): BatteryReading

    /** How many agent conversations run now, for the waiting line's words; 0 when unknown. */
    public suspend fun runningAgents(): Int
}

/** How a call to [ProcessBudgetGuard.admit] ended. */
public enum class BudgetAdmission { Admitted, Cancelled }

/** One piece of work the budget holds, for the line that says why. */
public class BudgetWaiter internal constructor(
    public val job: HeavyJob,
    public val since: Instant,
    public val wait: BudgetWait,
    internal val id: Long,
    internal val ready: CompletableDeferred<BudgetAdmission>,
) {
    internal fun waitingFor(reason: BudgetWait) = BudgetWaiter(job, since, reason, id, ready)
}

/** What the guard shows: what waits (oldest first) and the last reading, for the waiting line. */
public data class BudgetState(
    val waiting: List<BudgetWaiter> = emptyList(),
    val lastCount: Int? = null,
    val lastCeiling: Int = ProcessBudget.CAP,
    val runningAgents: Int = 0,
) {
    /** The most pressing reason anything waits; null when nothing does. */
    val wait: BudgetWait? get() = waiting.maxOfOrNull { it.wait }
}

/**
 * Holds heavy work until the phone has room, is cool and has battery (port of process_budget.dart
 * `ProcessBudgetGuard`; plan 11 §7.2 "Admission"). One per app; called by `:engine:agents`, `SetupEngine` and
 * `LinuxServices` before they start heavy work.
 *
 * The guard **waits, never kills**: it only holds what has not started, and waiting work starts by itself when room
 * frees ([pollEvery] while anything waits, on [scope]). A phone it cannot read never blocks work. Work admitted moments
 * ago counts for [reservationLife] before it shows in a reading, so two starts in a row cannot both take the last room.
 * Cancelling the caller's coroutine withdraws its wait; [close] ends every wait as [BudgetAdmission.Cancelled].
 */
public class ProcessBudgetGuard(
    private val readings: BudgetReadings,
    private val fullSpeed: () -> Boolean,
    private val clock: Clock,
    private val scope: CoroutineScope,
    public val pollEvery: Duration = 5.seconds,
    public val reservationLife: Duration = 45.seconds,
    /** False where there is no in-app Linux to count: callers are admitted at once and nothing is read. */
    public val enabled: Boolean = true,
) {
    private data class Inner(
        val visible: BudgetState = BudgetState(),
        val reservations: List<Pair<Instant, Int>> = emptyList(),
        val roomWaits: List<Instant> = emptyList(),
        val nextId: Long = 0,
        val closed: Boolean = false,
    )

    private val inner = MutableStateFlow(Inner())
    private val visible = MutableStateFlow(BudgetState())
    private val checking = Mutex()
    private var poller: Job? = null

    public val state: StateFlow<BudgetState> = visible.asStateFlow()

    /**
     * The budget held work for room at least twice in the last hour: the phone keeps running into Android's limit (the
     * guided Developer-options card may help). Heat and the battery do not count.
     */
    public fun queuesOften(): Boolean {
        val since = clock.now() - 1.hours
        return inner.value.roomWaits.count { it > since } >= 2
    }

    /** Waits until [job] may start. */
    public suspend fun admit(job: HeavyJob): BudgetAdmission {
        if (!enabled) return BudgetAdmission.Admitted
        if (inner.value.closed) return BudgetAdmission.Cancelled
        val now = clock.now()
        val first = decide(job) ?: return BudgetAdmission.Admitted.also { reserve(job) }
        val ready = CompletableDeferred<BudgetAdmission>()
        var waiter: BudgetWaiter? = null
        mutate {
            val w = BudgetWaiter(job, now, first, it.nextId, ready).also { made -> waiter = made }
            it.copy(
                visible = it.visible.copy(waiting = it.visible.waiting + w),
                roomWaits = if (first == BudgetWait.Room) it.roomWaits + now else it.roomWaits,
                nextId = it.nextId + 1,
            )
        }
        ensurePolling()
        try {
            return ready.await()
        } catch (e: CancellationException) {
            waiter?.let { remove(it.id) }
            throw e
        }
    }

    /** Looks again now and starts what fits, oldest first. A check already running makes this one return at once. */
    public suspend fun check() {
        if (!checking.tryLock()) return
        try {
            for (waiter in inner.value.visible.waiting) {
                val decision = decide(waiter.job)
                val current = inner.value.visible.waiting.firstOrNull { it.id == waiter.id } ?: continue
                when {
                    decision == null -> {
                        remove(current.id)
                        reserve(current.job)
                        current.ready.complete(BudgetAdmission.Admitted)
                    }
                    decision != current.wait ->
                        mutate { s ->
                            s.copy(
                                visible =
                                    s.visible.copy(
                                        waiting = s.visible.waiting.map { if (it.id == current.id) it.waitingFor(decision) else it },
                                    ),
                            )
                        }
                    // Oldest first: a younger job never jumps a waiting older one.
                    else -> break
                }
            }
        } finally {
            checking.unlock()
        }
    }

    /** Ends every wait as cancelled; the guard admits nothing afterwards. */
    public fun close() {
        poller?.cancel()
        var held = emptyList<BudgetWaiter>()
        mutate {
            held = it.visible.waiting
            it.copy(visible = it.visible.copy(waiting = emptyList()), closed = true)
        }
        held.forEach { it.ready.complete(BudgetAdmission.Cancelled) }
    }

    private fun ensurePolling() {
        if (poller?.isActive == true) return
        poller =
            scope.launch {
                while (inner.value.visible.waiting.isNotEmpty()) {
                    delay(pollEvery)
                    check()
                }
            }
    }

    private fun remove(id: Long) = mutate { s -> s.copy(visible = s.visible.copy(waiting = s.visible.waiting.filterNot { it.id == id })) }

    private fun reserve(job: HeavyJob) = mutate { it.copy(reservations = it.reservations + (clock.now() to job.processes)) }

    private fun reserved(): Int {
        val since = clock.now() - reservationLife
        mutate { s -> s.copy(reservations = s.reservations.filter { it.first > since }) }
        return inner.value.reservations.sumOf { it.second }
    }

    private suspend fun decide(job: HeavyJob): BudgetWait? {
        // Unreadable: start; the budget never blocks work it cannot measure.
        val count = readings.processCount() ?: return null
        val lifted = readings.limitLifted()
        val thermal = readings.thermal()
        val battery = readings.battery()
        val decision = ProcessBudget.decide(job, count + reserved(), lifted, thermal, battery, fullSpeed())
        val agents = if (decision == BudgetWait.Room) readings.runningAgents() else null
        mutate {
            it.copy(
                visible =
                    it.visible.copy(
                        lastCount = count,
                        lastCeiling = ProcessBudget.ceiling(lifted),
                        runningAgents = agents ?: it.visible.runningAgents,
                    ),
            )
        }
        return decision
    }

    private inline fun mutate(change: (Inner) -> Inner) {
        inner.update(change)
        visible.value = inner.value.visible
    }
}
