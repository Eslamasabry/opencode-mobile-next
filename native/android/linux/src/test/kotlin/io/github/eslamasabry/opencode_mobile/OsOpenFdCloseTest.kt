package io.github.eslamasabry.opencode_mobile

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-LINUX-617: Android libcore's FileInputStream(FileDescriptor) and FileOutputStream(FileDescriptor)
 * set isFdOwner = false, so wrapping an Os.open() descriptor directly in FileInputStream/FileOutputStream
 * without guaranteed Os.close(fd) (or OsFileDescriptors) permanently leaks a kernel FD per call.
 */
class OsOpenFdCloseTest {
    @Test
    fun `B-LINUX-617 every Os open wrapped in FileInputStream or FileOutputStream closes its FileDescriptor`() {
        val root = nativeRoot()
        val leaks =
            listOf(File(root, "android"), File(root, "androidApp"))
                .flatMap { scanDir(it, root) }

        assertTrue(
            "Found Os.open() wrapped in FileInputStream/FileOutputStream without closing the FD:\n" +
                leaks.joinToString("\n"),
            leaks.isEmpty(),
        )
    }

    private fun nativeRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    private fun scanDir(dir: File, nativeRoot: File): List<String> =
        dir.walkTopDown()
            .filter(::isScannedSource)
            .flatMap { file -> scanFile(file, nativeRoot) }
            .toList()

    private fun isScannedSource(file: File): Boolean =
        file.isFile &&
            file.extension == "kt" &&
            !file.path.contains("/build/") &&
            (file.path.contains("/src/main/") || file.path.contains("/src/androidTest/"))

    private fun scanFile(file: File, nativeRoot: File): List<String> {
        val lines = file.readLines()
        val rel = file.relativeTo(nativeRoot).path
        return lines.indices
            .filter { index -> isLeakingOpen(lines, index) }
            .map { index -> "$rel:${index + 1}" }
    }

    private fun isLeakingOpen(lines: List<String>, index: Int): Boolean {
        val trimmed = lines[index].trimStart()
        if (!trimmed.contains("Os.open(") || trimmed.startsWith("//") || trimmed.startsWith("*")) {
            return false
        }
        val windowStart = maxOf(0, index - 3)
        val maxEnd = minOf(lines.lastIndex, index + 25)
        val nextOpen = ((index + 1)..maxEnd).firstOrNull { lines[it].contains("Os.open(") }
        val windowEnd = if (nextOpen != null) nextOpen - 1 else maxEnd
        val window = lines.subList(windowStart, windowEnd + 1).joinToString("\n")
        val wrapsStream = window.contains("FileInputStream(") || window.contains("FileOutputStream(")
        val closesFd =
            window.contains("Os.close(") ||
                window.contains("OsFileDescriptors.") ||
                window.contains("closeQuietly(")
        return wrapsStream && !closesFd
    }
}
