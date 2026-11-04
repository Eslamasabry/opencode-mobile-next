package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.engine.delivery.live.PhoneChatDispatch
import io.github.eslamasabry.oc.engine.delivery.live.PhoneChatDispatchTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of `test/api2_phone_chat_dispatch_test.dart` (row B-PROTO-029), for prompt, shell and command sends. */
class PhoneChatDispatchTest {
    private val session = SessionId("ses_person")
    private val kinds = listOf("prompt", "shell", "command")

    @Test
    fun `B-PROTO-029 every send kind waits for admission before any request, and a refused admission settles nothing`() = runTest {
        for (kind in kinds) {
            val admission = CompletableDeferred<Outcome<Unit>>()
            var settled = 0
            val requests = mutableListOf<String>()
            val dispatch = PhoneChatDispatch(admit = { admission.await() }, settled = { settled++ })
            val result = async { dispatch.dispatch(session) { requests += "POST $kind"; Unit.ok() } }
            runCurrent()
            assertTrue(requests.isEmpty(), "$kind: nothing before admission")
            admission.complete(Outcome.Failed(Failure.Unexpected()))
            assertIs<Outcome.Failed>(result.await(), kind)
            assertTrue(requests.isEmpty(), kind)
            assertEquals(0, settled, kind)
        }
    }

    @Test
    fun `B-PROTO-029 an accepted dispatch of every kind settles exactly once`() = runTest {
        for (kind in kinds) {
            val callbacks = mutableListOf<String>()
            var posts = 0
            val dispatch = PhoneChatDispatch(
                admit = { callbacks += "before:${it.raw}"; Unit.ok() },
                settled = { callbacks += "settled:${it.raw}" },
            )
            assertEquals(Outcome.Ok(Unit), dispatch.dispatch(session) { posts++; Unit.ok() }, kind)
            assertEquals(listOf("before:ses_person", "settled:ses_person"), callbacks, kind)
            assertEquals(1, posts, kind)
        }
    }

    @Test
    fun `B-PROTO-029 an uncertain send settles without replaying, and the turn is kept until the server shows it ran`() = runTest {
        for (kind in kinds) {
            val tracker = PhoneChatDispatchTracker()
            var posts = 0
            val dispatch = PhoneChatDispatch(admit = { tracker.begin(it, "/work"); Unit.ok() }, settled = { tracker.settled(it) })
            assertIs<Outcome.Failed>(dispatch.dispatch(session) { posts++; Outcome.Failed(Failure.Server(500)) }, kind)
            assertEquals(1, posts, "$kind: one POST, never replayed")
            tracker.reconcile(emptyMap(), tracker.epoch, "/work")
            assertTrue(session in tracker.sessionIds, "$kind: idle before any busy read is not proof it ran")
            tracker.reconcile(mapOf(session to SessionStatus.Busy), tracker.epoch, "/work")
            tracker.reconcile(emptyMap(), tracker.epoch, "/work")
            assertTrue(tracker.sessionIds.isEmpty(), kind)
        }
    }
}
