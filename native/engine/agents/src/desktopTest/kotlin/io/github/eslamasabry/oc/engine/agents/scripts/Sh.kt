package io.github.eslamasabry.oc.engine.agents.scripts

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Test helper: runs POSIX sh scripts the way the Flutter tests' `Process.run('sh', …)` does. */
internal data class ShResult(val exitCode: Int, val stdout: String, val stderr: String)

/** Paths the Flutter CI image has in `/usr/bin`; this container keeps python3 under `/usr/local/bin`. */
internal const val TEST_PATH = "/usr/local/bin:/usr/bin:/bin"

internal fun sh(
    args: List<String>,
    environment: Map<String, String>? = null,
    workingDirectory: File? = null,
    stdin: String? = null,
    timeoutSeconds: Long = 60,
    clearEnvironment: Boolean = false,
): ShResult = run(listOf("sh") + args, environment, workingDirectory, stdin, timeoutSeconds, clearEnvironment)

internal fun run(
    command: List<String>,
    environment: Map<String, String>? = null,
    workingDirectory: File? = null,
    stdin: String? = null,
    timeoutSeconds: Long = 60,
    clearEnvironment: Boolean = false,
): ShResult {
    val builder = ProcessBuilder(command)
    if (environment != null) {
        val env = builder.environment()
        if (clearEnvironment) {
            env.clear()
            // Dart's Process.run inherits the parent environment and overlays the map; keep PATH sane either way.
            env["PATH"] = TEST_PATH
        }
        env.putAll(environment)
    }
    workingDirectory?.let { builder.directory(it) }
    val out = Files.createTempFile("oc-sh-out", ".txt").toFile()
    val err = Files.createTempFile("oc-sh-err", ".txt").toFile()
    builder.redirectOutput(out).redirectError(err)
    val process = builder.start()
    process.outputStream.use { stream -> stdin?.let { stream.write(it.toByteArray()) } }
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        error("Timed out: ${command.first()}")
    }
    val result = ShResult(process.exitValue(), out.readText(), err.readText())
    out.delete()
    err.delete()
    return result
}

internal fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()
