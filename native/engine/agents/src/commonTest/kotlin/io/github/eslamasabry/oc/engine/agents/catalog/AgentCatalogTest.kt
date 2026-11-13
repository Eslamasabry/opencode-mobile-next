package io.github.eslamasabry.oc.engine.agents.catalog

import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of test/agent_catalog_test.dart. */
class AgentCatalogTest {
    private fun artifact(member: String? = null, format: AgentArtifactFormat = AgentArtifactFormat.Executable) =
        AgentArtifact(url = "https://example.test/agent-1.0.0", sha256 = "a".repeat(64), format = format, archiveMember = member)

    private fun recipe(
        artifacts: Map<AgentArchitecture, AgentArtifact> = mapOf(AgentArchitecture.Arm64 to artifact()),
        args: List<String> = emptyList(),
    ) = AgentInstallRecipe(version = "1.0.0", executable = "agent", artifacts = artifacts, launchArgs = args)

    private fun agent(
        installRecipe: AgentInstallRecipe? = null,
        reason: AgentUnavailableReason? = null,
        availability: AgentAvailability = AgentAvailability.Limited,
    ) = AgentDescriptor(
        id = AgentId("new-agent"),
        name = "New Agent",
        iconKey = "new-agent",
        route = AgentRoute.AcpPaseo,
        providerId = "new-agent",
        signInMethod = AgentSignInMethod.ApiKeyHost,
        recipe = installRecipe,
        unavailableReason = reason,
        availability = availability,
        limitation = AgentLimitation.NeedsHostSetup,
        resumeReason = AgentResumeReason.SavedChatsNeedCheck,
    )

    @Test
    fun `B-AGENT-007 runtime capabilities fail closed even when installation is pinned`() {
        val entry = agent(installRecipe = recipe())
        assertTrue(entry.installableOn(AgentArchitecture.Arm64))
        assertFalse(entry.installableOn(AgentArchitecture.X64))
        assertTrue(entry.resumeUnverified)
        assertEquals(AgentCapabilities.None, entry.capabilities)
    }

    @Test
    fun `B-AGENT-007 unknown recipe stays hidden with a reason and unknown sizes stay unknown`() {
        val entry = agent(reason = AgentUnavailableReason.RecipeUnverified, availability = AgentAvailability.Hidden)
        assertFalse(entry.installableOn(AgentArchitecture.Arm64))
        assertNull(entry.artifactFor(AgentArchitecture.Arm64))
        assertNull(artifact().downloadBytes)
        assertNull(artifact().installedBytes)
        assertFailsWith<IllegalArgumentException> { agent() }
        assertFailsWith<IllegalArgumentException> { agent(installRecipe = recipe(), reason = AgentUnavailableReason.RecipeUnverified) }
    }

    @Test
    fun `B-AGENT-007 artifact rejects unpinned, credential-bearing or escaping inputs`() {
        fun exe(url: String, sha: String = "a".repeat(64)) = AgentArtifact(url, sha, AgentArtifactFormat.Executable)
        assertFailsWith<IllegalArgumentException> { exe("http://example.test/agent") }
        assertFailsWith<IllegalArgumentException> { exe("https://user:secret@example.test/agent") }
        assertFailsWith<IllegalArgumentException> { exe("https://example.test/agent#frag") }
        assertFailsWith<IllegalArgumentException> { exe("https:///agent") }
        assertFailsWith<IllegalArgumentException> { exe("https://example.test/agent", "unknown") }
        assertFailsWith<IllegalArgumentException> { exe("https://example.test/agent", "A".repeat(64)) }
        for (member in listOf("../agent", "/agent", "a/../agent", "a\\agent", "a//agent", "a/./b", "a\u0000b")) {
            assertFailsWith<IllegalArgumentException>(member) { artifact(member, AgentArtifactFormat.TarGz) }
        }
        assertFailsWith<IllegalArgumentException> { artifact(format = AgentArtifactFormat.TarGz) }
        assertFailsWith<IllegalArgumentException> { artifact(member = "agent") }
        assertEquals("./agent", artifact("./agent", AgentArtifactFormat.TarGz).archiveMember)
        assertFailsWith<IllegalArgumentException> {
            AgentArtifact("https://example.test/a", "a".repeat(64), AgentArtifactFormat.Executable, downloadBytes = 0)
        }
    }

    @Test
    fun `B-AGENT-113 recipes and catalogs defend against input mutation`() {
        val artifacts = mutableMapOf(AgentArchitecture.Arm64 to artifact())
        val args = mutableListOf("--acp")
        val installRecipe = recipe(artifacts, args)
        val entries = mutableListOf(agent(installRecipe = installRecipe))
        val catalog = AgentCatalog(entries)
        artifacts.clear()
        args.clear()
        entries.clear()
        assertEquals(1, catalog.agents.size)
        assertEquals(1, installRecipe.artifacts.size)
        assertEquals(listOf("--acp"), installRecipe.launchArgs)
        assertFailsWith<IllegalArgumentException> {
            AgentInstallRecipe("1.0.0", "agent", mapOf(AgentArchitecture.Arm64 to artifact()), listOf("a\u0000b"))
        }
        assertFailsWith<IllegalArgumentException> { AgentInstallRecipe("1.0.0", "Agent", mapOf(AgentArchitecture.Arm64 to artifact())) }
        assertFailsWith<IllegalArgumentException> { AgentInstallRecipe(" ", "agent", mapOf(AgentArchitecture.Arm64 to artifact())) }
    }

    @Test
    fun `B-AGENT-008 a future agent is one data entry with no provider dispatch change`() {
        val descriptor = agent(installRecipe = recipe())
        val catalog = AgentCatalog(listOf(descriptor))
        assertSame(descriptor, catalog.byId(AgentId("new-agent")))
        assertNull(catalog.byId(AgentId("missing")))
        assertFailsWith<IllegalArgumentException> { AgentCatalog(listOf(descriptor, descriptor)) }
    }

    @Test
    fun `B-AGENT-008 seed recipes are pinned for both architectures without runtime claims`() {
        val catalog = AgentCatalog.builtIn
        assertEquals(
            setOf("opencode2", "claude", "codex", "antigravity", "gemini", "qwen", "kilo", "cline", "goose", "omp-acp", "fx"),
            catalog.agents.map { it.id.raw }.toSet(),
        )
        for (entry in catalog.agents) {
            for (arch in AgentArchitecture.entries) {
                assertTrue(entry.installableOn(arch), entry.id.raw)
                assertTrue(Regex("^[a-f0-9]{64}$").matches(entry.artifactFor(arch)!!.sha256))
            }
            assertEquals(AgentCapabilities.None, entry.capabilities)
            assertEquals(AgentAvailability.Limited, entry.availability)
            assertTrue(entry.resumeUnverified)
        }
        assertEquals(AgentSignInMethod.ApiKeyHost, catalog.byId(AgentId("qwen"))!!.signInMethod)
        assertEquals(listOf("--acp"), catalog.byId(AgentId("gemini"))!!.recipe!!.launchArgs)
        assertEquals(AgentArtifactFormat.NpmTarGz, catalog.byId(AgentId("gemini"))!!.artifactFor(AgentArchitecture.Arm64)!!.format)
        assertEquals("2.1.319", catalog.byId(AgentId("claude"))!!.recipe!!.version)
        assertEquals(listOf("auth", "login", "--claudeai"), catalog.byId(AgentId("claude"))!!.recipe!!.signInArgs)
        assertEquals(AgentLimitation.NeedsPhoneCheck, catalog.byId(AgentId("claude"))!!.limitation)
        assertEquals(AgentLimitation.GooseProvider, catalog.byId(AgentId("goose"))!!.limitation)
        // Antigravity (agy) joins Gemini CLI in the catalog; it does not replace it.
        assertEquals(listOf("acp", "--disable-telemetry"), catalog.byId(AgentId("antigravity"))!!.recipe!!.launchArgs)
        assertEquals(listOf("acp", "--login"), catalog.byId(AgentId("antigravity"))!!.recipe!!.signInArgs)
        assertEquals("agy", catalog.byId(AgentId("antigravity"))!!.recipe!!.executable)
        assertEquals(AgentLimitation.AntigravityGoogleSignIn, catalog.byId(AgentId("antigravity"))!!.limitation)
    }

    @Test
    fun `B-AGENT-008 Kilo and Cline are pinned ACP agents with exact launch args`() {
        val catalog = AgentCatalog.builtIn
        val kilo = catalog.byId(AgentId("kilo"))!!
        val cline = catalog.byId(AgentId("cline"))!!
        assertEquals(AgentRoute.AcpPaseo, kilo.route)
        assertEquals(AgentRoute.AcpPaseo, cline.route)
        assertEquals("7.8.18", kilo.recipe!!.version)
        assertEquals("3.0.74", cline.recipe!!.version)
        assertEquals(listOf("acp"), kilo.recipe!!.launchArgs)
        assertEquals(listOf("--acp"), cline.recipe!!.launchArgs)
        assertEquals(listOf("auth", "login"), kilo.recipe!!.signInArgs)
        assertEquals(listOf("auth"), cline.recipe!!.signInArgs)
        assertEquals("package/bin/kilo", kilo.artifactFor(AgentArchitecture.Arm64)!!.archiveMember)
    }

    @Test
    fun `B-AGENT-008 the catalog installs only supported OpenCode 2`() {
        val oc = AgentCatalog.builtIn.byId(AgentId("opencode2"))!!
        assertEquals(AgentRoute.Opencode2, oc.route)
        assertFalse(oc.runsOnPhoneHost)
        assertEquals(listOf("serve", "--hostname", "127.0.0.1"), oc.recipe!!.launchArgs)
        assertTrue(oc.recipe!!.version.startsWith("2."))
    }

    @Test
    fun `B-AGENT-008 shared helper pins are the verified Paseo, lock and Node`() {
        val pins = AgentPins.builtIn
        assertEquals("0.15.3", pins.paseo.version)
        assertEquals("9a676a737ac42bc021d3a6fcf74b8e9d5b7c3bb9a300cb9288ca7254919c5f6a", pins.paseo.packageLockSha256)
        assertEquals("/home/oc/.local/share/oc-paseo/0.15.3-9a676a737ac4", pins.paseo.installDirectory)
        assertEquals("v24.22.2", pins.node.version)
        assertEquals("95c264150aceb7f3437a29fd6984924f5f8d1511ea3cbd6c508b83f6c555dae2", pins.node.sha256[AgentArchitecture.Arm64])
        assertEquals("361718c444e49af2ca5a110c83ba6a8901143f23ab285ebbef0341f391f482a9", pins.node.sha256[AgentArchitecture.X64])
        assertTrue("2.1.319" in pins.claudeCodeTestedVersions)
    }

    // Owner 2026-11-13: Gemini CLI is deprecated upstream and is no longer offered. A copy already on a phone keeps its
    // row, its recipe (the removal script and the sign-in terminal need the executable) and its Remove.
    @Test
    fun `B-AGENT-007 Gemini CLI is no longer offered for install, but a copy on the phone stays listed`() {
        val gemini = AgentCatalog.builtIn.byId(AgentId("gemini"))!!
        for (arch in AgentArchitecture.entries) assertFalse(gemini.installableOn(arch), arch.wire)
        assertEquals("gemini", gemini.recipe!!.executable)
        assertTrue(gemini.runsOnPhoneHost)
        val offered = AgentCatalog.builtIn.agents.filter { a -> AgentArchitecture.entries.any { a.installableOn(it) } }
        assertEquals(AgentCatalog.builtIn.agents.map { it.id.raw } - "gemini", offered.map { it.id.raw })
    }
}
