package io.github.eslamasabry.oc.engine.agents.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 17 §1.3: the pins are copied VERBATIM from the Flutter freeze tip. These read the Dart sources in the same
 * repository and compare every URL, hash, version and size byte for byte, and the generated Kotlin with the JSON file.
 */
class AgentPinsParityTest {
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
        val ourHashes = catalog.agents.flatMap { a -> a.recipe!!.artifacts.values.map { it.sha256 } }.toSet()
        val ourUrls = catalog.agents.flatMap { a -> a.recipe!!.artifacts.values.map { it.url } }.toSet()
        assertEquals(dartHashes, ourHashes)
        assertEquals(dartUrls, ourUrls)
        for (agent in catalog.agents) {
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
    }
}
