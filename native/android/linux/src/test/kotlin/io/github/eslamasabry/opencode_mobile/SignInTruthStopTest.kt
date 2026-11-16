package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * signin-truth (owner, two phones: "OAuth session expired and could not be refreshed" hours after signing in): the app
 * must not be able to break a sign-in. A sign-in check's stop sent SIGSTOP and SIGKILL at once, so a Claude Code CLI in
 * the middle of refreshing its sign-in could lose the new token; the refresh lock was removed at a helper start while a
 * CLI the app does not track could hold it; and a check could run beside a conversation's CLI on the same sign-in.
 */
class SignInTruthStopTest {
    private object Stub : Process() {
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun waitFor(): Int = 0
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = true
        override fun exitValue(): Int = 0
        override fun isAlive(): Boolean = false
        override fun destroy() = Unit
    }

    /** A process table: the probe's sh (100) runs python (101) runs claude (102); 500 is a Terminal's own claude. */
    private class Table(val politeOnTerm: Set<Int>) {
        var now = 0L
        val alive = linkedMapOf(500 to 1, 100 to 1, 101 to 100, 102 to 101)
        val signals = mutableListOf<Pair<Int, Int>>()
        val killedAt = mutableMapOf<Int, Long>()

        fun inventory() = alive.map { (pid, parent) -> PhoneAgentAuthProcessIdentity(pid, parent, "t$pid", 'S') }

        fun signal(pid: Int, signal: Int) {
            signals += pid to signal
            when (signal) {
                TERM -> if (pid in politeOnTerm) alive.remove(pid)
                KILL -> {
                    alive.remove(pid)
                    killedAt[pid] = now
                }
            }
        }
    }

    private fun tree(table: Table) =
        PhoneAgentAuthProcessTree(
            pid = { 100 },
            inventory = table::inventory,
            signal = table::signal,
            nano = { table.now },
            pause = { table.now += TimeUnit.MILLISECONDS.toNanos(it) },
        ).also { assertTrue(it.track(Stub, before = setOf(500 to "t500"))) }

    @Test
    fun `I147-TRUTH-20 a stopped sign-in check asks the CLI politely first, and never forces one that ended`() {
        val table = Table(politeOnTerm = setOf(100, 101, 102))
        assertTrue(tree(table).stop(Stub, TimeUnit.SECONDS.toNanos(2)))
        assertEquals(listOf(102 to TERM, 101 to TERM, 100 to TERM), table.signals)
        assertTrue("nothing was stopped or killed", table.killedAt.isEmpty())
    }

    @Test
    fun `I147-TRUTH-21 a CLI that does not end by itself is forced only after the bounded wait`() {
        // The CLI ignores SIGTERM (still writing); the rest of the check ends politely.
        val table = Table(politeOnTerm = setOf(100, 101))
        assertTrue(tree(table).stop(Stub, TimeUnit.SECONDS.toNanos(2)))
        val firstTerm = table.signals.indexOf(102 to TERM)
        val kill = table.signals.indexOf(102 to KILL)
        assertTrue("SIGTERM before any force: ${table.signals}", firstTerm in 0 until kill)
        assertTrue(
            "forced only after the polite wait",
            table.killedAt.getValue(102) >= TimeUnit.MILLISECONDS.toNanos(1_500),
        )
        assertFalse("STOP never came before TERM", table.signals.take(firstTerm).any { it.second == STOP })
    }

    @Test
    fun `I147-TRUTH-22 a CLI the app did not start for this check is never signalled`() {
        val table = Table(politeOnTerm = emptySet())
        tree(table).stop(Stub, TimeUnit.SECONDS.toNanos(2))
        assertTrue("the Terminal's own claude: ${table.signals}", table.signals.none { it.first == 500 })
        assertTrue(500 in table.alive)
    }

    @Test
    fun `I147-TRUTH-23 a polite stop waits a bounded time and says whether everything ended by itself`() {
        var now = 0L
        var left = 3
        val ended = PoliteStop.settle({ left-- > 0 }, untilNanos = 1_000_000_000L, nano = { now }, pause = { now += it * 1_000_000 })
        assertTrue(ended)
        now = 0L
        val stubborn = PoliteStop.settle({ true }, untilNanos = 1_000_000_000L, nano = { now }, pause = { now += it * 1_000_000 })
        assertFalse(stubborn)
        assertTrue("bounded", now in 1_000_000_000L..1_100_000_000L)
    }

    @Test
    fun `I147-TRUTH-24 the refresh lock goes only when the cold-start census proves no CLI could hold it`() {
        val root = Files.createTempDirectory("signin-truth").toFile()
        try {
            val lock = File(root, "linux/ubuntu/home/oc/.oc-profiles/p1/claude/.oauth_refresh.lock")
            assertTrue(lock.mkdirs())
            assertFalse(PhoneAgentAuthLock.clearWhenCold(root, "p1") { false })
            assertTrue("another task runs: kept", lock.isDirectory)
            assertFalse(PhoneAgentAuthLock.clearWhenCold(root, "p1") { error("census unreadable") })
            assertTrue("an unreadable census proves nothing: kept", lock.isDirectory)
            File(lock, "held").writeText("")
            assertFalse(PhoneAgentAuthLock.clearWhenCold(root, "p1") { true })
            assertTrue("a lock with contents is never removed", lock.isDirectory)
            File(lock, "held").delete()
            assertTrue(PhoneAgentAuthLock.clearWhenCold(root, "p1") { true })
            assertFalse(lock.exists())
            // The census itself: only the app's own registered processes may run.
            assertTrue(PhoneAgentAuthColdOwner.permitsLockCleanup(setOf(10), 11) { setOf(10, 11) })
            assertFalse(PhoneAgentAuthColdOwner.permitsLockCleanup(setOf(10), 11) { setOf(10, 11, 4242) })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `I147-TRUTH-25 a conversation's Claude Code CLI is seen from its arguments, other programs are not`() {
        fun cmd(vararg argv: String) = argv.joinToString("\u0000", postfix = "\u0000").toByteArray()
        val helper = PhoneAgentCliCensus.argv(cmd("node", "/home/oc/.local/bin/paseo", "daemon", "run", "--home", "/home/oc/.oc-profiles/p1/paseo"))
        val conversation = PhoneAgentCliCensus.argv(cmd("/home/oc/.local/bin/claude", "--output-format", "stream-json", "--verbose"))
        val native = PhoneAgentCliCensus.argv(cmd("/opt/oc-claude/bin/claude-2.1.0", "-p"))
        val npm = PhoneAgentCliCensus.argv(cmd("node", "/usr/lib/node_modules/@anthropic-ai/claude-code/cli.js"))
        val signIn = PhoneAgentCliCensus.argv(cmd("/bin/sh", "-c", "exec /home/oc/.local/bin/claude auth login"))
        val config = PhoneAgentCliCensus.argv(cmd("cat", "/home/oc/.oc-profiles/p1/.claude.json"))
        assertEquals(listOf("node", "/home/oc/.local/bin/paseo", "daemon", "run", "--home", "/home/oc/.oc-profiles/p1/paseo"), helper)
        assertFalse(PhoneAgentCliCensus.claudeAmong(listOf(helper, config, null)))
        for (argv in listOf(conversation, native, npm, signIn))
            assertTrue(argv.toString(), PhoneAgentCliCensus.claudeAmong(listOf(helper, argv)))
    }

    private companion object {
        const val TERM = 15
        const val STOP = 19
        const val KILL = 9
    }
}
