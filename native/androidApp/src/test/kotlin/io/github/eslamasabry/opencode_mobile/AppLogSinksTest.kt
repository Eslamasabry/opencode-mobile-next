package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.LogSink
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLogSinksTest {
    private val base = object : LogSink { override fun write(entry: LogEntry) = Unit }
    private val logcat = object : LogSink { override fun write(entry: LogEntry) = Unit }

    @Test
    fun `a release build keeps exactly its own sinks, a debuggable build adds logcat last`() {
        assertEquals(listOf(base), appLogSinks(debuggable = false, base = listOf(base), logcat = logcat))
        assertEquals(listOf(base, logcat), appLogSinks(debuggable = true, base = listOf(base), logcat = logcat))
    }
}
