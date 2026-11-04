package io.github.eslamasabry.oc.engine.runtime.install

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Real processes for the script tests (JVM only): a run's exit code and both streams. */
data class ShRun(val exitCode: Int, val stdout: String, val stderr: String) {
    val lastLine: String get() = (stdout + stderr).trimEnd().split('\n').last()
}

object Sh {
    /** Runs [command] with exactly [env] (no inherited environment), in [dir]. */
    fun run(command: List<String>, env: Map<String, String>, dir: File? = null, timeoutSeconds: Long = 120): ShRun {
        val builder = ProcessBuilder(command)
        builder.environment().clear()
        builder.environment().putAll(env)
        if (dir != null) builder.directory(dir)
        val out = Files.createTempFile("sh-out", ".txt").toFile()
        val err = Files.createTempFile("sh-err", ".txt").toFile()
        builder.redirectOutput(out)
        builder.redirectError(err)
        builder.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
        val process = builder.start()
        check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "timed out: $command" }
        val result = ShRun(process.exitValue(), out.readText(), err.readText())
        out.delete()
        err.delete()
        return result
    }

    fun exec(vararg command: String): ShRun = run(command.toList(), mapOf("PATH" to "/usr/local/bin:/usr/bin:/bin"))

    fun has(tool: String): Boolean = exec("sh", "-c", "command -v $tool").exitCode == 0

    fun chmod(mode: String, file: File) {
        check(exec("chmod", mode, file.path).exitCode == 0)
    }

    fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    /** The repository root (Gradle runs desktopTest in native/engine/runtime). */
    val repo: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "scripts/host/ubuntu-opencode.sh").isFile) dir = dir.parentFile
        checkNotNull(dir) { "repository root not found" }
    }

    /** A Dart raw string constant's text, as Dart reads it (a first whitespace-only line is dropped). */
    fun dartRaw(path: String, name: String): String {
        val text = File(repo, path).readText()
        val match =
            Regex(
                "(?:const )?" + Regex.escape(name) + "\\s*=\\s*(?:\\n\\s*)?(?:\\w+\\s*\\+\\s*)?r'''(.*?)'''",
                RegexOption.DOT_MATCHES_ALL,
            ).find(text)
        return checkNotNull(match) { "no $name in $path" }.groupValues[1].replaceFirst(Regex("^[ \\t]*\\n"), "")
    }
}
