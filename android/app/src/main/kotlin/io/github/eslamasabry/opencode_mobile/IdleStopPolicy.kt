package io.github.eslamasabry.opencode_mobile

/** A deadline requires confirmed idle work, independently of CPU lease expiry. */
internal class IdleStopPolicy(enabled: Boolean = false, idleMinutes: Int = 5) {
    data class Observation(val deadlineMillis: Long?, val stopDue: Boolean)

    private var enabled = enabled
    private var idleMinutes = idleMinutes
    private var deadlineMillis: Long? = null
    private var lastObservedMillis: Long? = null

    init { require(idleMinutes in 1..60) { "Idle minutes must be between 1 and 60." } }

    @Synchronized
    fun configure(enabled: Boolean, idleMinutes: Int) {
        require(idleMinutes in 1..60) { "Idle minutes must be between 1 and 60." }
        this.enabled = enabled
        this.idleMinutes = idleMinutes
        deadlineMillis = null
    }

    @Synchronized
    fun observe(nowMillis: Long, foreground: Boolean, workBusy: Boolean?): Observation {
        val previous = lastObservedMillis
        if (nowMillis < 0 || (previous != null && nowMillis < previous)) {
            deadlineMillis = null
            // Retain the high-water mark: a reversed clock cannot start a new idle interval.
            return Observation(null, false)
        }
        lastObservedMillis = nowMillis
        if (!enabled || foreground || workBusy != false) {
            deadlineMillis = null
            return Observation(null, false)
        }
        val deadline = deadlineMillis ?: run {
            val delayMillis = idleMinutes * 60_000L
            if (nowMillis > Long.MAX_VALUE - delayMillis) {
                deadlineMillis = null
                return Observation(null, false)
            }
            (nowMillis + delayMillis).also { deadlineMillis = it }
        }
        return Observation(deadline, nowMillis >= deadline)
    }
}
