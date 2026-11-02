package io.github.eslamasabry.oc.feature.linux.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports of test/linux_manager_parse_test.dart (ports, storage, packages, job) and the B-SEC-096 script guards. */
class LinuxManagerParseTest {
    // ---- B-LINUX-085 ports

    @Test
    fun `B-LINUX-085 finds the ports a command names`() {
        assertEquals(setOf(5173), LinuxPortScan.portsNamedIn("vite --port 5173"))
        assertEquals(setOf(3000), LinuxPortScan.portsNamedIn("serve -p 3000"))
        assertEquals(setOf(8081), LinuxPortScan.portsNamedIn("PORT=8081 node x.js"))
        assertEquals(setOf(8765), LinuxPortScan.portsNamedIn("python -m http.server 8765 --bind 0.0.0.0"))
        assertEquals(emptySet(), LinuxPortScan.portsNamedIn("sleep 30"))
        assertEquals(setOf(9229), LinuxPortScan.portsNamedIn("curl http://127.0.0.1:9229/json"))
        assertEquals(emptySet(), LinuxPortScan.portsNamedIn("x --port 80"))
    }

    @Test
    fun `B-LINUX-085 lists a port only when a connection to it worked`() {
        val ports = LinuxPortScan.parse("::open 5173\n::open 4097\nnoise\n::listen 00000000 0BB8\n")
        assertEquals(listOf(4097, 5173), ports.map { it.port })
    }

    @Test
    fun `B-LINUX-085 matches an owner from the commands and the server by its port`() {
        val ports =
            LinuxPortScan.parse(
                "::n 51 5173\n::c 51 node /root/projects/app/node_modules/.bin/vite --port 5173\n" +
                    "::open 5173\n::open 4097\n::open 3000",
            )
        assertEquals(listOf(3000, 4097, 5173), ports.map { it.port })
        assertNull(ports[0].owner)
        assertNull(ports[0].kind)
        assertTrue(ports[0].canPreview)
        assertEquals(LinuxProgramKind.OpenCodeServer, ports[1].kind)
        assertFalse(ports[1].canPreview)
        assertEquals(51, ports[2].owner!!.pid)
        assertEquals("vite", ports[2].owner!!.name)
        assertEquals(LinuxProgramKind.DevServer, ports[2].kind)
        assertTrue(ports[2].canPreview)
        assertEquals("http://127.0.0.1:5173/", ports[2].previewUrl)
    }

    @Test
    fun `B-LINUX-085 Paseo is not offered as a page`() {
        val port = LinuxPortScan.parse("::n 9 6767\n::c 9 node /home/oc/.local/share/oc-paseo/1.2/paseo daemon\n::open 6767").single()
        assertEquals(LinuxProgramKind.Paseo, port.kind)
        assertFalse(port.canPreview)
    }

    @Test
    fun `B-LINUX-085 reads interface reach from the kernel table when it is readable`() {
        val ports = LinuxPortScan.parse("::listen 00000000 1435\n::listen 0100007F 0FA1\n::open 5173\n::open 4001")
        assertEquals(true, ports.first { it.port == 5173 }.allInterfaces)
        assertEquals(false, ports.first { it.port == 4001 }.allInterfaces)
        assertNull(LinuxPortScan.parse("::open 5173").single().allInterfaces)
    }

    @Test
    fun `B-SEC-096 the probe script holds only numbers it was given`() {
        val script = LinuxPortScan.script(listOf(5173, 70000, 80, 5173))
        assertTrue(script.contains("5173"))
        assertFalse(script.contains("70000"))
        assertFalse(script.contains(" 80 "))
    }

    // ---- B-LINUX-089 storage

    @Test
    fun `B-LINUX-089 parses du lines and adds categories`() {
        val report =
            LinuxStorage.report(
                "::du 1000 /usr\n::du 100 /etc\n::du 5 /var/lib/dpkg\n::du 40 /var/cache/apt\n::du 60 /var/lib/apt\n" +
                    "::du 700 /root/projects\n::du 8 /tmp\nbroken\n::du x /var/tmp\n",
            )
        assertEquals(1105L * 1024, report.of(LinuxStorageCategory.Base))
        assertEquals(100L * 1024, report.of(LinuxStorageCategory.Packages))
        assertEquals(700L * 1024, report.of(LinuxStorageCategory.Projects))
        assertEquals(8L * 1024, report.of(LinuxStorageCategory.Temp))
        assertEquals(0L, report.of(LinuxStorageCategory.Agents))
        assertEquals((1105L + 100 + 700 + 8) * 1024, report.total)
    }

    @Test
    fun `B-LINUX-089 freed space is the drop between two readings and never negative`() {
        assertEquals(90L * 1024, LinuxStorage.freedBytes(mapOf("/a" to 100L, "/b" to 50L), mapOf("/a" to 10L, "/b" to 70L)))
        assertEquals(5L * 1024, LinuxStorage.freedBytes(mapOf("/a" to 5L), emptyMap()))
        assertEquals(0L, LinuxStorage.freedBytes(emptyMap(), mapOf("/a" to 5L)))
    }

    @Test
    fun `B-LINUX-089 safe paths are caches and never projects or credentials`() {
        for (ok in listOf("/var/cache/apt", "/root/.npm/_cacache", "/root/.cache", "/tmp", "/home/oc/.cache", "/home/oc/.local/share/oc-paseo", "/tmp/build-1")) {
            assertTrue(LinuxStorage.cleanupPathIsSafe(ok), ok)
        }
        for (
            bad in listOf(
                "/", "/root", "/root/projects", "/root/projects/app", "/root/.npm", "/root/.claude", "/root/.config/x", "/root/.ssh",
                "/home/oc", "/home/oc/.oc-profiles", "/home/oc/.oc-profiles/abc/claude", "/home/oc/.local", "/home/oc/.local/share",
                "/home/oc/.local/bin", "/opt/opencode2", "/usr/bin", "/etc", "/var", "/var/lib/dpkg", "/tmp/../root/projects",
                "/tmp/a b", "/tmp/\$(id)", "/tmp/", "tmp", "",
            )
        ) {
            assertFalse(LinuxStorage.cleanupPathIsSafe(bad), bad)
        }
    }

    @Test
    fun `B-SEC-096 every cleanup names only safe paths and its script stays inside them`() {
        val token = Regex("/[A-Za-z0-9_./-]*[A-Za-z0-9_]")
        for (action in LinuxStorage.cleanups) {
            assertTrue(action.paths.isNotEmpty())
            action.paths.forEach { assertTrue(LinuxStorage.cleanupPathIsSafe(it), it) }
            for (script in listOf(action.script, action.measureScript)) {
                for (protected in LinuxStorage.protectedPaths) {
                    if (protected in setOf("/etc", "/usr", "/opt")) continue
                    assertFalse(script.contains(protected), "${action.id} names $protected")
                }
                for (m in token.findAll(script)) {
                    val t = m.value
                    if (t == "/dev/null") continue
                    val named = action.paths + action.reads
                    assertTrue(named.any { t == it || t.startsWith("$it/") }, "${action.id}: $t")
                }
            }
            assertFalse(action.script.contains("rm -rf /"))
        }
    }

    @Test
    fun `B-LINUX-089 old agent versions are only removed when the current one is known`() {
        val script = LinuxStorage.oldAgentVersions.script
        assertTrue(script.contains("[ -n \"\$cur\" ]"))
        assertTrue(script.contains("case \"\$cur\" in /home/oc/.local/share/oc-paseo/*) ;; *) exit 0 ;; esac"))
        assertTrue(script.contains("case \"\$cur\" in \"\$d\"/*) continue ;; esac"))
        assertTrue(LinuxStorage.tempFiles.script.contains("-mtime +1"))
        assertTrue(LinuxStorage.tempFiles.script.contains("-mtime +7"))
    }

    // ---- B-LINUX-090 packages

    @Test
    fun `B-LINUX-090 validates names`() {
        for (ok in listOf("git", "g++", "libssl3", "python3.12", "a-b")) assertTrue(LinuxPackages.isValidName(ok), ok)
        for (bad in listOf("", "a", "Git", "a b", "a;b", "\$(x)", "-rf", "../x", "a/b")) assertFalse(LinuxPackages.isValidName(bad), bad)
        assertEquals(listOf("git", "htop", "tmux"), LinuxPackages.parseTyped("git, htop  tmux"))
        assertNull(LinuxPackages.parseTyped("git; rm -rf"))
        assertNull(LinuxPackages.parseTyped("  "))
    }

    @Test
    fun `B-LINUX-090 lists installed packages and marks the ones Linux needs`() {
        val list =
            LinuxPackages.parseInstalled(
                "::pkg bash\t5.2\t1400\tyes\trequired\tii \n::pkg htop\t3.3\t300\t\toptional\tii \n" +
                    "::pkg curl\t8\t500\t\toptional\tii \n::pkg broken\t1\t1\t\toptional\tiU \n::pkg gone\t1\t1\t\toptional\trc \ngarbage\n",
            )
        assertEquals(listOf("bash", "curl", "htop"), list.map { it.name })
        assertTrue(list[0].locked)
        assertTrue(list[1].locked, "setup relies on curl")
        assertFalse(list[2].locked)
        assertEquals(300L, list[2].sizeKb)
    }

    @Test
    fun `B-LINUX-090 reads candidates and removal plans`() {
        val c = LinuxPackages.parseCandidates("::cand htop\t3.3.0\t\n::cand nope\t\t\n::cand git\t2.43\tii \n")
        assertTrue(c[0].available)
        assertFalse(c[0].installed)
        assertFalse(c[1].available)
        assertTrue(c[2].installed)
        assertEquals(listOf("htop", "libx"), LinuxPackages.parseRemovePlan("::remv htop\n::remv libx\nx\n::remv ;bad"))
    }

    @Test
    fun `B-SEC-096 package scripts refuse text that is not a package name`() {
        assertFailsWith<IllegalArgumentException> { LinuxPackages.checkScript(listOf("a;b")) }
        assertFailsWith<IllegalArgumentException> { LinuxPackages.removePlanScript(emptyList()) }
        assertFailsWith<IllegalArgumentException> { LinuxPackages.jobScript(LinuxJobKind.Install, listOf("\$(id)")) }
        assertTrue(LinuxPackages.jobScript(LinuxJobKind.Update).contains("upgrade"))
        assertTrue(LinuxPackages.jobScript(LinuxJobKind.Install, listOf("htop")).contains("oc_apt_install htop"))
    }

    @Test
    fun `B-LINUX-090 a job stops itself after its budget and names what it is`() {
        val script = LinuxPackages.jobScript(LinuxJobKind.Remove, listOf("htop"))
        assertTrue(script.startsWith("# oc setup prelude"))
        assertTrue(script.contains("printf '::oc job remove\\n'"))
        assertTrue(script.contains("printf '::oc names %s\\n' 'htop'"))
        assertTrue(script.contains("[ \$oc_i -lt ${45 * 60 / 5} ]"))
        assertTrue(script.contains("oc_main=\$\$\n"))
        assertTrue(script.contains("printf '::oc job-exit 124\\n'"))
        assertTrue(script.contains("flock -n 9"))
    }

    // ---- B-LINUX-091 job state

    @Test
    fun `B-LINUX-091 a job log shows running with progress and ends as done failed or timed out`() {
        val running = LinuxJobState.fromLog("::oc job install\n::oc names htop tmux\n::oc stage Installing\n::oc percent 41.6\n", running = true)
        assertEquals(LinuxJobStatus.Running, running.status)
        assertEquals(LinuxJobKind.Install, running.kind)
        assertEquals(listOf("htop", "tmux"), running.names)
        assertEquals(42, running.percent)
        assertEquals("Installing", running.stage)
        assertEquals(LinuxJobStatus.Done, LinuxJobState.fromLog("::oc job update\n::oc job-exit 0\n", running = false).status)
        val failed = LinuxJobState.fromLog("::oc job remove\nE: apt broke\n::oc job-exit 100\n", running = false)
        assertEquals(LinuxJobStatus.Failed, failed.status)
        assertEquals(100, failed.exitCode)
        assertEquals(LinuxJobStatus.TimedOut, LinuxJobState.fromLog("::oc job update\n::oc job-exit 124\n", running = false).status)
        assertTrue(LinuxJobState.fromLog("::oc job update\n::oc job-exit 75\n", running = false).wasBusy)
        assertEquals(LinuxJobStatus.Interrupted, LinuxJobState.fromLog("::oc job update\n::oc stage x\n", running = false).status)
        assertEquals(LinuxJobState.None, LinuxJobState.fromLog("", running = false))
        assertEquals(LinuxJobState.None, LinuxJobState.fromLog("noise only\n", running = false))
    }
}
