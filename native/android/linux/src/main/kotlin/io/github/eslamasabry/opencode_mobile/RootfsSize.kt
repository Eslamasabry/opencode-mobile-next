package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal object RootfsWalk {
    fun size(root: Path): Long = sizeOf(root.toFile())

    private fun sizeOf(file: File): Long {
        val attrs =
            try {
                Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (_: IOException) {
                return 0
            }
        if (attrs.isSymbolicLink) return 0
        if (!file.isDirectory) return attrs.size()
        return file.listFiles()?.sumOf { sizeOf(it) } ?: 0
    }
}

internal class RootfsSizeMeter(
    private val walk: () -> Long,
    private val now: () -> Long,
    private val everyMs: Long,
) {
    @Volatile private var measured: Pair<Long, Long>? = null

    fun last(): Long? = measured?.second?.takeIf { it >= 0 }

    fun want(shown: Boolean) = Unit

    fun measureIfDue(allowed: Boolean): Boolean {
        val last = measured
        val due = last == null || now() - last.first > everyMs
        if (!due || !(last == null || allowed)) return false
        measured = now() to (last?.second ?: -1L)
        measured = now() to walk()
        return true
    }

    fun forget() {
        measured = null
    }
}
