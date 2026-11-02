package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EngEmbeddedEditTest {
    @Test
    fun `B-STATE-063 eng-4 embedded removal preserves another profile update across writer lanes`() = runTest {
        for (edit in listOf(FlutterEraEdits.OfflineQueue, FlutterEraEdits.SessionDrafts)) {
            val h = Harness(StandardTestDispatcher(testScheduler))
            val key = edit.keys.first()
            val initial = listOf(mapOf("id" to "a", "profileID" to "doomed"), mapOf("id" to "b", "profileID" to "keeper"))
            h.seed(key to json(initial))
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocking = async { h.lanes.app.write { entered.complete(Unit); release.await(); Unit.ok() } }
            entered.await()
            val cleaner = EmbeddedRecordCleaner(h.kv, h.lanes.app, edit, DeletionStep.QUEUE)
            val clearing = async { cleaner.clear(id("doomed"), DeletionOptions(confirmedQueue = h.confirmedQueue(id("doomed")))) }
            runCurrent()
            val updated = initial + mapOf("id" to "new", "profileID" to "keeper")
            h.kv.commit(WriterLane("other-profile")) { putString(key, json(updated)) }
            release.complete(Unit)
            blocking.await()
            assertIs<Outcome.Ok<Int>>(clearing.await())
            assertEquals(json(updated.drop(1)), h.string(key))
        }
    }

    @Test
    fun `B-STATE-063 eng-4 keeping removed prompts preserves concurrent saved prompts`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        val removed = mapOf("id" to "a", "profileID" to "doomed")
        val saved = mapOf("id" to "b", "profileID" to "keeper")
        val appended = mapOf("id" to "new", "profileID" to "keeper")
        h.seed("oc.offlineQueue" to json(listOf(removed)), "oc.keptQueuedPrompts" to json(listOf(saved)))
        val options = DeletionOptions(keepQueuedPromptsAsDrafts = true, confirmedQueue = h.confirmedQueue(id("doomed")))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocking = async { h.lanes.app.write { entered.complete(Unit); release.await(); Unit.ok() } }
        entered.await()
        val cleaner = EmbeddedRecordCleaner(h.kv, h.lanes.app, FlutterEraEdits.OfflineQueue, DeletionStep.QUEUE)
        val clearing = async { cleaner.clear(id("doomed"), options) }
        runCurrent()
        h.kv.commit(WriterLane("other-profile")) { putString("oc.keptQueuedPrompts", json(listOf(saved, appended))) }
        release.complete(Unit)
        blocking.await()
        assertIs<Outcome.Ok<Int>>(clearing.await())
        assertEquals("[]", h.string("oc.offlineQueue"))
        assertEquals(json(listOf(saved, appended, removed)), h.string("oc.keptQueuedPrompts"))
    }

    @Test
    fun `B-STATE-063 eng-4 a changed confirmed queue refuses deletion`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        val confirmed = json(listOf(mapOf("id" to "old", "profileID" to "doomed")))
        val changed = json(listOf(mapOf("id" to "new", "profileID" to "doomed")))
        h.seed("oc.offlineQueue" to changed)
        val cleaner = EmbeddedRecordCleaner(h.kv, h.lanes.app, FlutterEraEdits.OfflineQueue, DeletionStep.QUEUE)
        assertIs<Outcome.Failed>(cleaner.clear(id("doomed"), DeletionOptions(confirmedQueue = (QueuedPromptRemovalPlan.fromSnapshot(id("doomed"), confirmed) as Outcome.Ok).value)))
        assertEquals(changed, h.string("oc.offlineQueue"))
    }
}
