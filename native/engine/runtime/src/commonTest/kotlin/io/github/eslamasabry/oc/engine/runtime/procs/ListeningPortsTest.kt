package io.github.eslamasabry.oc.engine.runtime.procs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/listening_ports_test.dart groups 'parsing', 'scripts', 'names' and 'ansi'. */
class ListeningPortsTest {
    private companion object {
        // A LISTEN row of /proc/net/tcp: port 5173 (0x1435) on 127.0.0.1, inode 5001.
        const val VITE = "   0: 0100007F:1435 00000000:0000 0A 00000000:00000000 00:00000000 " +
            "00000000  1000        0 5001 1 0000000000000000 100 0 0 10 0"

        // Port 8080 (0x1F90) on all interfaces, inode 5002.
        const val ANY = "   1: 00000000:1F90 00000000:0000 0A 00000000:00000000 00:00000000 " +
            "00000000  1000        0 5002 1 0000000000000000 100 0 0 10 0"

        // Port 4097 (0x1001): the in-app OpenCode server.
        const val OWN = "   2: 0100007F:1001 00000000:0000 0A 00000000:00000000 00:00000000 " +
            "00000000  1000        0 5003 1 0000000000000000 100 0 0 10 0"

        // An established connection (state 01), not a listener.
        const val ESTABLISHED = "   3: 0100007F:1435 0100007F:C350 01 00000000:00000000 00:00000000 " +
            "00000000  1000        0 5004 1 0000000000000000 100 0 0 10 0"

        // IPv6 ::1 port 3000 (0x0BB8), inode 5005.
        const val V6 = "   0: 00000000000000000000000001000000:0BB8 " +
            "00000000000000000000000000000000:0000 0A 00000000:00000000 " +
            "00:00000000 00000000  1000        0 5005 1 0000000000000000 100 0 0 10 0"

        fun output(probe: List<String> = emptyList()): String = """
#T4
$VITE
$ANY
$OWN
#T6
$V6
#S
lrwx------ 1 root root 64 Oct 16 10:00 /proc/41/fd/3 -> socket:[5001]
lrwx------ 1 root root 64 Oct 16 10:00 /proc/42/fd/7 -> socket:[5002]
lrwx------ 1 root root 64 Oct 16 10:00 /proc/43/fd/7 -> socket:[5003]
lrwx------ 1 root root 64 Oct 16 10:00 /proc/44/fd/9 -> socket:[5005]
#C
41${'\t'}node /root/projects/shop/node_modules/.bin/vite --port 5173 ${'\t'}/root/projects/shop${'\t'}/root/projects/shop/dev.log
42${'\t'}python3 -m http.server 8080 ${'\t'}/root/projects/shop${'\t'}/dev/pts/0
43${'\t'}opencode serve --port 4097 ${'\t'}/root${'\t'}pipe:[1]
44${'\t'}node server.js ${'\t'}/root/other${'\t'}pipe:[2]
#P
${probe.joinToString("\n")}
"""
    }

    @Test
    fun `B-LINUX-086 a LISTEN row gives the port, inode and whether it is loopback`() {
        val a = ListeningPorts.parseSocketLine(VITE)
        assertEquals(ListeningSocket(5173, 5001, true), a)
        val b = ListeningPorts.parseSocketLine(ANY)
        assertEquals(8080, b?.port)
        assertEquals(false, b?.loopbackOnly)
        val c = ListeningPorts.parseSocketLine(V6)
        assertEquals(3000, c?.port)
        assertEquals(true, c?.loopbackOnly)
    }

    @Test
    fun `B-LINUX-086 an established connection and a header are not listeners`() {
        assertNull(ListeningPorts.parseSocketLine(ESTABLISHED))
        assertNull(ListeningPorts.parseSocketLine("  sl  local_address"))
        assertNull(ListeningPorts.parseSocketLine(""))
    }

    @Test
    fun `B-LINUX-086 processes come with ports, folder and log file`() {
        val found = ListeningPorts.parse(output())
        assertEquals(listOf(41, 42, 44), found.map { it.pid })
        val vite = found.first()
        assertEquals(listOf(5173), vite.ports)
        assertTrue(vite.command.startsWith("node /root/projects/shop"))
        assertEquals("/root/projects/shop", vite.cwd)
        assertEquals("/root/projects/shop/dev.log", vite.logPath)
        assertTrue(vite.loopbackOnly)
        assertNull(found[1].logPath, "a terminal is not a file")
        assertFalse(found[1].loopbackOnly)
    }

    @Test
    fun `B-LINUX-086 the in-app server and its own port are never listed`() {
        val found = ListeningPorts.parse(output())
        assertFalse(found.flatMap { it.ports }.contains(4097))
        assertTrue(found.none { it.command.startsWith("opencode") })
    }

    @Test
    fun `B-LINUX-086 a probed port with no process is listed once, without a pid`() {
        val found = ListeningPorts.parse(output(probe = listOf("5173", "4200", "4097")))
        val extra = found.filter { !it.hasProcess }
        assertEquals(listOf(4200), extra.map { it.ports.single() })
        assertEquals("", extra.single().command)
    }

    @Test
    fun `B-LINUX-086 hidden socket tables leave only what the probe found`() {
        val found = ListeningPorts.parse("#T4\n#T6\n#S\n#C\n#P\n3000\n")
        assertEquals(listOf(3000), found.map { it.ports.single() })
    }

    @Test
    fun `B-LINUX-086 without tables, a probed port goes to the process that named it`() {
        val out = "#T4\n#T6\n#S\n#C\n" +
            "50\tpython3 -m http.server 8765 \t/root/projects/app\t/dev/null\n" +
            "51\tcurl http://127.0.0.1:8765/ \t/root\tpipe:[1]\n" +
            "52\tpaseo daemon --port 6767 \t/root\tpipe:[2]\n" +
            "::n 50 8765\n::n 51 8765\n::n 52 6767\n" +
            "#P\n8765\n6767\n3000\n"
        val found = ListeningPorts.parse(out)
        assertEquals(listOf("50:8765", "0:3000"), found.map { "${it.pid}:${it.ports.joinToString(",")}" })
        assertEquals("/root/projects/app", found.first().cwd)
    }

    @Test
    fun `B-LINUX-086 garbage and an empty answer give an empty list`() {
        assertTrue(ListeningPorts.parse("").isEmpty())
        assertTrue(ListeningPorts.parse("bash: ls: not found\n").isEmpty())
    }

    @Test
    fun `B-LINUX-086 the listing script reads the tables and probes the common ports`() {
        val script = ListeningPorts.script()
        assertTrue(script.contains("/proc/net/tcp"))
        assertTrue(script.contains("/proc/net/tcp6"))
        assertTrue(script.contains("/dev/tcp/127.0.0.1/"))
        for (port in COMMON_DEV_PORTS) assertTrue(script.contains("$port"), "$port")
        // Hidden tables (as Android does) point the reads at nothing.
        assertFalse(ListeningPorts.script(socketTables = emptyList()).contains("/proc/net/tcp"))
    }

    @Test
    fun `B-LINUX-086 tail refuses anything that is not a plain file path, and a quote cannot end the quoting`() {
        assertEquals("exit 2", ListeningPorts.tailScript("/dev/pts/0"))
        assertEquals("exit 2", ListeningPorts.tailScript("/proc/1/fd/1"))
        assertEquals("exit 2", ListeningPorts.tailScript("pipe:[3]"))
        assertTrue(ListeningPorts.tailScript("/tmp/a b.log").contains("'/tmp/a b.log'"))
        assertTrue(ListeningPorts.tailScript("/tmp/x'; rm -rf /; '.log").contains("'\\''"))
    }

    @Test
    fun `B-LINUX-084 stop signals one pid only while its command is unchanged`() {
        val script = ListeningPorts.stopScript(41, "node vite --port 5173")
        assertTrue(script.contains("kill -TERM 41"))
        assertTrue(script.contains("kill -KILL 41"))
        assertTrue(script.contains("'node vite --port 5173'*"))
        assertEquals("exit 2", ListeningPorts.stopScript(1, "init"))
        assertEquals("exit 2", ListeningPorts.stopScript(0, "x"))
    }

    @Test
    fun `B-LINUX-087 ports are read from local links in a server's own output`() {
        assertEquals(
            listOf(3001, 5173),
            ListeningPorts.portsInOutput(
                "ready\n  Local: http://localhost:5173/\n  Network: http://0.0.0.0:5173\n" +
                    "api on http://127.0.0.1:3001.\n",
            ),
        )
        assertTrue(ListeningPorts.portsInOutput("no links here").isEmpty())
    }

    @Test
    fun `B-LINUX-087 a command's own words name its ports`() {
        assertEquals(setOf(8765), portsNamedIn("python3 -m http.server 8765"))
        assertEquals(setOf(5173), portsNamedIn("node vite --port 5173"))
        assertEquals(setOf(8081), portsNamedIn("PORT=8081 node server.js"))
        assertTrue(portsNamedIn("node server.js -p 80").isEmpty(), "below 1024 an app cannot listen")
    }

    @Test
    fun `B-LINUX-088 a short name from a command line`() {
        assertEquals("vite", CommandNames.serverName("node /x/node_modules/.bin/vite --port 1"))
        assertEquals("http.server", CommandNames.serverName("python3 -m http.server 8080"))
        assertEquals("npm run dev", CommandNames.serverName("npm run dev"))
        assertEquals("caddy", CommandNames.serverName("/usr/bin/caddy run"))
        assertEquals("", CommandNames.serverName("   "))
    }

    @Test
    fun `B-LINUX-088 colour, cursor and title codes go, text and lines stay`() {
        assertEquals("ready in 410 ms", CommandNames.withoutTerminalCodes("\u001B[32mready\u001B[0m in 410 ms"))
        assertEquals("done", CommandNames.withoutTerminalCodes("\u001B]0;title\u0007done"))
        assertEquals("a\nb\nc", CommandNames.withoutTerminalCodes("a\r\nb\rc"))
        assertEquals("xy", CommandNames.withoutTerminalCodes("x\u001B[2K\u001B[1Gy"))
        assertEquals("bell gone", CommandNames.withoutTerminalCodes("bell\u0007 gone"))
        assertEquals("", CommandNames.withoutTerminalCodes(""))
    }
}
