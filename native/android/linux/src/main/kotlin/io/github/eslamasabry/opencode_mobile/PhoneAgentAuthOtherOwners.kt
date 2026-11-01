package io.github.eslamasabry.opencode_mobile

import java.util.IdentityHashMap

/** Only native-host launches outside private auth may be excluded from its census. */
internal class PhoneAgentAuthOtherOwners(private val identity: (Process) -> Pair<Int, String>?) {
    private val roots = IdentityHashMap<Process, Pair<Int, String>>()

    fun register(process: Process, privateOutput: Boolean) = synchronized(roots) {
        // Capture at launch, never retrospectively certify a previously unknown PID.
        roots.remove(process)
        if (!privateOutput) read(process)?.let { roots[process] = it }
    }

    fun snapshot(): Set<Pair<Int, String>> = synchronized(roots) {
        val iterator = roots.entries.iterator()
        val result = mutableSetOf<Pair<Int, String>>()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (read(entry.key) == entry.value) result.add(entry.value) else iterator.remove()
        }
        result
    }

    private fun read(process: Process): Pair<Int, String>? = try {
        if (!process.isAlive) null else identity(process)?.takeIf {
            it.first > 0 && (it.second.toLongOrNull() ?: 0) > 0
        }
    } catch (_: Exception) { null }
}
