package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.feature.chats.presentation.BriefBlocker
import io.github.eslamasabry.oc.feature.chats.presentation.BriefPreferences
import io.github.eslamasabry.oc.feature.chats.presentation.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ReturnBriefAck
import io.github.eslamasabry.oc.feature.chats.presentation.ReturnBriefStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun briefSession(id: String, idle: Long?, parent: String? = null, updated: Long = 1): Session =
    Session(
        id = SessionId(id),
        title = "Chat $id",
        directory = "/work/app",
        parentId = parent?.let(::SessionId),
        time = SessionTime(createdMs = 1, updatedMs = updated, idleMs = idle),
    )

internal class MemoryPrefs : BriefPreferences {
    val values = mutableMapOf<String, String>()
    var refuse = false
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String): Boolean {
        gate?.await()
        if (refuse) return false
        values[key] = value
        return true
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

class ReturnBriefTest {
    private val profile = ProfileId("p1")
    private val location = """["https://example.test","/work/app",null]"""

    private fun brief(
        sessions: List<Session>,
        unread: Set<String> = sessions.map { it.id.raw }.toSet(),
        busy: Set<String> = emptySet(),
        blocked: Map<String, Pair<BriefBlocker, String>> = emptyMap(),
        readStateKnown: Boolean = true,
        partial: Boolean = false,
        ack: ReturnBriefAck? = null,
    ): ReturnBrief =
        ReturnBrief.build(
            sessions = sessions,
            readStateKnown = readStateKnown,
            inventoryPartial = partial,
            isUnread = { it.id.raw in unread },
            isBusy = { it.raw in busy },
            blockerOf = { blocked[it.raw] },
            ack = ack,
        )

    @Test
    fun `B-CHAT-093 the first-visit brief lists unreviewed identities, never an interval or outcome`() {
        val b = brief(listOf(briefSession("old", 10), briefSession("new", 30), briefSession("seen", 20), briefSession("child", 40, parent = "new")), unread = setOf("old", "new", "child"))
        assertEquals(listOf("new", "old"), b.unreviewed.map { it.session.id.raw })
        assertEquals(listOf(30L, 10L), b.unreviewed.map { it.idleAt })
        // Equal or older idle sessions are not blanket-acknowledged: dismissing "new" leaves "old" new.
        val ack = ReturnBrief.build(listOf(briefSession("new", 30)), true, false, { true }, { false }, { null }).acknowledgement(null)
        val after = brief(listOf(briefSession("old", 10), briefSession("new", 30), briefSession("same", 30)), ack = ack)
        assertEquals(listOf("same", "old"), after.unreviewed.map { it.session.id.raw })
        // A newer idle on a dismissed session is new again.
        assertEquals(listOf("new"), brief(listOf(briefSession("new", 31)), ack = ack).unreviewed.map { it.session.id.raw })
    }

    @Test
    fun `B-CHAT-094 dismissing acknowledges only the rows shown, by kind, session and id`() {
        val sessions = (1..5).map { briefSession("s$it", it * 10L) } + briefSession("ask", 99) + briefSession("busy", 98) + briefSession("viewed", 97)
        val b =
            brief(
                sessions,
                unread = sessions.map { it.id.raw }.toSet() - "viewed",
                busy = setOf("busy"),
                blocked = mapOf("ask" to (BriefBlocker.Permission to "perm-1")),
            )
        // Busy, viewed and blocked sessions are not result rows; the blocked one is a request.
        assertEquals(listOf("s5", "s4", "s3"), b.unreviewed.map { it.session.id.raw })
        assertEquals(2, b.hiddenUnreviewed)
        assertEquals(listOf("ask"), b.requests.map { it.session.id.raw })

        val ack = b.acknowledgement(null)
        val next = brief(sessions, unread = sessions.map { it.id.raw }.toSet() - "viewed", busy = setOf("busy"), blocked = mapOf("ask" to (BriefBlocker.Permission to "perm-1")), ack = ack)
        assertEquals(listOf("s2", "s1"), next.unreviewed.map { it.session.id.raw }, "overflow stays unacknowledged")
        assertTrue(next.requests.isEmpty())
        // The same session asking again with a new request id, or another kind, is new.
        val again = brief(sessions, blocked = mapOf("ask" to (BriefBlocker.Question to "perm-1")), ack = ack)
        assertEquals(listOf("ask"), again.requests.map { it.session.id.raw })
        val newId = brief(sessions, blocked = mapOf("ask" to (BriefBlocker.Permission to "perm-2")), ack = ack)
        assertEquals(listOf("ask"), newId.requests.map { it.session.id.raw })
    }

    @Test
    fun `B-CHAT-095 unsupported read state and a partial inventory are not an empty brief`() {
        val unknown = brief(listOf(briefSession("a", 10)), readStateKnown = false)
        assertTrue(unknown.isEmpty)
        assertTrue(unknown.unknown)
        val partial = brief(emptyList(), partial = true)
        assertTrue(partial.unknown)
        val empty = brief(emptyList())
        assertTrue(empty.isEmpty)
        assertFalse(empty.unknown)
    }

    @Test
    fun `B-STATE-082 acknowledgements are stored per profile and location and survive restart`() =
        runTest {
            val prefs = MemoryPrefs()
            val b = brief(listOf(briefSession("a", 10), briefSession("b", 20)))
            assertIs<Outcome.Ok<Unit>>(ReturnBriefStore(prefs).acknowledge(profile, location, b))
            val restarted = ReturnBriefStore(prefs)
            assertTrue(restarted.acknowledged(profile, location).coversRun(SessionId("a"), 10))
            assertFalse(restarted.acknowledged(profile, "other").coversRun(SessionId("a"), 10))
            assertFalse(restarted.acknowledged(ProfileId("p2"), location).coversRun(SessionId("a"), 10))
            assertTrue(prefs.values.keys.single().startsWith("oc.returnBrief.p1"))
        }

    @Test
    fun `B-STATE-082 a refused write stays unacknowledged`() =
        runTest {
            val prefs = MemoryPrefs().apply { refuse = true }
            val store = ReturnBriefStore(prefs)
            val result = store.acknowledge(profile, location, brief(listOf(briefSession("a", 10))))
            assertEquals(Failure.StorageRefused(), (result as Outcome.Failed).failure)
            assertFalse(store.acknowledged(profile, location).coversRun(SessionId("a"), 10))
            assertFalse(ReturnBriefStore(prefs).acknowledged(profile, location).coversRun(SessionId("a"), 10))
        }

    @Test
    fun `B-STATE-082 queued writes merge and new arrivals stay new`() =
        runTest {
            val prefs = MemoryPrefs().apply { gate = CompletableDeferred() }
            val store = ReturnBriefStore(prefs)
            val first = async { store.acknowledge(profile, location, brief(listOf(briefSession("a", 10)))) }
            val second = async { store.acknowledge(profile, location, brief(listOf(briefSession("b", 20)))) }
            testScheduler.runCurrent()
            prefs.gate!!.complete(Unit)
            first.await()
            second.await()
            val ack = ReturnBriefStore(prefs).acknowledged(profile, location)
            assertTrue(ack.coversRun(SessionId("a"), 10))
            assertTrue(ack.coversRun(SessionId("b"), 20))
            assertFalse(ack.coversRun(SessionId("c"), 30), "a run that arrived during the save is still new")
        }

    @Test
    fun `B-STATE-082 corrupt or malformed entries start unacknowledged`() =
        runTest {
            val prefs = MemoryPrefs()
            prefs.values[ReturnBriefStore.key(profile)] = "{not json"
            assertEquals(ReturnBriefAck.Empty, ReturnBriefStore(prefs).acknowledged(profile, location))
            val badRun = kotlinx.serialization.json.Json.parseToJsonElement("""{"runs":[["a",-1]],"requests":[]}""")
            prefs.values[ReturnBriefStore.key(profile)] = kotlinx.serialization.json.JsonObject(mapOf(location to badRun)).toString()
            assertEquals(ReturnBriefAck.Empty, ReturnBriefStore(prefs).acknowledged(profile, location))
            assertNull(ReturnBriefAck.fromJson(kotlinx.serialization.json.JsonPrimitive(3)))
        }

    @Test
    fun `B-STATE-083 deleting a profile drains pending dismissals and clears both copies`() =
        runTest {
            val prefs = MemoryPrefs().apply { gate = CompletableDeferred() }
            val store = ReturnBriefStore(prefs)
            val pending = async { store.acknowledge(profile, location, brief(listOf(briefSession("a", 10)))) }
            testScheduler.runCurrent()
            val deleting = async { store.deleteProfile(profile) }
            testScheduler.runCurrent()
            assertFalse(deleting.isCompleted, "deletion waits for the queued dismissal")
            prefs.gate!!.complete(Unit)
            pending.await()
            deleting.await()
            assertTrue(prefs.values.isEmpty())
            assertEquals(ReturnBriefAck.Empty, store.acknowledged(profile, location))
        }
}
