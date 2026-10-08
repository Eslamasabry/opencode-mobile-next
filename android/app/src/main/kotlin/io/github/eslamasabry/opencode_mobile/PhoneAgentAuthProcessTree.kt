package io.github.eslamasabry.opencode_mobile

import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit

internal data class PhoneAgentAuthProcessIdentity(val pid: Int, val parent: Int, val start: String, val state: Char)

/** Kernel identities survive a dead root; an empty current map proves nothing. */
internal class PhoneAgentAuthProcessTree(
    private val pid: (Process) -> Int?,
    private val inventory: () -> List<PhoneAgentAuthProcessIdentity>,
    private val signal: (Int, Int) -> Unit,
    private val nano: () -> Long = { System.nanoTime() },
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    private class Entry(val root: Int?, var before: Set<Pair<Int, String>>?) {
        val tokens = mutableMapOf<Int, String>()
    }
    private val owned = IdentityHashMap<Process, Entry>()

    fun track(process: Process, before: Set<Pair<Int, String>>? = null): Boolean = synchronized(owned) {
        val entry = Entry(pid(process), before)
        owned[process] = entry
        try {
            val entries = inventory()
            if (entry.before == null) entry.before = entries.map { it.pid to it.start }.toSet()
            val root = entries.firstOrNull { it.pid == entry.root }
            if (root != null) entry.tokens[root.pid] = root.start
            collect(entry.tokens, entries)
            root != null
        } catch (_: Exception) { false }
    }

    fun capture(process: Process) = synchronized(owned) {
        val tokens = checkNotNull(owned[process]).tokens
        check(tokens.isNotEmpty())
        collect(tokens, inventory())
    }

    private fun collect(tokens: MutableMap<Int, String>, entries: List<PhoneAgentAuthProcessIdentity>) {
        var frontier = entries.filter { tokens[it.pid] == it.start }.map { it.pid }.toSet()
        val visited = tokens.keys.toMutableSet()
        while (frontier.isNotEmpty()) {
            val children = entries.filter { it.parent in frontier && it.pid !in visited }
            children.forEach { tokens[it.pid] = it.start }
            frontier = children.map { it.pid }.toSet()
            visited.addAll(frontier)
        }
    }

    fun stop(process: Process, budgetNanos: Long = TimeUnit.SECONDS.toNanos(2)): Boolean = synchronized(owned) {
        val entry = owned[process] ?: return false
        if (entry.root == null || entry.before == null) return false
        try {
            val deadline = nano() + budgetNanos
            collect(entry.tokens, inventory())
            signalOwned(entry.tokens, STOP)
            collect(entry.tokens, inventory())
            signalOwned(entry.tokens, KILL)
            while (!drained(entry) && nano() < deadline) {
                collect(entry.tokens, inventory())
                signalOwned(entry.tokens, KILL)
                pause(POLL_MILLIS)
            }
            val complete = drained(entry)
            if (complete) owned.remove(process)
            complete
        } catch (_: Exception) { false }
    }

    private fun signalOwned(tokens: Map<Int, String>, signalValue: Int) {
        val targets = live(tokens, inventory()).asReversed()
        // Freeze root first; kill children first. Revalidate every individual task.
        val ordered = if (signalValue == STOP) targets.asReversed() else targets
        ordered.forEach { target ->
            if (live(tokens, inventory()).any { it.pid == target.pid && it.start == target.start })
                signal(target.pid, signalValue)
        }
    }

    private fun drained(entry: Entry): Boolean {
        val entries = inventory()
        val known = owned.values.flatMap { it.tokens.entries.map { token -> token.key to token.value } }.toSet()
        val unknown = entries.any { running(it) && (it.pid to it.start) !in checkNotNull(entry.before) &&
            (it.pid to it.start) !in known }
        return live(entry.tokens, entries).isEmpty() && !unknown
    }

    private fun live(tokens: Map<Int, String>, entries: List<PhoneAgentAuthProcessIdentity>) =
        entries.filter { tokens[it.pid] == it.start && running(it) }

    private fun running(entry: PhoneAgentAuthProcessIdentity): Boolean = entry.state != 'Z' && entry.state != 'X'

    private companion object {
        const val STOP = 19
        const val KILL = 9
        const val POLL_MILLIS = 20L
    }
}
