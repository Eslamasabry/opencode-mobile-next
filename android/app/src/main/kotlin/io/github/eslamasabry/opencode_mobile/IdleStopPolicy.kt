package io.github.eslamasabry.opencode_mobile

/** A deadline requires confirmed idle work, independently of CPU lease expiry. */
internal class IdleStopPolicy(enabled: Boolean = false, idleMinutes: Int = 5) {
    data class Observation(val deadlineMillis: Long?, val stopDue: Boolean)

    private var enabled = enabled
    private var idleMinutes = idleMinutes
    private var deadlineMillis: Long? = null
    private var lastObservedMillis: Long? = null

    init { require(idleMinutes in 1..MAX_IDLE_MINUTES) { "Idle minutes must be between 1 and 60." } }

    @Synchronized
    fun configure(enabled: Boolean, idleMinutes: Int) {
        require(idleMinutes in 1..MAX_IDLE_MINUTES) { "Idle minutes must be between 1 and 60." }
        this.enabled = enabled
        this.idleMinutes = idleMinutes
        deadlineMillis = null
    }

    @Synchronized
    fun observe(nowMillis: Long, foreground: Boolean, workBusy: Boolean?): Observation {
        val previous = lastObservedMillis
        val clockReversed = nowMillis < 0 || (previous != null && nowMillis < previous)
        // Retain the high-water mark: a reversed clock cannot start a new idle interval.
        if (!clockReversed) lastObservedMillis = nowMillis
        val idle = !clockReversed && enabled && !foreground && workBusy == false
        val deadline = if (idle) deadlineMillis ?: newDeadline(nowMillis) else null
        deadlineMillis = deadline
        return Observation(deadline, deadline != null && nowMillis >= deadline)
    }

    private fun newDeadline(nowMillis: Long): Long? {
        val delayMillis = idleMinutes * MILLIS_PER_MINUTE
        return if (nowMillis > Long.MAX_VALUE - delayMillis) null else nowMillis + delayMillis
    }

    private companion object {
        const val MAX_IDLE_MINUTES = 60
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
