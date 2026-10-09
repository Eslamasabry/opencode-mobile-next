package io.github.eslamasabry.opencode_mobile

/** Volatile Dart work evidence. An expired/missing sample never establishes idle. */
internal class NativeIdleHeartbeat(private val freshForMs: Long = 45_000) {
    private data class Evidence(val owner: String, val busy: Boolean?, val observedAt: Long)
    private var evidence: Evidence? = null
    private var highWater: Long? = null

    init { require(freshForMs > 0) { "Heartbeat freshness must be positive." } }

    @Synchronized fun observe(owner: String, busy: Boolean?, nowMillis: Long): Boolean {
        val timeValid = acceptTime(nowMillis)
        if (!timeValid || !validOwner(owner)) {
            evidence = null
            return false
        }
        evidence = Evidence(owner, busy, nowMillis)
        return true
    }

    @Synchronized fun busy(owner: String, nowMillis: Long): Boolean? {
        if (!acceptTime(nowMillis) || !validOwner(owner)) return null
        val sample = evidence ?: return null
        if (owner != sample.owner) return null
        // Accepted nonnegative monotonic values make this subtraction safe even at Long.MAX_VALUE.
        val elapsed = nowMillis - sample.observedAt
        return if (elapsed < freshForMs) sample.busy else null
    }

    @Synchronized fun clear() { evidence = null }

    private fun acceptTime(nowMillis: Long): Boolean {
        val previous = highWater
        if (nowMillis < 0 || (previous != null && nowMillis < previous)) {
            evidence = null
            return false
        }
        highWater = nowMillis
        return true
    }

    private fun validOwner(owner: String) = Regex("[A-Za-z0-9_-]{1,80}").matches(owner)
}
