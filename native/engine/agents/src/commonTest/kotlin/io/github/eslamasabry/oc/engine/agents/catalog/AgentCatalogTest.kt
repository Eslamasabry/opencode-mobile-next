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
            setOf("opencode2", "claude", "codex", "gemini", "qwen", "kilo", "cline", "goose", "omp-acp", "fx"),
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
        assertEquals("2.1.316", catalog.byId(AgentId("claude"))!!.recipe!!.version)
        assertEquals(listOf("auth", "login", "--claudeai"), catalog.byId(AgentId("claude"))!!.recipe!!.signInArgs)
        assertEquals(AgentLimitation.NeedsPhoneCheck, catalog.byId(AgentId("claude"))!!.limitation)
        assertEquals(AgentLimitation.GooseProvider, catalog.byId(AgentId("goose"))!!.limitation)
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
        assertEquals("0.11.2", pins.paseo.version)
        assertEquals("b0bcc4f6191bd5c2daeda8ae398d636f3575173e699fa3868ab9e24f2e1fcdaa", pins.paseo.packageLockSha256)
        assertEquals("/home/oc/.local/share/oc-paseo/0.11.2-b0bcc4f6191b", pins.paseo.installDirectory)
        assertEquals("v24.21.0", pins.node.version)
        assertEquals("724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5", pins.node.sha256[AgentArchitecture.Arm64])
        assertEquals("6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff", pins.node.sha256[AgentArchitecture.X64])
        assertTrue("2.1.316" in pins.claudeCodeTestedVersions)
    }
}
