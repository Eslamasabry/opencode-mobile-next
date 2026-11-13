package io.github.eslamasabry.oc.engine.agents.catalog

import io.github.eslamasabry.oc.engine.agents.scripts.PaseoPhoneScripts
import io.github.eslamasabry.oc.engine.agents.scripts.freezeTipSource
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
    // Agents whose pins moved after the Flutter freeze (Flutter takes no new work since 2026-11-08): checked on their
    // own below, and their old Flutter entries are left out of the comparison.
    private val postFreeze = setOf("antigravity", "opencode2")

    private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "lib/domain/agent_catalog.dart").isFile }
    // The catalog and the tested sets come from the freeze tip's copies: since the master merge (4a0eac1cf4) lib/ holds
    // the released 2.1.5 app, which predates the Gemini, Qwen, Kilo and Cline pins and the extra tested versions.
    private val dartCatalog = freezeTipSource("lib/domain/agent_catalog.dart")
        .let { it.substring(it.indexOf("AgentCatalog _builtInCatalog()")) }
        .replace("'\$github", "'https://github.com")
    private val paseoScripts = File(repo, "lib/builtin/agents/paseo_scripts.dart").readText()
    private val testedVersions = freezeTipSource("lib/paseo/tested_versions.dart")
    private val pinsFile = File(repo, "native/engine/agents/pins/agent-pins.json")

    @Test
    fun `B-AGENT-008 the generated Kotlin pins equal the pins file byte for byte`() {
        assertEquals(pinsFile.readText(), AGENT_PINS_JSON)
    }

    @Test
    fun `B-AGENT-008 every catalog URL, hash and size equals the freeze tip`() {
        val pins = AgentPins.builtIn
        val catalog = pins.catalog()
        // The Flutter catalog without its entries for post-freeze agents (an entry runs from "entry(" to the next one).
        val entries = dartCatalog.split("\n      entry(")
        val frozenDart = entries.filterNot { e -> postFreeze.any { id -> e.trimStart().startsWith("'$id'") } }.joinToString("\n      entry(")
        val dartHashes = Regex("'([a-f0-9]{64})'").findAll(frozenDart).map { it.groupValues[1] }.toSet()
        val dartUrls = Regex("'(https://[^']+)'").findAll(frozenDart).map { it.groupValues[1] }
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
            // Native-owned since 2026-11-13 (the Flutter app is no longer the spec): omp's set-up terminal runs
            // `omp login`, which the freeze tip never had.
            if (recipe.signInArgs.isNotEmpty() && agent.id.raw != "omp-acp") {
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
        assertEquals("1.5.3", recipe.version)
        assertEquals("agy", recipe.executable)
        assertEquals(listOf("acp", "--disable-telemetry"), recipe.launchArgs)
        assertEquals(listOf("acp", "--login"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.3-6604829099950080/linux-arm/cli_linux_arm64.tar.gz",
            arm.url,
        )
        assertEquals("2d50060c7b1327995e5c81ec58829c91c2564ebb9fec165d3f9f1325ec188675", arm.sha256)
        assertEquals(69843447L, arm.downloadBytes)
        assertEquals(235622776L, arm.installedBytes)
        assertEquals("antigravity", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.3-6604829099950080/linux-x64/cli_linux_x64.tar.gz",
            x64.url,
        )
        assertEquals("1b4850599cce73a17f94ffb422df3bf9531531b77dbddc40c1a435e0c2c16807", x64.sha256)
        assertEquals(73694700L, x64.downloadBytes)
        assertEquals(246837648L, x64.installedBytes)
        // Gemini stays: the catalog only gains Antigravity.
        assertNotNull(AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("gemini")))
    }

    @Test
    fun `B-AGENT-008 OpenCode 2 pins equal the native pin of commit 93fcf4049`() {
        // Moved after the freeze (2.2.5 -> 2.2.6). Each SHA-256 is the registry tarball's, whose sha512 matches npm's
        // published integrity; the same values pin the in-app Ubuntu install (OpenCodePins).
        val agent = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("opencode2"))!!
        val recipe = agent.recipe!!
        assertEquals("2.2.6", recipe.version)
        assertEquals("opencode", recipe.executable)
        assertEquals(listOf("serve", "--hostname", "127.0.0.1"), recipe.launchArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals("https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.2.6.tgz", arm.url)
        assertEquals("22352e200630ec61a48a65b9f5c2b627328ae5002658d81c067f67c5fb689898", arm.sha256)
        assertEquals("package/bin/opencode", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.2.6.tgz",
            x64.url,
        )
        assertEquals("00d8b036689fc35927d8f20f57000d5c091fe7a5a3345fef5326f330bfe6d0ce", x64.sha256)
        assertEquals("package/bin/opencode", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Paseo, lock, Node and tested Claude versions equal the freeze tip`() {
        val pins = AgentPins.builtIn
        fun dartConst(name: String) = Regex("static const $name =\\s*'([^']*)'").find(paseoScripts)!!.groupValues[1]
        // Paseo moved to 0.15.3 after the Flutter freeze (B-193): the pins equal the install script's own checks.
        assertEquals(PaseoPhoneScripts.VERSION, pins.paseo.version)
        assertEquals(PaseoPhoneScripts.PACKAGE_LOCK_SHA256, pins.paseo.packageLockSha256)
        assertEquals(PaseoPhoneScripts.CLI_SHA256, pins.paseo.cliSha256)
        assertEquals(PaseoPhoneScripts.PAYLOAD_SHA256, pins.paseo.payloadSha256)
        assertEquals(PaseoPhoneScripts.CODE_TREE_SHA256, pins.paseo.codeTreeSha256)
        assertEquals(dartConst("nodeVersion"), pins.node.version)
        assertEquals(dartConst("nodeArm64Sha256"), pins.node.sha256[AgentArchitecture.Arm64])
        assertEquals(dartConst("nodeX64Sha256"), pins.node.sha256[AgentArchitecture.X64])
        assertEquals("/home/oc/.local/share/oc-paseo/0.15.3-9a676a737ac4", PaseoPhoneScripts.INSTALL_DIRECTORY)
        val tested = Regex("claudeCodeTestedVersions = \\{([^}]*)\\}").find(testedVersions)!!.groupValues[1]
        assertEquals(Regex("'([^']+)'").findAll(tested).map { it.groupValues[1] }.toList(), pins.claudeCodeTestedVersions)
        val paseoTested = Regex("paseoTestedVersions = \\{([^}]*)\\}").find(testedVersions)!!.groupValues[1]
        assertEquals(Regex("'([^']+)'").findAll(paseoTested).map { it.groupValues[1] }.toList() + "0.15.3", pins.paseoTestedVersions)
    }
}
