package io.github.eslamasabry.oc.feature.files

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ids.WorkspaceId
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.domain.LocatedFileGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.feature.files.presentation.ConversationFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Codex files #1: Files reads the conversation's own project, not the gateway's shared location. */
class ConversationFilesTest {
    private class Located(val fake: FakeServerGateway) : ServerGateway by fake, LocatedFileGateway {
        val at = mutableListOf<ServerLocation>()

        override fun filesAt(location: ServerLocation): FileGateway {
            at += location
            return FakeFileGateway(nodes = mapOf("" to listOf(FileNode("a.kt", "a.kt", false))))
        }
    }

    @Test
    fun `B-NAV-026 a conversation in project A lists A even while the gateway points at B`() = runTest {
        val fake = FakeServerGateway(listOf(Session(SessionId("s1"), directory = "/work/a", workspaceId = WorkspaceId("w"))))
        fake.setLocation(ServerLocation("/work/b"))
        val gateway = Located(fake)
        val files = ConversationFiles(gateway, SessionId("s1"))
        val listed = files.listFiles("")
        assertEquals(listOf("a.kt"), (listed as Outcome.Ok).value.map { it.path })
        assertEquals(listOf(ServerLocation("/work/a", "w")), gateway.at)
        assertEquals("/work/a", files.directory())
        assertTrue("listFiles" !in fake.calls, "the shared-location tree was read")
    }

    @Test
    fun `B-NAV-026 an unreadable conversation fails the read instead of showing another project`() = runTest {
        val fake = FakeServerGateway(listOf(Session(SessionId("s1"), directory = "/work/a")))
        fake.failNext = Failure.Server(500)
        val gateway = Located(fake)
        assertIs<Outcome.Failed>(ConversationFiles(gateway, SessionId("s1")).listFiles(""))
        assertTrue(gateway.at.isEmpty())
    }
}
