package io.github.eslamasabry.oc.engine.runtime.procs

import io.github.eslamasabry.oc.core.common.getOrNull
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The scripts run for real under `sh` on this machine (port of the live parts of test/linux_manager_parse_test.dart
 * and test/listening_ports_test.dart group 'live run without the socket tables (Android 10+)').
 */
class ProcsLiveTest {
    private val started = mutableListOf<Long>()

    @AfterTest
    fun cleanUp() {
        for (pid in started) ProcessHandle.of(pid).ifPresent { it.destroyForcibly() }
    }

    private fun sh(script: String, env: Map<String, String> = emptyMap()): String {
        val pb = ProcessBuilder("sh", "-c", script).redirectError(ProcessBuilder.Redirect.DISCARD)
        pb.environment().putAll(env)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "script timed out")
        return out
    }

    private fun sleeper(): Process = ProcessBuilder("sleep", "120").start().also { started.add(it.pid()) }

    @Test
    fun `B-LINUX-084 scan and stop scripts work on a real process`() {
        val sleeper = sleeper()
        val live = LinuxProcessScan.parse(sh(LINUX_PROCESS_SCAN_SCRIPT))
        val mine = live.processes.filter { it.pid.toLong() == sleeper.pid() }
        assertEquals(1, mine.size)
        assertEquals("sleep 120", mine.single().cmd)
        assertEquals("sleep", mine.single().name)
        assertTrue(live.processes.none { it.cmd.contains("oc-linux-scan") })

        val script = assertNotNull(linuxStopScript(listOf(mine.single().identity)).getOrNull())
        val result = LinuxStopResult.parse(sh(script))
        assertNotNull(result.outcomes[mine.single().identity])
        assertEquals(1, result.ended)
        assertTrue(sleeper.waitFor(5, TimeUnit.SECONDS))
    }

    @Test
    fun `B-LINUX-084 a changed start time leaves the process alone`() {
        val sleeper = sleeper()
        val script = assertNotNull(linuxStopScript(listOf("${sleeper.pid()}:1")).getOrNull())
        val result = LinuxStopResult.parse(sh(script))
        assertEquals(1, result.changed)
        assertEquals(0, result.ended)
        // Still alive: killing it ourselves is what ends it.
        assertTrue(sleeper.isAlive)
    }

    @Test
    fun `B-LINUX-084 the one-pid stop signals only while the command is unchanged`() {
        val sleeper = sleeper()
        val other = ProcessBuilder("sh", "-c", ListeningPorts.stopScript(sleeper.pid().toInt(), "sleep 999")).start()
        other.waitFor(10, TimeUnit.SECONDS)
        assertEquals(4, other.exitValue(), "a different command line is left alone")
        assertTrue(sleeper.isAlive)
        val stop = ProcessBuilder("sh", "-c", ListeningPorts.stopScript(sleeper.pid().toInt(), "sleep 120")).start()
        stop.waitFor(10, TimeUnit.SECONDS)
        assertEquals(0, stop.exitValue())
        assertTrue(sleeper.waitFor(5, TimeUnit.SECONDS))
    }

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    private fun waitForPort(port: Int) {
        repeat(50) {
            try {
                Socket("127.0.0.1", port).close()
                return
            } catch (_: java.io.IOException) {
                Thread.sleep(100)
            }
        }
        error("server on $port did not start")
    }

    @Test
    fun `B-LINUX-087 finds python http server on an arbitrary port, with its process`() {
        val port = freePort()
        val tmp = System.getProperty("java.io.tmpdir")
        // Started the way the QA did it: in the background of a subshell that exits (an orphan).
        val pid = sh("cd \"$tmp\" && (python3 -m http.server $port --bind 127.0.0.1 >/dev/null 2>&1 & echo \$!)")
            .trim().toLong()
        started.add(pid)
        waitForPort(port)
        val out = sh(ListeningPorts.script(socketTables = emptyList()))
        val server = ListeningPorts.parse(out).filter { port in it.ports }
        assertEquals(1, server.size, out)
        assertEquals(pid.toInt(), server.single().pid)
        assertTrue(server.single().command.contains("http.server"))
    }

    @Test
    fun `B-LINUX-087 a port named only by PORT is found, and its log file is offered`() {
        val port = freePort()
        val dir = Files.createTempDirectory("oc-ports").toFile()
        try {
            val log = File(dir, "server.log").path
            val pid = sh(
                "cd \"${dir.path}\" && (python3 -c \"import http.server,os;" +
                    "http.server.test(HandlerClass=http.server.SimpleHTTPRequestHandler," +
                    "port=int(os.environ['PORT']),bind='127.0.0.1')\" >$log 2>&1 & echo \$!)",
                env = mapOf("PORT" to "$port"),
            ).trim().toLong()
            started.add(pid)
            waitForPort(port)
            val out = sh(ListeningPorts.script(socketTables = emptyList()))
            val server = ListeningPorts.parse(out).filter { port in it.ports }
            assertEquals(1, server.size, out)
            assertEquals(pid.toInt(), server.single().pid)
            assertEquals(log, server.single().logPath)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `B-LINUX-086 the listing script reads the real socket tables when they are readable`() {
        val port = freePort()
        val pid = sh("(python3 -m http.server $port --bind 127.0.0.1 >/dev/null 2>&1 & echo \$!)").trim().toLong()
        started.add(pid)
        waitForPort(port)
        val found = ListeningPorts.parse(sh(ListeningPorts.script())).filter { port in it.ports }
        assertEquals(1, found.size)
        assertEquals(pid.toInt(), found.single().pid)
        assertTrue(found.single().loopbackOnly)
    }
}
