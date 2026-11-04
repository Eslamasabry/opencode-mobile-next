package io.github.eslamasabry.oc.engine.agents.runtime

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** Port of test/local_agent_runtime_test.dart: the script structure, LocalAgentStatus and LocalAgentRuntime groups. */
class LocalAgentRuntimeTest {
    private val script = LocalAgentsScript.SCRIPT

    private fun runtimeFor(
        opened: MutableList<String>? = null,
        answer: suspend (verb: String, script: String) -> Outcome<String>,
    ) = LocalAgentRuntime(
        runner = { script, _ ->
            val inline = Regex("exec bash \"\\\$CLAUDE\" ([a-z-]+)").find(script)?.groupValues?.get(1)
            val detached = Regex("nohup bash \"\\\$CLAUDE\" '([a-z]+)'").find(script)?.groupValues?.get(1)
            answer(inline ?: detached ?: "log-tail", script)
        },
        terminalOpener = { command ->
            opened?.add(command)
            true.ok()
        },
        timeSource = TestTimeSource(),
        pollInterval = 5.milliseconds,
    )

    private fun <T> LocalAgentResult<T>.value(): T = assertIs<LocalAgentResult.Done<T>>(this).value

    private fun LocalAgentResult<*>.failure(): LocalAgentFailure = assertIs<LocalAgentResult.Failed>(this).failure

    // ---- the embedded script: structure

    @Test
    fun `B-AGENT-121 packages go to the app prefix with Paseo pinned exactly`() {
        assertTrue("npm install -g --no-fund --no-audit --prefix \"\$1\"" in script)
        assertTrue("npm_install \"@getpaseo/cli@\$paseo_version\"" in script)
        assertTrue("npm_install \"@anthropic-ai/claude-code@\$claude_version\"" in script)
        assertFalse("npm_install '@anthropic-ai/claude-code'" in script)
    }

    @Test
    fun `B-AGENT-121 remove keeps the Claude sign-in unless --forget-signin`() {
        val remove = script.substring(script.indexOf("remove_verb() {"), script.indexOf("# sign-in"))
        val forget = remove.indexOf("if [ \"\$forget\" = 1 ]; then")
        assertTrue(forget > 0)
        assertTrue(remove.indexOf(".claude") > forget)
        assertTrue("kept \$UBUNTU_HOME/.claude" in remove)
    }

    @Test
    fun `B-AGENT-122 the dispatch rewrites script and pins, and detaches long verbs`() {
        val install = LocalAgentsScript.verbScript("install")
        assertTrue("bash \"\$CLAUDE\" queue 'install'" in install)
        assertTrue("nohup bash \"\$CLAUDE\" 'install'" in install)
        assertTrue("claude-started:" in install)
        assertTrue("node_version=v" in install)
        val status = LocalAgentsScript.verbScript("status")
        assertTrue("exec bash \"\$CLAUDE\" status" in status)
        assertFailsWith<IllegalArgumentException> { LocalAgentsScript.verbScript("signin") }
        assertFailsWith<IllegalArgumentException> { LocalAgentsScript.verbScript("run-daemon") }
        assertTrue(
            "'/root/it'\"'\"'s'" in LocalAgentsScript.verbScript("ensure-project", listOf("/root/it's")),
        )
    }

    // ---- LocalAgentStatus

    @Test
    fun `B-AGENT-127 parses every phase`() {
        val phases = mapOf(
            "absent" to LocalAgentPhase.Absent,
            "needs_ubuntu" to LocalAgentPhase.NeedsUbuntu,
            "installing" to LocalAgentPhase.Installing,
            "installed" to LocalAgentPhase.Installed,
            "starting" to LocalAgentPhase.Starting,
            "ready" to LocalAgentPhase.Ready,
            "stopping" to LocalAgentPhase.Stopping,
            "removing" to LocalAgentPhase.Removing,
            "failed" to LocalAgentPhase.Failed,
            "something-new" to LocalAgentPhase.Unknown,
        )
        for ((raw, phase) in phases) assertEquals(phase, LocalAgentStatus.parse("phase=$raw\n").phase, raw)
        assertEquals(LocalAgentPhase.Unknown, LocalAgentStatus.parse("garbage").phase)
    }

    @Test
    fun `B-AGENT-127 parses a full answer`() {
        val status = LocalAgentStatus.parse(
            "phase=ready\nmessage=Claude Code is running on this phone\n" +
                "busy=no\nverb=start\nstep=\ninstalled=yes\nnode_version=v24.21.0\n" +
                "paseo_version=0.8.0\nclaude_version=2.1.278\npid=4242\nport=6767\n" +
                "signed_in=yes\nfailure_kind=\nkilled=no\nupdated_at=1789000000\n",
        )
        assertTrue(status.isReady)
        assertTrue(status.installed)
        assertEquals("v24.21.0", status.nodeVersion)
        assertEquals("0.8.0", status.paseoVersion)
        assertEquals("2.1.278", status.claudeVersion)
        assertEquals(4242, status.pid)
        assertEquals(6767, status.port)
        assertEquals(LocalAgentSignIn.Yes, status.signedIn)
        assertNull(status.failureKind)
        assertNull(status.failure)
        assertEquals(1789000000000L, status.updatedAtMillis)
    }

    @Test
    fun `B-AGENT-127 maps every failure kind the script writes`() {
        val kinds = mapOf(
            "needs_ubuntu" to LocalAgentFailureKind.NeedsUbuntu,
            "no_space" to LocalAgentFailureKind.NoSpace,
            "download" to LocalAgentFailureKind.Download,
            "checksum" to LocalAgentFailureKind.Checksum,
            "native_build" to LocalAgentFailureKind.NativeBuild,
            "port_in_use" to LocalAgentFailureKind.PortInUse,
            "timeout" to LocalAgentFailureKind.Timeout,
            "not_installed" to LocalAgentFailureKind.NotInstalled,
            "unsupported_arch" to LocalAgentFailureKind.UnsupportedArch,
            "interrupted" to LocalAgentFailureKind.Interrupted,
            "daemon_exited" to LocalAgentFailureKind.Daemon,
            "unhealthy" to LocalAgentFailureKind.Daemon,
            "npm" to LocalAgentFailureKind.Packages,
            "pins" to LocalAgentFailureKind.Other,
            "error" to LocalAgentFailureKind.Other,
            "" to LocalAgentFailureKind.Other,
        )
        for ((raw, kind) in kinds) {
            val status = LocalAgentStatus.parse("phase=failed\nmessage=why\nfailure_kind=$raw\n")
            assertEquals(kind, status.failureKind, raw)
            assertEquals("why", status.failure!!.scriptMessage)
        }
        // Every kind the script can write is one this table knows.
        val written = Regex("fail ([a-z_]+) ").findAll(script).map { it.groupValues[1] }.toSet()
        assertTrue((written - kinds.keys).isEmpty(), "${written - kinds.keys}")
        // A failure kind on a phase that is not failed is ignored.
        assertNull(LocalAgentStatus.parse("phase=installed\nfailure_kind=timeout\n").failureKind)
    }

    // ---- LocalAgentRuntime

    @Test
    fun `B-AGENT-128 a verb dispatches, polls until idle and answers the last`() = runTest {
        var polls = 0
        val verbs = mutableListOf<String>()
        val runtime = runtimeFor { verb, _ ->
            verbs.add(verb)
            if (verb != "status") return@runtimeFor "claude-queued:$verb\nclaude-started:9\n".ok()
            polls++
            (if (polls < 3) "phase=installing\nbusy=yes\nstep=paseo\n" else "phase=installed\nbusy=no\ninstalled=yes\n").ok()
        }
        val status = runtime.install().value()
        assertEquals(LocalAgentPhase.Installed, status.phase)
        assertEquals(3, polls)
        assertEquals("install", verbs.first())

        polls = 0
        val seen = runtime.statusStream().toList().map { it.value().phase }
        assertEquals(listOf(LocalAgentPhase.Installing, LocalAgentPhase.Installing, LocalAgentPhase.Installed), seen)
    }

    @Test
    fun `B-AGENT-128 a failed verb answers its typed failure`() = runTest {
        val runtime = runtimeFor { verb, _ ->
            (
                if (verb == "status") {
                    "phase=failed\nbusy=no\nfailure_kind=port_in_use\nmessage=Port 6767 on this phone is already used\n"
                } else {
                    "claude-started:9\n"
                }
                ).ok()
        }
        val failure = runtime.start().failure()
        assertEquals(LocalAgentFailureKind.PortInUse, failure.kind)
        assertTrue("6767" in failure.scriptMessage)
    }

    @Test
    fun `B-AGENT-128 a bridge error and a refused dispatch are bridge failures`() = runTest {
        var runtime = runtimeFor { _, _ -> Failure.TimedOut().failed() }
        assertEquals(LocalAgentFailureKind.Bridge, runtime.status().failure().kind)
        runtime = runtimeFor { _, _ -> "claude-busy:install:7\n".ok() }
        assertEquals(LocalAgentFailureKind.Bridge, runtime.restart().failure().kind)
        assertTrue(runtime.logTail().isNotEmpty())
    }

    @Test
    fun `B-AGENT-128 remove passes --forget-signin only when asked`() = runTest {
        val scripts = mutableListOf<String>()
        val runtime = runtimeFor { verb, script ->
            scripts.add(script)
            (if (verb == "status") "phase=absent\nbusy=no\n" else "claude-started:9\n").ok()
        }
        runtime.remove().value()
        assertTrue("nohup bash \"\$CLAUDE\" 'remove'  >" in scripts.first())
        scripts.clear()
        runtime.remove(forgetSignIn = true).value()
        assertTrue("'remove' '--forget-signin'" in scripts.first())
    }

    @Test
    fun `B-AGENT-128 password accepts only the script's own format`() = runTest {
        val good = "ab".repeat(24)
        var runtime = runtimeFor { _, _ -> "$good\n".ok() }
        assertEquals(good, runtime.password().value())
        runtime = runtimeFor { _, _ -> "usage: claude.sh\n".ok() }
        assertEquals(LocalAgentFailureKind.PasswordMissing, runtime.password().failure().kind)
    }

    @Test
    fun `B-AGENT-128 sign-in opens a visible terminal on the script and nothing else`() = runTest {
        val opened = mutableListOf<String>()
        val verbs = mutableListOf<String>()
        val runtime = runtimeFor(opened) { verb, _ ->
            verbs.add(verb)
            "signed_in=no\n".ok()
        }
        assertTrue(runtime.openSignIn().value())
        // The script is written to disk before the terminal names it.
        assertEquals(listOf("signin-status"), verbs)
        assertEquals(listOf("bash \"\$HOME/.oc/claude.sh\" signin"), opened)
        assertEquals(LocalAgentSignIn.No, runtime.signInStatus().value())
    }

    @Test
    fun `B-AGENT-126 projects reads paths as Ubuntu sees them`() = runTest {
        val runtime = runtimeFor { verb, _ ->
            (if (verb == "projects") "project=/root/projects/a\nnoise\nproject=/root/projects/b\n" else "project=/root/projects/c\n").ok()
        }
        assertEquals(listOf("/root/projects/a", "/root/projects/b"), runtime.projects().value())
        assertEquals("/root/projects/c", runtime.ensureProject("/root/projects/c").value())
        assertEquals(LocalAgentFailureKind.ProjectNotCreated, runtime.ensureProject("/root/projects/d").failure().kind)
    }
}
