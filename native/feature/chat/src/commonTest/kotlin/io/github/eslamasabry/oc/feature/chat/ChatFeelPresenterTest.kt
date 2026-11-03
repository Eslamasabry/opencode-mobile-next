package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.CodeCopy
import io.github.eslamasabry.oc.feature.chat.presentation.FenceText
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.StatusPick
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P12-CHAT-FEEL: message actions, links and code copy (Flutter `chat_message_actions.dart`, `openExternalLink`). */
class ChatFeelPresenterTest {
    private class RecordingOutside : ChatOutside {
        val calls = mutableListOf<String>()
        var copyWorks = true
        var check: LinkCheck = LinkCheck.Ask("example.com")
        var opens = true

        override fun copy(text: String): Boolean = copyWorks.also { calls += "copy $text" }

        override fun share(text: String): Boolean = true.also { calls += "share $text" }

        override fun checkLink(url: String): LinkCheck = check.also { calls += "check $url" }

        override fun openLink(url: String): Boolean = opens.also { calls += "open $url" }
    }

    private class RevertWorld : FakeChatWorld() {
        var revertResult: Outcome<Unit> = Outcome.Ok(Unit)
        var picture: Outcome<ByteArray> = Outcome.Ok(byteArrayOf(1, 2, 3))

        override suspend fun readPicture(path: String): Outcome<ByteArray> {
            calls += "read $path"
            return picture
        }

        override suspend fun revertFrom(from: MessageId): Outcome<Unit> {
            calls += "revert ${from.raw}"
            return revertResult
        }
    }

    private val world = RevertWorld()
    private val nav = RecordingNavigator()
    private val outside = RecordingOutside()

    private fun presenter() = ChatPresenter(world, nav, Immediate, outside = outside)

    @Test
    fun `B-KIT-051 a prompt's menu lists copy, share, select text, edit and resend, send again, fork, undo, delete last`() {
        world.transcript.value = stateOf(*turn(1, ask = "make it faster").toTypedArray())
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        assertEquals(
            listOf(
                MessageAction.Copy, MessageAction.Share, MessageAction.SelectText, MessageAction.EditAndResend,
                MessageAction.SendAgain, MessageAction.Fork, MessageAction.UndoFromHere, MessageAction.Delete,
            ),
            p.current().menu!!.actions,
        )
        p.openMessageMenu("footer:u1")
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.Share, MessageAction.SelectText, MessageAction.Changes, MessageAction.Delete),
            p.current().menu!!.actions,
            "a reply has no prompt-only actions; its turn's changed files are P12-REVIEW's",
        )
    }

    @Test
    fun `B-CHAT-120 undo from here follows the server's flag and is not offered while a reply runs`() {
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        world.capabilities.value = ServerCapabilities(sessionRevert = false)
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        assertTrue(MessageAction.UndoFromHere !in p.current().menu!!.actions)
        world.capabilities.value = ServerCapabilities()
        world.transcript.value = stateOf(*turn(1).toTypedArray(), run = RunFacts(busy = true))
        p.openMessageMenu("prompt:u1")
        val actions = p.current().menu!!.actions
        assertTrue(MessageAction.UndoFromHere !in actions && MessageAction.SendAgain !in actions, "steer, never queue")
        assertTrue(MessageAction.EditAndResend in actions, "editing into the composer is always allowed")
    }

    @Test
    fun `B-CHAT-120 undo from here opens the review's undo page, which asks first, and undoes nothing from the chat`() {
        // P12 integration: P12-REVIEW owns Undo from here (staged and one-step servers); the chat only opens its page.
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.UndoFromHere)
        assertEquals(listOf<AppRoute>(ReviewRoute.UndoFromHere(world.ref, "u1")), nav.opened)
        assertNull(p.current().confirm)
        assertEquals(emptyList(), world.calls, "nothing is undone before the review page confirms")
    }

    @Test
    fun `09-6_6 copy and share hand the words to the platform verbatim, and a failed copy says so`() {
        world.transcript.value =
            stateOf(prompt("u1", "Fix **the** build"), block("u1", "a1", "p", 0, "Done."), block("u1", "a1", "p", 7, "All green."), footer("u1"))
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.Copy)
        p.openMessageMenu("footer:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.Share)
        assertEquals(listOf("copy Fix **the** build", "share Done.\n\nAll green."), outside.calls)
        assertNull(p.current().clip, "the platform copies; nothing waits on the screen")
        assertNull(p.current().status)
        outside.copyWorks = false
        p.copyText("x")
        assertEquals(ChatStatusKind.ActionFailed, p.current().status?.kind)
    }

    @Test
    fun `09-6_6 select text opens the message's words in a sheet that Back closes before leaving`() {
        world.transcript.value = stateOf(*turn(1, answer = "Pick any **part**").toTypedArray())
        val p = presenter()
        p.openMessageMenu("footer:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.SelectText)
        assertEquals("Pick any **part**", p.current().select?.text)
        assertTrue(p.current().guardBack)
        p.leaveRequested()
        assertNull(p.current().select)
        assertEquals(emptyList(), nav.calls, "Back closed the sheet, it did not leave")
    }

    @Test
    fun `09-6_6 edit and resend puts the prompt's words in the composer once, never sends them`() {
        world.transcript.value = stateOf(*turn(1, ask = "rename the module").toTypedArray())
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.EditAndResend)
        val insert = assertNotNull(p.current().insert)
        assertEquals("rename the module", insert.text)
        p.insertDone(insert.id)
        assertNull(p.current().insert)
        assertEquals(emptyList(), world.calls, "the person sends it, the app never does")
    }

    @Test
    fun `R17 a reply link asks first naming its host, and opens only after yes`() {
        val p = presenter()
        p.openLink("https://example.com/docs")
        val ask = assertIs<ChatConfirm.OpenLink>(p.current().confirm)
        assertEquals("example.com", ask.host)
        assertEquals(listOf("check https://example.com/docs"), outside.calls)
        p.dismissConfirm()
        assertEquals(listOf("check https://example.com/docs"), outside.calls, "Cancel opens nothing")
        p.openLink("https://example.com/docs")
        p.confirmOpenLink()
        assertEquals("open https://example.com/docs", outside.calls.last())
        assertNull(p.current().confirm)
    }

    @Test
    fun `R17 a link the policy refuses says so on a dismissible status line and never opens`() {
        outside.check = LinkCheck.Refused
        val p = presenter()
        p.openLink("intent://evil")
        assertNull(p.current().confirm)
        val line = assertNotNull(p.current().status)
        assertEquals(ChatStatusKind.LinkRefused, line.kind)
        assertTrue(outside.calls.none { it.startsWith("open") })
        p.dismissStatus(StatusPick.LINK)
        assertNull(p.current().status)
    }

    @Test
    fun `B-CHAT-056 a code block copies its source without the fences, whole across stream chunks`() {
        assertEquals("val x = 1\nval y = 2", FenceText.body("```kotlin\nval x = 1\nval y = 2\n```"))
        assertEquals("kotlin", FenceText.info("```kotlin\nval x = 1\n```"))
        assertEquals(null, FenceText.info("plain"))
        fun chunk(start: Int, text: String, continued: Boolean, continues: Boolean) =
            BlockRow("b:a1/p:$start", "u1", RowKind.Code, "p", start, text, false, false, Origin.Loaded, continued, continues)
        val state =
            stateOf(
                prompt("u1", "show me"),
                chunk(0, "```sh\nline 1\nline 2", continued = false, continues = true),
                chunk(20, "line 3\nline 4", continued = true, continues = true),
                chunk(40, "line 5\n```", continued = true, continues = false),
                footer("u1"),
            )
        val rows = state.rows
        val expected = "line 1\nline 2\nline 3\nline 4\nline 5"
        for (key in listOf("b:a1/p:0", "b:a1/p:20", "b:a1/p:40")) {
            assertEquals(expected, CodeCopy.of(rows, rows.indexOfFirst { it.key == key }), "from $key")
        }
        assertNull(CodeCopy.of(rows, rows.indexOfFirst { it.key == "prompt:u1" }), "only code copies as code")
    }

    @Test
    fun `B-CHAT-059 a server picture opens in the viewer through the chat's own transport, and Back closes it`() {
        val p = presenter()
        p.openPicture("out/plot.png", "plot.png")
        val shown = assertNotNull(p.current().picture)
        assertEquals("plot.png", shown.name)
        assertEquals(3, shown.bytes?.size)
        assertEquals(listOf("read out/plot.png"), world.calls)
        p.leaveRequested()
        assertNull(p.current().picture)
        assertEquals(emptyList(), nav.calls, "Back closed the viewer first")
        world.picture = Outcome.Failed(Failure.Unexpected())
        p.openPicture("gone.png", "gone.png")
        assertTrue(p.current().picture!!.failed, "a plain line, never the error text")
    }
}
