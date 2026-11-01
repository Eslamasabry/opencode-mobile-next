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
import kotlin.test.assertNull

class PhoneGateTest {
    private val pins = AgentPins.builtIn
    private val claude = AgentCatalog.builtIn.byId(AgentId.CLAUDE)!!

    private fun gate(vararg fp: String?, arch: String = "arm64") =
        JsonObject(mapOf("fingerprint" to JsonPrimitive(AgentJson.encodeStrings(fp.toList())), "architecture" to JsonPrimitive(arch)))

    @Test
    fun `B-LINUX-134 the gate fingerprint is Dart jsonEncode of the six pins, byte for byte`() {
        assertEquals(
            "[\"claude\",\"2.1.316\",\"55cdb8e162d190432696ea54010e0fca2e18d06fa7a7127fd563b9ffd4f22624\"," +
                "\"0.11.2\",\"b0bcc4f6191bd5c2daeda8ae398d636f3575173e699fa3868ab9e24f2e1fcdaa\",\"v24.21.0\"]",
            PhoneGate.fingerprint(claude, AgentArchitecture.Arm64, pins),
        )
        // The x64 artifact differs, so an x64 pass can never stand in for arm64 proof.
        assertEquals(
            "fd13c70000fec3f2116dfbcbb6121210b4a29328bf0e46210a59dd594a67d824",
            (AgentJson.parseOrNull(PhoneGate.fingerprint(claude, AgentArchitecture.X64, pins)) as JsonArray)[2]
                .let { (it as JsonPrimitive).content },
        )
    }

    @Test
    fun `B-LINUX-132 an unchanged pair keeps proof and a changed pin names why a check is needed`() {
        val v = "2.1.316"
        val sha = claude.artifactFor(AgentArchitecture.Arm64)!!.sha256
        assertEquals(CheckRequiredReason.FirstCheck, PhoneGate.changeReason(null, claude, pins))
        assertEquals(CheckRequiredReason.AgentVersion, PhoneGate.changeReason(gate("claude", "2.1.294", sha, "0.11.2", pins.paseo.packageLockSha256, "v24.21.0"), claude, pins))
        assertEquals(CheckRequiredReason.HelperVersion, PhoneGate.changeReason(gate("claude", v, sha, "0.9.2", pins.paseo.packageLockSha256, "v24.21.0"), claude, pins))
        assertEquals(CheckRequiredReason.HelperVersion, PhoneGate.changeReason(gate("claude", v, sha, "0.11.2", pins.paseo.packageLockSha256, "v22.0.0"), claude, pins))
        assertEquals(CheckRequiredReason.InstallationChanged, PhoneGate.changeReason(gate("claude", v, "0".repeat(64), "0.11.2", pins.paseo.packageLockSha256, "v24.21.0"), claude, pins))
        // Unreadable proof is never qualification.
        assertEquals(CheckRequiredReason.InstallationChanged, PhoneGate.changeReason(JsonObject(mapOf("fingerprint" to JsonPrimitive("broken"))), claude, pins))
    }

    @Test
    fun `B-LINUX-133 an older qualified version is offered only for the same CPU, and a tested Claude`() {
        val sha = claude.artifactFor(AgentArchitecture.Arm64)!!.sha256
        val older = gate("claude", "2.1.294", sha, "0.11.2", pins.paseo.packageLockSha256, "v24.21.0")
        assertEquals("2.1.294", PhoneGate.olderQualifiedVersion(older, claude, AgentArchitecture.Arm64, pins))
        assertNull(PhoneGate.olderQualifiedVersion(older, claude, AgentArchitecture.X64, pins))
        val untested = gate("claude", "2.1.200", sha, "0.11.2", pins.paseo.packageLockSha256, "v24.21.0")
        assertNull(PhoneGate.olderQualifiedVersion(untested, claude, AgentArchitecture.Arm64, pins))
        val helperMoved = gate("claude", "2.1.294", sha, "0.9.2", pins.paseo.packageLockSha256, "v24.21.0")
        // Faithful to the Dart order: the agent version is compared first, so a proof whose helper also moved is still
        // offered (recorded as an open problem in the S3-AGENTSE report).
        assertEquals("2.1.294", PhoneGate.olderQualifiedVersion(helperMoved, claude, AgentArchitecture.Arm64, pins))
    }

    @Test
    fun `B-LINUX-134 stored JSON is written the way Dart jsonEncode writes it`() {
        assertEquals("[\"a\\\"b\",\"\\\\\",\"\\n\\t\\u0001\",\"é/\",null]", AgentJson.encodeStrings(listOf("a\"b", "\\", "\n\t\u0001", "é/", null)))
        val roundTrip = "{\"fx\":{\"fingerprint\":\"x\",\"architecture\":\"x64\",\"n\":1.0,\"b\":true}}"
        assertEquals(roundTrip, AgentJson.encode(AgentJson.parseOrNull(roundTrip)!!))
        assertNull(AgentJson.parseOrNull("{broken"))
    }
}
