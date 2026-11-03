package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerPage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionTailLoaderTest {
    private val one = ProfileId("one")

    private class Context(override val source: SessionTailSource<String>?) : SessionTailContext<String> {
        override var profile: ProfileId? = ProfileId("one")
        var readable = true
        var deletions = 0
        val boundaries = HashMap<SessionId, MessageId>()

        override fun isProfileReadable(profile: ProfileId): Boolean = readable

        override fun deletionRevision(profile: ProfileId): Int = deletions

        override fun historyRevision(id: SessionId): Int = 0

        override fun stagedBoundary(id: SessionId): MessageId? = boundaries[id]

        override fun isDeleted(id: SessionId): Boolean = false
    }

    @Test
    fun `B-STATE-122 tail prefetch joins ongoing hydration and a late result for a deleted profile is rejected`() = runTest {
        val result = CompletableDeferred<ServerPage<String>>()
        var reads = 0
        val source = SessionTailSource<String> { _, _ ->
            reads++
            Outcome.Ok(result.await())
        }
        val context = Context(source)
        val saved = mutableListOf<List<String>>()
        val loader = SessionTailLoader(backgroundScope, context) { _, _, items, isCurrent -> if (isCurrent()) saved += items }
        val session = SessionId("session")
        val prefetch = async { loader.prefetch(session) }
        val hydration = async { loader.load(session) }
        runCurrent()
        assertEquals(1, reads)
        // The profile is deleted while the read is on the wire.
        context.readable = false
        context.deletions++
        result.complete(ServerPage(persistentListOf("m1"), null))
        prefetch.await()
        val outcome = hydration.await()
        assertIs<Outcome.Failed>(outcome)
        assertIs<Failure.Refused>(outcome.failure)
        runCurrent()
        assertTrue(saved.isEmpty())
        assertEquals(1, reads)
    }

    @Test
    fun `B-STATE-122 a current tail is shared once and cached, a changed staged boundary rejects it`() = runTest {
        val result = CompletableDeferred<ServerPage<String>>()
        val source = SessionTailSource<String> { _, _ -> Outcome.Ok(result.await()) }
        val context = Context(source)
        val saved = mutableListOf<List<String>>()
        val loader = SessionTailLoader(backgroundScope, context) { _, _, items, isCurrent -> if (isCurrent()) saved += items }
        val session = SessionId("session")
        result.complete(ServerPage(persistentListOf("m1"), null))
        assertEquals(listOf("m1"), (loader.load(session) as Outcome.Ok).value.items)
        runCurrent()
        assertEquals(listOf(listOf("m1")), saved)

        val held = CompletableDeferred<ServerPage<String>>()
        val heldContext = Context(SessionTailSource { _, _ -> Outcome.Ok(held.await()) })
        val heldLoader = SessionTailLoader(backgroundScope, heldContext) { _, _, items, isCurrent -> if (isCurrent()) saved += items }
        val pending = async { heldLoader.load(session) }
        runCurrent()
        heldContext.boundaries[session] = MessageId("m0")
        held.complete(ServerPage(persistentListOf("m2"), null))
        assertIs<Outcome.Failed>(pending.await())
        runCurrent()
        assertEquals(listOf(listOf("m1")), saved)
        assertEquals(one, context.profile)
    }
}
