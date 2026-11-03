package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The OC2 reads behind Search conversations and Usage (unit P12-SEARCH-USAGE). */
class Oc2SearchUsageTest {
    private val requests = mutableListOf<HttpRequestData>()

    /** [reply] answers by path; a null answer is a 404. */
    private fun gateway(reply: (HttpRequestData) -> String?): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(
                    MockEngine { request ->
                        requests += request
                        val body = reply(request)
                        if (body == null) respond("""{"_tag":"NotFoundError"}""", HttpStatusCode.NotFound)
                        else respond(body, HttpStatusCode.OK)
                    }
                ) {
                    expectSuccess = false
                    followRedirects = false
                },
                { TransportError.Other },
            ),
            ServerOrigin("https", "example.test", 443),
            ConnectionCredentials.None,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
            },
            Redactor(),
            initialLocation = ServerLocation(directory = "/work/app"),
            initialDialect = Oc2Dialect.Beta,
        )

    private fun session(id: String, project: String, directory: String, archived: Long? = null): String =
        """{"id":"$id","projectID":"$project","title":"t $id","location":{"directory":"$directory"},""" +
            """"time":{"created":1,"updated":2${archived?.let { ",\"archived\":$it" } ?: ""}}}"""

    @Test
    fun `B-PROTO-020 a short page keeps the cursor the server returned`() = runTest {
        val g = gateway { """{"data":[${session("ses_1", "prj_a", "/work/app")}],"cursor":{"next":"c2"}}""" }
        try {
            val page = assertIs<Outcome.Ok<*>>(Oc2SearchUsage.searchSessions(g, "plan", null, 50)).value
                as io.github.eslamasabry.oc.core.model.ServerPage<*>
            assertEquals(1, page.items.size)
            assertEquals("c2", page.nextCursor)
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-PROTO-020 an archived-only page arrives empty and still keeps its cursor`() = runTest {
        val g = gateway { """{"data":[${session("ses_1", "prj_a", "/work/app", archived = 5)}],"cursor":{"next":"c2"}}""" }
        try {
            val page = assertIs<Outcome.Ok<*>>(Oc2SearchUsage.searchSessions(g, "plan", null, 50)).value
                as io.github.eslamasabry.oc.core.model.ServerPage<*>
            assertEquals(0, page.items.size)
            assertEquals("c2", page.nextCursor)
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-PROTO-122 the current project is offered for usage even when no recent conversation is in it`() = runTest {
        val g = gateway { request ->
            when (request.url.encodedPath) {
                "/api/session" -> """{"data":[${session("ses_1", "prj_other", "/work/other")}],"cursor":{"next":"c2"}}"""
                "/api/location" ->
                    """{"directory":"/work/app","project":{"id":"prj_app","directory":"/work/app","canonical":"/work/app"}}"""
                else -> null
            }
        }
        try {
            val choices = assertIs<Outcome.Ok<List<Oc2ProjectChoice>>>(Oc2SearchUsage.projectChoices(g)).value
            assertEquals(
                listOf(Oc2ProjectChoice("prj_app", "/work/app"), Oc2ProjectChoice("prj_other", "/work/other")),
                choices,
            )
            val location = requests.first { it.url.encodedPath == "/api/location" }
            assertEquals("/work/app", location.url.parameters["location[directory]"])
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-PROTO-122 a server answering only project current still names the current project`() = runTest {
        val g = gateway { request ->
            when (request.url.encodedPath) {
                "/api/session" -> """{"data":[]}"""
                "/api/project/current" -> """{"id":"prj_app","worktree":"/work/app"}"""
                else -> null
            }
        }
        try {
            val choices = assertIs<Outcome.Ok<List<Oc2ProjectChoice>>>(Oc2SearchUsage.projectChoices(g)).value
            assertEquals(listOf("prj_app"), choices.map { it.id })
        } finally {
            g.close()
        }
    }

    @Test
    fun `B-PROTO-122 a current project that cannot be read keeps the projects from conversations`() = runTest {
        val g = gateway { request ->
            when (request.url.encodedPath) {
                "/api/session" -> """{"data":[${session("ses_1", "prj_other", "/work/other")}]}"""
                else -> null
            }
        }
        try {
            val choices = assertIs<Outcome.Ok<List<Oc2ProjectChoice>>>(Oc2SearchUsage.projectChoices(g)).value
            assertEquals(listOf("prj_other"), choices.map { it.id })
        } finally {
            g.close()
        }
    }
}
