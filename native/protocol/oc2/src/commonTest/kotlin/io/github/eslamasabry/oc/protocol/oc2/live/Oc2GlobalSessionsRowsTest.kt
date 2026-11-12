package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.GlobalSessionResult
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** `Oc2Gateway.listGlobalSessions`: the unscoped root list Flutter's OpenCode 2 client sends (ops_projects.dart). */
class Oc2GlobalSessionsRowsTest {
    private val here = ServerLocation(directory = "/work/app", workspace = "wrk_1")

    private fun row(id: String, project: String, directory: String, archived: Boolean = false, parent: String? = null) =
        """{"id":"$id","projectID":"$project","title":"t $id",""" +
            (parent?.let { """"parentID":"$it",""" } ?: "") +
            """"location":{"directory":"$directory"},"time":{"created":1,"updated":2${if (archived) ",\"archived\":3" else ""}}}"""

    private fun page(vararg rows: String, next: String? = null) =
        """{"data":[${rows.joinToString(",")}],"cursor":{"next":${next?.let { "\"$it\"" } ?: "null"}}}"""

    @Test
    fun `the first page asks for every project's roots with no directory and no workspace`() = runTest {
        val server = RowServer(location = here) { json(page(row("ses_1", "prj_a", "/work/app"))) }
        try {
            server.gateway.listGlobalSessions(search = null, includeArchived = false, cursor = null, limit = 50)
            val sent = server.seen.single { it.path == "/api/session" }
            assertEquals("GET", sent.method)
            assertEquals("50", sent.query["limit"])
            assertEquals("desc", sent.query["order"])
            assertEquals("null", sent.query["parentID"])
            assertNull(sent.query["directory"])
            assertNull(sent.query["workspace"])
            assertNull(sent.query["search"])
        } finally {
            server.gateway.close()
        }
    }

    @Test
    fun `a typed search is trimmed and sent, a blank one is not`() = runTest {
        val server = RowServer(location = here) { json(page()) }
        try {
            server.gateway.listGlobalSessions(search = "  plan ", includeArchived = false, cursor = null, limit = 20)
            server.gateway.listGlobalSessions(search = "   ", includeArchived = false, cursor = null, limit = 20)
            val sent = server.seen.filter { it.path == "/api/session" }
            assertEquals("plan", sent[0].query["search"])
            assertNull(sent[1].query["search"])
        } finally {
            server.gateway.close()
        }
    }

    @Test
    fun `a later page sends the cursor alone`() = runTest {
        val server = RowServer(location = here) { json(page(row("ses_2", "prj_b", "/work/other"))) }
        try {
            server.gateway.listGlobalSessions(search = "plan", includeArchived = false, cursor = "c9", limit = 50)
            val sent = server.seen.single { it.path == "/api/session" }
            assertEquals(mapOf("cursor" to "c9"), sent.query)
        } finally {
            server.gateway.close()
        }
    }

    @Test
    fun `rows of other projects arrive with their project and folder, and the cursor is kept`() = runTest {
        val server =
            RowServer(location = here) {
                json(
                    page(
                        row("ses_1", "prj_a", "/work/app"),
                        row("ses_2", "prj_b", "/work/other"),
                        next = "c2",
                    )
                )
            }
        try {
            val result =
                assertIs<Outcome.Ok<ServerPage<GlobalSessionResult>>>(
                        server.gateway.listGlobalSessions(null, false, null, 50)
                    )
                    .value
            assertEquals(listOf("ses_1", "ses_2"), result.items.map { it.session.id.raw })
            assertEquals(listOf("prj_a", "prj_b"), result.items.map { it.session.projectId?.raw })
            assertEquals(listOf("/work/app", "/work/other"), result.items.map { it.projectDirectory })
            assertEquals("/work/other", result.items[1].session.directory)
            assertEquals("c2", result.nextCursor)
        } finally {
            server.gateway.close()
        }
    }

    @Test
    fun `archived rows stay out unless asked for, and an all-archived page keeps its cursor`() = runTest {
        val server =
            RowServer(location = here) { json(page(row("ses_1", "prj_a", "/work/app", archived = true), next = "c2")) }
        try {
            val hidden =
                assertIs<Outcome.Ok<ServerPage<GlobalSessionResult>>>(
                        server.gateway.listGlobalSessions(null, false, null, 50)
                    )
                    .value
            assertTrue(hidden.items.isEmpty())
            assertEquals("c2", hidden.nextCursor)
            val shown =
                assertIs<Outcome.Ok<ServerPage<GlobalSessionResult>>>(
                        server.gateway.listGlobalSessions(null, true, null, 50)
                    )
                    .value
            assertEquals(listOf("ses_1"), shown.items.map { it.session.id.raw })
        } finally {
            server.gateway.close()
        }
    }

    @Test
    fun `a server that answers not found is a failure the caller can fall back from`() = runTest {
        val server =
            RowServer(location = here) {
                json("""{"_tag":"NotFoundError"}""", io.ktor.http.HttpStatusCode.NotFound)
            }
        try {
            val failed = assertIs<Outcome.Failed>(server.gateway.listGlobalSessions(null, false, null, 50))
            assertIs<Failure.NotFound>(failed.failure)
        } finally {
            server.gateway.close()
        }
    }

    @Test
    fun `the session's outcome reaches the core session, an unknown or missing one stays unknown`() = runTest {
        fun withOutcome(id: String, outcome: String?) =
            """{"id":"$id","projectID":"prj_a","title":"t","location":{"directory":"/work/app"},""" +
                (outcome?.let { """"outcome":"$it",""" } ?: "") +
                """"time":{"created":1,"updated":2}}"""
        val server =
            RowServer(location = here) {
                json(
                    page(
                        withOutcome("ses_ok", "succeeded"),
                        withOutcome("ses_bad", "failed"),
                        withOutcome("ses_int", "interrupted"),
                        withOutcome("ses_new", "something-newer"),
                        withOutcome("ses_none", null),
                    )
                )
            }
        try {
            val items =
                assertIs<Outcome.Ok<ServerPage<GlobalSessionResult>>>(
                        server.gateway.listGlobalSessions(null, false, null, 50)
                    )
                    .value
                    .items
                    .associate { it.session.id.raw to it.session.lastRunOutcome }
            assertEquals(io.github.eslamasabry.oc.core.model.SessionRunOutcome.Succeeded, items["ses_ok"])
            assertEquals(io.github.eslamasabry.oc.core.model.SessionRunOutcome.Failed, items["ses_bad"])
            assertEquals(io.github.eslamasabry.oc.core.model.SessionRunOutcome.Interrupted, items["ses_int"])
            assertNull(items["ses_new"])
            assertNull(items["ses_none"])
        } finally {
            server.gateway.close()
        }
    }
}
