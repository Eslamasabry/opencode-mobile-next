package io.github.eslamasabry.oc.engine.runtime.heat

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The agent helper's scheduling level (plan 11 §5.4 `ProcessHost.setAgentPriority`, §7.2 "Agent priority"). [nice] is
 * the renice fallback's value; the Android binding moves the Paseo helper trees to the background class instead.
 * Nothing is ever signalled: only priorities change.
 */
public enum class AgentPriorityLevel(public val nice: Int) {
    Normal(0),
    Slow(10),
    Hold(15),
    ;

    public companion object {
        public fun of(tier: HeatTier): AgentPriorityLevel =
            when (tier) {
                HeatTier.Normal -> Normal
                HeatTier.Slow -> Slow
                HeatTier.Hold -> Hold
            }
    }
}

/**
 * Follows [HeatTierPolicy] and applies the result to the phone's agents (port of heat_tier.dart `AgentThrottle`).
 * One per app, fed by the one thermal listener's readings; the visibility binding calls [evaluate] when the app is
 * shown or hidden.
 *
 * Going up is at once. Coming down waits for [HeatTierPolicy.calmFor] of clear readings (a hot one restarts the wait),
 * except that "Always full speed" restores at once. While slowed, priorities are set again every [reapplyEvery] so a
 * helper started since is caught. A failed [setPriority] is tried again at the next reading. Decisions run one at a
 * time, in order.
 *
 * [setPriority] answers how many processes it moved (0 when no helper runs).
 */
public class AgentThrottle(
    private val fullSpeed: () -> Boolean,
    private val setPriority: suspend (AgentPriorityLevel) -> Outcome<Int>,
    private val battery: suspend () -> BatteryReading,
    private val powerSave: suspend () -> Boolean,
    private val hidden: () -> Boolean,
    private val clock: Clock,
) {
    private val lane = Mutex()
    private val tierState = MutableStateFlow(HeatTier.Normal)
    private var applied = HeatTier.Normal
    private var calmSince: Instant? = null
    private var last = ThermalReading.Unknown
    private var lastRun: Instant? = null

    /** The tier in force. */
    public val tier: StateFlow<HeatTier> = tierState.asStateFlow()

    /** One reading from the thermal listener. */
    public suspend fun observe(reading: ThermalReading) {
        lane.withLock { last = reading }
        evaluate()
    }

    /** Decides again from the latest reading. */
    public suspend fun evaluate(): Unit =
        lane.withLock {
            val now = clock.now()
            val reading = battery()
            val full = fullSpeed()
            val wanted =
                HeatTierPolicy.decide(
                    thermal = last,
                    batteryLevel = reading.level,
                    charging = reading.charging,
                    hidden = hidden(),
                    fullSpeed = full,
                    powerSave = powerSave(),
                )
            val current = tierState.value
            if (wanted.ordinal >= current.ordinal) {
                tierState.value = wanted
                calmSince = null
            } else {
                val since = calmSince ?: now.also { calmSince = it }
                if (now - since >= HeatTierPolicy.calmFor || (wanted == HeatTier.Normal && full)) {
                    tierState.value = wanted
                    calmSince = null
                }
            }
            val inForce = tierState.value
            val previous = lastRun
            val due = inForce != applied || (inForce.slowsAgents && (previous == null || now - previous >= reapplyEvery))
            if (!due) return@withLock
            val result = setPriority(AgentPriorityLevel.of(inForce))
            lastRun = now
            if (result is Outcome.Ok) applied = inForce
        }

    public companion object {
        /** While easing, priorities are set again this often: a helper started since inherits nothing from the app. */
        public val reapplyEvery: Duration = 5.minutes
    }
}
