package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayOutputStream

/** Bounded ephemeral stdout; stderr is redirected to /dev/null by the launcher. */
internal class PhoneAgentAuthCapture(private val process: Process) {
    private companion object {
        const val READ_BYTES = 4096
        const val MAX_OUTPUT_BYTES = 65536
        const val READER_JOIN_MILLIS = 200L
    }
    private val output = ByteArrayOutputStream()
    @Volatile private var invalid = false
    @Volatile private var overflow = false
    private val reader = Thread({ read() }, "private-agent-projection").apply { isDaemon = true }

    fun start() { reader.start() }

    private fun read() {
        try {
            process.inputStream.use { input ->
                val buffer = ByteArray(READ_BYTES)
                var count = input.read(buffer)
                while (count >= 0) {
                    synchronized(output) {
                        if (output.size() + count > MAX_OUTPUT_BYTES) { invalid = true; overflow = true }
                        if (!invalid) output.write(buffer, 0, count)
                    }
                    count = input.read(buffer)
                }
            }
        } catch (_: Exception) { invalid = true }
    }

    fun result(exitCode: Int): Map<String, Any?> {
        reader.join(READER_JOIN_MILLIS)
        if (reader.isAlive || invalid) return PhoneAgentAuthProjection.error("invalidResponse")
        return synchronized(output) { PhoneAgentAuthProjection.parse(output.toByteArray(), exitCode) }
    }

    fun overflowed(): Boolean = overflow

    fun close() {
        try { process.inputStream.close() } catch (_: Exception) { /* Already closed. */ }
        try { process.errorStream.close() } catch (_: Exception) { /* Already closed. */ }
        try { process.outputStream.close() } catch (_: Exception) { /* Already closed. */ }
        try { reader.join(READER_JOIN_MILLIS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        synchronized(output) { output.reset() }
    }
}
