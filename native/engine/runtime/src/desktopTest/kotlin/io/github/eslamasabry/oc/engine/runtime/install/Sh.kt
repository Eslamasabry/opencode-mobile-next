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
    fun dartRaw(path: String, name: String): String = dartRawIn(File(repo, path).readText(), path, name)

    /**
     * The released Flutter app (tag v2.1.4+62), the source of truth for behaviour. The working tree's lib/ is not that
     * release, so a "byte for byte" check reads the release's files.
     */
    const val RELEASED_FLUTTER: String = "2dec68a250fec08909593fd3b1a8125de908f0c2"

    /**
     * [path] as the released Flutter app has it: the verbatim twin in `resources/flutter-2.1.4` (CI checks out one
     * commit, without the release in its history). When git does have the release, the twin must equal it.
     */
    fun releasedText(path: String): String {
        val twin =
            checkNotNull(Sh::class.java.getResource("/flutter-2.1.4/$path.txt")) { "missing released twin $path" }
                .readText()
        val shown =
            run(
                listOf("git", "-C", repo.path, "show", "$RELEASED_FLUTTER:$path"),
                mapOf("PATH" to "/usr/local/bin:/usr/bin:/bin", "HOME" to System.getProperty("java.io.tmpdir")),
            )
        if (shown.exitCode == 0) {
            check(shown.stdout == twin) { "the released twin of $path differs from $RELEASED_FLUTTER" }
        }
        return twin
    }

    /** A Dart raw string constant's text in the released Flutter app (see [releasedText]). */
    fun releasedDartRaw(path: String, name: String): String = dartRawIn(releasedText(path), path, name)

    private fun dartRawIn(text: String, path: String, name: String): String {
        val match =
            Regex(
                "(?:const )?" + Regex.escape(name) + "\\s*=\\s*(?:\\n\\s*)?(?:\\w+\\s*\\+\\s*)?r'''(.*?)'''",
                RegexOption.DOT_MATCHES_ALL,
            ).find(text)
        return checkNotNull(match) { "no $name in $path" }.groupValues[1].replaceFirst(Regex("^[ \\t]*\\n"), "")
    }
}
