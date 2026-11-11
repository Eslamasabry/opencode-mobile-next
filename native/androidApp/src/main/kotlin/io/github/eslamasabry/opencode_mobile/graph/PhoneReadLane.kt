package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.opencode_mobile.ProbeStop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one lane the phone's agent reads and a person's steps take (AppPhoneAgents). Nothing in it runs beside anything
 * else: each read check and each step starts processes in the phone's Linux, and Android stops an app's child
 * processes past a limit. A read used to hold the lane through every sign-in and version check (up to 65 s each), so
 * an Install pressed during a read waited for all of them. Now a read gives way: a step asking for the lane stops the
 * running read ([ProbeStop] ends the check process it runs and lets no further one start), takes the lane as soon as
 * that process has ended, and its own re-read afterwards replaces what the stopped read did not finish.
 *
 * [offMain] runs the blocking [ProbeStop.stop] off the caller's thread.
 */
internal class PhoneReadLane(private val offMain: suspend (() -> Unit) -> Unit) {
    private val mutex = Mutex()
    private val lock = Any()
    private var waiting = 0
    private var reading: ProbeStop? = null

    /** A read of the phone; null when a step was waiting, so it did not start. [block] stops once its stop is set. */
    suspend fun <T> read(block: suspend (ProbeStop) -> T): T? = mutex.withLock { readHeld(block) }

    /** A read inside a step that already holds the lane (the step's own re-read): it gives way to the next step too. */
    suspend fun <T> readHeld(block: suspend (ProbeStop) -> T): T? {
        val stop = ProbeStop()
        synchronized(lock) {
            if (waiting > 0) return null
            reading = stop
        }
        try {
            return block(stop)
        } finally {
            synchronized(lock) { if (reading === stop) reading = null }
        }
    }

    /** A person's step: a running read is stopped, then the step runs alone. Steps keep their order. */
    suspend fun <T> step(block: suspend () -> T): T {
        val running = synchronized(lock) {
            waiting++
            reading
        }
        var counted = true
        try {
            running?.let { stop -> offMain { stop.stop() } }
            mutex.lock()
            synchronized(lock) {
                waiting--
                counted = false
            }
        } finally {
            if (counted) synchronized(lock) { waiting-- }
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}
