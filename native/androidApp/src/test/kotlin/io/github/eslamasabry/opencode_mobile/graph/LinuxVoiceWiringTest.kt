package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** P12-FIX-LINUX-VOICE: rootfs discovery, the phone-server scripts and the status/failure mapping of LinuxVoiceWiring. */
class LinuxVoiceWiringTest {
    private fun filesDir(): File = Files.createTempDirectory("oc-files").toFile().also { it.deleteOnExit() }

    /** The layout the Flutter app's setup leaves: files/linux/ubuntu (rootfs), files/linux/ubuntu.ready, /root/projects. */
    private fun flutterInstall(files: File): File {
        val rootfs = File(files, "linux/ubuntu").apply { mkdirs() }
        File(rootfs, "root/projects/my-app").mkdirs()
        File(files, "linux/ubuntu.ready").writeText("")
        return rootfs
    }

    @Test
    fun `B-MIGR-008 the Flutter-installed rootfs and ready marker and projects are found in place`() {
        val files = filesDir()
        flutterInstall(files)
        assertEquals(LinuxPresence.Ready, BuiltinDiscovery.presence(files))
        assertEquals(File(files, "linux/ubuntu"), BuiltinDiscovery.rootfs(files))
        assertTrue(File(BuiltinDiscovery.projects(files), "my-app").isDirectory)
    }

    @Test
    fun `B-MIGR-008 no install or a rootfs without its ready marker offers setup instead`() {
        val empty = filesDir()
        assertEquals(LinuxPresence.NotInstalled, BuiltinDiscovery.presence(empty))
        val half = filesDir()
        File(half, "linux/ubuntu").mkdirs() // an interrupted setup: the marker is written last
        assertEquals(LinuxPresence.NotInstalled, BuiltinDiscovery.presence(half))
        val markerOnly = filesDir()
        File(markerOnly, "linux").mkdirs()
        File(markerOnly, "linux/ubuntu.ready").writeText("")
        assertEquals(LinuxPresence.NotInstalled, BuiltinDiscovery.presence(markerOnly))
    }

    @Test
    fun `B-LINUX-008 setup json running is a running setup and anything else is not`() {
        assertTrue(BuiltinDiscovery.setupRunning("""{"jobId":"j","state": "running","percent":12}"""))
        assertFalse(BuiltinDiscovery.setupRunning("""{"jobId":"j","state":"done"}"""))
        assertFalse(BuiltinDiscovery.setupRunning(null))
    }

    @Test
    fun `B-LINUX-023 the in-app server has its own port and binds loopback and reads the password from the root-only file`() {
        val script = PhoneServerScripts.server()
        assertEquals(4097, PhoneServerScripts.PORT)
        assertTrue(script.endsWith("exec opencode2 serve --hostname 127.0.0.1 --port 4097\n"))
        assertTrue("cd /root/projects\n" in script)
        assertTrue("[ -s /root/.oc-builtin/server.password ] ||" in script)
        assertTrue("export OPENCODE_SERVER_USERNAME=opencode\n" in script)
        assertTrue("export XDG_DATA_HOME=/root/.oc-opencode2/data\n" in script)
        assertTrue(") || exit 74\n" in script, "the OpenCode 1 history copy must block a start that would lose chats")
    }

    @Test
    fun `B-LINUX-037 writing the password keeps it off the command line and quotes it safely`() {
        val script = PhoneServerScripts.writePassword("pa'ss word")
        assertTrue("umask 077\n" in script)
        assertTrue("printf '%s' 'pa'\"'\"'ss word' > /root/.oc-builtin/server.password.tmp\n" in script)
        assertTrue(script.endsWith("mv /root/.oc-builtin/server.password.tmp /root/.oc-builtin/server.password\n"))
        assertFalse("opencode2 serve" in script)
    }

    @Test
    fun `B-LINUX-024 every phone-server script parses as shell`() {
        val sh = File("/bin/sh")
        if (!sh.canExecute()) return
        for (script in listOf(PhoneServerScripts.server(), PhoneServerScripts.writePassword("x"))) {
            val p = ProcessBuilder(sh.path, "-n").redirectErrorStream(true).start()
            p.outputStream.use { it.write(script.toByteArray()) }
            val output = p.inputStream.bufferedReader().readText()
            assertEquals(0, p.waitFor(), output)
        }
    }

    @Test
    fun `B-LINUX-023 the OpenCode 1 history copy is the Flutter script verbatim`() {
        val dart = File("../../lib/domain/opencode_history_copy.dart").takeIf { it.isFile } ?: return
        val text = dart.readText()
        val provenance = text.substringAfter("const _historyCopyProvenance = r'''").substringBefore("''';")
        val copy = text.substringAfter("const openCode1HistoryCopyScript =").substringAfter("r'''").substringBefore("''';")
        assertEquals(provenance + copy, PhoneServerScripts.HISTORY_COPY)
    }

    @Test
    fun `B-LINUX-037 the probe reads 2xx as answered and 401 as our password refused and anything else as no answer`() {
        assertEquals(ProbeAnswer.Answered, PhoneServerScripts.answer(200))
        assertEquals(ProbeAnswer.NeedsPassword, PhoneServerScripts.answer(401))
        assertEquals(ProbeAnswer.NoAnswer, PhoneServerScripts.answer(503))
        assertEquals(ProbeAnswer.NoAnswer, PhoneServerScripts.answer(null))
        assertEquals(listOf("/api/health", "/api/info", "/global/health"), PhoneServerScripts.PROBE_PATHS)
    }

    @Test
    fun `B-LINUX-020 status reads installed, running, port and uptime, and a stopped server shows no port`() {
        val running = runtimeStatusOf(installed = true, installing = false, serverRunning = true, port = 4097, uptimeMs = 5_000, services = listOf("server"))
        assertTrue(running.installed)
        assertEquals(RuntimePhase.Ready, running.phase)
        assertEquals(4097, running.serverPort)
        assertEquals(5.seconds, running.serverUptime)
        val stopped = runtimeStatusOf(installed = true, installing = false, serverRunning = false, port = 4097, uptimeMs = null, services = emptyList())
        assertNull(stopped.serverPort)
        assertEquals(RuntimePhase.Installing, runtimeStatusOf(true, true, false, null, null, emptyList()).phase)
        assertEquals(RuntimePhase.Idle, runtimeStatusOf(false, false, false, null, null, emptyList()).phase)
    }

    @Test
    fun `B-LINUX-092 runtime refusals classify without their text`() {
        fun problem(message: String) = (classifyRuntime(IllegalStateException(message)) as Failure.Runtime).problem
        assertEquals(RuntimeProblem.SlotBusy, problem("Another phone task is running"))
        assertEquals(RuntimeProblem.NotInstalled, problem("Ubuntu is not installed yet"))
        assertEquals(RuntimeProblem.Installing, problem("Finish or cancel setup before removing the runtime"))
        assertEquals(RuntimeProblem.Generic, problem("secret /root path"))
        val details = (classifyRuntime(IllegalStateException("secret /root path")) as Failure.Runtime).details
        assertEquals("IllegalStateException", details?.text)
    }
}
