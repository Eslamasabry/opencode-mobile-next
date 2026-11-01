package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.app.Application
import android.os.Looper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** S2-FIELD Android bindings over Robolectric's real activity lifecycle (plan 13 §3.6: everything off while hidden). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FieldFrameStatsRobolectricTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `WP-13_4 FIELD the frame listener is attached only while an activity is resumed`() {
        val stats = FieldFrameStats(app)
        stats.start()
        val controller = Robolectric.buildActivity(Activity::class.java).setup() // created, started, resumed
        assertEquals(1, stats.attachedWindows)
        controller.pause()
        assertEquals(0, stats.attachedWindows, "paused: off")
        controller.resume()
        assertEquals(1, stats.attachedWindows)
        controller.pause().stop()
        assertEquals(0, stats.attachedWindows, "hidden: off")
        controller.start().resume()
        stats.close()
        assertEquals(0, stats.attachedWindows, "closed: off even while resumed")
        stats.close() // repeats safely
        controller.pause().resume()
        assertEquals(0, stats.attachedWindows, "closed recorder ignores later resumes")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `WP-13_4 FIELD nothing is attached before start, and an already resumed activity can be attached`() {
        val stats = FieldFrameStats(app)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        stats.attach(controller.get())
        assertEquals(0, stats.attachedWindows, "not started")
        stats.start()
        stats.attach(controller.get())
        stats.attach(controller.get())
        assertEquals(1, stats.attachedWindows, "one listener per window")
        controller.pause()
        assertEquals(0, stats.attachedWindows)
        assertEquals(FrameSummary.Empty, stats.summary())
        stats.close()
    }

    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000L)
    }

    @Test
    fun `WP-13_4 FIELD Android 15 reads the system start history without throwing`() = runTest {
        val source = AndroidStartInfoSource(app)
        assertTrue(source.supported)
        val history = StartupHistory(source, clock, StandardTestDispatcher(testScheduler))
        history.start()
        assertTrue(history.refresh().supported)
        history.close()
    }

    @Test
    @Config(sdk = [26])
    fun `WP-13_4 FIELD below Android 15 there is no start history`() = runTest {
        val source = AndroidStartInfoSource(app)
        assertFalse(source.supported)
        assertTrue(source.history(8).isEmpty())
        assertEquals(StartupSummary.Unsupported, StartupHistory(source, clock, StandardTestDispatcher(testScheduler)).refresh())
    }

    @Test
    fun `WP-13_4 FIELD below Android 16 there is no trace switch`() = runTest {
        val system = AndroidTraceSystem(app)
        assertFalse(system.supported)
        assertNull(system.traceDir)
        assertFalse(system.setTriggers(true))
        assertEquals(TraceState.Off, TraceRecorder(system, StandardTestDispatcher(testScheduler)).state.value)
    }
}
