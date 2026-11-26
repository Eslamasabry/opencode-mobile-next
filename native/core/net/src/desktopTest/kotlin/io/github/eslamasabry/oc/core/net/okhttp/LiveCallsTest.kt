package io.github.eslamasabry.oc.core.net.okhttp

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Closing a client cancels the calls it remembers weakly (plan 04 §3.1). The garbage collector may drop a finished call
 * at any moment, also while the close is copying the set: line run 44079402632 failed in `NetClient.close()` with
 * `NoSuchElementException` when the only remembered call was collected between the set's size and its first element.
 */
class LiveCallsTest {
    /** Answers every call at once, after [LiveCalls] has remembered it; no socket. */
    private val client: OkHttpClient =
        LiveCalls.install(OkHttpClient()).newBuilder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

    private val liveCalls: LiveCalls = client.interceptors.filterIsInstance<LiveCalls>().single()

    @Test
    fun `B-PROTO-148 closing never fails while the collector drops the only remembered call`() {
        val thrown = AtomicReference<Throwable?>(null)
        val stop = AtomicBoolean(false)
        // Closes again and again, as many clients closing at once would, while finished calls are collected.
        val closer = thread(name = "closer") {
            while (!stop.get()) {
                try {
                    liveCalls.cancelAll()
                } catch (e: Throwable) {
                    thrown.compareAndSet(null, e)
                    return@thread
                }
            }
        }
        try {
            repeat(ROUNDS) {
                if (thrown.get() == null) {
                    rememberOneCall()
                    collectGarbage()
                }
            }
        } finally {
            stop.set(true)
            closer.join(10_000)
        }
        assertNull(thrown.get(), "closing threw ${thrown.get()}")
    }

    @Test
    fun `B-617 completed and failed calls drop tracked count to zero without gc`() {
        val okCall = client.newCall(Request.Builder().url("http://127.0.0.1:9/ok").build())
        val response = okCall.execute()
        assertEquals(1, liveCalls.trackedCount(), "open response should be tracked until closed")
        response.close()
        assertEquals(0, liveCalls.trackedCount(), "closed response must drop tracked count to zero immediately")
        liveCalls.cancelAll()
        assertFalse(okCall.isCanceled(), "completed call should no longer be cancelled by cancelAll")

        val failingClient =
            LiveCalls.install(OkHttpClient()).newBuilder()
                .addInterceptor { throw IOException("simulated failure") }
                .build()
        val failingLiveCalls = failingClient.interceptors.filterIsInstance<LiveCalls>().single()
        val failedCall = failingClient.newCall(Request.Builder().url("http://127.0.0.1:9/fail").build())
        assertFailsWith<IOException> { failedCall.execute() }
        assertEquals(0, failingLiveCalls.trackedCount(), "failed call must drop tracked count to zero immediately")
        failingLiveCalls.cancelAll()
        assertFalse(failedCall.isCanceled(), "failed call should no longer be cancelled by cancelAll")
    }

    private fun LiveCalls.trackedCount(): Int {
        val field = LiveCalls::class.java.getDeclaredField("calls")
        field.isAccessible = true
        return (field.get(this) as Collection<*>).size
    }

    /** This test is about the collector clearing a weak key mid-close, so it asks for a collection on purpose. */
    @Suppress("ExplicitGarbageCollectionCall")
    private fun collectGarbage() = System.gc()

    /** One finished call that nothing else holds, so the collector may take it. */
    private fun rememberOneCall() {
        try {
            client.newCall(Request.Builder().url("http://127.0.0.1:9/").build()).execute().close()
        } catch (_: IOException) {
            // Cancelled by the closer while it ran: it was still remembered first.
        }
    }

    private companion object {
        const val ROUNDS = 300
    }
}
