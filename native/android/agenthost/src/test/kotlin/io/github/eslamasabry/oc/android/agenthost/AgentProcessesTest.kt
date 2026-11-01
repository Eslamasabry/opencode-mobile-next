package io.github.eslamasabry.oc.android.agenthost

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** The agent block of `BuiltinLinux` behind the runtime ports (plan 11 §5.3 `AgentProcesses`, map BUILTIN-LINUX-52). */
class AgentProcessesTest {
    private val dir = Files.createTempDirectory("agent-processes-").toFile().canonicalFile
    private val ports = FakeRuntimePorts(dir).also { File(dir, "linux/ubuntu/home/oc").mkdirs() }
    private val processes = ports.processes()
    private val started = mutableListOf<Process>()

    @After fun cleanUp() {
        started.forEach { it.destroyForcibly(); it.waitFor(2, TimeUnit.SECONDS) }
        ports.serviceMap.values.forEach { it.destroyForcibly() }
        dir.walkBottomUp().forEach { it.setWritable(true); it.setReadable(true); it.setExecutable(true) }
        dir.deleteRecursively()
    }

    private fun start(profile: String = "qa", args: List<String> = listOf("/bin/true"), privateOutput: Boolean = false) =
        processes.startAgentProcess(profile, args, privateOutput = privateOutput).also { started += it }

    private fun envOf(command: List<String>): Map<String, String> =
        command.dropWhile { it != "/usr/bin/env" }.drop(1).takeWhile { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    @Test fun `B-LINUX-125 every phone agent can open its sign-in terminal`() {
        // The catalog's phone agents (PaseoNative and AcpPaseo with a recipe) and their executables (issue #95).
        val phoneAgents = setOf("claude", "codex", "gemini", "qwen", "kilo", "cline", "goose", "omp", "fx")
        assertTrue(AgentProcesses.SIGN_IN_PROGRAMS.containsAll(phoneAgents))
        for (program in phoneAgents) {
            val (argv, url) = processes.agentSignInCommand("qa", listOf(program, "auth", "login"))
            assertEquals(program, argv[argv.size - 3])
            assertEquals(File(dir, "linux/ubuntu/home/oc/.oc-profiles/qa/.oc-open-url"), url)
        }
        assertFailsWith<IllegalStateException> { processes.agentSignInCommand("qa", listOf("bash")) }
        assertFailsWith<IllegalStateException> { processes.agentSignInCommand("qa", listOf("claude", "a;b")) }
        assertFailsWith<IllegalStateException> { processes.agentSignInCommand("qa", List(6) { "claude" }) }
        assertFailsWith<IllegalStateException> { processes.agentSignInCommand("../x", listOf("claude")) }
    }

    @Test fun `B-LINUX-125 Codex's folder exists before any agent process starts`() {
        val codex = File(dir, "linux/ubuntu/home/oc/.oc-profiles/qa/codex")
        ports.guestScript = { if (codex.isDirectory) "exit 0" else "exit 9" }
        val child = start()
        assertTrue(child.waitFor(5, TimeUnit.SECONDS))
        assertEquals(0, child.exitValue())
        assertEquals("/home/oc/.oc-profiles/qa/codex", envOf(ports.launched.single().second)["CODEX_HOME"])
        assertEquals(448, ports.modes.modesSet[codex.path])
        val (argv, _) = processes.agentSignInCommand("other", listOf("codex", "login"))
        assertTrue(File(dir, "linux/ubuntu/home/oc/.oc-profiles/other/codex").isDirectory)
        assertEquals("/home/oc/.oc-profiles/other/codex", envOf(argv)["CODEX_HOME"])
    }

    @Test fun `B-AGENT-316 every agent launch that sets CLAUDE_CONFIG_DIR sets CLAUDE_HOME to the same folder`() {
        start()
        processes.startSignInProcess("qa", listOf("claude", "auth", "status"), foreground = false).also { started += it }
        val terminal = processes.agentSignInCommand("qa", listOf("claude", "auth", "login")).first
        val launches = ports.launched.map { it.second } + listOf(terminal)
        assertEquals(3, launches.size)
        for (command in launches) {
            val env = envOf(command)
            assertEquals("/home/oc/.oc-profiles/qa/claude", env["CLAUDE_CONFIG_DIR"])
            assertEquals(env["CLAUDE_CONFIG_DIR"], env["CLAUDE_HOME"])
            assertEquals("/home/oc/.oc-profiles/qa", env["HOME"])
            assertEquals("1", env["DISABLE_AUTOUPDATER"])
        }
        assertEquals(listOf(ProcessOwner.Paseo, ProcessOwner.AgentSignIn), ports.launched.map { it.first })
    }

    @Test fun `B-LINUX-117 agents run as the fixed agent user and never as root`() {
        start()
        val command = ports.launched.single().second
        assertEquals("--change-id=1000:1000", command[1])
        assertEquals("/usr/bin/env", command[2])
        ports.installed = false
        assertFailsWith<IllegalStateException> { start() }
        ports.installed = true
        assertFailsWith<IllegalStateException> { processes.startAgentProcess("qa", emptyList()) }
        assertFailsWith<IllegalStateException> { processes.startAgentProcess("a/b", listOf("x")) }
    }

    @Test fun `B-LINUX-117 a helper ticket taken before the activity paused is refused after it`() {
        val ticket = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        ports.state = ports.state.copy(activityResumed = false, activityEpoch = 2)
        val error = assertFailsWith<IllegalStateException> { processes.withAgentHostStart(ticket) { start() } }
        assertEquals("idle_resume_stale", error.message)
        assertTrue(ports.launched.isEmpty())
        assertNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
    }

    @Test fun `B-LINUX-117 a helper ticket is rechecked at launch and at service registration`() {
        val ticket = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        val child = processes.withAgentHostStart(ticket) { start(args = listOf("paseo")) }
        processes.trackPrivateAgentService("agent-host.qa", child, 4099)
        assertTrue(ports.serviceMap["agent-host.qa"] === child)
        assertEquals(listOf(42L), ports.authorized)
        // A second launch whose epoch moved between capture and registration is refused at registration.
        val second = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        val late = processes.withAgentHostStart(second) { start(args = listOf("paseo")) }
        ports.state = ports.state.copy(activityEpoch = 9)
        assertFailsWith<IllegalStateException> { processes.trackPrivateAgentService("agent-host.qa", late, 4099) }
    }

    @Test fun `B-LINUX-117 an idle resume ticket needs a positive generation and the idle owner's helper`() {
        assertNull(processes.captureAgentHostStart("qa", idleResume = true, generation = null))
        assertNull(processes.captureAgentHostStart("qa", idleResume = true, generation = 0))
        ports.state = ports.state.copy(idle = AgentIdleFacts(generation = 7, stopped = false, helperStopped = true,
            owner = "owner", helper = "qa"))
        assertNotNull(processes.captureAgentHostStart("qa", idleResume = true, generation = 7))
        assertNull(processes.captureAgentHostStart("other", idleResume = true, generation = 7))
        assertNull(processes.captureAgentHostStart("qa", idleResume = true, generation = 6))
    }

    @Test fun `B-LINUX-117 a completed idle token admits only its owner's helper and its gate clears once`() {
        ports.state = ports.state.copy(idle = AgentIdleFacts(generation = 7, owner = "owner"))
        ports.helperOwnerOf = { if (it == "owner") "qa" else null }
        assertNull(processes.captureAgentHostStart("other", idleResume = false, generation = null))
        val ticket = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        val child = processes.withAgentHostStart(ticket) { start(args = listOf("paseo")) }
        processes.trackPrivateAgentService("agent-host.qa", child, 4099)
        assertEquals(listOf("owner" to 7L), ports.clearedGates)
    }

    @Test fun `B-LINUX-117 a refused foreground service stops the helper and leaves no service`() {
        ports.foregroundRefuses = true
        val ticket = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        val child = processes.withAgentHostStart(ticket) { start(args = listOf("paseo")) }
        val error = assertFailsWith<IllegalStateException> { processes.trackPrivateAgentService("agent-host.qa", child, 4099) }
        assertEquals("The agent host could not start.", error.message)
        assertFalse(child.isAlive)
        assertNull(ports.serviceMap["agent-host.qa"])
    }

    @Test fun `B-LINUX-117 an exited helper leaves the services map through the runtime`() {
        val ticket = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        ports.guestScript = { "exit 3" }
        val child = processes.withAgentHostStart(ticket) { start(args = listOf("paseo")) }
        processes.trackPrivateAgentService("agent-host.qa", child, 4099)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (ports.exits.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals(listOf("agent-host.qa"), ports.exits)
    }

    @Test fun `B-LINUX-117 an update needs the exact owned helper and no other agent process`() {
        ports.guestScript = { "exit 0" }
        assertFailsWith<PhoneAgentHost.Failure> { processes.requireAgentHostUpdateIdle("qa", start()) }
            .also { assertEquals("agent_host_unowned", it.code) }
        ports.guestScript = { "exec sleep 10" }
        val ticket = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        val helper = processes.withAgentHostStart(ticket) { start(args = listOf("paseo")) }
        processes.trackPrivateAgentService("agent-host.qa", helper, 4099)
        // The first /bin/true has exited; nothing else runs: idle.
        started.first().waitFor(5, TimeUnit.SECONDS)
        processes.requireAgentHostUpdateIdle("qa", helper)
        assertFailsWith<PhoneAgentHost.Failure> { processes.requireAgentHostUpdateIdle("qa", null) }
            .also { assertEquals("agent_host_unowned", it.code) }
        ports.guestScript = { "exec sleep 10" }
        start(args = listOf("claude"))
        assertFailsWith<PhoneAgentHost.Failure> { processes.requireAgentHostUpdateIdle("qa", helper) }
            .also { assertEquals("agent_host_busy", it.code) }
    }

    @Test fun `B-LINUX-117 stopping an agent drains its process tree and marks the service stop as requested`() {
        ports.guestScript = { "sleep 30 & sleep 30 & wait" }
        val child = start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val root = checkNotNull(GuestProc.pid(child))
        while (GuestProc.descendants(root).size < 2 && System.nanoTime() < deadline) Thread.sleep(20)
        val below = GuestProc.descendants(root)
        assertEquals(2, below.size)
        processes.stopAgentProcess(child)
        assertFalse(child.isAlive)
        Thread.sleep(100)
        assertTrue(below.none(GuestProc::alive))
        assertTrue(child in ports.stopRequested)
    }

    @Test fun `B-LINUX-117 the Paseo config is version 1, private and replaced atomically`() {
        processes.writeAgentConfig("qa", """{"version":1,"daemon":{}}""")
        val config = File(dir, "linux/ubuntu/home/oc/.oc-profiles/qa/paseo/config.json")
        assertEquals("""{"version":1,"daemon":{}}""", config.readText())
        assertEquals(384, ports.modes.modesSet[File(config.parentFile, "config.json.tmp").path])
        assertFalse(File(config.parentFile, "config.json.tmp").exists())
        assertFailsWith<IllegalStateException> { processes.writeAgentConfig("qa", """{"version":2}""") }
        assertFailsWith<IllegalStateException> { processes.writeAgentConfig("qa", "{\"version\":1,\"x\":\"" + "a".repeat(65536) + "\"}") }
    }

    @Test fun `B-LINUX-117 a stale Claude login lock is cleared only while none of the profile's processes runs`() {
        val lock = File(dir, "linux/ubuntu/home/oc/.oc-profiles/qa/claude/.oauth_refresh.lock").apply { mkdirs() }
        File(lock, "held").writeText("x")
        ports.guestScript = { "exec sleep 10" }
        val child = start()
        processes.clearStaleAgentLoginLock("qa")
        assertTrue(lock.isDirectory)
        processes.stopAgentProcess(child)
        processes.clearStaleAgentLoginLock("qa")
        assertFalse(lock.exists())
    }

    @Test fun `B-LINUX-117 deleting the agent home never follows a link and never runs while its helper runs`() {
        val outside = File(dir, "outside").apply { mkdirs() }
        File(outside, "keep").writeText("person's file")
        val home = File(dir, "linux/ubuntu/home/oc/.oc-profiles/qa").apply { mkdirs() }
        File(home, "claude").mkdirs()
        File(home, "claude/.credentials.json").writeText("secret")
        Files.createSymbolicLink(File(home, "escape").toPath(), outside.toPath())
        val ticket = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        val helper = processes.withAgentHostStart(ticket) { start(args = listOf("paseo")) }
        processes.trackPrivateAgentService("agent-host.qa", helper, 4099)
        assertFailsWith<IllegalStateException> { processes.deleteAgentHome("qa") }
        assertTrue(home.isDirectory)
        processes.stopAgentProcess(helper)
        processes.deleteAgentHome("qa")
        assertFalse(home.exists())
        assertEquals("person's file", File(outside, "keep").readText())
        assertEquals(listOf(setOf("agent-auth.qa", "agent-host.qa")), ports.forgotten)
    }

    @Test fun `B-LINUX-117 a blocked profile starts nothing and its auth checks are drained`() {
        ports.guestScript = { "exec sleep 10" }
        val child = start()
        processes.blockAgentProfile("qa")
        assertFalse(child.isAlive)
        assertEquals(listOf("qa"), ports.blockedAuth)
        assertTrue(processes.isBlocked("qa"))
        assertFailsWith<IllegalStateException> { start() }
        assertFailsWith<IllegalStateException> { processes.agentPaseoHome("qa") }
        assertFailsWith<IllegalStateException> { processes.agentSignInCommand("qa", listOf("claude")) }
    }

    @Test fun `P11-7-2 process budget hooks report the helper roots and the live agent processes`() {
        assertEquals(listOf(ProcessOwner.Paseo, ProcessOwner.AgentSignIn), ports.rootOwners)
        ports.guestScript = { "exec sleep 10" }
        val ticket = assertNotNull(processes.captureAgentHostStart("qa", idleResume = false, generation = null))
        val helper = processes.withAgentHostStart(ticket) { start(args = listOf("paseo")) }
        processes.trackPrivateAgentService("agent-host.qa", helper, 4099)
        val signIn = processes.startSignInProcess("qa", listOf("claude"), foreground = false).also { started += it }
        assertEquals(2, processes.liveAgentProcesses())
        assertTrue(processes.anyAgentProcessAlive())
        assertEquals(setOf(checkNotNull(GuestProc.pid(helper))), processes.helperRootPids())
        assertEquals(setOf(checkNotNull(GuestProc.pid(helper))), ports.rootPids.getValue(ProcessOwner.Paseo)().toSet())
        assertEquals(setOf(checkNotNull(GuestProc.pid(signIn))), ports.rootPids.getValue(ProcessOwner.AgentSignIn)().toSet())
        processes.stopAgentProcess(signIn)
        processes.stopAgentProcess(helper)
        assertEquals(0, processes.liveAgentProcesses())
        assertFalse(processes.anyAgentProcessAlive())
    }

    @Test fun `B-LINUX-115 the private auth probe launches with private output under the profile`() {
        processes.agentAuthProbe(mapOf("profileId" to "qa"))
        assertEquals(listOf<Map<*, *>>(mapOf("profileId" to "qa")), ports.authRuns)
        ports.guestScript = { "exit 0" }
        processes.authLaunch("qa", listOf("python3", "-")).also { started += it }
        assertEquals(ProcessOwner.Paseo, ports.launched.single().first)
        assertTrue(processes.authOtherOwnerSnapshot().isEmpty()) // private output is never another owner
    }

    @Test fun `B-AGENT-050 every app launch of Claude shares the Terminal sign-in folder`() {
        val agentHome = File(dir, "linux/ubuntu/home/oc")
        val link = File(agentHome, ".claude").toPath()
        start()
        assertTrue(Files.isSymbolicLink(link))
        assertEquals(".oc-profiles/qa/claude", Files.readSymbolicLink(link).toString())
        processes.agentSignInCommand("other", listOf("claude", "auth", "login"))
        assertEquals(".oc-profiles/other/claude", Files.readSymbolicLink(link).toString())
        processes.deleteAgentHome("other")
        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }
}
