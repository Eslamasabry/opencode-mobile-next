package io.github.eslamasabry.oc.engine.agents.gate

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneGateTest {
    private val pins = AgentPins.builtIn
    private val claude = AgentCatalog.builtIn.byId(AgentId.CLAUDE)!!

    private fun gate(vararg fp: String?, arch: String = "arm64") =
        JsonObject(mapOf("fingerprint" to JsonPrimitive(AgentJson.encodeStrings(fp.toList())), "architecture" to JsonPrimitive(arch)))

    @Test
    fun `B-LINUX-134 the gate fingerprint is Dart jsonEncode of the six pins, byte for byte`() {
        assertEquals(
            "[\"claude\",\"2.1.319\",\"a7e687fd1cb054d651c7588223de3786c99d4c682365e3c3ae1e59ce7f3a591f\"," +
                "\"0.15.3\",\"9a676a737ac42bc021d3a6fcf74b8e9d5b7c3bb9a300cb9288ca7254919c5f6a\",\"v24.22.2\"]",
            PhoneGate.fingerprint(claude, AgentArchitecture.Arm64, pins),
        )
        // The x64 artifact differs, so an x64 pass can never stand in for arm64 proof.
        assertEquals(
            "dabafec82ca1319fa43d8be36a10fc61b1d11821cb98ad5df04ad95e199b8eed",
            (AgentJson.parseOrNull(PhoneGate.fingerprint(claude, AgentArchitecture.X64, pins)) as JsonArray)[2]
                .let { (it as JsonPrimitive).content },
        )
    }

    @Test
    fun `B-LINUX-132 an unchanged pair keeps proof and a changed pin names why a check is needed`() {
        val v = "2.1.319"
        val sha = claude.artifactFor(AgentArchitecture.Arm64)!!.sha256
        assertEquals(CheckRequiredReason.FirstCheck, PhoneGate.changeReason(null, claude, pins))
        assertEquals(CheckRequiredReason.AgentVersion, PhoneGate.changeReason(gate("claude", "2.1.294", sha, "0.15.3", pins.paseo.packageLockSha256, "v24.22.2"), claude, pins))
        assertEquals(CheckRequiredReason.HelperVersion, PhoneGate.changeReason(gate("claude", v, sha, "0.9.2", pins.paseo.packageLockSha256, "v24.22.2"), claude, pins))
        assertEquals(CheckRequiredReason.HelperVersion, PhoneGate.changeReason(gate("claude", v, sha, "0.15.3", pins.paseo.packageLockSha256, "v22.0.0"), claude, pins))
        assertEquals(CheckRequiredReason.InstallationChanged, PhoneGate.changeReason(gate("claude", v, "0".repeat(64), "0.15.3", pins.paseo.packageLockSha256, "v24.22.2"), claude, pins))
        // Unreadable proof is never qualification.
        assertEquals(CheckRequiredReason.InstallationChanged, PhoneGate.changeReason(JsonObject(mapOf("fingerprint" to JsonPrimitive("broken"))), claude, pins))
    }

    @Test
    fun `B-LINUX-133 an older qualified version is offered only for the same CPU, and a tested Claude`() {
        val sha = claude.artifactFor(AgentArchitecture.Arm64)!!.sha256
        val older = gate("claude", "2.1.294", sha, "0.15.3", pins.paseo.packageLockSha256, "v24.22.2")
        assertEquals("2.1.294", PhoneGate.olderQualifiedVersion(older, claude, AgentArchitecture.Arm64, pins))
        assertNull(PhoneGate.olderQualifiedVersion(older, claude, AgentArchitecture.X64, pins))
        val untested = gate("claude", "2.1.200", sha, "0.15.3", pins.paseo.packageLockSha256, "v24.22.2")
        assertNull(PhoneGate.olderQualifiedVersion(untested, claude, AgentArchitecture.Arm64, pins))
        val helperMoved = gate("claude", "2.1.294", sha, "0.9.2", pins.paseo.packageLockSha256, "v24.22.2")
        // Faithful to the Dart order: the agent version is compared first, so a proof whose helper also moved is still
        // offered (recorded as an open problem in the S3-AGENTSE report).
        assertEquals("2.1.294", PhoneGate.olderQualifiedVersion(helperMoved, claude, AgentArchitecture.Arm64, pins))
    }

    @Test
    fun `proof from the Flutter app still counts when it was given on another tested helper, never on another CPU or version`() {
        val sha = claude.artifactFor(AgentArchitecture.Arm64)!!.sha256
        val lock = pins.paseo.packageLockSha256
        val flutter = gate("claude", "2.1.316", sha, "0.11.2", "other-lock", "v22.0.0")
        // The agent runs at the version the proof was given on, and that version is a tested one.
        assertTrue(PhoneGate.qualifies(flutter, claude, AgentArchitecture.Arm64, pins, "2.1.316"))
        assertFalse(PhoneGate.qualifies(flutter, claude, AgentArchitecture.Arm64, pins, "2.1.319"))
        assertFalse(PhoneGate.qualifies(flutter, claude, AgentArchitecture.X64, pins, "2.1.316"))
        assertFalse(PhoneGate.qualifies(null, claude, AgentArchitecture.Arm64, pins, "2.1.316"))
        // A helper that was never tested does not carry proof.
        val odd = gate("claude", "2.1.316", sha, "0.1.0", lock, "v24.22.2")
        assertFalse(PhoneGate.qualifies(odd, claude, AgentArchitecture.Arm64, pins, "2.1.316"))
        // The current pair qualifies exactly.
        val now = gate("claude", "2.1.319", sha, "0.15.3", lock, "v24.22.2")
        assertTrue(PhoneGate.qualifies(now, claude, AgentArchitecture.Arm64, pins, "2.1.319"))
    }

    @Test
    fun `B-LINUX-134 stored JSON is written the way Dart jsonEncode writes it`() {
        assertEquals("[\"a\\\"b\",\"\\\\\",\"\\n\\t\\u0001\",\"é/\",null]", AgentJson.encodeStrings(listOf("a\"b", "\\", "\n\t\u0001", "é/", null)))
        val roundTrip = "{\"fx\":{\"fingerprint\":\"x\",\"architecture\":\"x64\",\"n\":1.0,\"b\":true}}"
        assertEquals(roundTrip, AgentJson.encode(AgentJson.parseOrNull(roundTrip)!!))
        assertNull(AgentJson.parseOrNull("{broken"))
    }
}
