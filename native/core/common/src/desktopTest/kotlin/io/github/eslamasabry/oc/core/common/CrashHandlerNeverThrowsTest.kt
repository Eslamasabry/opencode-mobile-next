package io.github.eslamasabry.oc.core.common

import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** B-STATE-073: a crash while logging must not close the app (the handler itself may never throw out). */
class CrashHandlerNeverThrowsTest {
    @Volatile private var armed = true

    private val sink: RedactingLogSink =
        object : RedactingLogSink {
            override fun write(entry: LogEntry) {
                if (armed) throw OutOfMemoryError("test")
            }
        }

    @Test
    fun `B-STATE-073 a failing log inside the coroutine handler never reaches the thread`() {
        AppLog.install(sink, Severity.Debug)
        val recorded = Collections.synchronizedList(mutableListOf<Throwable>())
        val recorder = Thread.UncaughtExceptionHandler { _, e -> recorded.add(e) }
        val previousDefault = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(recorder)
        var flag = false
        var scope: CoroutineScope? = null
        try {
            val worker =
                Thread {
                    val inner = CoroutineScope(supervisedContext(null, Dispatchers.Unconfined, "b-state-073"))
                    scope = inner
                    inner.launch { throw IllegalStateException("child") }
                    inner.launch { flag = true }
                }
            worker.uncaughtExceptionHandler = recorder
            worker.start()
            worker.join()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousDefault)
            armed = false
        }
        assertTrue(recorded.isEmpty(), "the failure reached the thread's handler: $recorded")
        assertTrue(scope!!.coroutineContext[Job]!!.isActive, "the scope died with the log failure")
        assertTrue(flag, "a later launch never ran after the log failure")
    }
}
