package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PhoneTeamTurnOnTest {
    private class Ports(val failAt: String? = null, val address: String = "http://127.0.0.1:4098") : PhoneTeamPorts {
        val calls = mutableListOf<String>()

        private fun step(name: String): Outcome<Unit> {
            calls += name
            return if (name == failAt) Outcome.Failed(Failure.Unexpected()) else Outcome.Ok(Unit)
        }

        override suspend fun stopServer() = step("stopServer")
        override suspend fun closeTerminals() = step("closeTerminals")
        override suspend fun startEngine(): Outcome<PhoneEngineAddress> {
            calls += "startEngine"
            return if (failAt == "startEngine") Outcome.Failed(Failure.Unexpected()) else Outcome.Ok(PhoneEngineAddress(address, "tok"))
        }
        override suspend fun stopEngine() { calls += "stopEngine" }
        override suspend fun startServer() = step("startServer")
        override suspend fun saveTeam(address: PhoneEngineAddress) = step("saveTeam")
    }

    @Test
    fun `B-TEAM-ON-1 turn on stops OpenCode, proves the phone, brings OpenCode back and keeps the team`() = runTest {
        val ports = Ports()
        assertTrue(PhoneTeamTurnOn(ports).run() is Outcome.Ok)
        assertEquals(listOf("stopServer", "closeTerminals", "startEngine", "startServer", "saveTeam"), ports.calls)
    }

    @Test
    fun `B-TEAM-ON-2 a failed safety check leaves the phone as it was`() = runTest {
        val ports = Ports(failAt = "startEngine")
        assertTrue(PhoneTeamTurnOn(ports).run() is Outcome.Failed)
        assertEquals(listOf("stopServer", "closeTerminals", "startEngine", "stopEngine", "startServer"), ports.calls)
    }

    @Test
    fun `B-TEAM-ON-3 an engine that is not on loopback is refused and saves nothing`() = runTest {
        val ports = Ports(address = "http://192.168.1.2:4098")
        assertTrue(PhoneTeamTurnOn(ports).run() is Outcome.Failed)
        assertTrue("saveTeam" !in ports.calls)
        assertEquals("startServer", ports.calls.last())
    }

    @Test
    fun `B-TEAM-ON-4 a refused save stops the engine and restores OpenCode`() = runTest {
        val ports = Ports(failAt = "saveTeam")
        assertTrue(PhoneTeamTurnOn(ports).run() is Outcome.Failed)
        assertEquals(listOf("stopEngine", "startServer"), ports.calls.takeLast(2))
    }
}
