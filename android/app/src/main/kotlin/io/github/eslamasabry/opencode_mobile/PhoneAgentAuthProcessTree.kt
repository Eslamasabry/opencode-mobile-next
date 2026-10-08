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
    private val outsideOwners: () -> Set<Pair<Int, String>> = { emptySet() },
) {
    private class Entry(val root: Int?, var before: Set<Pair<Int, String>>?) {
        val tokens = mutableMapOf<Int, String>()
        // Once observed under an exact registered root, an outside child keeps its
        // birth identity even if the helper exits and the child is reparented.
        val outside = mutableMapOf<Int, String>()
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
            observe(entry, entries)
            root != null
        } catch (_: Exception) { false }
    }

    fun capture(process: Process) = synchronized(owned) {
        val entry = checkNotNull(owned[process])
        check(entry.tokens.isNotEmpty())
        observe(entry, inventory())
    }

    private fun collect(
        tokens: MutableMap<Int, String>,
        entries: List<PhoneAgentAuthProcessIdentity>,
        excluded: Set<Pair<Int, String>> = emptySet(),
    ) {
        var frontier = entries.filter { tokens[it.pid] == it.start && (it.pid to it.start) !in excluded }
            .map { it.pid }.toSet()
        val visited = tokens.keys.toMutableSet()
        while (frontier.isNotEmpty()) {
            val children = entries.filter { it.parent in frontier && it.pid !in visited &&
                (it.pid to it.start) !in excluded }
            children.forEach { tokens[it.pid] = it.start }
            frontier = children.map { it.pid }.toSet()
            visited.addAll(frontier)
        }
    }

    private fun privateIdentities() = owned.values.flatMap {
        it.tokens.entries.map { token -> token.key to token.value }
    }.toSet()

    private fun observe(entry: Entry, entries: List<PhoneAgentAuthProcessIdentity>) {
        collect(entry.tokens, entries)
        val capturedPrivate = privateIdentities()
        val registered = outsideOwners()
        entry.outside.entries.removeAll { (it.key to it.value) in capturedPrivate }
        entries.filter { (it.pid to it.start) in registered && (it.pid to it.start) !in capturedPrivate }
            .forEach { entry.outside[it.pid] = it.start }
        collect(entry.outside, entries, capturedPrivate)
    }

    fun stop(process: Process, budgetNanos: Long = TimeUnit.SECONDS.toNanos(2)): Boolean = synchronized(owned) {
        val entry = owned[process] ?: return false
        if (entry.root == null || entry.before == null) return false
        try {
            val deadline = nano() + budgetNanos
            observe(entry, inventory())
            signalOwned(entry.tokens, STOP)
            observe(entry, inventory())
            signalOwned(entry.tokens, KILL)
            while (!drained(entry) && nano() < deadline) {
                observe(entry, inventory())
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
        observe(entry, entries)
        val known = privateIdentities()
        val outside = entry.outside.entries.map { it.key to it.value }.toSet()
        val unknown = entries.any { running(it) && (it.pid to it.start) !in checkNotNull(entry.before) &&
            (it.pid to it.start) !in known && (it.pid to it.start) !in outside }
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
