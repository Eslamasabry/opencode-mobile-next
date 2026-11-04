package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeUnavailable
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderPresenter
import io.github.eslamasabry.oc.feature.chat.header.presentation.ClipKind
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P12-BUILD-AGENTS: the chat ⋯ "Continue on computer" journey (Flutter chat_session_menu `_continueOnComputer`). */
class ContinueOnComputerTest {
    private val world = ModelWorld()
    private val port = FakeHeaderPort()
    private val p = ChatHeaderPresenter(world, port, RecordingNavigator(), Immediate)

    @Test
    fun `B-AGENT-227 the menu offers Continue on computer after Continue with only when the server can resume`() {
        world.capabilities.value = ServerCapabilities(cliSessionResume = true, sessionModelProviderSwitching = true, serverCatalog = true)
        val menu = p.current().menu
        assertEquals(menu.indexOf(HeaderAction.ContinueWith) + 1, menu.indexOf(HeaderAction.ContinueOnComputer))
        world.capabilities.value = ServerCapabilities(cliSessionResume = false)
        assertFalse(HeaderAction.ContinueOnComputer in p.current().menu)
    }

    @Test
    fun `B-AGENT-227 the sheet shows the copyable resume command for this conversation's folder and Copy only writes the clipboard`() {
        port.facts.value = port.facts.value.copy(directory = "/home/me/my app")
        p.choose(HeaderAction.ContinueOnComputer)
        val sheet = assertIs<HeaderSheet.ContinueOnComputer>(p.current().sheet)
        assertEquals("cd '/home/me/my app' && opencode2 --session 's1'", sheet.command.command)
        assertEquals("opencode2", sheet.command.binary)
        assertTrue("refresh" in port.calls, "built from the folder the server reports now")
        p.copyResumeCommand()
        val clip = assertNotNull(p.current().clip)
        assertEquals(ClipKind.ResumeCommand, clip.kind)
        assertEquals("cd '/home/me/my app' && opencode2 --session 's1'", clip.text)
        assertEquals(listOf("refresh"), port.calls, "copying sends nothing to the server")
    }

    @Test
    fun `B-AGENT-228 a managed workspace or a missing folder says why instead of guessing a command`() {
        port.facts.value = port.facts.value.copy(workspaceId = "wrk_1")
        p.choose(HeaderAction.ContinueOnComputer)
        var sheet = assertIs<HeaderSheet.ContinueOnComputer>(p.current().sheet)
        assertNull(sheet.command.command)
        assertEquals(SessionResumeUnavailable.ManagedWorkspace, sheet.command.unavailable)
        port.facts.value = port.facts.value.copy(workspaceId = null, directory = "  ")
        sheet = assertIs<HeaderSheet.ContinueOnComputer>(p.current().sheet)
        assertEquals(SessionResumeUnavailable.MissingDirectory, sheet.command.unavailable)
        p.copyResumeCommand()
        assertNull(p.current().clip, "nothing to copy")
    }
}
