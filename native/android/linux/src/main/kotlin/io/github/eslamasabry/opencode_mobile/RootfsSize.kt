package io.github.eslamasabry.opencode_mobile

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object RootfsWalk {
    fun size(root: Path): Long {
        var total = 0L
        val visitor =
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isSymbolicLink) total += attrs.size()
                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    FileVisitResult.CONTINUE

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                    FileVisitResult.CONTINUE

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult =
                    FileVisitResult.CONTINUE
            }
        Files.walkFileTree(root, emptySet(), Int.MAX_VALUE, visitor)
        return total
    }
}

internal class RootfsSizeMeter(
    private val walk: () -> Long,
    private val now: () -> Long,
    private val everyMs: Long,
) {
    private data class State(val generation: Int, val endedAt: Long?, val size: Long?)

    private val wanted = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val state = AtomicReference(State(0, null, null))

    fun last(): Long? = state.get().size

    fun want(shown: Boolean) = wanted.set(shown)

    fun measureIfDue(allowed: Boolean): Boolean {
        if (!wanted.get() || !allowed || !running.compareAndSet(false, true)) return false
        try {
            val seen = state.get()
            val endedAt = seen.endedAt
            val due = endedAt == null || now() - endedAt > everyMs
            if (due) {
                val size = walk()
                state.compareAndSet(seen, State(seen.generation, now(), size))
            }
            return due
        } finally {
            running.set(false)
        }
    }

    fun forget() {
        state.updateAndGet { State(it.generation + 1, null, null) }
    }
}
