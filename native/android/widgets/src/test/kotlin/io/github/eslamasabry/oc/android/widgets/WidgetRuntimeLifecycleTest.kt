package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetRuntimeLifecycleTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After fun reset() = WidgetFeed.reset()

    private fun runner(start: ((Map<*, *>?) -> Unit) -> WidgetBackgroundRunner.Handle) {
        WidgetFeed.backgroundRunner =
            object : WidgetBackgroundRunner {
                override fun start(
                    context: Context,
                    payload: Map<String, Any?>,
                    snapshotJson: String,
                    deadlineEpochMs: Long,
                    operation: String,
                    complete: (Map<*, *>?) -> Unit,
                ) = start(complete)
            }
    }

    @Test
    fun `widgets-MAJOR-4 inline completion cancels the returned handle`() {
        val cancels = AtomicInteger()
        lateinit var runtime: WidgetActionRuntime
        runner { complete ->
            complete(emptyMap<String, Any>())
            WidgetBackgroundRunner.Handle { cancels.incrementAndGet() }
        }
        runtime = WidgetActionRuntime(context, emptyMap(), JSONObject(), 0L) { runtime.destroy() }
        runtime.start()
        assertEquals(1, cancels.get())
    }

    @Test
    fun `widgets-MAJOR-4 destruction during startup cancels the returned handle`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancels = AtomicInteger()
        runner { _ ->
            entered.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            WidgetBackgroundRunner.Handle { cancels.incrementAndGet() }
        }
        val runtime =
            WidgetActionRuntime(context, emptyMap(), JSONObject(), 0L) { fail("late completion") }
        val thread = Thread { runtime.start() }.apply { start() }
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        runtime.destroy()
        release.countDown()
        thread.join(5000)
        assertFalse(thread.isAlive)
        assertEquals(1, cancels.get())
    }

    @Test
    fun `widgets-MAJOR-4 concurrent timeout and completion settle once and cancel once`() {
        val cancels = AtomicInteger()
        val completions = AtomicInteger()
        lateinit var callback: (Map<*, *>?) -> Unit
        runner { complete ->
            callback = complete
            WidgetBackgroundRunner.Handle { cancels.incrementAndGet() }
        }
        val runtime =
            WidgetActionRuntime(context, emptyMap(), JSONObject(), 0L) {
                completions.incrementAndGet()
            }
        runtime.start()
        val go = CountDownLatch(1)
        val threads =
            List(8) { i ->
                Thread {
                    go.await()
                    if (i % 2 == 0) callback(null) else runtime.destroy()
                }
                    .apply { start() }
            }
        go.countDown()
        threads.forEach {
            it.join(5000)
            assertFalse(it.isAlive)
        }
        assertTrue(completions.get() <= 1)
        assertEquals(1, cancels.get())
    }
}
