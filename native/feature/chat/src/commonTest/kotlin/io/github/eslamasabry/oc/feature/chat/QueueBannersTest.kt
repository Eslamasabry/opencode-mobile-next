package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.delivery.queue.QueueEviction
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.QueueBanners
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.QueueEvictedNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** C3 and S7: the chat says when the queue dropped drafts, and where drafts for other servers wait. */
class QueueBannersTest {
    private fun prompt(id: String, profile: String) =
        QueuedPrompt(id, ProfileId(profile), SessionId("s"), "text", createdAt = 1L)

    @Test
    fun `an eviction counts too old and over the limit apart, oversized with the limit`() {
        val notice = QueueBanners.evicted(QueueEviction(kept = emptyList(), expired = 2, overflowed = 1, oversized = 1))
        assertEquals(QueueEvictedNotice(tooOld = 2, overLimit = 2), notice)
        assertEquals(4, notice?.total)
    }

    @Test
    fun `no eviction and an empty eviction say nothing`() {
        assertNull(QueueBanners.evicted(null))
        assertNull(QueueBanners.evicted(QueueEviction(kept = emptyList())))
    }

    @Test
    fun `drafts under other servers are counted per server, mine are not`() {
        val others =
            QueueBanners.others(
                listOf(prompt("1", "a"), prompt("2", "b"), prompt("3", "b"), prompt("4", "me")),
                ProfileId("me"),
            )
        assertEquals(listOf(ProfileId("b") to 2, ProfileId("a") to 1), others.map { it.source to it.count })
    }
}
