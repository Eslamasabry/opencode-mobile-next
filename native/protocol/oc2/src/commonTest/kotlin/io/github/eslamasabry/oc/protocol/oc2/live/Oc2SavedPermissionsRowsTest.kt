package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.SavedPermission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * B-164: "Always allowed actions" on an OpenCode 2 server (Flutter lib/api2/gateway_operations/ops_sessions.dart
 * listSavedPermissions / removeSavedPermission): a rule saved by 2.1.4 can be seen and removed after the update.
 */
class Oc2SavedPermissionsRowsTest {
    private val here = ServerLocation(directory = "/root/shop")

    @Test
    fun `B-164 OpenCode 2 advertises the always allowed list`() = runTest {
        assertTrue(Oc2Gateway.Capabilities.savedPermissionList)
        val server = RowServer { error("reading capabilities sends nothing") }
        assertTrue(server.gateway.capabilities.value.savedPermissionList)
        server.gateway.close()
    }

    @Test
    fun `OpenCode 2 does not promise listGlobalSessions, which its gateway does not implement`() {
        assertEquals(false, Oc2Gateway.Capabilities.globalSessionSearch)
    }

    @Test
    fun `B-164 the list reads the current project's saved grants by project id without a location`() = runTest {
        val server =
            RowServer(location = here) { seen ->
                if (seen.path.endsWith("/location")) json("""{"directory":"/root/shop","project":{"id":"prj_1"}}""")
                else
                    json(
                        """{"data":[
                          {"id":"psv_2","projectID":"prj_1","action":"bash","resource":"ls"},
                          {"id":"","action":"bash","resource":"rm"},
                          {"id":"psv_1","projectID":"prj_1","action":"bash","resource":"git status"}
                        ]}"""
                    )
            }
        val grants = assertIs<Outcome.Ok<List<SavedPermission>>>(server.gateway.savedPermissions()).value
        assertEquals(
            listOf(
                SavedPermission("psv_1", "prj_1", "bash", "git status"),
                SavedPermission("psv_2", "prj_1", "bash", "ls"),
            ),
            grants,
        )
        val (project, list) = server.seen
        assertEquals("/root/shop", project.query["location[directory]"])
        assertEquals("/api/permission/saved", list.path)
        assertEquals(mapOf("projectID" to "prj_1"), list.query)
        server.gateway.close()
    }

    @Test
    fun `B-164 removing a rule deletes it by id and sends no location`() = runTest {
        val server = RowServer(location = here) { respond("", HttpStatusCode.NoContent) }
        assertEquals(Outcome.Ok(Unit), server.gateway.removeSavedPermission("psv 1"))
        val delete = server.seen.single()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/permission/saved/psv%201", delete.path)
        assertTrue(delete.query.isEmpty())
        server.gateway.close()
    }

    @Test
    fun `B-164 a blank id sends nothing and a refused read is a failure`() = runTest {
        val blank = RowServer { error("nothing is sent for a blank id") }
        assertIs<Outcome.Failed>(blank.gateway.removeSavedPermission(" "))
        assertTrue(blank.seen.isEmpty())
        blank.gateway.close()

        val refused = RowServer { json("""{"error":"nope"}""", HttpStatusCode.InternalServerError) }
        assertIs<Outcome.Failed>(refused.gateway.savedPermissions())
        refused.gateway.close()
    }
}
