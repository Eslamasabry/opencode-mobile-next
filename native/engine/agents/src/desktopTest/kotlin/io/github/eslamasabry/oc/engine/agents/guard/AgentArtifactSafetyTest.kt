package io.github.eslamasabry.oc.engine.agents.guard

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifact
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifactFormat
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentInstallRecipe
import io.github.eslamasabry.oc.engine.agents.catalog.AgentLimitation
import io.github.eslamasabry.oc.engine.agents.catalog.AgentResumeReason
import io.github.eslamasabry.oc.engine.agents.catalog.AgentRoute
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Port of test/agent_catalog_test.dart:75 and :126 against the native catalog (catalog/Catalog.kt). */
class AgentArtifactSafetyTest {
    private fun artifact(member: String? = null, format: AgentArtifactFormat = AgentArtifactFormat.Executable) =
        AgentArtifact(url = "https://example.test/agent-1.0.0", sha256 = "a".repeat(64), format = format, archiveMember = member)

    private fun agent(recipe: AgentInstallRecipe) = AgentDescriptor(
        id = AgentId("new-agent"),
        name = "New Agent",
        iconKey = "new-agent",
        route = AgentRoute.AcpPaseo,
        providerId = "new-agent",
        signInMethod = AgentSignInMethod.ApiKeyHost,
        recipe = recipe,
        availability = AgentAvailability.Limited,
        limitation = AgentLimitation.NeedsHostSetup,
        resumeReason = AgentResumeReason.SavedChatsNeedCheck,
    )

    @Test
    fun `B-SEC-002 artifact rejects unpinned, credential-bearing, or escaping inputs`() {
        fun exe(url: String, sha: String = "a".repeat(64)) = AgentArtifact(url, sha, AgentArtifactFormat.Executable)
        assertFailsWith<IllegalArgumentException> { exe("http://example.test/agent") }
        assertFailsWith<IllegalArgumentException> { exe("https://user:secret@example.test/agent") }
        assertFailsWith<IllegalArgumentException> { exe("https://example.test/agent", "unknown") }
        for (member in listOf("../agent", "/agent", "a/../agent", "a\\agent", "a//agent")) {
            assertFailsWith<IllegalArgumentException>(member) { artifact(member, AgentArtifactFormat.TarGz) }
        }
        assertFailsWith<IllegalArgumentException> { artifact(format = AgentArtifactFormat.TarGz) }
        assertFailsWith<IllegalArgumentException> { artifact(member = "agent") }
        assertEquals("./agent", artifact("./agent", AgentArtifactFormat.TarGz).archiveMember)
    }

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Test
    fun `B-SEC-002 recipes and catalogs defend against input mutation`() {
        val artifacts = mutableMapOf(AgentArchitecture.Arm64 to artifact())
        val args = mutableListOf("--acp")
        val installRecipe = AgentInstallRecipe("1.0.0", "agent", artifacts, launchArgs = args)
        val entries = mutableListOf(agent(installRecipe))
        val catalog = AgentCatalog(entries)
        artifacts.clear()
        args.clear()
        entries.clear()
        assertEquals(1, catalog.agents.size)
        assertEquals(1, installRecipe.artifacts.size)
        assertEquals(listOf("--acp"), installRecipe.launchArgs)
        // The Dart views throw UnsupportedError; the persistent collections refuse JVM mutation the same way.
        assertFailsWith<UnsupportedOperationException> { (catalog.agents as java.util.List<AgentDescriptor>).clear() }
        assertFailsWith<UnsupportedOperationException> {
            (installRecipe.artifacts as java.util.Map<AgentArchitecture, AgentArtifact>).clear()
        }
        assertFailsWith<UnsupportedOperationException> { (installRecipe.launchArgs as java.util.List<String>).clear() }
        assertFailsWith<UnsupportedOperationException> { (installRecipe.signInArgs as java.util.List<String>).add("login") }
    }
}
