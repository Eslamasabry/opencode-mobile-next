package io.github.eslamasabry.oc.engine.runtime.heat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.recovery.AutomationGate
import io.github.eslamasabry.oc.engine.runtime.recovery.RecoveryGate
import io.github.eslamasabry.oc.engine.runtime.recovery.RuntimeAutomation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Pauses what runs on this phone when Android says it is hot, and gives it back once cool (port of
 * lib/builtin/thermal_guard.dart `ThermalGuard`; plan 11 §8.2). The rules are [ThermalPolicy]'s; this layer acts through
 * [port], saves every hold through [store] so a process that dies mid-episode is finished by the next one
 * (B-ANDR-090), and tells the person once per episode:
 *
 * - [notice]: the shell's one line until dismissed (B-ANDR-110);
 * - [pauseActive]: folded into the one status notification ("Paused · the phone is hot"); the guard posts no
 *   notification of its own, on screen or off (B-ANDR-086);
 * - [onAct]: once per target, after a CONFIRMED pause, stop or resume (P6.2 While you were away).
 *
 * The protective pause and stop always run; giving back is gated per profile on [RuntimeAutomation.ThermalRecovery],
 * read again before the resume and passed to the port to ask before every step (B-ANDR-088). An unconfirmed resume
 * keeps the hold and waits another [ThermalPolicy.coolFor] from that try (B-ANDR-087).
 *
 * One per app, fed by the one thermal listener. Readings are handled one at a time, in order. The binding calls
 * [evaluate] at [nextCheckAt], so a cool phone that sends no more readings is still given back.
 */
public class ThermalGuard(
    private val port: HeatPausePort,
    private val store: ThermalGuardStore,
    automation: AutomationGate,
    private val clock: Clock,
    private val policy: ThermalPolicy = ThermalPolicy(),
    private val onReading: ((ThermalReading) -> Unit)? = null,
    private val onAct: (suspend (HeatAct) -> Unit)? = null,
) {
    private val recovery = RecoveryGate(automation, RuntimeAutomation.ThermalRecovery)
    private val lane = Mutex()
    private val heldNow = LinkedHashMap<ProfileId, HeatHold>()
    private var last = ThermalReading.Unknown
    private var started = false
    private var closed = false

    private val noticeState = MutableStateFlow<ThermalNotice?>(null)
    private val activeState = MutableStateFlow(false)
    private val nextCheckState = MutableStateFlow<Instant?>(null)

    /** The shell line to show until [dismiss]. */
    public val notice: StateFlow<ThermalNotice?> = noticeState.asStateFlow()

    /** True while the guard holds anything paused or stopped (Flutter `thermalPauseActive`). */
    public val pauseActive: StateFlow<Boolean> = activeState.asStateFlow()

    /** When the binding should call [evaluate] again; null when nothing waits on a cool phone recovery may resume. */
    public val nextCheckAt: StateFlow<Instant?> = nextCheckState.asStateFlow()

    public val hold: ThermalHold get() = policy.hold

    public val lastReading: ThermalReading get() = last

    /** What is held now, by profile. */
    public val holds: Map<ProfileId, HeatHold> get() = heldNow.toMap()

    /** "Pause the AI Team when the phone is hot" (Keep running); on when never set. */
    public suspend fun enabled(): Boolean = store.readEnabled() ?: true

    public suspend fun setEnabled(value: Boolean): Outcome<Unit> {
        val written = store.writeEnabled(value)
        evaluate()
        return written
    }

    public fun dismiss() {
        noticeState.value = null
    }

    /**
     * Once per process: what an earlier process held (it may have died while something was paused), then [current].
     * The binding then feeds every change to [observe].
     */
    public suspend fun start(current: ThermalReading) {
        if (started) return
        started = true
        lane.withLock { restoreHolds() }
        observe(current)
    }

    /** One reading from Android. */
    public suspend fun observe(reading: ThermalReading) {
        lane.withLock { last = reading }
        onReading?.invoke(reading)
        evaluate()
    }

    /** Applies the rules to the newest reading. */
    public suspend fun evaluate(): Unit =
        lane.withLock {
            if (closed) return@withLock
            val now = clock.now()
            when (policy.decide(last, now, enabled())) {
                ThermalAction.None -> Unit
                ThermalAction.Pause -> pause(now, alsoStop = false)
                ThermalAction.Stop -> pause(now, alsoStop = true)
                ThermalAction.Resume -> resume(now)
            }
            scheduleCoolCheck(now)
        }

    /** The app is going away: nothing more is done, and the status no longer says paused. Holds stay saved. */
    public fun close() {
        closed = true
        activeState.value = false
        nextCheckState.value = null
    }

    private suspend fun pause(now: Instant, alsoStop: Boolean) {
        val escalating = policy.hold == ThermalHold.Paused
        val paused = mutableListOf<HeatHold>()
        if (!escalating) {
            for (target in port.runningHere()) {
                if (target.id in heldNow) continue
                val held = port.pause(target, now) ?: continue
                heldNow[target.id] = held.copy(status = last.status)
                paused += held
            }
            if (heldNow.isEmpty()) return // nothing works on this phone
        }
        if (alsoStop) {
            for ((id, held) in heldNow.toList()) heldNow[id] = port.stop(held).copy(status = last.status)
        }
        for (held in heldNow.values) store.writeHold(held.target.id, HeatHoldCodec.encode(held))
        policy.applied(if (alsoStop) ThermalAction.Stop else ThermalAction.Pause)
        val kind = if (alsoStop) ThermalNoticeKind.Stopped else ThermalNoticeKind.Paused
        val told = if (alsoStop) heldNow.values.toList() else paused
        onAct?.let { act -> for (held in told) act(HeatAct(kind, held.target, held.since, now)) }
        noticeState.value = ThermalNotice(kind, now)
        publish()
    }

    private suspend fun resume(now: Instant) {
        var allBack = true
        for ((id, held) in heldNow.toList()) {
            if (!recovery.allows(id)) {
                allBack = false
                continue
            }
            if (port.resume(held) { recovery.allows(id) }) {
                heldNow.remove(id)
                store.removeHold(id.raw)
                onAct?.invoke(HeatAct(ThermalNoticeKind.Resumed, held.target, held.since, now))
            } else {
                allBack = false
            }
        }
        if (!allBack) {
            restartCoolWait(now)
            return
        }
        policy.applied(ThermalAction.Resume)
        noticeState.value = ThermalNotice(ThermalNoticeKind.Resumed, now)
        publish()
    }

    /**
     * Flutter sets `policy.coolSince = now` after an unconfirmed resume. [ThermalPolicy] keeps its wait private, so the
     * same is reached through its own API: re-apply the hold in force (clears the wait), then one decision on the
     * newest reading, which is cool (a resume was decided on it), starts the wait at [now].
     */
    private suspend fun restartCoolWait(now: Instant) {
        policy.applied(if (policy.hold == ThermalHold.Stopped) ThermalAction.Stop else ThermalAction.Pause)
        policy.decide(last, now, enabled())
    }

    private suspend fun scheduleCoolCheck(now: Instant) {
        val at = policy.resumeAt
        if (at == null || closed || heldNow.keys.none { recovery.allows(it) }) {
            nextCheckState.value = null
            return
        }
        nextCheckState.value = if (at < now) now else at + 1.seconds
    }

    private suspend fun restoreHolds() {
        for ((id, raw) in store.readHolds()) {
            val held = HeatHoldCodec.decode(id, raw)
            if (held == null) store.removeHold(id) else heldNow[held.target.id] = held
        }
        if (heldNow.isEmpty()) return
        policy.applied(if (heldNow.values.any { it.serviceStopped }) ThermalAction.Stop else ThermalAction.Pause)
        publish()
    }

    private fun publish() {
        if (closed) return
        activeState.value = policy.hold != ThermalHold.None
    }
}
