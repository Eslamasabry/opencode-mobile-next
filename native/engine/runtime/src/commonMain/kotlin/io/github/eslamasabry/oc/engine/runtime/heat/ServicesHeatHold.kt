package io.github.eslamasabry.oc.engine.runtime.heat

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The services heat hold (port of lib/builtin/linux_services/linux_services_heat.dart; plan 11 §8.2, BUILTIN-LINUX-46):
 * at CRITICAL or worse every Linux service is held off (the person's on/off choices untouched); once the phone has stayed
 * cool for [coolFor] the hold goes and the services the person keeps on start again. Same thresholds and wait as
 * [ThermalPolicy] ([ThermalPolicy.veryHot], [ThermalPolicy.cool]).
 *
 * The hold is a file the supervisor reads ([readHold], [setHold] are the binding's), so it outlives the app: a process
 * that died while the phone was hot finds it again and gives it back once cool. Readings are handled one at a time, in
 * order. [readHold] answers null when the file cannot be read: that reading is skipped and the next one reads again.
 * A failed [setHold] leaves the state as it was, so the next reading tries again.
 */
public class ServicesHeatHold(
    private val readHold: suspend () -> Boolean?,
    private val setHold: suspend (held: Boolean) -> Outcome<Unit>,
    private val enabled: () -> Boolean,
    private val clock: Clock,
    public val coolFor: Duration = 2.minutes,
) {
    private val lane = Mutex()
    private var known: Boolean? = null
    private var coolSince: Instant? = null

    /** Whether the services are held off now, as far as this process knows. */
    public val held: Boolean get() = known ?: false

    /** One reading from the one thermal listener. */
    public suspend fun observe(reading: ThermalReading): Unit =
        lane.withLock {
            val now = clock.now()
            val held = known ?: readHold()?.also { known = it } ?: return@withLock
            if (!held) {
                coolSince = null
                if (enabled() && ThermalPolicy.veryHot(reading) && setHold(true) is Outcome.Ok) known = true
                return@withLock
            }
            if (!ThermalPolicy.cool(reading)) {
                coolSince = null
                return@withLock
            }
            val since = coolSince ?: now.also { coolSince = it }
            if (now - since >= coolFor && setHold(false) is Outcome.Ok) {
                known = false
                coolSince = null
            }
        }
}
