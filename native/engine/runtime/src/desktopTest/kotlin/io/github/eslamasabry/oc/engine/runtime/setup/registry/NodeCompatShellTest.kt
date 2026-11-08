package io.github.eslamasabry.oc.engine.runtime.setup.registry

import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.engine.runtime.bridge.BuiltinScripts
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Node safe-mode shell (#117/#119) against a fake node that crashes (SIGSEGV) unless it is started a certain way,
 * as the Flutter fix/117-adapt test/node_compat_test.dart does. JVM only: commonTest cannot start processes. Each test
 * passes vacuously on a machine without `sh` (never on the Linux CI and dev machines).
 */
class NodeCompatShellTest {
    private val root: File = Files.createTempDirectory("oc-node-compat").toFile()
    private val node = File(root, "opt/node/bin/node")
    private val link = File(root, "usr/local/bin/node")
    private val state = File(root, "etc/oc-setup/node-compat")
    private val env = File(root, "etc/oc-setup/node-compat.env")

    private data class Run(val exitCode: Int, val output: String)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun hasSh(): Boolean =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it, "sh").canExecute() }

    /**
     * A node that crashes unless [need] holds: `armcap` wants OPENSSL_armcap=0; `single` wants UV_THREADPOOL_SIZE=1
     * and both command-line flags; `both` wants all of it; `never` always crashes. It answers --version.
     */
    private fun fakeNode(need: String) {
        node.parentFile.mkdirs()
        File(root, "opt/node/lib/node_modules/npm/bin").mkdirs()
        File(root, "opt/node/lib/node_modules/npm/bin/npm-cli.js").writeText("")
        node.writeText(
            """
            #!/bin/sh
            armcap=0; single=0
            [ "${'$'}{OPENSSL_armcap:-}" = 0 ] && armcap=1
            case " ${'$'}* " in *" --single-threaded --v8-pool-size=1 "*)
              [ "${'$'}{UV_THREADPOOL_SIZE:-}" = 1 ] && single=1 ;; esac
            case '$need' in
              none) ;;
              armcap) [ ${'$'}armcap = 1 ] || kill -SEGV ${'$'}${'$'} ;;
              single) [ ${'$'}single = 1 ] || kill -SEGV ${'$'}${'$'} ;;
              both) { [ ${'$'}armcap = 1 ] && [ ${'$'}single = 1 ]; } || kill -SEGV ${'$'}${'$'} ;;
              never) kill -SEGV ${'$'}${'$'} ;;
            esac
            for a in "${'$'}@"; do [ "${'$'}a" = --version ] && echo v24.22.2; done
            exit 0
            """.trimIndent() + "\n"
        )
        node.setExecutable(true)
    }

    private fun sh(script: String, compat: Boolean = false, path: String? = null): Run {
        val builder = ProcessBuilder("sh", "-c", script).redirectErrorStream(true)
        if (compat) builder.environment()["GLIBC_TUNABLES"] = "glibc.pthread.rseq=0"
        else builder.environment().remove("GLIBC_TUNABLES")
        if (path != null) builder.environment()["PATH"] = path + File.pathSeparator + System.getenv("PATH")
        val process = builder.start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(120, TimeUnit.SECONDS)) { "timed out" }
        return Run(process.exitValue(), output)
    }

    private fun startCheck(compat: Boolean = false): Run =
        sh(
            "set -eu\n" +
                "oc_stage() { echo \"stage: ${'$'}*\"; }\n" +
                "oc_compat_dir='${state.parent}' oc_node_bin='${node.path}' oc_node_link='${link.path}'\n" +
                NodeCompatScripts.FUNCTIONS +
                NodeCompatScripts.START_CHECK +
                "echo started\n",
            compat,
        )

    private fun viaLink(): Run = sh("'${link.path}' --version")

    @Test
    fun `B-LINUX-203 a phone that runs Node as it is keeps a plain link and no saved mode`() {
        if (!hasSh()) return
        fakeNode("none")
        val run = startCheck()
        assertEquals(0, run.exitCode, run.output)
        assertTrue(run.output.contains("[oc] probe none-version 0 # ${node.path} --version"), run.output)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertFalse(state.exists())
        assertFalse(env.exists())
    }

    @Test
    fun `B-LINUX-203 OpenSSL without CPU extensions is kept when it is what passes`() {
        if (!hasSh()) return
        fakeNode("armcap")
        val run = startCheck()
        assertEquals(0, run.exitCode, run.output)
        assertTrue(run.output.contains("[oc] probe none-version 139 #"), run.output)
        assertTrue(run.output.contains("[oc] probe armcap-start 0 # OPENSSL_armcap=0 ${node.path} -e <start check>"))
        assertEquals("armcap\n", state.readText())
        assertEquals("OPENSSL_armcap=0\n", env.readText())
        assertFalse(Files.isSymbolicLink(link.toPath()))
        val wrapped = viaLink()
        assertEquals(0 to "v24.22.2\n", wrapped.exitCode to wrapped.output)
    }

    @Test
    fun `B-LINUX-203 single-threaded uses command-line flags, never NODE_OPTIONS`() {
        if (!hasSh()) return
        fakeNode("single")
        val run = startCheck()
        assertEquals(0, run.exitCode, run.output)
        assertEquals("single\n", state.readText())
        assertEquals("UV_THREADPOOL_SIZE=1\n", env.readText())
        val wrapper = link.readText()
        assertTrue(wrapper.contains("--single-threaded --v8-pool-size=1 \"\$@\""), wrapper)
        assertFalse(wrapper.contains("NODE_OPTIONS"))
        assertEquals(0, viaLink().exitCode)
    }

    @Test
    fun `B-LINUX-203 both safe modes together are the last try`() {
        if (!hasSh()) return
        fakeNode("both")
        val run = startCheck()
        assertEquals(0, run.exitCode, run.output)
        assertEquals("both\n", state.readText())
        assertEquals("OPENSSL_armcap=0\nUV_THREADPOOL_SIZE=1\n", env.readText())
        assertEquals("v24.22.2\n", viaLink().output)
    }

    @Test
    fun `B-LINUX-203 nothing passing exits 139 so the runner retries in compatibility mode`() {
        if (!hasSh()) return
        fakeNode("never")
        val run = startCheck()
        assertEquals(139, run.exitCode, run.output)
        assertFalse(run.output.contains(NodeCompatScripts.FAILURE_LINE))
        // Probe crashes are counted, not reported by the shell as a crash of the step itself.
        assertFalse(run.output.contains("Segmentation fault"), run.output)
        assertFalse(state.exists())
    }

    @Test
    fun `B-LINUX-203 nothing passing in compatibility mode prints the give-up line`() {
        if (!hasSh()) return
        fakeNode("never")
        val run = startCheck(compat = true)
        assertEquals(1, run.exitCode, run.output)
        assertTrue(run.output.trimEnd().endsWith(NodeCompatScripts.FAILURE_LINE), run.output)
    }

    @Test
    fun `B-LINUX-203 a mode found in compatibility mode tells the server proot to use it too`() {
        if (!hasSh()) return
        fakeNode("none")
        assertEquals(0, startCheck(compat = true).exitCode)
        assertEquals("none\nproot-compat\n", state.readText())
        assertEquals("", env.readText())
    }

    @Test
    fun `B-LINUX-203 the early check asks for compatibility mode when threads crash, once`() {
        if (!hasSh()) return
        val bin = File(root, "bin").apply { mkdirs() }
        File(bin, "sort").apply {
            writeText("#!/bin/sh\nkill -SEGV \$\$\n")
            setExecutable(true)
        }
        val script = "set -eu\n" + NodeCompatScripts.FUNCTIONS + NodeCompatScripts.EARLY_CHECK + "echo continued\n"
        val crashing = sh(script, path = bin.path)
        assertEquals(139, crashing.exitCode, crashing.output)
        assertTrue(crashing.output.contains("[oc] probe early-sort 139 # sort --parallel=4"), crashing.output)
        val again = sh(script, compat = true, path = bin.path)
        assertEquals(0, again.exitCode, again.output)
        assertTrue(again.output.endsWith("continued\n"))
        val healthy = sh(script)
        assertEquals(0, healthy.exitCode, healthy.output)
    }

    @Test
    fun `B-LINUX-203 the server exports only the saved safe-mode variables`() {
        if (!hasSh()) return
        env.parentFile.mkdirs()
        env.writeText("OPENSSL_armcap=0\nUV_THREADPOOL_SIZE=1\nLD_PRELOAD=/x.so\n")
        val exports = NodeCompatScripts.SERVER_ENV.replace("/etc/oc-setup/node-compat.env", env.path)
        val run = sh("set -eu\n$exports" + "echo \"\${OPENSSL_armcap:-}|\${UV_THREADPOOL_SIZE:-}|\${LD_PRELOAD:-}\"\n")
        assertEquals(0 to "0|1|\n", run.exitCode to run.output)
        env.delete()
        assertEquals(0, sh("set -eu\n$exports").exitCode)
    }

    @Test
    fun `B-LINUX-024 the Node install and the server script parse as shell and export before exec`() {
        if (!hasSh()) return
        for (script in listOf(PhoneSetupScripts.NODE_INSTALL, BuiltinScripts.serverScript().getOrNull()!!)) {
            val process = ProcessBuilder("sh", "-n", "-c", script).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
        }
        val server = BuiltinScripts.serverScript().getOrNull()!!
        assertTrue(server.indexOf(NodeCompatScripts.SERVER_ENV) in 0 until server.indexOf("exec opencode2 serve"))
        val install = PhoneSetupScripts.NODE_INSTALL
        assertTrue(install.indexOf(NodeCompatScripts.EARLY_CHECK) < install.indexOf("oc_stage 'Downloading Node.js"))
        assertTrue(install.indexOf(NodeCompatScripts.START_CHECK) < install.indexOf("npm config set prefix"))
    }
}
