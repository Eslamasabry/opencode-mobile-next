package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.input.ComposerInput
import io.github.eslamasabry.oc.engine.composer.input.ComposerValue
import io.github.eslamasabry.oc.engine.composer.input.HistoryKey
import io.github.eslamasabry.oc.engine.composer.input.Mentions
import io.github.eslamasabry.oc.engine.composer.input.PromptHistoryKeys
import io.github.eslamasabry.oc.engine.composer.input.PromptHistoryNavigation
import io.github.eslamasabry.oc.engine.composer.input.SendAction
import io.github.eslamasabry.oc.engine.composer.input.SendBlock
import io.github.eslamasabry.oc.engine.composer.input.SendDecision
import io.github.eslamasabry.oc.engine.composer.input.SendInputs
import io.github.eslamasabry.oc.engine.composer.input.TypedCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposerInputTest {
    private fun file(path: String) = Mentions.projectFile("/srv/app", path)

    @Test
    fun `B-CHAT-087 a mentioned file keeps its exact reference and range`() {
        val text = "Explain @lib/server.dart please"
        val resolved = Mentions.resolveFileMentions(text, listOf(file("lib/server.dart"))).single()
        assertEquals("text/plain", resolved.mime)
        assertEquals("server.dart", resolved.filename)
        assertEquals("file:///srv/app/lib/server.dart", resolved.url)
        assertEquals(8, resolved.mentionStart)
        assertEquals(24, resolved.mentionEnd)
        assertEquals("@lib/server.dart", text.substring(resolved.mentionStart!!, resolved.mentionEnd!!))
    }

    @Test
    fun `B-CHAT-087 a mention deleted before Send sends no file part`() {
        assertEquals(emptyList(), Mentions.resolveFileMentions("Explain it", listOf(file("lib/server.dart"))))
    }

    @Test
    fun `B-CHAT-087 a path that only starts like the mention does not count`() {
        assertEquals(emptyList(), Mentions.resolveFileMentions("see @lib/server.dartx", listOf(file("lib/server.dart"))))
        assertEquals(emptyList(), Mentions.resolveFileMentions("mail a@lib/server.dart", listOf(file("lib/server.dart"))))
        assertEquals(1, Mentions.resolveFileMentions("(@lib/server.dart).", listOf(file("lib/server.dart"))).size)
    }

    @Test
    fun `B-CHAT-087 other attachments pass through and an unranged file has no range`() {
        val photo = PromptAttachment("image/png", "a.png", "data:image/png;base64,AA==")
        assertEquals(listOf(photo), Mentions.resolveFileMentions("hi", listOf(photo)))
        assertNull(file("a.dart").mentionStart)
    }

    @Test
    fun `B-CHAT-087 file URLs are encoded like Dart Uri file`() {
        assertEquals("file:///srv/my%20app/a%23b.dart", Mentions.fileUrl("/srv/my app/a#b.dart"))
        assertEquals("file:///C:/work/a.dart", Mentions.fileUrl("C:\\work\\a.dart"))
    }

    @Test
    fun `agent mentions use the boundary rules, longest names first`() {
        val text = "ask @review and @reviewer, not mail@review or @reviewing"
        val mentions = Mentions.agentMentions(text, listOf("review", "reviewer", ""))
        assertEquals(listOf("review" to 4, "reviewer" to 16), mentions.map { it.name to it.start })
        assertEquals("@reviewer", mentions[1].value)
        assertEquals(25, mentions[1].end)
    }

    @Test
    fun `slash and mention triggers follow the caret`() {
        assertTrue(ComposerInput.slashWordAtCaret("/", 1))
        assertTrue(ComposerInput.slashWordAtCaret("fix this /rev", 13))
        assertFalse(ComposerInput.slashWordAtCaret("a/b", 3))
        assertTrue(ComposerInput.slashWordAtCaret("  /new", null))
        assertEquals("rev", ComposerInput.slashQuery("/rev"))
        assertNull(ComposerInput.slashQuery("/rev this"))
        assertEquals("lib/se", ComposerInput.mentionQueryAtCaret("look at @lib/se", 15))
        assertNull(ComposerInput.mentionQueryAtCaret("look at lib", 11))
        assertEquals(TypedCommand("review", "the diff\nplease"), ComposerInput.typedCommand(" /review the diff\nplease "))
        assertEquals("ls -la", ComposerInput.shellCommand("!ls -la"))
        assertNull(ComposerInput.shellCommand("! "))
    }

    @Test
    fun `steer never queue - a running turn is steered where steering exists`() {
        val base = SendInputs(text = "also check the tests", busy = true)
        assertEquals(SendAction.Steer, SendDecision.decide(base.copy(steering = true)))
        assertEquals(SendAction.SendMidTurn(MidTurnPrompt.RestartsTurn), SendDecision.decide(base.copy(midTurn = MidTurnPrompt.RestartsTurn)))
        assertEquals(SendAction.Blocked(SendBlock.BusyCannotSendYet), SendDecision.decide(base))
        assertEquals(SendAction.Send, SendDecision.decide(base.copy(busy = false)))
    }

    @Test
    fun `send guards - empty, read-only, in flight, offline, commands and shell`() {
        assertEquals(SendAction.Empty, SendDecision.decide(SendInputs(text = "  ")))
        assertEquals(SendAction.Send, SendDecision.decide(SendInputs(text = "", references = 1)))
        assertEquals(SendAction.Blocked(SendBlock.ReadOnly), SendDecision.decide(SendInputs(text = "hi", readOnly = true)))
        assertEquals(SendAction.Blocked(SendBlock.Sending), SendDecision.decide(SendInputs(text = "hi", sending = true)))
        assertEquals(SendAction.SendWhenOnline, SendDecision.decide(SendInputs(text = "hi", offline = true, busy = true)))
        assertEquals(SendAction.Blocked(SendBlock.UnknownCommand), SendDecision.decide(SendInputs(text = "/compact")))
        assertEquals(SendAction.Command(TypedCommand("compact", "")), SendDecision.decide(SendInputs(text = "/compact", commands = setOf("compact"))))
        assertEquals(SendAction.Send, SendDecision.decide(SendInputs(text = "/compact", attachments = 1)), "a command is not a prompt; with files it is")
        assertEquals(SendAction.Shell("ls"), SendDecision.decide(SendInputs(text = "!ls", shell = true)))
        assertEquals(SendAction.Blocked(SendBlock.NoShell), SendDecision.decide(SendInputs(text = "!ls")))
    }

    @Test
    fun `B-CHAT-089 history keys preserve selection, multi-line movement and composing text`() {
        val start = ComposerValue.collapsed("first\nsecond", 0)
        assertTrue(PromptHistoryKeys.isHistoryKey(HistoryKey.Up, start, suggestionsOpen = false, modifier = false))
        assertFalse(PromptHistoryKeys.isHistoryKey(HistoryKey.Up, start.copy(selectionStart = 7, selectionEnd = 7), false, false))
        assertFalse(PromptHistoryKeys.isHistoryKey(HistoryKey.Up, start.copy(selectionEnd = 3), false, false))
        assertFalse(PromptHistoryKeys.isHistoryKey(HistoryKey.Up, start.copy(composingStart = 0, composingEnd = 2), false, false))
        assertFalse(PromptHistoryKeys.isHistoryKey(HistoryKey.Up, start, suggestionsOpen = true, modifier = false))
        assertFalse(PromptHistoryKeys.isHistoryKey(HistoryKey.Up, start, suggestionsOpen = false, modifier = true))
        assertTrue(PromptHistoryKeys.isHistoryKey(HistoryKey.Down, ComposerValue.collapsed("ab", 2), false, false))
        val navigation = PromptHistoryNavigation()
        assertEquals("recent", navigation.move(true, start, listOf("recent", "older"))?.text)
        assertEquals("older", navigation.move(true, start, emptyList())?.text)
        assertNull(navigation.move(true, start, emptyList()))
        assertEquals(ComposerValue.collapsed("recent", 6), navigation.move(false, start, emptyList()))
        assertEquals(start, navigation.move(false, start, emptyList()), "walking back past the newest restores the draft")
        assertNull(navigation.original)
    }
}
