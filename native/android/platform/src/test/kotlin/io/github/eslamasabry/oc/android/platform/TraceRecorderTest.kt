package io.github.eslamasabry.oc.android.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** S2-FIELD trace switch and kept traces (plan 13 §3.6, P13-D3, WP-13.4). Names carry the work package. */
class TraceRecorderTest {
    private val root: File = Files.createTempDirectory("trace-recorder").toFile()
    private val dir = File(root, "profiling")

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private class FakeSystem(override val supported: Boolean, override val traceDir: File?, var accept: Boolean = true) : TraceSystem {
        val calls = mutableListOf<Boolean>()

        override fun setTriggers(enabled: Boolean): Boolean {
            calls += enabled
            return accept
        }
    }

    private fun trace(name: String, at: Long, bytes: Int = 10): File =
        File(dir, name).also { dir.mkdirs(); it.writeBytes(ByteArray(bytes)); it.setLastModified(at) }

    @Test
    fun `WP-13_4 FIELD the recorder is off until the person turns it on`() = runTest {
        val system = FakeSystem(supported = true, traceDir = dir)
        val recorder = TraceRecorder(system, StandardTestDispatcher(testScheduler))
        assertEquals(TraceState(supported = true, enabled = false, latest = null), recorder.state.value)
        assertTrue(system.calls.isEmpty(), "nothing is registered with the system by default")
        assertTrue(recorder.setEnabled(true).enabled)
        assertFalse(recorder.setEnabled(false).enabled)
        assertEquals(listOf(true, false), system.calls)
    }

    @Test
    fun `WP-13_4 FIELD a refused registration leaves the switch off`() = runTest {
        val system = FakeSystem(supported = true, traceDir = dir, accept = false)
        val recorder = TraceRecorder(system, StandardTestDispatcher(testScheduler))
        assertFalse(recorder.setEnabled(true).enabled)
    }

    @Test
    fun `WP-13_4 FIELD an older Android has no switch and touches nothing`() = runTest {
        trace("old.perfetto-trace", 1_000)
        val system = FakeSystem(supported = false, traceDir = dir)
        val recorder = TraceRecorder(system, StandardTestDispatcher(testScheduler))
        assertEquals(TraceState.Off, recorder.state.value)
        assertEquals(TraceState.Off, recorder.setEnabled(true))
        assertEquals(TraceState.Off, recorder.refresh())
        assertTrue(system.calls.isEmpty())
        assertTrue(File(dir, "old.perfetto-trace").exists())
    }

    @Test
    fun `WP-13_4 FIELD only the newest kept traces stay on the phone`() = runTest {
        trace("a", 1_000)
        trace("b", 3_000, bytes = 42)
        trace("c", 2_000)
        trace("empty", 4_000, bytes = 0) // a half-written file is not offered
        val recorder = TraceRecorder(FakeSystem(supported = true, traceDir = dir), StandardTestDispatcher(testScheduler))
        val latest = recorder.refresh().latest!!
        assertEquals(File(dir, "b").path, latest.path)
        assertEquals(42, latest.sizeBytes)
        assertEquals(3_000, latest.atEpochMs)
        assertFalse(File(dir, "a").exists(), "older than the newest ${TraceRecorder.MAX_KEPT}")
        assertTrue(File(dir, "c").exists())
    }

    @Test
    fun `WP-13_4 FIELD a symlink in the trace folder is never offered or followed`() = runTest {
        val outside = File(root, "secret").apply { writeText("not a trace") }
        dir.mkdirs()
        Files.createSymbolicLink(File(dir, "link").toPath(), outside.toPath())
        val recorder = TraceRecorder(FakeSystem(supported = true, traceDir = dir), StandardTestDispatcher(testScheduler))
        assertNull(recorder.refresh().latest)
        recorder.deleteAll()
        assertTrue(outside.exists(), "the link target is not deleted")
    }

    @Test
    fun `WP-13_4 FIELD delete removes kept traces and keeps the switch`() = runTest {
        trace("a", 1_000)
        val recorder = TraceRecorder(FakeSystem(supported = true, traceDir = dir), StandardTestDispatcher(testScheduler))
        recorder.setEnabled(true)
        recorder.refresh()
        val after = recorder.deleteAll()
        assertNull(after.latest)
        assertTrue(after.enabled)
        assertFalse(File(dir, "a").exists())
        assertNull(recorder.refresh().latest)
    }

    @Test
    fun `WP-13_4 FIELD no trace folder yet is simply no trace`() = runTest {
        val recorder = TraceRecorder(FakeSystem(supported = true, traceDir = File(root, "missing")), StandardTestDispatcher(testScheduler))
        assertNull(recorder.refresh().latest)
    }
}
