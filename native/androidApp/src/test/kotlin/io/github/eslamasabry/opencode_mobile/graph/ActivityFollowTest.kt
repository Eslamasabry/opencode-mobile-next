package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertEquals

/** The phone server follows the activity; a refused follow is asked again once, then reported once. */
class ActivityFollowTest {
    private fun refusing(times: Int): (Boolean) -> Unit {
        var left = times
        return { if (left-- > 0) error("refused") }
    }

    @Test
    fun `B-LINUX-207 a refused follow is asked again on the next resume and nothing is reported when that lands`() {
        var told = 0
        val follow = ActivityFollow { told++ }
        val call = refusing(1)
        follow.follow(true, call)
        follow.follow(true, call)
        assertEquals(0, told)
    }

    @Test
    fun `B-LINUX-207 two refusals in a row are reported once and never loop`() {
        var told = 0
        var asked = 0
        val follow = ActivityFollow { told++ }
        val call: (Boolean) -> Unit = {
            asked++
            error("refused")
        }
        follow.follow(true, call)
        follow.follow(true, call)
        follow.follow(true, call)
        assertEquals(1, told)
        assertEquals(3, asked)
    }

    @Test
    fun `B-LINUX-207 a landed follow rearms the report`() {
        var told = 0
        val follow = ActivityFollow { told++ }
        val bad: (Boolean) -> Unit = { error("refused") }
        follow.follow(true, bad)
        follow.follow(true, bad)
        follow.follow(false) {}
        follow.follow(true, bad)
        follow.follow(true, bad)
        assertEquals(2, told)
    }
}
