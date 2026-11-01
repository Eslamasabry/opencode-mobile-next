package io.github.eslamasabry.opencode_mobile

/**
 * Which processes belong below a runtime root.
 *
 * A program started in the background from a shell inside Ubuntu
 * (`(python3 -m http.server 8765 &)` in a terminal) loses its parent when
 * that subshell exits: the kernel moves it to init, so its `stat` says
 * parent 1 and a walk by parent no longer reaches it from the terminal's
 * proot. It is still that proot's tracee, though: proot ptraces everything
 * it runs, and the kernel names the tracer in `/proc/<pid>/status`
 * (TracerPid). Build 2400 counted such a program as an unknown writer, so
 * every Linux read (Running, Ports, Packages, Development services) failed
 * with "Could not read Linux" while it ran (docs/qa/linux-fixes-2026-10-16).
 *
 * The tracer is a kernel fact a process cannot set for itself, and only a
 * process that is already below [root] can be its tracer here, so following
 * it never adopts a stranger. Pure: the caller reads /proc.
 */
internal object NativeProcessTree {
    /**
     * Every process below [root]: its children by parent, and the processes
     * any of them traces, repeated until nothing new joins. [parents] and
     * [tracers] map a pid to its parent and to its tracer (absent or 0 when
     * none). [root] itself is not included.
     */
    fun descendants(root: Int, parents: Map<Int, Int>, tracers: Map<Int, Int>): List<Int> {
        val found = LinkedHashSet<Int>()
        val inTree = hashSetOf(root)
        var frontier = setOf(root)
        while (frontier.isNotEmpty()) {
            val next = LinkedHashSet<Int>()
            for ((pid, parent) in parents) {
                if (pid !in inTree && parent in frontier) next.add(pid)
            }
            for ((pid, tracer) in tracers) {
                if (tracer > 1 && isNewTracee(pid, root, inTree) && tracer in frontier) next.add(pid)
            }
            next.removeAll(inTree)
            found.addAll(next)
            inTree.addAll(next)
            frontier = next
        }
        return found.toList()
    }

    private fun isNewTracee(pid: Int, root: Int, inTree: Set<Int>): Boolean = pid != root && pid !in inTree

    /** The TracerPid of a `/proc/<pid>/status` text, or null when there is none. */
    fun tracerOf(status: String): Int? {
        val line = status.lineSequence().firstOrNull { it.startsWith("TracerPid:") } ?: return null
        return line.substringAfter(':').trim().toIntOrNull()?.takeIf { it > 1 }
    }
}
