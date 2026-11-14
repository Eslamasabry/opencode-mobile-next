package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerSnapshot
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.OlderUi
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Audit 1 (2026-11-14), the top of the transcript: the way to earlier messages is the transcript's first row (the
 * screen draws it from [io.github.eslamasabry.oc.feature.chat.presentation.ChatUi.older]), and a timeline pick goes to
 * that message with nothing else opening.
 */
class Audit1TopTest {
    private class World : FakeChatWorld(), TurnFeedSource {
        override val turnFeed = MutableStateFlow(TurnFeed())
        val older = MutableStateFlow(false)
        override val hasOlder: StateFlow<Boolean>
            get() = older
        var resume: Continuation<Outcome<Unit>>? = null

        override suspend fun loadOlder(): Outcome<Unit> {
            calls += "older"
            return suspendCoroutine { resume = it }
        }
    }

    private fun message(id: String, role: Role, text: String) =
        MessageWithParts(
            MessageInfo(id, Ref.sessionId.raw, role),
            listOf(Part("p-$id", id, Ref.sessionId.raw, PartType.Text, "text", text = text)).toImmutableList(),
        )

    @Test
    fun `AUDIT1-01 earlier messages the server still has are offered, with the read in flight and after it`() {
        val world = World()
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        assertNull(p.current().older, "the whole conversation is loaded: no row")
        world.older.value = true
        assertEquals(OlderUi(loading = false, failure = null), p.current().older)
        p.loadOlder()
        assertEquals(OlderUi(loading = true, failure = null), p.current().older, "the read is in flight")
        p.loadOlder()
        assertEquals(1, world.calls.count { it == "older" }, "a second tap while it reads does nothing")
        world.older.value = false
        assertNotNull(world.resume).resumeWith(Result.success(Outcome.Ok(Unit)))
        assertNull(p.current().older, "everything is loaded: the row goes")
    }

    @Test
    fun `AUDIT1-05 a timeline pick goes to that message, and Find does not open`() {
        val world = World()
        // The picked prompt says "Below"; a later reply has "below" inside its code, the first hit Find would take.
        world.transcript.value =
            stateOf(
                prompt("m1", "Below are two code blocks"),
                block("m1", "m2", "p2", 0, "values below one are clamped"),
                footer("m1"),
                prompt("m3", "say hi"),
                block("m3", "m4", "p4", 0, "Hi"),
                footer("m3"),
            )
        world.turnFeed.value =
            TurnFeed(
                ledger =
                    LedgerSnapshot(
                        1,
                        listOf(
                            message("m1", Role.User, "Below are two code blocks"),
                            message("m2", Role.Assistant, "values below one are clamped"),
                            message("m3", Role.User, "say hi"),
                            message("m4", Role.Assistant, "Hi"),
                        ),
                        emptyList(),
                    )
            )
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        p.openTimeline()
        p.timelineQuery("Below")
        assertNotNull(p.current().timeline?.entries?.firstOrNull { it.messageId == "m1" }, "the prompt is a result")
        p.timelineSelect("m1")
        val ui = p.current()
        assertNull(ui.find, "nothing else opens")
        assertNull(ui.timeline, "the timeline closes")
        assertEquals(world.transcript.value.indexOf("prompt:m1"), ui.arrival?.landOnIndex, "the view goes to that message")
    }
}
