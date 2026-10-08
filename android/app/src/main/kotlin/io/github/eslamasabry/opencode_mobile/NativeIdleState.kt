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
                require(minutes in 1L..60L) { UNAVAILABLE }
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
        if (!usable || minutes !in 1..60) return false
        if (state.enabled == enabled && state.idleMinutes == minutes) return true
        val next = nextCounter() ?: return false
        return save(state.copy(enabled = enabled, idleMinutes = minutes, counter = next,
            generation = if (state.generation > 0) next else 0))
    }

    @Synchronized fun beginStop(owner: String, helper: String?, helperWasLive: Boolean): Long? {
        if (!usable || !state.enabled || !identifierValid(owner) ||
            helperWasLive != (helper != null) || (helper != null && !identifierValid(helper))) return null
        val next = nextCounter() ?: return null
        return if (save(state.copy(counter = next, generation = next, stopped = true,
                helperStopped = helperWasLive, owner = owner, helper = helper))) next else null
    }

    @Synchronized fun revoke(): Boolean {
        if (!usable) return false
        val next = nextCounter() ?: return false
        return save(state.copy(counter = next, generation = 0, stopped = false,
            helperStopped = false, owner = null, helper = null))
    }

    @Synchronized fun resumeAllowed(owner: String, expectedGen: Long): Boolean =
        matches(owner, expectedGen) && (state.stopped || state.helperStopped)

    @Synchronized fun markServerResumed(owner: String, gen: Long): Boolean {
        if (!matches(owner, gen)) return false
        return !state.stopped || save(state.copy(stopped = false))
    }

    /** A crash before owed helper completion keeps the original idle transition restartable. */
    @Synchronized fun markServerLost(owner: String, gen: Long): Boolean {
        if (!matches(owner, gen) || !state.helperStopped) return false
        return state.stopped || save(state.copy(stopped = true))
    }

    @Synchronized fun complete(owner: String, gen: Long, helperRunning: Boolean): Boolean {
        if (!matches(owner, gen) || state.stopped || (state.helperStopped && !helperRunning)) return false
        if (!state.helperStopped) return true // Keep the completed token for one gated fresh helper start.
        return save(state.copy(generation = 0, stopped = false, helperStopped = false,
            owner = null, helper = null))
    }

    @Synchronized fun clearCompletedHelperGate(owner: String, expectedGen: Long): Boolean {
        if (!matches(owner, expectedGen) || state.stopped || state.helperStopped) return false
        val next = nextCounter() ?: return false
        return save(state.copy(counter = next, generation = 0, owner = null, helper = null))
    }

    private fun matches(owner: String, generation: Long) =
        usable && generation > 0 && state.generation == generation && state.owner == owner

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
        val KEYS = setOf("version", "enabled", "idleMinutes", "counter", "generation",
            "stopped", "helperStopped", "owner", "helper")
        fun identifierValid(value: String) = Regex("[A-Za-z0-9_-]{1,80}").matches(value)
        fun valid(value: Snapshot): Boolean {
            if (value.idleMinutes !in 1..60 || value.counter < 0 || value.generation < 0) return false
            if (value.generation == 0L) return !value.stopped && !value.helperStopped &&
                value.owner == null && value.helper == null
            return value.generation == value.counter && value.owner?.let(::identifierValid) == true &&
                value.helperStopped == (value.helper != null) &&
                (value.helper == null || identifierValid(value.helper))
        }
    }
}
