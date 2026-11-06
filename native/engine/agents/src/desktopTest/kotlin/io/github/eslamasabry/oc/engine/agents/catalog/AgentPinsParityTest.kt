package io.github.eslamasabry.oc.engine.agents.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plan 17 §1.3: the pins are copied VERBATIM from the Flutter freeze tip. These read the Dart sources in the same
 * repository and compare every URL, hash, version and size byte for byte, and the generated Kotlin with the JSON file.
 */
class AgentPinsParityTest {
    /**
     * Agents added to Flutter after the freeze tip that this repository's Dart sources still lack: they are checked
     * against the commit's literal values below, not against the (older) Dart file.
     */
    private val postFreeze = setOf("antigravity")

    private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "lib/domain/agent_catalog.dart").isFile }
    private val dartCatalog = File(repo, "lib/domain/agent_catalog.dart").readText()
        .let { it.substring(it.indexOf("AgentCatalog _builtInCatalog()")) }
        .replace("'\$github", "'https://github.com")
    private val paseoScripts = File(repo, "lib/builtin/agents/paseo_scripts.dart").readText()
    private val testedVersions = File(repo, "lib/paseo/tested_versions.dart").readText()
    private val pinsFile = File(repo, "native/engine/agents/pins/agent-pins.json")

    @Test
    fun `B-AGENT-008 the generated Kotlin pins equal the pins file byte for byte`() {
        assertEquals(pinsFile.readText(), AGENT_PINS_JSON)
    }

    @Test
    fun `B-AGENT-008 every catalog URL, hash and size equals the freeze tip`() {
        val pins = AgentPins.builtIn
        val catalog = pins.catalog()
        val dartHashes = Regex("'([a-f0-9]{64})'").findAll(dartCatalog).map { it.groupValues[1] }.toSet()
        val dartUrls = Regex("'(https://[^']+)'").findAll(dartCatalog).map { it.groupValues[1] }
            .filter { it != "https://github.com" }.toSet() // the `const github` prefix itself
        val frozen = catalog.agents.filter { it.id.raw !in postFreeze }
        val ourHashes = frozen.flatMap { a -> a.recipe!!.artifacts.values.map { it.sha256 } }.toSet()
        val ourUrls = frozen.flatMap { a -> a.recipe!!.artifacts.values.map { it.url } }.toSet()
        assertEquals(dartHashes, ourHashes)
        assertEquals(dartUrls, ourUrls)
        for (agent in frozen) {
            val recipe = agent.recipe!!
            assertTrue("version: '${recipe.version}'" in dartCatalog, agent.id.raw)
            assertTrue("executable: '${recipe.executable}'" in dartCatalog, agent.id.raw)
            for (artifact in recipe.artifacts.values) {
                artifact.downloadBytes?.let { assertTrue(it.toString() in dartCatalog, "${agent.id} size $it") }
                artifact.installedBytes?.let { assertTrue(it.toString() in dartCatalog, "${agent.id} size $it") }
                artifact.archiveMember?.let { assertTrue("'$it'" in dartCatalog, "${agent.id} member $it") }
            }
            if (recipe.launchArgs.isNotEmpty()) {
                val literal = recipe.launchArgs.joinToString(", ", "launchArgs: [", "]") { "'$it'" }
                assertTrue(literal in dartCatalog, "${agent.id} $literal")
            }
            if (recipe.signInArgs.isNotEmpty()) {
                val literal = recipe.signInArgs.joinToString(", ", "signInArgs: [", "]") { "'$it'" }
                assertTrue(literal in dartCatalog, "${agent.id} $literal")
            }
        }
    }

    @Test
    fun `B-AGENT-008 Antigravity pins equal Flutter commit 4a75d8593`() {
        val agent = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("antigravity"))!!
        val recipe = agent.recipe!!
        assertEquals("Antigravity", agent.name)
        assertEquals(AgentRoute.AcpPaseo, agent.route)
        assertEquals("antigravity", agent.providerId)
        assertEquals(AgentSignInMethod.BrowserOAuthHost, agent.signInMethod)
        assertEquals(AgentLimitation.AntigravityGoogleSignIn, agent.limitation)
        assertEquals("1.5.0", recipe.version)
        assertEquals("agy", recipe.executable)
        assertEquals(listOf("acp", "--disable-telemetry"), recipe.launchArgs)
        assertEquals(listOf("acp", "--login"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.0-5269980577660928/linux-arm/cli_linux_arm64.tar.gz",
            arm.url,
        )
        assertEquals("87e73a5ab4ecb3a9668660d259dafd13f5443149da0113664d59129a21a62ba4", arm.sha256)
        assertEquals(66011999L, arm.downloadBytes)
        assertEquals(227627384L, arm.installedBytes)
        assertEquals("antigravity", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.0-5269980577660928/linux-x64/cli_linux_x64.tar.gz",
            x64.url,
        )
        assertEquals("f65e365a67be49abdaf92f5669c3157bd4b9c569f6b9f448340b00ca261b0f01", x64.sha256)
        assertEquals(69781910L, x64.downloadBytes)
        assertEquals(238604688L, x64.installedBytes)
        // Gemini stays: the catalog only gains Antigravity.
        assertNotNull(AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("gemini")))
    }

    @Test
    fun `B-AGENT-008 Paseo, lock, Node and tested Claude versions equal the freeze tip`() {
        val pins = AgentPins.builtIn
        fun dartConst(name: String) = Regex("static const $name =\\s*'([^']*)'").find(paseoScripts)!!.groupValues[1]
        assertEquals(dartConst("version"), pins.paseo.version)
        assertEquals(dartConst("packageLockSha256"), pins.paseo.packageLockSha256)
        assertEquals(dartConst("cliSha256"), pins.paseo.cliSha256)
        assertEquals(dartConst("payloadSha256"), pins.paseo.payloadSha256)
        assertEquals(dartConst("codeTreeSha256"), pins.paseo.codeTreeSha256)
        assertEquals(dartConst("nodeVersion"), pins.node.version)
        assertEquals(dartConst("nodeArm64Sha256"), pins.node.sha256[AgentArchitecture.Arm64])
        assertEquals(dartConst("nodeX64Sha256"), pins.node.sha256[AgentArchitecture.X64])
        assertTrue("installDirectory =\n      '/home/oc/.local/share/oc-paseo/\$version-b0bcc4f6191b'" in paseoScripts)
        val tested = Regex("claudeCodeTestedVersions = \\{([^}]*)\\}").find(testedVersions)!!.groupValues[1]
        assertEquals(Regex("'([^']+)'").findAll(tested).map { it.groupValues[1] }.toList(), pins.claudeCodeTestedVersions)
        val paseoTested = Regex("paseoTestedVersions = \\{([^}]*)\\}").find(testedVersions)!!.groupValues[1]
        assertEquals(Regex("'([^']+)'").findAll(paseoTested).map { it.groupValues[1] }.toList(), pins.paseoTestedVersions)
    }
}
