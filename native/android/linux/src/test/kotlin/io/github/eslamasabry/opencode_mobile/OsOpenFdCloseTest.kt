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
        val nativeRoot =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        val scanRoots = listOf(File(nativeRoot, "android"), File(nativeRoot, "androidApp"))
        val leaks = mutableListOf<String>()

        for (root in scanRoots) {
            root.walkTopDown()
                .filter {
                    it.isFile &&
                        it.extension == "kt" &&
                        !it.path.contains("/build/") &&
                        (it.path.contains("/src/main/") || it.path.contains("/src/androidTest/"))
                }
                .forEach { file ->
                    val lines = file.readLines()
                    for (index in lines.indices) {
                        val line = lines[index]
                        if (!line.contains("Os.open(")) continue
                        if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) continue
                        val windowStart = maxOf(0, index - 3)
                        val maxEnd = minOf(lines.lastIndex, index + 25)
                        val nextOpen =
                            ((index + 1)..maxEnd).firstOrNull { lines[it].contains("Os.open(") }
                        val windowEnd = if (nextOpen != null) nextOpen - 1 else maxEnd
                        val window = lines.subList(windowStart, windowEnd + 1).joinToString("\n")
                        val wrapsStream =
                            window.contains("FileInputStream(") || window.contains("FileOutputStream(")
                        if (!wrapsStream) continue
                        val closesFd =
                            window.contains("Os.close(") ||
                                window.contains("OsFileDescriptors.") ||
                                window.contains("closeQuietly(")
                        if (!closesFd) {
                            val rel = file.relativeTo(nativeRoot).path
                            leaks += "$rel:${index + 1}"
                        }
                    }
                }
        }

        assertTrue(
            "Found Os.open() wrapped in FileInputStream/FileOutputStream without closing the FD:\n" +
                leaks.joinToString("\n"),
            leaks.isEmpty(),
        )
    }
}
