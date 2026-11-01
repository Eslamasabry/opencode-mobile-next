package io.github.eslamasabry.opencode_mobile

/** Durable idle intent only. The caller independently proves runtime and resume authority. */
internal class NativeIdleState(
    initial: Snapshot? = Snapshot(),
    private val persist: (Snapshot) -> Boolean,
) {
    data class Snapshot(
        val enabled: Boolean = false,
        val idleMinutes: Int = 5,
        val counter: Long = 0,
        val generation: Long = 0,
        val stopped: Boolean = false,
        val helperStopped: Boolean = false,
        val owner: String? = null,
        val helper: String? = null,
    ) {
        /** Whether this snapshot is the live idle token of [owner] at [generation]. */
        fun holds(owner: String, generation: Long) =
            generation > 0 && this.generation == generation && this.owner == owner

        fun map(): Map<String, Any?> {
            require(valid(this)) { UNAVAILABLE }
            return mapOf("version" to 1, "enabled" to enabled, "idleMinutes" to idleMinutes,
                "counter" to counter, "generation" to generation, "stopped" to stopped,
                "helperStopped" to helperStopped, "owner" to owner, "helper" to helper)
        }

        companion object {
            fun read(value: Map<String, Any?>): Snapshot {
                require(value.keys == KEYS) { UNAVAILABLE }
                fun integer(key: String): Long = when (val number = value[key]) {
                    is Int -> number.toLong()
                    is Long -> number
                    else -> throw IllegalArgumentException(UNAVAILABLE)
                }
                fun flag(key: String): Boolean = value[key] as? Boolean
                    ?: throw IllegalArgumentException(UNAVAILABLE)
                fun identifier(key: String): String? = when (val text = value[key]) {
                    null -> null
                    is String -> text
                    else -> throw IllegalArgumentException(UNAVAILABLE)
                }
                require(integer("version") == 1L) { UNAVAILABLE }
                val minutes = integer("idleMinutes")
                require(minutes in 1L..MAX_IDLE_MINUTES.toLong()) { UNAVAILABLE }
                return Snapshot(flag("enabled"), minutes.toInt(), integer("counter"),
                    integer("generation"), flag("stopped"), flag("helperStopped"),
                    identifier("owner"), identifier("helper")).also {
                    require(valid(it)) { UNAVAILABLE }
                }
            }
        }
    }

    private var state = initial?.takeIf { valid(it) } ?: Snapshot()
    private var usable = initial != null && valid(initial)
    val available: Boolean get() = synchronized(this) { usable }

    @Synchronized fun snapshot(): Snapshot = state

    @Synchronized fun configure(enabled: Boolean, minutes: Int): Boolean {
        val valid = usable && minutes in 1..MAX_IDLE_MINUTES
        val unchanged = state.enabled == enabled && state.idleMinutes == minutes
        val next = if (valid && !unchanged) nextCounter() else null
        return valid && (unchanged || (next != null &&
            save(state.copy(enabled = enabled, idleMinutes = minutes, counter = next,
                generation = if (state.generation > 0) next else 0))))
    }

    @Synchronized fun beginStop(owner: String, helper: String?, helperWasLive: Boolean): Long? {
        val admitted = usable && state.enabled && identifierValid(owner) &&
            helperWasLive == (helper != null) && (helper == null || identifierValid(helper))
        val next = if (admitted) nextCounter() else null
        return if (next != null && save(state.copy(counter = next, generation = next,
                stopped = true, helperStopped = helperWasLive, owner = owner, helper = helper))) {
            next
        } else {
            null
        }
    }

    @Synchronized fun revoke(): Boolean {
        val next = if (usable) nextCounter() else null
        return next != null && save(state.copy(counter = next, generation = 0, stopped = false,
            helperStopped = false, owner = null, helper = null))
    }

    @Synchronized fun markServerResumed(owner: String, gen: Long): Boolean {
        if (!(usable && state.holds(owner, gen))) return false
        return !state.stopped || save(state.copy(stopped = false))
    }

    /** A crash before owed helper completion keeps the original idle transition restartable. */
    @Synchronized fun markServerLost(owner: String, gen: Long): Boolean {
        if (!(usable && state.holds(owner, gen)) || !state.helperStopped) return false
        return state.stopped || save(state.copy(stopped = true))
    }

    @Synchronized fun complete(owner: String, gen: Long, helperRunning: Boolean): Boolean {
        val held = usable && state.holds(owner, gen)
        val pending = state.stopped || (state.helperStopped && !helperRunning)
        // Keep the completed token for one gated fresh helper start.
        return held && !pending && (!state.helperStopped || save(state.copy(generation = 0,
            stopped = false, helperStopped = false, owner = null, helper = null)))
    }

    @Synchronized fun clearCompletedHelperGate(owner: String, expectedGen: Long): Boolean {
        val ready = usable && state.holds(owner, expectedGen) && !state.stopped && !state.helperStopped
        val next = if (ready) nextCounter() else null
        return next != null && save(state.copy(counter = next, generation = 0, owner = null, helper = null))
    }

    private fun nextCounter(): Long? {
        if (state.counter == Long.MAX_VALUE) {
            usable = false
            return null
        }
        return state.counter + 1
    }

    private fun save(next: Snapshot): Boolean {
        val saved = try { persist(next) } catch (_: Throwable) { false }
        if (!saved) {
            usable = false
            return false
        }
        state = next
        return true
    }

    private companion object {
        const val UNAVAILABLE = "state unavailable"
        const val MAX_IDLE_MINUTES = 60
        val KEYS = setOf("version", "enabled", "idleMinutes", "counter", "generation",
            "stopped", "helperStopped", "owner", "helper")
        fun identifierValid(value: String) = Regex("[A-Za-z0-9_-]{1,80}").matches(value)
        fun valid(value: Snapshot): Boolean {
            val helper = value.helper
            val basic = value.idleMinutes in 1..MAX_IDLE_MINUTES && value.counter >= 0 && value.generation >= 0
            return basic && if (value.generation == 0L) {
                !value.stopped && !value.helperStopped && value.owner == null && helper == null
            } else {
                value.generation == value.counter && value.owner?.let(::identifierValid) == true &&
                    value.helperStopped == (helper != null) &&
                    (helper == null || identifierValid(helper))
            }
        }
    }
}

/** True when [owner]'s idle token at [expectedGen] has stopped work that may resume. */
internal fun NativeIdleState.resumeAllowed(owner: String, expectedGen: Long): Boolean =
    synchronized(this) {
        val state = snapshot()
        available && state.holds(owner, expectedGen) && (state.stopped || state.helperStopped)
    }
