package io.github.eslamasabry.oc.engine.agents.readiness

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCapabilities
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentInstallRecipe
import io.github.eslamasabry.oc.engine.agents.catalog.AgentLimitation
import io.github.eslamasabry.oc.engine.agents.catalog.AgentResumeReason
import io.github.eslamasabry.oc.engine.agents.catalog.AgentRoute
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.catalog.AgentUnavailableReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/phone_agents_test.dart (B-LINUX-140, B-LINUX-141) plus the plan 17 §1.4 order table. */
class AgentReadinessTest {
    private val claude = AgentCatalog.builtIn.byId(AgentId("claude"))!!
    private val allProof = AgentCapabilities(true, true, true, true, true)
    private val server = ServerCapabilities(hostAgentPermissionActions = true)

    private fun runtime(
        id: String = "claude",
        installed: Boolean = true,
        available: Boolean = true,
        qualified: Boolean = true,
        stopped: Boolean = false,
        phase: AgentSignInPhase? = AgentSignInPhase.SignedIn,
        capabilities: AgentCapabilities = allProof,
        resetAt: Long? = null,
    ) = PhoneAgentRuntime(
        agentId = AgentId(id),
        installed = installed,
        hostAvailable = available,
        architectureQualified = qualified,
        stoppedInBackground = stopped,
        signInPhase = phase,
        capabilities = capabilities,
        resetAtEpochMillis = resetAt,
    )

    private fun row(
        fact: PhoneAgentRuntime? = null,
        policy: UnverifiedResumePolicy = UnverifiedResumePolicy.Label,
        serverCapabilities: ServerCapabilities = server,
        descriptor: AgentDescriptor = claude,
        architecture: AgentArchitecture = AgentArchitecture.Arm64,
    ) = AgentReadiness.row(descriptor, architecture, serverCapabilities, fact, policy)

    @Test
    fun `B-LINUX-140 uninstalled Claude remains in setup with Install despite unknown resume`() {
        val result = row(PhoneAgentRuntime(AgentId("claude")))
        assertTrue(result.setupVisible)
        assertTrue(result.installable)
        assertEquals(PhoneAgentStatus.NeedsInstall, result.status)
        assertEquals(AgentFixAction.Install, result.fixAction)
        assertEquals(AgentHiddenReason.NeedsInstall, result.hiddenReason)
        assertFalse(result.chatVisible)
        assertFalse(result.chatSelectable)
        assertFalse(result.capabilities.resumeVerified)
        assertFalse(result.installedOnPhone)
    }

    @Test
    fun `B-LINUX-140 unknown or mismatched host facts cannot borrow another agent proof`() {
        for (fact in listOf(null, runtime(id = "codex"))) {
            val result = row(fact)
            assertEquals(PhoneAgentStatus.NeedsInstall, result.status)
            assertEquals(AgentFixAction.Install, result.fixAction)
            assertEquals(AgentHiddenReason.RuntimeUnknown, result.hiddenReason)
            assertTrue(result.setupVisible)
            assertFalse(result.chatSelectable)
            assertFalse(result.capabilities.resumeVerified)
            assertFalse(result.capabilities.permissions)
        }
    }

    @Test
    fun `B-LINUX-140 default runtime is unqualified and defaults every capability off`() {
        val fact = PhoneAgentRuntime(AgentId("claude"))
        assertFalse(fact.architectureQualified)
        assertEquals(AgentCapabilities.None, fact.capabilities)
        assertNull(fact.signInPhase)
        val result = row(runtime(qualified = false))
        assertEquals(PhoneAgentStatus.NeedsQualification, result.status)
        assertEquals(AgentFixAction.RunPhoneCheck, result.fixAction)
        assertFalse(result.chatSelectable)
        assertFalse(result.capabilities.resumeVerified)
        assertTrue(result.installedOnPhone)
    }

    @Test
    fun `B-LINUX-140 partial files permit recovery without installation or capability proof`() {
        val fx = AgentCatalog.builtIn.byId(AgentId("fx"))!!
        val partial = row(PhoneAgentRuntime(AgentId("fx"), payloadPresent = true), descriptor = fx)
        assertTrue(partial.hasPartialPayload)
        assertEquals(PhoneAgentStatus.NeedsInstall, partial.status)
        assertEquals(AgentFixAction.Install, partial.fixAction)
        assertFalse(partial.chatVisible)
        assertFalse(partial.chatSelectable)
        assertFalse(partial.capabilities.cancel)
        assertFalse(row(PhoneAgentRuntime(AgentId("claude"), payloadPresent = true), descriptor = fx).hasPartialPayload)
        assertFalse(
            row(PhoneAgentRuntime(AgentId("fx"), installed = true, payloadPresent = true), descriptor = fx)
                .hasPartialPayload,
        )
    }

    @Test
    fun `B-LINUX-140 a fresh install with no host yet goes to the phone check, never Resume`() {
        val result = row(runtime(available = false, qualified = false, phase = AgentSignInPhase.SignedOut))
        assertEquals(PhoneAgentStatus.NeedsQualification, result.status)
        assertEquals(AgentFixAction.RunPhoneCheck, result.fixAction)
        assertFalse(result.chatSelectable)
    }

    @Test
    fun `B-LINUX-140 unverified restoration is shown and selectable by default`() {
        val result = row(runtime(capabilities = AgentCapabilities.None))
        assertTrue(result.setupVisible)
        assertEquals(PhoneAgentStatus.Ready, result.status)
        assertTrue(result.chatVisible)
        assertTrue(result.chatSelectable)
        assertNull(result.hiddenReason)
        assertTrue(result.resumeUnverified)
        assertEquals(AgentStatusKey.AgentResumeUnverified, result.statusKey)
    }

    @Test
    fun `B-LINUX-140 legacy hide argument cannot reintroduce the replaced owner rule`() {
        val result = row(runtime(capabilities = AgentCapabilities.None), policy = UnverifiedResumePolicy.Hide)
        assertTrue(result.chatVisible)
        assertTrue(result.chatSelectable)
        assertTrue(result.resumeUnverified)
        assertFalse(result.capabilities.resumeVerified)
        assertFalse(result.capabilities.images)
    }

    @Test
    fun `B-LINUX-140 runtime proof enables selection without inferring it from recipe or id`() {
        val result = row(runtime())
        assertFalse(claude.capabilities.resumeVerified)
        assertTrue(result.chatSelectable)
        assertTrue(result.capabilities.resumeVerified)
        assertFalse(result.resumeUnverified)
        assertNull(result.hiddenReason)
        assertEquals(AgentStatusKey.AgentReady, result.statusKey)
        assertTrue(result.installedOnPhone)
    }

    @Test
    fun `B-LINUX-140 matching server gates narrow images and request-scoped permissions`() {
        val result = row(
            runtime(),
            serverCapabilities = ServerCapabilities(
                promptAttachments = false,
                hostAgentPermissionActions = false,
                cliSessionResume = false,
                serverCatalog = false,
            ),
        )
        assertFalse(result.capabilities.images)
        assertFalse(result.capabilities.permissions)
        // These gates do not describe model listing or durable ACP resume.
        assertTrue(result.capabilities.resumeVerified)
        assertTrue(result.capabilities.modelList)
        assertTrue(result.capabilities.cancel)
    }

    @Test
    fun `B-LINUX-140 sign-in and background stop override stale capability proofs`() {
        val signedOut = row(runtime(phase = AgentSignInPhase.SignedOut))
        assertEquals(PhoneAgentStatus.SignedOut, signedOut.status)
        assertEquals(AgentFixAction.SignIn, signedOut.fixAction)
        assertFalse(signedOut.chatSelectable)
        assertFalse(signedOut.capabilities.cancel)
        val stopped = row(runtime(stopped = true, available = false))
        assertEquals(PhoneAgentStatus.StoppedInBackground, stopped.status)
        assertEquals(AgentFixAction.Resume, stopped.fixAction)
        assertFalse(stopped.chatSelectable)
        val unavailable = row(runtime(available = false))
        assertEquals(PhoneAgentStatus.Unavailable, unavailable.status)
        assertEquals(AgentFixAction.Resume, unavailable.fixAction)
        assertEquals(AgentHiddenReason.HostUnavailable, unavailable.hiddenReason)
    }

    @Test
    fun `B-LINUX-141 usage limit retains only host reset time and never estimates one`() {
        val resetAt = 1_791_115_200_000L // 2026-10-04T12:00Z
        val limited = row(runtime(phase = AgentSignInPhase.LimitReached, resetAt = resetAt))
        assertEquals(PhoneAgentStatus.LimitReached, limited.status)
        assertFalse(limited.chatSelectable)
        assertEquals(resetAt, limited.resetAtEpochMillis)
        assertNull(limited.fixAction)
        assertNull(row(runtime(phase = AgentSignInPhase.LimitReached)).resetAtEpochMillis)
        assertNull(row(runtime(resetAt = resetAt)).resetAtEpochMillis)
    }

    @Test
    fun `B-LINUX-140 unavailable recipes cannot become selectable from fabricated host proof`() {
        val unavailable = AgentDescriptor(
            id = AgentId("future"),
            name = "Future",
            iconKey = "future",
            route = AgentRoute.AcpPaseo,
            providerId = "future",
            signInMethod = AgentSignInMethod.None,
            availability = AgentAvailability.Hidden,
            unavailableReason = AgentUnavailableReason.RecipeUnverified,
            resumeReason = AgentResumeReason.SavedChatsNeedCheck,
        )
        val result = row(runtime(id = "future"), descriptor = unavailable)
        assertFalse(result.chatSelectable)
        assertFalse(result.installable)
        assertEquals(PhoneAgentStatus.Unavailable, result.status)
        assertEquals(AgentHiddenReason.CatalogUnavailable, result.hiddenReason)
    }

    @Test
    fun `B-LINUX-140 server types are not phone agents and are never listed`() {
        for (id in listOf("opencode", "opencode2")) {
            val descriptor = AgentCatalog.builtIn.byId(AgentId(id)) ?: continue
            assertFalse(row(null, descriptor = descriptor).setupVisible, id)
        }
        val visible = AgentCatalog.builtIn.agents.filter { row(null, descriptor = it).setupVisible }.map { it.route }
        assertTrue(visible.isNotEmpty())
        assertTrue(visible.all { it == AgentRoute.PaseoNative || it == AgentRoute.AcpPaseo })
    }

    @Test
    fun `B-LINUX-141 a processor with no download says so, not not available yet`() {
        val recipe = claude.recipe!!
        val arm64Only = AgentDescriptor(
            id = AgentId("arm-only"),
            name = "Arm only",
            iconKey = "arm-only",
            route = AgentRoute.PaseoNative,
            providerId = "arm-only",
            signInMethod = AgentSignInMethod.None,
            recipe = AgentInstallRecipe(
                recipe.version,
                recipe.executable,
                mapOf(AgentArchitecture.Arm64 to recipe.artifacts.values.first()),
            ),
            limitation = AgentLimitation.NeedsPhoneCheck,
            resumeReason = AgentResumeReason.SavedChatsNeedCheck,
        )
        val result = row(null, descriptor = arm64Only, architecture = AgentArchitecture.X64)
        assertEquals(PhoneAgentStatus.Unavailable, result.status)
        assertEquals(AgentHiddenReason.UnsupportedArchitecture, result.hiddenReason)
        assertEquals(AgentStatusKey.AgentUnsupportedArchitecture, result.statusKey)
        assertNull(result.fixAction)
    }

    /** Plan 17 §1.4: each line's condition, with every earlier line false, gives exactly this state. */
    @Test
    fun `B-LINUX-140 the readiness order table holds line by line`() {
        data class Line(val fact: PhoneAgentRuntime?, val status: PhoneAgentStatus, val key: AgentStatusKey, val action: AgentFixAction?)
        val qwen = AgentCatalog.builtIn.byId(AgentId("qwen"))!!
        val lines = listOf(
            Line(null, PhoneAgentStatus.NeedsInstall, AgentStatusKey.AgentInstallToStart, AgentFixAction.Install),
            Line(runtime(installed = false), PhoneAgentStatus.NeedsInstall, AgentStatusKey.AgentInstallToStart, AgentFixAction.Install),
            Line(runtime(qualified = false, stopped = true, available = false, phase = null), PhoneAgentStatus.NeedsQualification, AgentStatusKey.AgentCheckNeeded, AgentFixAction.RunPhoneCheck),
            Line(runtime(stopped = true, available = false, phase = null), PhoneAgentStatus.StoppedInBackground, AgentStatusKey.AgentStoppedInBackground, AgentFixAction.Resume),
            Line(runtime(available = false, phase = null), PhoneAgentStatus.Unavailable, AgentStatusKey.AgentHostStopped, AgentFixAction.Resume),
            Line(runtime(phase = null), PhoneAgentStatus.Unavailable, AgentStatusKey.AgentCheckingSignIn, null),
            Line(runtime(phase = AgentSignInPhase.LimitReached), PhoneAgentStatus.LimitReached, AgentStatusKey.AgentUsageLimit, null),
            Line(runtime(phase = AgentSignInPhase.Failed), PhoneAgentStatus.Unavailable, AgentStatusKey.AgentSignInFix, AgentFixAction.SignIn),
            Line(runtime(phase = AgentSignInPhase.AwaitingCode), PhoneAgentStatus.SignedOut, AgentStatusKey.AgentSignedOut, AgentFixAction.SignIn),
            Line(runtime(), PhoneAgentStatus.Ready, AgentStatusKey.AgentReady, null),
        )
        for ((index, line) in lines.withIndex()) {
            val result = row(line.fact)
            assertEquals(line.status, result.status, "line $index")
            assertEquals(line.key, result.statusKey, "line $index")
            assertEquals(line.action, result.fixAction, "line $index")
        }
        val keyHost = row(runtime(id = "qwen", phase = AgentSignInPhase.SignedOut), descriptor = qwen)
        assertEquals(AgentStatusKey.AgentSignedOutApiKey, keyHost.statusKey)
        val hidden = AgentDescriptor(
            AgentId("gone"), "Gone", "gone", AgentRoute.AcpPaseo, "gone", AgentSignInMethod.None,
            availability = AgentAvailability.Hidden, unavailableReason = AgentUnavailableReason.RecipeUnverified,
            resumeReason = AgentResumeReason.ResumeUnverified,
        )
        assertEquals(AgentStatusKey.AgentUnavailable, row(runtime(id = "gone"), descriptor = hidden).statusKey)
    }

    @Test
    fun `B-LINUX-132 an automatic update replaces the phone check action and keeps others`() {
        val check = row(runtime(qualified = false)).withAutoUpdate(AgentAutoUpdate.Updating, "2.1.316")
        assertNull(check.fixAction)
        assertEquals(AgentAutoUpdate.Updating, check.autoUpdate)
        assertEquals("2.1.316", check.autoUpdateVersion)
        val signIn = row(runtime(phase = AgentSignInPhase.SignedOut)).withAutoUpdate(AgentAutoUpdate.WaitingWifi)
        assertEquals(AgentFixAction.SignIn, signIn.fixAction)
        assertTrue(row(runtime().copy(behindPin = true)).behindPin)
        assertFalse(row(runtime(id = "codex").copy(behindPin = true)).behindPin)
    }
}
