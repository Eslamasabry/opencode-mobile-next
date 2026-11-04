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

    fun temp(prefix: String): File = Files.createTempDirectory(prefix).toFile().canonicalFile.also { temps += it }

    fun cleanUp() {
        temps.forEach { it.deleteRecursively() }
        temps.clear()
    }

    /**
     * Hermetic run (coordinator rule after the 2026-11-03 /etc wipe): a clean environment whose HOME, PREFIX and
     * TMPDIR live in a fresh temp root, the working directory in that root, and a PATH-first stub directory. File
     * changing commands (rm, rmdir, mv, cp, ln, chmod, chown, truncate) refuse any path outside the root (and the
     * manager's own `/tmp/opencode-mobile-npm.*` cache); package and container tools (apt, apt-get, dpkg, pkg,
     * proot-distro, pkill, killall, termux-*) are inert unless the test puts its own fake first on PATH.
     */
    fun run(
        command: List<String>,
        env: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 120,
        stdin: File? = null,
    ): Result {
        val root = temp("oc-hermetic-")
        val stubs = stubDirectory(root)
        val merged = linkedMapOf(
            "HOME" to File(root, "home").apply { mkdirs() }.path,
            "PREFIX" to File(root, "prefix").apply { mkdirs() }.path,
            "TMPDIR" to File(root, "tmp").apply { mkdirs() }.path,
            "LANG" to "C.UTF-8",
        )
        merged.putAll(env)
        // The test's own PATH entries (fakes) come first, then the guards, then the system.
        val testPath = env["PATH"]?.split(':')?.filter { it.isNotEmpty() && isUnder(File(it), temps) } ?: emptyList()
        merged["PATH"] = (testPath + stubs.path + listOf("/usr/bin", "/bin")).joinToString(":")
        merged["OC_TEST_ROOTS"] = temps.joinToString(":") { it.path }
        for (key in listOf("HOME", "PREFIX", "TMPDIR")) {
            val value = merged.getValue(key)
            check(value.isNotBlank() && value != "/" && isUnder(File(value), temps)) { "$key must be inside a test temp dir: $value" }
        }
        val builder = ProcessBuilder(command).directory(root)
        builder.environment().clear()
        builder.environment().putAll(merged)
        if (stdin != null) builder.redirectInput(stdin)
        val out = File(root, ".stdout")
        val err = File(root, ".stderr")
        builder.redirectOutput(out).redirectError(err)
        val process = builder.start()
        check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "timed out: $command" }
        return Result(process.exitValue(), out.readText(), err.readText())
    }

    private fun isUnder(file: File, roots: List<File>): Boolean {
        val path = file.absoluteFile.normalize().path
        return roots.any { path == it.path || path.startsWith(it.path + "/") }
    }

    private const val GUARD =
        "#!/bin/bash\n" +
            "real=\"/usr/bin/\$(basename \"\$0\")\"; [ -x \"\$real\" ] || real=\"/bin/\$(basename \"\$0\")\"\n" +
            "IFS=: read -r -a roots <<< \"\$OC_TEST_ROOTS\"\n" +
            "for arg in \"\$@\"; do\n" +
            "  case \"\$arg\" in -*|'') continue ;; esac\n" +
            "  case \"\$(basename \"\$0\")\" in chmod|chown) case \"\$arg\" in [0-7]*|*[+=]*) continue ;; esac ;; esac\n" +
            "  path=\$(realpath -m -- \"\$arg\")\n" +
            "  ok=0\n" +
            "  for root in \"\${roots[@]}\"; do case \"\$path\" in \"\$root\"|\"\$root\"/*) ok=1 ;; esac; done\n" +
            "  case \"\$path\" in /tmp/opencode-mobile-npm.*) ok=1 ;; esac\n" +
            "  [ \"\$ok\" = 1 ] || { echo \"hermetic guard: \$(basename \"\$0\") refused \$path\" >&2; exit 97; }\n" +
            "done\n" +
            "exec \"\$real\" \"\$@\"\n"

    private fun stubDirectory(root: File): File {
        val dir = File(root, ".stubs").apply { mkdirs() }
        for (name in listOf("rm", "rmdir", "mv", "cp", "ln", "chmod", "chown", "truncate")) {
            File(dir, name).apply { writeText(GUARD); setExecutable(true) }
        }
        for (name in listOf(
            "apt", "apt-get", "dpkg", "pkg", "proot-distro", "pkill", "killall", "termux-wake-lock",
            "termux-wake-unlock", "termux-reload-settings", "reboot", "shutdown",
        )) {
            File(dir, name).apply { writeText("#!/bin/sh\necho \"hermetic stub: $name\" >&2\nexit 0\n"); setExecutable(true) }
        }
        return dir
    }

    fun bash(script: String, env: Map<String, String> = emptyMap()): Result = run(listOf("bash", "-c", script), env)

    fun bashFile(file: File, vararg args: String, env: Map<String, String> = emptyMap()): Result =
        run(listOf("bash", file.path) + args, env)

    fun syntax(shell: String, text: String): Result {
        val file = File(temp("oc-syntax-"), "script.sh").apply { writeText(text) }
        return run(listOf(shell, "-n", file.path))
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
