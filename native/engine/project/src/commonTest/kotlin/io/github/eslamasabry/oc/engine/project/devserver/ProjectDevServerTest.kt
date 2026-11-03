package io.github.eslamasabry.oc.engine.project.devserver

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The phone's listing: vite on 5173 run from /root/p, and a python server on 8080 run from /other.
private val PHONE =
    "#T4\n" +
        "   0: 0100007F:1435 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 5001 1 0000000000000000 100 0 0 10 0\n" +
        "   1: 00000000:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 5002 1 0000000000000000 100 0 0 10 0\n" +
        "#S\n" +
        "lrwx------ 1 root root 64 Oct 16 10:00 /proc/41/fd/3 -> socket:[5001]\n" +
        "lrwx------ 1 root root 64 Oct 16 10:00 /proc/42/fd/3 -> socket:[5002]\n" +
        "#C\n" +
        "41\tnode vite --port 5173 \t/root/p\t/root/p/dev.log\n" +
        "42\tpython3 -m http.server 8080 \t/other\t/dev/pts/0\n" +
        "#P\n"

class ProjectDevServerTest {
    @Test
    fun `B-NAV-043 nothing known means no line`() = runTest {
        assertNull(ProjectDevServerLook.lookFor(directory = "/root/projects/neon").getOrNull())
    }

    @Test
    fun `B-NAV-043 a look that finds one says so once, and a failed look keeps it`() = runTest {
        var answer: DevPreviewPort? = DevPreviewPort(5173)
        var fail = false
        val watch = ProjectDevServer(backgroundScope, look = { if (fail) Failure.Device().failed() else answer.ok() })
        val seen = ArrayList<DevPreviewPort?>()
        backgroundScope.launch { watch.running.collect { seen += it } }
        runCurrent()
        watch.refresh()
        runCurrent()
        assertEquals(5173, watch.running.value?.port)
        watch.refresh()
        runCurrent()
        assertEquals(listOf(null, DevPreviewPort(5173)), seen, "the same server says nothing new")
        fail = true
        watch.refresh()
        assertEquals(5173, watch.running.value?.port)
        fail = false
        answer = null
        watch.refresh()
        runCurrent()
        assertNull(watch.running.value)
        assertEquals(listOf(null, DevPreviewPort(5173), null), seen)
    }

    @Test
    fun `B-NAV-043 a program listening inside the project is its dev server`() = runTest {
        assertEquals(5173, ProjectDevServerLook.lookFor("/root/p", listening = { PHONE.ok() }).getOrNull()?.port)
        assertEquals(5173, ProjectDevServerLook.lookFor("/root", listening = { PHONE.ok() }).getOrNull()?.port)
    }

    @Test
    fun `B-NAV-043 programs listening outside the project are not this project's`() = runTest {
        assertNull(ProjectDevServerLook.lookFor("/root/q", listening = { PHONE.ok() }).getOrNull())
        // A prefix that is not a parent folder does not count.
        assertNull(ProjectDevServerLook.lookFor("/root/p2", listening = { PHONE.ok() }).getOrNull())
    }

    @Test
    fun `B-NAV-043 a running saved service wins at its own saved port and a stopped one does not count`() = runTest {
        val saved = { listOf(SavedServiceRun("http://localhost:3000/", running = false), SavedServiceRun("http://localhost:4321", running = true)).ok() }
        assertEquals(4321, ProjectDevServerLook.lookFor("/root/q", saved = saved, listening = { PHONE.ok() }).getOrNull()?.port)
        assertEquals(5173, ProjectDevServerLook.lookFor("/root/p", saved = { listOf(SavedServiceRun("http://localhost/", true)).ok() }, listening = { PHONE.ok() }).getOrNull()?.port)
    }

    @Test
    fun `B-NAV-043 the listing parser keeps owners, cwd and log files`() {
        val parsed = ListeningPorts.parse(PHONE)
        assertEquals(listOf(41, 42), parsed.map { it.pid })
        assertEquals(listOf(5173), parsed[0].ports)
        assertEquals("/root/p", parsed[0].cwd)
        assertEquals("/root/p/dev.log", parsed[0].logPath)
        assertEquals(true, parsed[0].loopbackOnly)
        assertNull(parsed[1].logPath)
        assertEquals(false, parsed[1].loopbackOnly)
        // Without socket tables, a probed port belongs to the process that named it; the app's own server never counts.
        val probed = ListeningPorts.parse("#C\n7\tnode serve.js\t/root/p\t\n9\tcurl x\t/root/p\t\n::n 7 3000\n::n 9 3000\n#P\n3000\n4097\n8888\n")
        assertEquals(listOf(7 to listOf(3000), 0 to listOf(8888)), probed.map { it.pid to it.ports })
    }
}
