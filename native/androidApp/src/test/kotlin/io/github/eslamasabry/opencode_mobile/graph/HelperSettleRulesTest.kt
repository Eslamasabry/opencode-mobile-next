package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The agents' source stops being "starting" when it is listed; the clock is only the bound for a listing that never comes. */
class HelperSettleRulesTest {
    @Test
    fun `B-HOME-228 the helper's settling ends when its source is listed, not on the clock`() {
        // Listed 2 s after the work: done at once, far inside the 30 s bound.
        assertFalse(HelperSettleRules.settling(sinceWorkMs = 2_000, boundMs = 30_000, listed = true))
        // Not listed yet: still settling inside the bound.
        assertEquals(true, HelperSettleRules.settling(sinceWorkMs = 2_000, boundMs = 30_000, listed = false))
        // The listing never comes: the existing bound still ends it (the notice then speaks, B-HOME-227).
        assertFalse(HelperSettleRules.settling(sinceWorkMs = 30_000, boundMs = 30_000, listed = false))
    }

    @Test
    fun `B-HOME-229 the stage clears within a second of the listing, and by the bound when none comes`() {
        val id = io.github.eslamasabry.oc.core.common.ids.ProfileId("agents")
        val settle = HelperSettle(boundMs = 30_000)
        // The helper's work shows at t=0 and ends; the source is not listed yet.
        assertEquals(true, settle.settling(id, workNow = true, listed = false, nowMs = 0))
        assertEquals(true, settle.settling(id, workNow = false, listed = false, nowMs = 10_000))
        // Listed at t=10 s: one second later Home no longer says starting.
        assertFalse(settle.settling(id, workNow = false, listed = true, nowMs = 11_000))
        // No listing ever: the bound ends it.
        val never = HelperSettle(boundMs = 30_000)
        assertEquals(true, never.settling(id, workNow = true, listed = false, nowMs = 0))
        assertFalse(never.settling(id, workNow = false, listed = false, nowMs = 30_000))
    }

    @Test
    fun `B-HOME-229 Home reads again when a source is listed`() {
        val src = listOf(
            java.io.File("src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ChatsHomeWiring.kt"),
            java.io.File("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ChatsHomeWiring.kt"),
        ).first { it.exists() }.readText()
        kotlin.test.assertTrue(src.contains("runtime.loadedSourceFlow.collect { publish() }"))
    }
}
