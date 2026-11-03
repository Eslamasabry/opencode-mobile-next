package io.github.eslamasabry.oc.engine.runtime.install

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Port of test/git/builtin_git_secret_drop_test.dart "the shell returns the run output and maps bridge failures". */
class BuiltinShellTest {
    private class FakeLinux(var answer: LinuxBridgeAnswer) : BuiltinLinuxPort {
        val runs = mutableListOf<Pair<String, Duration>>()

        override suspend fun run(script: String, timeout: Duration): LinuxBridgeAnswer {
            runs += script to timeout
            return answer
        }
    }

    @Test
    fun `B-LINUX-071 the shell returns the run output`() = runTest {
        val linux = FakeLinux(LinuxBridgeAnswer.Ran(exitCode = 0, output = "out"))
        val shell = BuiltinShell(linux)
        assertEquals(ShellAnswer.Output("out"), shell.run("echo", timeout = 1.seconds))
        assertEquals(listOf("echo" to 1.seconds), linux.runs, "the script and its timeout reach the bridge unchanged")
    }

    @Test
    fun `B-LINUX-071 a non-zero run still returns its output, like Flutter`() = runTest {
        val shell = BuiltinShell(FakeLinux(LinuxBridgeAnswer.Ran(exitCode = 3, output = "fatal: no repo")))
        assertEquals(ShellAnswer.Output("fatal: no repo"), shell.run("git status", 5.seconds))
    }

    @OptIn(RedactorApi::class)
    @Test
    fun `B-LINUX-071 bridge failures map to timed out or unavailable, details kept apart`() = runTest {
        val details = Redacted("proot exited")
        val linux = FakeLinux(LinuxBridgeAnswer.BridgeFailed(code = "timeout", details = details))
        val shell = BuiltinShell(linux)
        assertEquals(ShellAnswer.Failed(ShellFailure.TimedOut, details), shell.run("x", 1.seconds))
        linux.answer = LinuxBridgeAnswer.BridgeFailed(code = "not_installed", details = details)
        assertEquals(ShellAnswer.Failed(ShellFailure.Unavailable, details), shell.run("x", 1.seconds))
        linux.answer = LinuxBridgeAnswer.BridgeFailed(code = null)
        assertEquals(ShellAnswer.Failed(ShellFailure.Unavailable), shell.run("x", 1.seconds))
        linux.answer = LinuxBridgeAnswer.TimedOut
        assertEquals(ShellAnswer.Failed(ShellFailure.TimedOut), shell.run("x", 1.seconds))
        linux.answer = LinuxBridgeAnswer.Crashed
        assertEquals(ShellAnswer.Failed(ShellFailure.Unavailable), shell.run("x", 1.seconds))
    }
}
