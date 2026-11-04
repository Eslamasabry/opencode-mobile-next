package io.github.eslamasabry.oc.engine.attention.activity

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AutomaticActivityTest {
    private val time = Instant.parse("2026-09-27T12:00:00Z")
    private val disk = FakeAttentionKeyStore()
    private val redactor = Redactor()
    private var profilePresent = true

    private suspend fun open(profileId: String = "alpha"): AutomaticActivity =
        assertNotNull(AutomaticActivity.open(disk, redactor, profileId) { profilePresent })

    private suspend fun AutomaticActivity.rec(
        eventId: String,
        location: String = "/project/one",
        summary: String = "Reconnected automatically",
        occurredAt: Instant = time,
        sessionId: String? = null,
        undo: (suspend () -> Boolean)? = null,
    ): Boolean = record(eventId, location, AutomaticActKind.Reconnect, summary, occurredAt, sessionId, undo)

    private fun TestScope.later(block: suspend () -> Any?) = async(start = CoroutineStart.UNDISPATCHED) { block() }

    private fun at(offset: Duration) = time + offset

    @Test
    fun `B-STATE-006 profiles and locations are isolated and the profile key is oc_automaticActivity_profileId`() =
        runTest {
            val controller = open()
            val other = open("beta")
            controller.rec("same-event")
            controller.rec("same-event", location = "/project/two")
            assertEquals(2, controller.currentActs.size)
            assertEquals(1, controller.forLocation("/project/one").size)
            assertEquals(1, controller.forLocation("/project/two").size)
            assertTrue(controller.forLocation("/project/missing").isEmpty())
            assertTrue(other.currentActs.isEmpty())
            assertEquals("oc.automaticActivity.alpha", controller.storageKey)
            assertEquals(controller.storageKey, AutomaticActivity.keyFor("alpha"))
            assertNotEquals("/project/one", controller.locationKey("/project/one"))
        }

    @Test
    fun `B-STATE-006 a duplicate occurrence keeps the original report and a new occurrence appears`() =
        runTest {
            val controller = open()
            controller.rec("event-1")
            val originalId = controller.currentActs.single().id
            assertTrue(controller.rec("event-1", summary = "Changed duplicate"))
            assertEquals(originalId, controller.currentActs.single().id)
            assertEquals("Reconnected automatically", controller.currentActs.single().summary)
            controller.rec("event-2", occurredAt = at(1.minutes))
            assertEquals(2, controller.currentActs.size)
            assertNotEquals(originalId, controller.currentActs.first().id)
        }

    @Test
    fun `B-STATE-006 history survives reload ordered by occurrence time and is read-only`() =
        runTest {
            val controller = open()
            controller.rec("newer", occurredAt = at(1.minutes))
            controller.rec("older", summary = "Earlier reconnect")
            val restored = open()
            assertEquals(listOf("Reconnected automatically", "Earlier reconnect"), restored.currentActs.map { it.summary })
            assertTrue(restored.currentActs is ImmutableList<*>)
            assertTrue(restored.forLocation("/project/one") is ImmutableList<*>)
            assertEquals(2, restored.currentActs.size)
        }

    @Test
    fun `B-STATE-006 history keeps the newest 200 occurrences even with late older arrivals`() =
        runTest {
            val controller = open()
            for (index in 0 until AutomaticActivity.MAX_ENTRIES + 2) controller.rec("event-$index", occurredAt = at(index.seconds))
            controller.rec("very-old", occurredAt = time - 1.days)
            assertEquals(200, controller.currentActs.size)
            assertEquals(at(201.seconds), controller.currentActs.first().occurredAt)
            assertEquals(at(2.seconds), controller.currentActs.last().occurredAt)
            assertEquals(200, open().currentActs.size)
        }

    @Test
    fun `B-SEC-015 persisted reports redact copy and never store raw location or event identity`() =
        runTest {
            val controller = open()
            val fakeSecret = "synthetic-private-value-for-activity-test"
            redactor.registerSecret(fakeSecret)
            controller.rec(
                "event-$fakeSecret",
                location = "https://example.invalid/project/$fakeSecret",
                summary = "Reconnected\npassword=$fakeSecret\nReady",
                sessionId = "session-$fakeSecret",
            )
            val persisted = disk.data.getValue(controller.storageKey)
            assertFalse(persisted.contains(fakeSecret))
            assertFalse(persisted.contains("https://example.invalid"))
            assertFalse(persisted.contains("event-"))
            val act = controller.currentActs.single()
            assertFalse(act.summary.contains("\n"))
            assertTrue(act.summary.contains(Redactor.MASK))
            assertFalse(act.sessionId?.contains(fakeSecret) ?: false)
        }

    @Test
    fun `B-SEC-015 profile ids must be opaque and cannot persist registered credentials in keys`() =
        runTest {
            val fakeSecret = "synthetic-sensitive-profile-value"
            redactor.registerSecret(fakeSecret)
            for (invalid in listOf("", "https://example.invalid", "alpha.beta", fakeSecret)) {
                assertNull(AutomaticActivity.open(disk, redactor, invalid) { true })
            }
            assertTrue(disk.data.isEmpty())
        }

    @Test
    fun `B-STATE-007 acknowledging a captured snapshot leaves later arrivals unread`() =
        runTest {
            val controller = open()
            controller.rec("shown")
            val captured = controller.currentActs.map { it.id }
            controller.rec("arrived-later", occurredAt = at(1.seconds))
            assertTrue(controller.acknowledge(captured))
            assertFalse(controller.currentActs.first().acknowledged)
            assertTrue(controller.currentActs.last().acknowledged)
            val restored = open()
            assertFalse(restored.currentActs.first().acknowledged)
            assertTrue(restored.currentActs.last().acknowledged)
        }

    @Test
    fun `B-STATE-007 refused and thrown writes preserve published and restored state then retry`() =
        runTest {
            val controller = open()
            controller.rec("saved")
            val savedId = controller.currentActs.single().id
            disk.refuse = true
            assertFalse(controller.rec("refused"))
            assertTrue(controller.state.value.persistenceFailed)
            assertEquals(savedId, controller.currentActs.single().id)
            assertEquals(savedId, open().currentActs.single().id)
            disk.refuse = false
            disk.throwOnWrite = true
            assertFalse(controller.rec("throws"))
            assertEquals(savedId, controller.currentActs.single().id)
            disk.throwOnWrite = false
            assertTrue(controller.rec("refused"))
            assertEquals(2, controller.currentActs.size)
            assertFalse(controller.state.value.persistenceFailed)
        }

    @Test
    fun `B-STATE-007 malformed persisted histories read as empty and are visibly marked corrupt`() =
        runTest {
            for (value in listOf("not JSON", """{"version":999,"acts":[]}""", "[null]")) {
                disk.data[AutomaticActivity.keyFor("alpha")] = value
                val restored = open()
                assertTrue(restored.currentActs.isEmpty())
                assertTrue(restored.state.value.corruptHistory)
            }
        }

    @Test
    fun `B-STATE-007 a queued acknowledgement captures ids before the caller changes them`() =
        runTest {
            val controller = open()
            controller.rec("shown")
            val shownId = controller.currentActs.single().id
            val ids = mutableListOf(shownId)
            disk.gate = CompletableDeferred()
            disk.writeStarted = CompletableDeferred()
            val arriving = later { controller.rec("later", occurredAt = at(1.seconds)) }
            disk.writeStarted!!.await()
            val acknowledged = later { controller.acknowledge(ids) }
            ids.clear()
            disk.gate!!.complete(Unit)
            assertEquals(true, arriving.await())
            assertEquals(true, acknowledged.await())
            assertFalse(controller.currentActs.first().acknowledged)
            assertTrue(controller.currentActs.single { it.id == shownId }.acknowledged)
        }

    @Test
    fun `B-STATE-007 invalid observations are refused without saving empty history`() =
        runTest {
            val controller = open()
            assertFalse(controller.rec(""))
            assertFalse(controller.rec("empty", summary = " \n "))
            assertFalse(controller.rec("invalid-time", occurredAt = Instant.fromEpochMilliseconds(0)))
            assertTrue(controller.currentActs.isEmpty())
            assertFalse(disk.data.containsKey(controller.storageKey))
        }

    @Test
    fun `B-AGENT-030 concurrent Undo invokes its callback once and survives restart as unavailable`() =
        runTest {
            val controller = open()
            var calls = 0
            val started = CompletableDeferred<Unit>()
            val completion = CompletableDeferred<Boolean>()
            controller.rec("undoable", undo = {
                calls++
                started.complete(Unit)
                completion.await()
            })
            val id = controller.currentActs.single().id
            assertTrue(controller.canUndo(id))
            val first = later { controller.undo(id) }
            started.await()
            val second = later { controller.undo(id) }
            completion.complete(true)
            assertEquals(AutomaticUndoResult.Undone, first.await())
            assertEquals(AutomaticUndoResult.Unavailable, second.await())
            assertEquals(1, calls)
            assertFalse(controller.canUndo(id))
            assertEquals(AutomaticUndoResult.Unavailable, open().undo(id))
            assertEquals(1, calls)
        }

    @Test
    fun `B-AGENT-030 Undo false and exceptions are distinguished and never retried or leaked`() =
        runTest {
            val controller = open()
            var calls = 0
            controller.rec("false", undo = {
                calls++
                false
            })
            val failedId = controller.currentActs.single().id
            assertEquals(AutomaticUndoResult.Failed, controller.undo(failedId))
            assertEquals(AutomaticUndoResult.Unavailable, controller.undo(failedId))
            val fakeError = "synthetic-sensitive-exception-value"
            controller.rec("throws", occurredAt = at(1.seconds), undo = {
                calls++
                error(fakeError)
            })
            val thrownId = controller.currentActs.first().id
            assertEquals(AutomaticUndoResult.Unconfirmed, controller.undo(thrownId))
            assertEquals(AutomaticUndoResult.Unavailable, controller.undo(thrownId))
            assertEquals(2, calls)
            assertFalse(disk.data.getValue(controller.storageKey).contains(fakeError))
        }

    @Test
    fun `B-AGENT-030 Undo needs a durable attempt marker before invoking its callback`() =
        runTest {
            val controller = open()
            var calls = 0
            controller.rec("undoable", undo = {
                calls++
                true
            })
            val id = controller.currentActs.single().id
            disk.refuse = true
            assertEquals(AutomaticUndoResult.Failed, controller.undo(id))
            assertEquals(0, calls)
            assertTrue(controller.state.value.persistenceFailed)
            disk.refuse = false
            assertEquals(AutomaticUndoResult.Undone, controller.undo(id))
            assertEquals(1, calls)
        }

    @Test
    fun `B-AGENT-030 an unsaved Undo outcome stays uncertain and cannot repeat`() =
        runTest {
            val controller = open()
            var calls = 0
            controller.rec("inverse-applied", undo = {
                calls++
                disk.refuse = true // the intent was saved; the outcome write is refused
                true
            })
            val id = controller.currentActs.single().id
            assertEquals(AutomaticUndoResult.Unconfirmed, controller.undo(id))
            assertTrue(controller.currentActs.single().undoAttempted)
            assertFalse(controller.currentActs.single().undone)
            assertTrue(controller.state.value.persistenceFailed)
            disk.refuse = false
            assertEquals(AutomaticUndoResult.Unavailable, controller.undo(id))
            val restored = open()
            assertTrue(restored.currentActs.single().undoAttempted)
            assertFalse(restored.currentActs.single().undone)
            assertEquals(AutomaticUndoResult.Unavailable, restored.undo(id))
            assertEquals(1, calls)
        }

    @Test
    fun `B-AGENT-030 restart keeps reports but does not revive process-local Undo callbacks`() =
        runTest {
            val controller = open()
            var calls = 0
            controller.rec("undoable", undo = {
                calls++
                true
            })
            val id = controller.currentActs.single().id
            val restored = open()
            assertEquals(1, restored.currentActs.size)
            assertFalse(restored.canUndo(id))
            assertEquals(AutomaticUndoResult.Unavailable, restored.undo(id))
            assertEquals(0, calls)
        }

    @Test
    fun `B-SEC-016 deleted profiles refuse records and Undo without invoking callbacks`() =
        runTest {
            val controller = open()
            var calls = 0
            controller.rec("undoable", undo = {
                calls++
                true
            })
            val id = controller.currentActs.single().id
            profilePresent = false
            assertFalse(controller.rec("after-delete"))
            assertEquals(AutomaticUndoResult.Unavailable, controller.undo(id))
            assertEquals(0, calls)
        }

    @Test
    fun `B-SEC-016 reversible deletion preparation preserves the shared owner history and Undo`() =
        runTest {
            val registry = AutomaticActivityRegistry(disk, redactor)
            val shared = assertNotNull(registry.forProfile("shared-alpha") { profilePresent })
            var inverseCalls = 0
            shared.rec("undoable", undo = {
                inverseCalls++
                true
            })
            val id = shared.currentActs.single().id
            val original = disk.data[shared.storageKey]
            shared.prepareForDeletion()
            assertEquals(id, shared.currentActs.single().id)
            assertEquals(original, disk.data[shared.storageKey])
            assertFalse(shared.canUndo(id))
            assertEquals(AutomaticUndoResult.Unavailable, shared.undo(id))
            assertSame(shared, registry.forProfile("shared-alpha") { profilePresent })
            shared.cancelDeletion()
            assertTrue(shared.canUndo(id))
            assertEquals(AutomaticUndoResult.Undone, shared.undo(id))
            assertEquals(1, inverseCalls)
            assertTrue(disk.data.getValue(shared.storageKey).contains("\"undone\":true"))
        }

    @Test
    fun `B-SEC-016 deletion preparation drains admitted records and keeps their Undo`() =
        runTest {
            val controller = open()
            disk.gate = CompletableDeferred()
            disk.writeStarted = CompletableDeferred()
            var inverseCalls = 0
            val first = later { controller.rec("first") }
            disk.writeStarted!!.await()
            val second = later {
                controller.rec("second", occurredAt = at(1.seconds), undo = {
                    inverseCalls++
                    true
                })
            }
            var drained = false
            val preparation = later {
                controller.prepareForDeletion()
                drained = true
            }
            assertFalse(controller.rec("not-admitted"))
            runCurrent()
            assertFalse(drained)
            disk.gate!!.complete(Unit)
            assertEquals(true, first.await())
            assertEquals(true, second.await())
            preparation.await()
            assertEquals(2, controller.currentActs.size)
            val id = controller.currentActs.first().id
            assertFalse(controller.canUndo(id))
            controller.cancelDeletion()
            assertTrue(controller.canUndo(id))
            assertEquals(AutomaticUndoResult.Undone, controller.undo(id))
            assertEquals(1, inverseCalls)
            assertTrue(controller.rec("after-cancel"))
        }

    @Test
    fun `B-SEC-016 deletion preparation persists a confirmed in-flight Undo before cancellation`() =
        runTest {
            val controller = open()
            val started = CompletableDeferred<Unit>()
            val completion = CompletableDeferred<Boolean>()
            controller.rec("undoable", undo = {
                started.complete(Unit)
                completion.await()
            })
            val id = controller.currentActs.single().id
            val undo = later { controller.undo(id) }
            started.await()
            var drained = false
            val preparation = later {
                controller.prepareForDeletion()
                drained = true
            }
            runCurrent()
            assertFalse(drained)
            completion.complete(true)
            assertEquals(AutomaticUndoResult.Undone, undo.await())
            preparation.await()
            controller.cancelDeletion()
            assertTrue(controller.currentActs.single().undone)
            assertFalse(controller.canUndo(id))
            assertTrue(open().currentActs.single().undone)
            assertTrue(controller.rec("after-cancel"))
        }

    @Test
    fun `B-SEC-016 deletion preparation drains an Undo admitted behind a pending record`() =
        runTest {
            val controller = open()
            var inverseCalls = 0
            controller.rec("undoable", undo = {
                inverseCalls++
                true
            })
            val id = controller.currentActs.single().id
            disk.gate = CompletableDeferred()
            disk.writeStarted = CompletableDeferred()
            val pending = later { controller.rec("pending") }
            disk.writeStarted!!.await()
            val undo = later { controller.undo(id) }
            val preparation = later { controller.prepareForDeletion() }
            disk.gate!!.complete(Unit)
            assertEquals(true, pending.await())
            assertEquals(AutomaticUndoResult.Undone, undo.await())
            preparation.await()
            controller.cancelDeletion()
            assertEquals(1, inverseCalls)
            assertTrue(controller.currentActs.single { it.id == id }.undone)
            assertTrue(open().currentActs.single { it.id == id }.undone)
        }

    @Test
    fun `B-SEC-016 deletion waits for a pending write and never resurrects history`() =
        runTest {
            val controller = open()
            disk.gate = CompletableDeferred()
            disk.writeStarted = CompletableDeferred()
            val pending = later { controller.rec("pending") }
            disk.writeStarted!!.await()
            val deleted = later { controller.deleteProfileData() }
            assertFalse(controller.rec("after-delete-started"))
            disk.gate!!.complete(Unit)
            pending.await()
            assertEquals(true, deleted.await())
            assertTrue(controller.currentActs.isEmpty())
            assertFalse(disk.data.containsKey(controller.storageKey))
            assertFalse(controller.rec("after-delete-finished"))
        }

    @Test
    fun `B-SEC-016 deletion drains an in-flight Undo before erasing its durable history`() =
        runTest {
            val controller = open()
            val started = CompletableDeferred<Unit>()
            val completion = CompletableDeferred<Boolean>()
            controller.rec("undoable", undo = {
                started.complete(Unit)
                completion.await()
            })
            val undo = later { controller.undo(controller.currentActs.single().id) }
            started.await()
            var finished = false
            val deletion = later { controller.deleteProfileData().also { finished = true } }
            runCurrent()
            assertFalse(finished)
            completion.complete(true)
            undo.await()
            assertEquals(true, deletion.await())
            assertTrue(controller.currentActs.isEmpty())
            assertFalse(disk.data.containsKey(controller.storageKey))
        }

    @Test
    fun `B-SEC-016 a failed deletion reports failure and a retry erases the kept history`() =
        runTest {
            val controller = open()
            controller.rec("saved")
            disk.refuse = true
            assertFalse(controller.deleteProfileData())
            assertTrue(controller.state.value.persistenceFailed)
            assertFalse(controller.rec("after-failed-delete"))
            disk.refuse = false
            assertTrue(controller.deleteProfileData())
            assertTrue(controller.currentActs.isEmpty())
            assertFalse(disk.data.containsKey(controller.storageKey))
        }

    @Test
    fun `B-SEC-016 dispose stops writes and notifications without deleting history`() =
        runTest {
            val controller = open()
            controller.rec("saved")
            val before = controller.state.value
            controller.dispose()
            assertFalse(controller.rec("after-dispose"))
            assertSame(before, controller.state.value)
            assertTrue(disk.data.containsKey(controller.storageKey))
            assertEquals(1, open().currentActs.size)
        }

    @Test
    fun `B-SEC-016 closing a shared profile erases it and a later owner starts empty`() =
        runTest {
            val registry = AutomaticActivityRegistry(disk, redactor)
            val shared = assertNotNull(registry.forProfile("gamma") { profilePresent })
            shared.rec("saved")
            assertTrue(registry.closeProfile("gamma"))
            assertFalse(disk.data.containsKey(AutomaticActivity.keyFor("gamma")))
            val next = assertNotNull(registry.forProfile("gamma") { profilePresent })
            assertTrue(next.currentActs.isEmpty())
        }
}
