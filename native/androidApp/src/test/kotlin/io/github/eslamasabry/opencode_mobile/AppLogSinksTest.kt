package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.LogSink
import io.github.eslamasabry.oc.core.common.Severity
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLogSinksTest {
    private val base = object : LogSink { override fun write(entry: LogEntry) = Unit }
    private val written = mutableListOf<String>()
    private val logcat = object : LogSink { override fun write(entry: LogEntry) { written += entry.tag } }

    private fun line(tag: String) = LogEntry(Severity.Info, tag, "a line", null)

    @Test
    fun `a debuggable build adds logcat last and writes every tag`() {
        val sinks = appLogSinks(debuggable = true, base = listOf(base), logcat = logcat, asked = { false })
        assertEquals(listOf(base, logcat), sinks)
    }

    @Test
    fun `a release build writes to logcat only the tags a tester asked for`() {
        val sinks = appLogSinks(debuggable = false, base = listOf(base), logcat = logcat, asked = { it == "Asked" })
        assertEquals(base, sinks.first())
        assertEquals(2, sinks.size)
        sinks.forEach { it.write(line("Asked")) }
        sinks.forEach { it.write(line("Other")) }
        assertEquals(listOf("Asked"), written)
    }

    @Test
    fun `a release build nobody is testing on writes nothing to logcat`() {
        val sinks = appLogSinks(debuggable = false, base = listOf(base), logcat = logcat, asked = { false })
        sinks.forEach { it.write(line("OcAutoUpdate")) }
        assertEquals(emptyList(), written)
    }
}
