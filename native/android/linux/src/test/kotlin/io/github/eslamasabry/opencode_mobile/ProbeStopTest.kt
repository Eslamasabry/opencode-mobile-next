package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A person's step ends a read's sign-in probe instead of waiting up to its 65 s limit (w4-probes). The stopped probe's
 * process is really ended, a stopped read starts no further probe, and the profile's next probe still runs: a process
 * drained from another thread is not left "retired" (which would refuse every later probe of that profile).
 */
class ProbeStopTest {
    /** A probe child: [answer] is its output once it ends by itself; a [blocking] one runs until it is ended. */
    private class Child(private val answer: String, private val blocking: Boolean) : Process() {
        val exit = CountDownLatch(if (blocking) 1 else 0)
        val started = CountDownLatch(1)
        @Volatile var ended = false

        fun end() {
            ended = true
            exit.countDown()
        }

        override fun getInputStream(): InputStream {
            started.countDown()
            if (!blocking) return ByteArrayInputStream(answer.toByteArray())
            return object : InputStream() {
                override fun read(): Int {
                    exit.await()
                    return -1
                }
            }
        }

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun waitFor(): Int {
            exit.await()
            return if (ended) 137 else 0
        }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exit.await(timeout, unit)
        override fun exitValue(): Int =
            if (exit.count == 0L) (if (ended) 137 else 0) else throw IllegalThreadStateException()
        override fun isAlive(): Boolean = exit.count > 0L
        override fun destroy() = end()
    }

    private val script =
        checkNotNull(javaClass.getResource("/auth-scripts/codex-probe.txt")).readText()

    private fun request() =
        mapOf(
            "profileId" to "synthetic-profile",
            "agentId" to "codex",
            "action" to "probe",
            "script" to script,
            "timeoutSeconds" to 10L,
        )

    private val children = ArrayDeque<Child>()
    private var starts = 0

    /** Whether a drain confirms the process tree gone; false is a tree that would not die (it stays "retired"). */
    @Volatile private var drains = true
    @Volatile private var drainCalls = 0
    private val probe =
        PhoneAgentAuthProbe(
            start = { _, _ ->
                starts++
                children.removeFirst()
            },
            stop = { process ->
                drainCalls++
                (process as Child).end()
                drains
            },
            waitFor = { process, seconds -> process.waitFor(seconds, TimeUnit.SECONDS) },
        )

    @Test
    fun `a stopped probe ends its process at once and the next probe of that profile still runs`() {
        val slow = Child("", blocking = true)
        children += slow
        children += Child("{\"state\":\"signedIn\"}\n", blocking = false)
        val stop = ProbeStop()
        var answer: Map<String, Any?>? = null
        val done = CountDownLatch(1)
        Thread {
                answer = probe.run(request(), stop)
                done.countDown()
            }
            .start()
        assertTrue("the probe started", slow.started.await(5, TimeUnit.SECONDS))

        val began = System.nanoTime()
        stop.stop()
        assertTrue("the stopped probe's process was ended", slow.ended)
        assertTrue("the probe answered at once, not after its 10 s limit", done.await(5, TimeUnit.SECONDS))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) < 5_000)
        // A stopped probe's answer is not news about the account.
        assertEquals(mapOf("state" to "error", "error" to "hostUnavailable"), answer)

        // The next read of the same profile is not refused by a process left behind as "retired".
        assertEquals("signedIn", probe.run(request())["state"])
        assertEquals(2, starts)
    }

    @Test
    fun `a read stopped before its next probe starts no process`() {
        val stop = ProbeStop()
        stop.stop()
        children += Child("{\"state\":\"signedIn\"}\n", blocking = false)

        assertEquals(mapOf("state" to "error", "error" to "hostUnavailable"), probe.run(request(), stop))
        assertEquals(0, starts)
    }

    @Test
    fun `a probe that ends by itself is not touched by a later stop`() {
        val stop = ProbeStop()
        children += Child("{\"state\":\"signedOut\"}\n", blocking = false)

        assertEquals("signedOut", probe.run(request(), stop)["state"])
        stop.stop()
        assertEquals(1, starts)
    }

    @Test
    fun `a process the stop could not drain stays retired: the profile's next probe is refused until it is gone`() {
        val slow = Child("", blocking = true)
        children += slow
        children += Child("{\"state\":\"signedIn\"}\n", blocking = false)
        drains = false
        val stop = ProbeStop()
        var answer: Map<String, Any?>? = null
        val done = CountDownLatch(1)
        Thread {
                answer = probe.run(request(), stop)
                done.countDown()
            }
            .start()
        assertTrue(slow.started.await(5, TimeUnit.SECONDS))

        stop.stop()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(mapOf("state" to "error", "error" to "hostUnavailable"), answer)
        // The stopping thread tried, then the probe's own thread tried again: neither may forget the process.
        assertEquals(2, drainCalls)

        // Not drained: the next probe of that profile tries the drain again and, still failing, starts nothing.
        assertEquals(mapOf("state" to "error", "error" to "hostUnavailable"), probe.run(request()))
        assertEquals(1, starts)

        // Once the tree is really gone the profile probes again.
        drains = true
        assertEquals("signedIn", probe.run(request())["state"])
        assertEquals(2, starts)
    }

    @Test
    fun `a check's own cleanup waits for a stop that is ending its process right now`() {
        val stop = ProbeStop()
        val ending = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ended = CountDownLatch(1)
        assertTrue(stop.attach {
            ending.countDown()
            release.await(5, TimeUnit.SECONDS)
            ended.countDown()
        })
        Thread { stop.stop() }.start()
        assertTrue("the stop is ending the process", ending.await(5, TimeUnit.SECONDS))

        // The check's thread is done with its process and is about to free its slot: it must not do so mid-stop.
        val detached = CountDownLatch(1)
        Thread {
                stop.detach()
                detached.countDown()
            }
            .start()
        assertTrue("detach waited for the stop", !detached.await(300, TimeUnit.MILLISECONDS))
        release.countDown()
        assertTrue(detached.await(5, TimeUnit.SECONDS))
        assertEquals("the stop had finished before detach returned", 0L, ended.count)
        assertTrue(stop.isStopped)
        // Nothing attaches after a stop: a later check is ended at once by its own thread.
        assertTrue(!stop.attach {})
    }

    @Test
    fun `detach with no stop running returns at once`() {
        val stop = ProbeStop()
        assertTrue(stop.attach {})
        stop.detach()
        stop.stop()
        assertTrue(stop.isStopped)
    }
}
