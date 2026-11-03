package io.github.eslamasabry.opencode_mobile.graph

import androidx.work.ListenableWorker
import kotlin.test.Test
import kotlin.test.assertTrue

class FlutterWorkerNameTest {
    @Test
    fun `B-MIGR-013 Flutter-era scheduled work still finds a worker under its frozen class name`() {
        // WorkManager stores the worker's class name; the Flutter app armed this one.
        val worker = Class.forName("io.github.eslamasabry.opencode_mobile.ScheduledPromptWorker")
        assertTrue(ListenableWorker::class.java.isAssignableFrom(worker))
    }
}
