package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifact
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifactFormat
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCommandApproval
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentInstallRecipe
import io.github.eslamasabry.oc.engine.agents.catalog.AgentLimitation
import io.github.eslamasabry.oc.engine.agents.catalog.AgentResumeReason
import io.github.eslamasabry.oc.engine.agents.catalog.AgentRoute
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSetupMethod
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.phoneAgentChoosable
import io.github.eslamasabry.oc.feature.agents.hub.phoneAgentShown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnharnessedReadinessTest {
    @Test
    fun `B-AGENT-UH-024 setup and readiness use the same private owner behind a phone alias`() {
        val phone = ProfileId("phone-view")
        assertEquals(ProfileId("private-owner"), PhoneProviderOwner.resolve(phone, "private-owner"))
        assertEquals(phone, PhoneProviderOwner.resolve(phone, null))
        assertEquals(phone, PhoneProviderOwner.resolve(phone, "../outside"))
    }

    @Test
    fun `B-AGENT-UH-022 a listed model cannot make an unconfigured agent ready anywhere`() {
        val descriptor =
            AgentDescriptor(
                AgentId("unharnessed"),
                "Unharnessed",
                "unharnessed",
                AgentRoute.AcpPaseo,
                "unharnessed",
                AgentSignInMethod.ApiKeyHost,
                AgentInstallRecipe(
                    "0.9.8-phone.1",
                    "unharnessed",
                    mapOf(
                        AgentArchitecture.Arm64 to
                            AgentArtifact(
                                "https://example.invalid/bundle",
                                "a".repeat(64),
                                AgentArtifactFormat.Executable,
                            )
                    ),
                ),
                limitation = AgentLimitation.NeedsHostSetup,
                resumeReason = AgentResumeReason.ResumeUnverified,
                commandApproval = AgentCommandApproval.NeverAsks,
                setupMethod = AgentSetupMethod.KeyOrLocalServer,
            )
        for (phase in listOf(null, AgentSignInPhase.Failed, AgentSignInPhase.SignedOut)) {
            val status =
                PhoneAgentRules.status(descriptor, AgentArchitecture.Arm64, true, true, true, phase)
            assertEquals(PhoneAgentStatus.SignInNeeded, status)
            val row = PhoneAgentRow("unharnessed", "Unharnessed", status)
            assertFalse(phoneAgentChoosable(phoneAgentShown(row, null)))
        }
        val configured =
            PhoneAgentRules.status(
                descriptor,
                AgentArchitecture.Arm64,
                true,
                true,
                true,
                AgentSignInPhase.SignedIn,
            )
        assertEquals(PhoneAgentStatus.Ready, configured)
        assertTrue(
            phoneAgentChoosable(
                phoneAgentShown(PhoneAgentRow("unharnessed", "Unharnessed", configured), null)
            )
        )
    }
}
