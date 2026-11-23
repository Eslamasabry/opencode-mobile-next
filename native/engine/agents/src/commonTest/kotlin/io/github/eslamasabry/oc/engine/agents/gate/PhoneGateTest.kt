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
            "[\"claude\",\"2.1.330\",\"6016f90cac783e367fb1aa97c768a2efb94a0128a5b46b70fb3d8fcbbd9dd444\"," +
                "\"0.17.3\",\"5fcd5dc3354c0b6ac915876597704e716eb5b9c32ba7ba3540fa6ef8821ca8cc\",\"v24.22.3\"]",
            PhoneGate.fingerprint(claude, AgentArchitecture.Arm64, pins),
        )
        // The x64 artifact differs, so an x64 pass can never stand in for arm64 proof.
        assertEquals(
            "f390eaa0814e6a1bff74cbb47acb74cb9c89d30ced809fe0af5af00c7bfe8ebb",
            (AgentJson.parseOrNull(PhoneGate.fingerprint(claude, AgentArchitecture.X64, pins)) as JsonArray)[2]
                .let { (it as JsonPrimitive).content },
        )
    }

    @Test
    fun `B-LINUX-132 an unchanged pair keeps proof and a changed pin names why a check is needed`() {
        val v = "2.1.330"
        val sha = claude.artifactFor(AgentArchitecture.Arm64)!!.sha256
        assertEquals(CheckRequiredReason.FirstCheck, PhoneGate.changeReason(null, claude, pins))
        assertEquals(
            CheckRequiredReason.AgentVersion,
            PhoneGate.changeReason(
                gate("claude", "2.1.294", sha, "0.17.3", pins.paseo.packageLockSha256, "v24.22.3"),
                claude,
                pins,
            ),
        )
        assertEquals(
            CheckRequiredReason.HelperVersion,
            PhoneGate.changeReason(
                gate("claude", v, sha, "0.9.2", pins.paseo.packageLockSha256, "v24.22.3"),
                claude,
                pins,
            ),
        )
        assertEquals(
            CheckRequiredReason.HelperVersion,
            PhoneGate.changeReason(
                gate("claude", v, sha, "0.17.3", pins.paseo.packageLockSha256, "v22.0.0"),
                claude,
                pins,
            ),
        )
        assertEquals(
            CheckRequiredReason.InstallationChanged,
            PhoneGate.changeReason(
                gate("claude", v, "0".repeat(64), "0.17.3", pins.paseo.packageLockSha256, "v24.22.3"),
                claude,
                pins,
            ),
        )
        // Unreadable proof is never qualification.
        assertEquals(CheckRequiredReason.InstallationChanged, PhoneGate.changeReason(JsonObject(mapOf("fingerprint" to JsonPrimitive("broken"))), claude, pins))
    }

    @Test
    fun `B-LINUX-133 an older qualified version is offered only for the same CPU, and a tested Claude`() {
        val sha = claude.artifactFor(AgentArchitecture.Arm64)!!.sha256
        val older = gate("claude", "2.1.294", sha, "0.17.3", pins.paseo.packageLockSha256, "v24.22.3")
        assertEquals("2.1.294", PhoneGate.olderQualifiedVersion(older, claude, AgentArchitecture.Arm64, pins))
        assertNull(PhoneGate.olderQualifiedVersion(older, claude, AgentArchitecture.X64, pins))
        val untested = gate("claude", "2.1.200", sha, "0.17.3", pins.paseo.packageLockSha256, "v24.22.3")
        assertNull(PhoneGate.olderQualifiedVersion(untested, claude, AgentArchitecture.Arm64, pins))
        val helperMoved = gate("claude", "2.1.294", sha, "0.9.2", pins.paseo.packageLockSha256, "v24.22.3")
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
        // It carries only while the pinned helper is itself tested: a pin not yet proved on a device is not listed.
        assertEquals(
            pins.paseo.version in pins.paseoTestedVersions,
            PhoneGate.qualifies(flutter, claude, AgentArchitecture.Arm64, pins, "2.1.316"),
        )
        assertFalse(PhoneGate.qualifies(flutter, claude, AgentArchitecture.Arm64, pins, "2.1.330"))
        assertFalse(PhoneGate.qualifies(flutter, claude, AgentArchitecture.X64, pins, "2.1.316"))
        assertFalse(PhoneGate.qualifies(null, claude, AgentArchitecture.Arm64, pins, "2.1.316"))
        // A helper that was never tested does not carry proof.
        val odd = gate("claude", "2.1.316", sha, "0.1.0", lock, "v24.22.3")
        assertFalse(PhoneGate.qualifies(odd, claude, AgentArchitecture.Arm64, pins, "2.1.316"))
        // The current pair qualifies exactly.
        val now = gate("claude", "2.1.330", sha, "0.17.3", lock, "v24.22.3")
        assertTrue(PhoneGate.qualifies(now, claude, AgentArchitecture.Arm64, pins, "2.1.330"))
    }

    @Test
    fun `B-LINUX-134 stored JSON is written the way Dart jsonEncode writes it`() {
        assertEquals("[\"a\\\"b\",\"\\\\\",\"\\n\\t\\u0001\",\"é/\",null]", AgentJson.encodeStrings(listOf("a\"b", "\\", "\n\t\u0001", "é/", null)))
        val roundTrip = "{\"fx\":{\"fingerprint\":\"x\",\"architecture\":\"x64\",\"n\":1.0,\"b\":true}}"
        assertEquals(roundTrip, AgentJson.encode(AgentJson.parseOrNull(roundTrip)!!))
        assertNull(AgentJson.parseOrNull("{broken"))
    }
}
