package io.github.eslamasabry.oc.quality.sec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackedSecurityFailuresTest {
    @Test
    fun `K-SEC exact known findings fail on new occurrences changed messages and stale entries`() {
        val known = Violation("K-SEC-4", "p/Target.kt", 4, "Target.password: String must be Secret")
        val entry = TrackedSecurityFailures.Entry("SEC301-OPAQUE-CREDENTIALS", "K-SEC-4", "K-SEC-4 p/Target.kt Target.password: String must be Secret", 1)
        assertEquals(emptyList(), TrackedSecurityFailures.remaining(listOf(known), "K-SEC-4", listOf(entry)))
        assertTrue(TrackedSecurityFailures.remaining(listOf(known, known), "K-SEC-4", listOf(entry)).isNotEmpty())
        assertTrue(TrackedSecurityFailures.remaining(emptyList(), "K-SEC-4", listOf(entry)).isNotEmpty())
        assertTrue(TrackedSecurityFailures.remaining(listOf(known.copy(what = "Other.password: String must be Secret")), "K-SEC-4", listOf(entry)).isNotEmpty())
        assertTrue(TrackedSecurityFailures.remaining(listOf(known), "K-SEC-4", emptyList()).isNotEmpty())
    }
}
