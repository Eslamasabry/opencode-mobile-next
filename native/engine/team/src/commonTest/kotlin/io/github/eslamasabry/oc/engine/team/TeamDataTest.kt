package io.github.eslamasabry.oc.engine.team

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamDataTest {
    @Test
    fun `B-TEAM-010 Flutter's saved phone-team config reads as the phone engine`() {
        // The shape Dart `OrchestrationConfig.toJson` writes into the profile's `orchestration`.
        val config =
            TeamConfig.parse(
                """{"provider":"phoneEngine","url":"http://127.0.0.1:4098","city":"","hostMode":"phone",""" +
                    """"hostKind":"phone","front":true,"enabledAt":"2026-10-01T09:00:00.000Z"}"""
            )
        assertNotNull(config)
        assertEquals(TeamProvider.PhoneEngine, config.provider)
        assertEquals(TeamHostMode.Phone, config.hostMode)
        assertTrue(config.front)
        assertEquals("http://127.0.0.1:4098", config.loopbackBase)
    }

    @Test
    fun `B-TEAM-011 a config without an address reads as team off and an unknown provider as Gas City`() {
        assertNull(TeamConfig.parse("""{"provider":"phoneEngine","url":""}"""))
        assertNull(TeamConfig.parse("[]"))
        assertNull(TeamConfig.parse(null))
        assertEquals(TeamProvider.GasCity, TeamConfig.parse("""{"provider":"later","url":"https://h:1"}""")?.provider)
    }

    @Test
    fun `B-TEAM-012 the phone engine address must be loopback http with a port and nothing else`() {
        assertEquals("http://127.0.0.1:4098", TeamConfig.loopback("http://127.0.0.1:4098/"))
        assertEquals("http://[::1]:4098", TeamConfig.loopback("http://[::1]:4098"))
        listOf(
                "https://127.0.0.1:4098",
                "http://10.0.0.2:4098",
                "http://127.0.0.1",
                "http://u:p@127.0.0.1:4098",
                "http://127.0.0.1:4098/v1",
                "http://127.0.0.1:4098?x=1",
                "http://127.0.0.1:99999",
            )
            .forEach { assertNull(TeamConfig.loopback(it), it) }
    }

    @Test
    fun `B-TEAM-013 a schema-1 workspace decodes with Dart's defaults`() {
        val workspace =
            TeamWorkspaceJson.decode(
                """
                {"schemaVersion":1,"revision":12,"simulated":false,
                 "servers":[{"id":"s1","name":"This phone","phone":true}],
                 "roles":[{"id":"backend","name":"Backend"}],
                 "projects":[{"id":"p1","name":"Shop","status":"running","revision":3,"simulated":false,
                   "settings":{"mode":"parallel","maxLanes":2,"budget":{"daily":5}},
                   "specDraft":{"goal":"Sell","milestones":[{"id":"m1","title":"Cart","criteria":["adds"]}]},
                   "phases":[{"id":"ph1","milestoneId":"m1"}],
                   "tasks":[{"id":"t1","title":"Cart API","phaseId":"ph1","status":"running","dependsOn":["t0"],"steps":2},
                            {"id":"t2"}],
                   "requests":[{"id":"r1","title":"Which DB?","taskId":"t1"},{"id":"r2","answered":true}],
                   "spentToday":1.5,"usageReported":true}]}
                """
                    .trimIndent()
            )
        assertNotNull(workspace)
        assertEquals(12, workspace.revision)
        assertFalse(workspace.simulated)
        val project = workspace.projects.single()
        assertEquals(2, project.settings.maxLanes)
        assertEquals(5.0, project.settings.budget.daily)
        assertEquals("Cart", project.specDraft.milestones.single().title)
        assertEquals(listOf("t0"), project.tasks.first().dependsOn)
        // Dart defaults for a bare task.
        assertEquals("queued", project.tasks[1].status)
        assertEquals("frontend", project.tasks[1].roleId)
        assertEquals(listOf("r1"), project.openRequests.map { it.id })
        assertEquals("This phone", workspace.servers.single().name)
        assertTrue(workspace.servers.single().online)
    }

    @Test
    fun `B-TEAM-014 a workspace outside the shipped contract is refused whole`() {
        assertNull(TeamWorkspaceJson.decode("""{"schemaVersion":2,"revision":1,"projects":[],"servers":[],"roles":[]}"""))
        assertNull(TeamWorkspaceJson.decode("""{"schemaVersion":1,"projects":[],"servers":[],"roles":[]}"""))
        assertNull(TeamWorkspaceJson.decode("""{"schemaVersion":1,"revision":1,"projects":[],"roles":[]}"""))
        assertNull(TeamWorkspaceJson.decode("not json"))
    }
}
