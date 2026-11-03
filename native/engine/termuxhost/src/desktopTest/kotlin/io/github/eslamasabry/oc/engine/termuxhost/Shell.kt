package io.github.eslamasabry.oc.engine.termuxhost

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Runs the rendered scripts with the real bash/sh/dash, as the Flutter tests do with Process.runSync. */
internal object Shell {
    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        override fun toString(): String = "exit=$exitCode\nstdout:\n$stdout\nstderr:\n$stderr"
    }

    private val temps = mutableListOf<File>()

    fun temp(prefix: String): File = Files.createTempDirectory(prefix).toFile().also { temps += it }

    fun cleanUp() {
        temps.forEach { it.deleteRecursively() }
        temps.clear()
    }

    fun run(
        command: List<String>,
        env: Map<String, String> = emptyMap(),
        inheritEnv: Boolean = true,
        timeoutSeconds: Long = 120,
        stdin: File? = null,
    ): Result {
        val builder = ProcessBuilder(command)
        if (!inheritEnv) builder.environment().clear()
        builder.environment().putAll(env)
        if (stdin != null) builder.redirectInput(stdin)
        val out = File.createTempFile("oc-out", ".txt")
        val err = File.createTempFile("oc-err", ".txt")
        builder.redirectOutput(out).redirectError(err)
        val process = builder.start()
        check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "timed out: $command" }
        return Result(process.exitValue(), out.readText(), err.readText()).also {
            out.delete()
            err.delete()
        }
    }

    fun bash(script: String, env: Map<String, String> = emptyMap()): Result = run(listOf("bash", "-c", script), env)

    fun bashFile(file: File, vararg args: String, env: Map<String, String> = emptyMap()): Result =
        run(listOf("bash", file.path) + args, env)

    fun syntax(shell: String, text: String): Result {
        val file = File.createTempFile("oc-syntax", ".sh")
        file.writeText(text)
        return run(listOf(shell, "-n", file.path)).also { file.delete() }
    }

    fun resource(name: String): String =
        checkNotNull(Shell::class.java.getResource("/termux/$name")) { "missing resource $name" }.readText()

    /** Fills `{{name}}` placeholders of an extracted Flutter fixture. */
    fun fill(template: String, vararg values: Pair<String, String>): String =
        values.fold(template) { text, (key, value) -> text.replace("{{$key}}", value) }

    /** Everything before the manager's verb dispatch: its functions, without running a verb. */
    fun managerFunctions(): String {
        val manager = TermuxScripts.managerScript
        return manager.substring(0, manager.indexOf("\ncase \"\${1:-status}\" in"))
    }

    /** test/termux_scripts_test.dart `_runManagerClockProbe`. */
    fun managerProbe(commands: String): Result {
        val directory = temp("oc-manager-clock-")
        val home = File(directory, "home").apply { mkdirs() }
        val probe = File(directory, "probe.sh").apply { writeText("${managerFunctions()}\n$commands\n") }
        return bashFile(probe, env = mapOf("HOME" to home.path))
    }

    /** The Flutter checkout this port follows (the repository root above native/). */
    val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "lib/termux/bridge.dart").isFile) dir = dir.parentFile
        checkNotNull(dir) { "repository root not found" }
    }
}
