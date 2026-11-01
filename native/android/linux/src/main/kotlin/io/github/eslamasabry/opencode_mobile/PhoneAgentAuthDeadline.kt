package io.github.eslamasabry.opencode_mobile

import java.util.concurrent.TimeUnit

/** One operation budget includes launch, capture and confirmed tree cleanup. */
internal class PhoneAgentAuthDeadline(seconds: Long, private val nano: () -> Long = { System.nanoTime() }) {
    private val end = nano() + TimeUnit.SECONDS.toNanos(seconds)

    fun executionMillis(): Long = TimeUnit.NANOSECONDS.toMillis(
        (end - nano() - DRAIN_NANOS - CAPTURE_NANOS).coerceAtLeast(0)
    )

    fun drainNanos(): Long = (end - nano() - CAPTURE_NANOS).coerceIn(0, DRAIN_NANOS)

    private companion object {
        val DRAIN_NANOS = TimeUnit.SECONDS.toNanos(2)
        val CAPTURE_NANOS = TimeUnit.MILLISECONDS.toNanos(400)
    }
}
