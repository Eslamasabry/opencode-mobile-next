package io.github.eslamasabry.oc.engine.runtime.components

import java.io.File
import java.util.concurrent.TimeUnit

/** Runs a real shell for the script tests (desktop JVM only). */
internal data class ShellRun(val exitCode: Int, val stdout: String, val stderr: String)

internal fun runShell(
    shell: String,
    script: String,
    environment: Map<String, String> = emptyMap(),
    replaceEnvironment: Boolean = false,
): ShellRun {
    val builder = ProcessBuilder(shell, "-c", script)
    if (replaceEnvironment) builder.environment().clear()
    builder.environment().putAll(environment)
    val process = builder.start()
    process.outputStream.close()
    val out = StringBuilder()
    val err = StringBuilder()
    val readers =
        listOf(
            Thread { out.append(process.inputStream.bufferedReader().readText()) },
            Thread { err.append(process.errorStream.bufferedReader().readText()) },
        )
    readers.forEach(Thread::start)
    check(process.waitFor(120, TimeUnit.SECONDS)) { "shell did not finish" }
    readers.forEach(Thread::join)
    return ShellRun(process.exitValue(), out.toString(), err.toString())
}

/** The shell at [name] on PATH, or null (dash is not on every machine). */
internal fun shellOnPath(name: String): String? =
    (System.getenv("PATH") ?: "").split(File.pathSeparator)
        .map { File(it, name) }
        .firstOrNull { it.canExecute() }
        ?.path
