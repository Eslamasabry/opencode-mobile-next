package io.github.eslamasabry.oc.quality.sec

import java.io.File

/**
 * Owner decision 2026-11-07: the native app does not use the Termux app. Built-in Linux (bundled proot + Ubuntu) is
 * the only engine on the phone; a computer is the other way. This gate keeps the Termux-app bridge from coming back:
 *
 * - no Gradle module whose path names Termux (a Termux host, bridge or import module);
 * - no `com.termux.permission` and no `RUN_COMMAND` (the Termux run-command bridge) in any source, manifest or build
 *   file;
 * - no string resource that mentions Termux, except the one notice that tells a Flutter Termux user it is gone.
 *
 * The terminal emulator library (`:android:terminal`, Java package `com.termux.terminal`, Apache-2.0) is not the
 * Termux app and stays: its module path does not name Termux and it carries neither the permission nor the bridge.
 */
object NoTermuxApp {
    data class Finding(val id: String, val where: String)

    /** The only string resources allowed to mention Termux: the retirement notice (androidApp, English and Arabic). */
    val NOTICE_STRINGS: Set<String> = setOf("andr_termuxRetiredNotice")

    private val SKIP_DIRS = setOf("build", ".gradle", ".kotlin", "node_modules", ".git")

    /** This gate's own files name what they forbid. */
    private val SELF = setOf("NoTermuxApp.kt", "NoTermuxAppTest.kt")

    private val BRIDGE = Regex("com\\.termux\\.permission|RUN_COMMAND")

    private fun walk(root: File): Sequence<File> =
        root.walkTopDown().onEnter { it == root || it.name !in SKIP_DIRS }.filter { it.isFile }

    /** Module paths (`:a:b`) of every directory holding a `build.gradle.kts`, outside build-logic. */
    fun modules(root: File): List<String> =
        walk(root)
            .filter { it.name == "build.gradle.kts" && it.parentFile != root }
            .map { it.parentFile.relativeTo(root).invariantSeparatorsPath }
            .filterNot { it.startsWith("build-logic") }
            .map { ":" + it.replace('/', ':') }
            .sorted()
            .toList()

    fun check(root: File): List<Finding> = buildList {
        modules(root).filter { it.contains("termux", ignoreCase = true) }.forEach { add(Finding("TERMUX-MODULE", it)) }
        walk(root).filter { it.name !in SELF && it.extension in SOURCES }.forEach { addAll(file(root, it)) }
    }

    private val SOURCES = setOf("kt", "kts", "java", "xml")
    private val PROJECT = Regex("project\\(\"[^\"]*termux", RegexOption.IGNORE_CASE)

    private fun file(root: File, file: File): List<Finding> = buildList {
        val rel = file.relativeTo(root).invariantSeparatorsPath
        val text = file.readText()
        text.lineSequence().forEachIndexed { i, line ->
            if (BRIDGE.containsMatchIn(line)) add(Finding("TERMUX-BRIDGE", "$rel:${i + 1}"))
            if (file.extension == "kts" && PROJECT.containsMatchIn(line)) add(Finding("TERMUX-MODULE", "$rel:${i + 1}"))
        }
        if (isStringResource(rel)) addAll(strings(rel, text))
    }

    /** Android `res/values*` and Compose `composeResources/values*` string files. */
    private fun isStringResource(rel: String): Boolean =
        rel.endsWith(".xml") && Regex("/(res|composeResources)/values[^/]*/[^/]+\\.xml$").containsMatchIn(rel)

    private fun strings(rel: String, text: String): List<Finding> =
        Regex("<(string|plurals|string-array)\\s+name=\"([^\"]+)\"[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .filter { it.groupValues[3].contains("termux", ignoreCase = true) && it.groupValues[2] !in NOTICE_STRINGS }
            .map { Finding("TERMUX-STRING", "$rel: ${it.groupValues[2]}") }
            .toList()
}
