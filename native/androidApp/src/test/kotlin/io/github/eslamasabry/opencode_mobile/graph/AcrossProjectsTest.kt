package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.GlobalSessionResult
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest

/** The home reads every project's conversations on a server that can list across projects (Flutter `_ocAcross`). */
class AcrossProjectsTest {
    private fun s(id: String, dir: String, parent: String? = null) =
        Session(SessionId(id), directory = dir, parentId = parent?.let(::SessionId))

    private class Ops(private val pages: Map<String?, ServerPage<GlobalSessionResult>>, private val fail: Failure? = null) :
        FakeOperationsGateway() {
        val asked = mutableListOf<Pair<String?, Boolean>>()

        override suspend fun listGlobalSessions(
            search: String?,
            includeArchived: Boolean,
            cursor: String?,
            limit: Int,
        ): Outcome<ServerPage<GlobalSessionResult>> {
            asked += cursor to includeArchived
            fail?.let { return Outcome.Failed(it) }
            return Outcome.Ok(pages.getValue(cursor))
        }
    }

    private fun result(vararg rows: Session, next: String? = null) =
        ServerPage(rows.map { GlobalSessionResult(it, null, it.directory) }.toImmutableList(), next)

    private fun gateway(across: Boolean, vararg own: Session) =
        FakeServerGateway(own.toList()).also {
            it.capabilities.value = ServerCapabilities.Unknown.copy(globalSessionSearch = across)
        }

    @Test
    fun `other projects' conversations join the current project's, children of the current project kept`() = runTest {
        val server = gateway(true, s("here", "/work/app"), s("kid", "/work/app", parent = "here"))
        val ops = Ops(mapOf(null to result(s("here", "/work/app"), s("far", "/work/other"), next = "c1"), "c1" to result(s("far2", "/work/web"))))
        val read = assertIs<Outcome.Ok<SessionPages>>(readFeedSessionPages(server, ops)).value
        assertEquals(listOf("here", "kid", "far", "far2"), read.sessions.map { it.id.raw })
        assertTrue(read.complete)
        assertEquals(listOf<Pair<String?, Boolean>>(null to true, "c1" to true), ops.asked)
    }

    @Test
    fun `a server that cannot list across projects reads the current project only`() = runTest {
        val server = gateway(false, s("here", "/work/app"))
        val ops = Ops(emptyMap())
        val read = assertIs<Outcome.Ok<SessionPages>>(readFeedSessionPages(server, ops)).value
        assertEquals(listOf("here"), read.sessions.map { it.id.raw })
        assertTrue(ops.asked.isEmpty())
    }

    @Test
    fun `a failed cross-project read keeps the current project and marks the list incomplete`() = runTest {
        val server = gateway(true, s("here", "/work/app"))
        val ops = Ops(emptyMap(), fail = Failure.Unavailable(FeatureId("globalSessionSearch")))
        val read = assertIs<Outcome.Ok<SessionPages>>(readFeedSessionPages(server, ops)).value
        assertEquals(listOf("here"), read.sessions.map { it.id.raw })
        assertFalse(read.complete)
    }

    @Test
    fun `four pages is the cap and says the list is incomplete`() = runTest {
        val server = gateway(true)
        val ops = Ops((0 until 6).associate { (if (it == 0) null else "c$it") to result(s("r$it", "/w$it"), next = "c${it + 1}") })
        val read = assertIs<Outcome.Ok<SessionPages>>(readFeedSessionPages(server, ops)).value
        assertEquals(4, read.sessions.size)
        assertFalse(read.complete)
    }

    @Test
    fun `only running conversations of another project are asked for their own requests`() = runTest {
        val asked = mutableListOf<String>()
        val base = gateway(true)
        base.capabilities.value = base.capabilities.value.copy(permissionRequests = true, forms = false)
        val server =
            object : io.github.eslamasabry.oc.core.domain.ServerGateway by base {
                override suspend fun sessionPermissions(
                    session: SessionId
                ): Outcome<kotlinx.collections.immutable.ImmutableList<io.github.eslamasabry.oc.core.model.PermissionRequest>> {
                    asked += session.raw
                    return Outcome.Ok(kotlinx.collections.immutable.persistentListOf())
                }
            }
        val sessions = listOf(s("near", "/work/app/"), s("far-run", "/work/other"), s("far-idle", "/work/web"))
        val running = mapOf(SessionId("near") to SessionStatus.Busy, SessionId("far-run") to SessionStatus.Busy)
        readOtherProjectAsks(server, sessions, running, "/work/app")
        assertEquals(listOf("far-run"), asked)
        asked.clear()
        readOtherProjectAsks(server, sessions, null, "/work/app")
        assertTrue(asked.isEmpty(), "unknown statuses read nothing")
    }
}
