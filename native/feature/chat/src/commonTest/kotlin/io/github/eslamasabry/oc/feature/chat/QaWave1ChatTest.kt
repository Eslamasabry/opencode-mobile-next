package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.feature.chat.ui.distinctKeys
import io.github.eslamasabry.oc.feature.chat.ui.failureCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.collections.immutable.persistentListOf

/** QA wave 1 (docs/native/qa/wave1): chat presentation fixes. */
class QaWave1ChatTest {
    @Test
    fun `B0 QA wave1 rows with a repeated key reach the list once`() {
        val rows = persistentListOf(notice("u1", "v2-0"), prompt("u1", "Hello"), notice("u1", "v2-0"), notice("u1", "v2-0"))
        val unique = rows.distinctKeys()
        assertEquals(listOf("notice:v2-0", "prompt:u1"), unique.map { it.key })
    }

    @Test
    fun `B0 QA wave1 a list of unique keys passes through as the same instance`() {
        val rows = persistentListOf(prompt("u2", "Again"), notice("u1", "n1"), prompt("u1", "Hello"))
        assertSame(rows, rows.distinctKeys())
    }

    @Test
    fun `B5 QA wave1 a failed reply's server text becomes a plain reason with a next step`() {
        assertEquals(ReplyFailureKind.SignIn, ReplyFailureKind.of("API key expired. Please renew it (401)"))
        assertEquals(ReplyFailureKind.SignIn, ReplyFailureKind.of("Unauthorized"))
        assertEquals(ReplyFailureKind.Busy, ReplyFailureKind.of("429 Too Many Requests: rate limit reached"))
        assertEquals(ReplyFailureKind.Model, ReplyFailureKind.of("Model not found: glm-9"))
        assertEquals(ReplyFailureKind.Other, ReplyFailureKind.of("socket hang up"))
        assertEquals(ReplyFailureKind.Other, ReplyFailureKind.of(null))
    }

    @Test
    fun `B2 QA wave1 a not-sent bubble names its failure kind under Details, never server text`() {
        assertEquals("Refused.StaleScope", failureCode(Failure.Refused(RefusalReason.StaleScope)))
        assertEquals("Network.Offline", failureCode(Failure.Network(NetworkWhy.Offline)))
        assertEquals("Server.500", failureCode(Failure.Server(500)))
    }
}
