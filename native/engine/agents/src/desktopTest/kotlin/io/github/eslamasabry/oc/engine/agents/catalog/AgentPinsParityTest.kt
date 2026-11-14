package io.github.eslamasabry.oc.engine.agents.catalog

import io.github.eslamasabry.oc.engine.agents.scripts.ClaudeScripts
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
    private val postFreeze = setOf("antigravity", "opencode2", "claude", "codex", "goose", "omp-acp", "fx")

    private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "native/settings.gradle.kts").isFile }
    // The catalog and the tested sets come from the freeze tip's copies: since the master merge (4a0eac1cf4) lib/ holds
    // the released 2.1.5 app, which predates the Gemini, Qwen, Kilo and Cline pins and the extra tested versions.
    private val dartCatalog = freezeTipSource("lib/domain/agent_catalog.dart")
        .let { it.substring(it.indexOf("AgentCatalog _builtInCatalog()")) }
        .replace("'\$github", "'https://github.com")
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
    fun `B-AGENT-008 Claude Code pins equal the release manifest values and the install script`() {
        // Moved after the freeze (2.1.319 -> 2.1.321: Paseo 0.15.4 lists Fable 5.2 only for Claude Code 2.1.321 or newer).
        // Each SHA-256 and size is the release manifest's, and equals the bytes of the downloaded binary.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("claude"))!!.recipe!!
        assertEquals("2.1.321", recipe.version)
        assertEquals(ClaudeScripts.VERSION, recipe.version)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals("https://downloads.claude.ai/claude-code-releases/2.1.321/linux-arm64/claude", arm.url)
        assertEquals("6bea0633074627ba79328e00115ed0588c3acf906a8463612b25db696dbfd3f0", arm.sha256)
        assertEquals(ClaudeScripts.ARM64_SHA256, arm.sha256)
        assertEquals(341501368L, arm.downloadBytes)
        assertEquals(341501368L, arm.installedBytes)
        assertEquals(AgentArtifactFormat.Executable, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals("https://downloads.claude.ai/claude-code-releases/2.1.321/linux-x64/claude", x64.url)
        assertEquals("426c33815ca6645a772f722fbff10dbf2cbdb124a8aeab733c1365dbf8bbf61f", x64.sha256)
        assertEquals(ClaudeScripts.X64_SHA256, x64.sha256)
        assertEquals(341910032L, x64.downloadBytes)
        assertEquals(341910032L, x64.installedBytes)
        assertEquals(AgentArtifactFormat.Executable, x64.format)
    }

    @Test
    fun `B-AGENT-008 Codex pins equal the release assets checked on 2026-11-14`() {
        // Moved after the freeze (0.167.0 -> 0.169.0). Each SHA-256 equals GitHub's own asset digest and the bytes fetched;
        // installedBytes is the archive member's size.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("codex"))!!.recipe!!
        assertEquals("0.169.0", recipe.version)
        assertEquals("codex", recipe.executable)
        assertEquals(listOf("app-server"), recipe.launchArgs)
        assertEquals(listOf("login", "--device-auth"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals("https://github.com/openai/codex/releases/download/rust-v0.169.0/codex-aarch64-unknown-linux-musl.tar.gz", arm.url)
        assertEquals("97b97d87eea075c5cb7709c7f6156a451622a7667953ecda40e0276637196f3f", arm.sha256)
        assertEquals(119987185L, arm.downloadBytes)
        assertEquals(290069784L, arm.installedBytes)
        assertEquals("codex-aarch64-unknown-linux-musl", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals("https://github.com/openai/codex/releases/download/rust-v0.169.0/codex-x86_64-unknown-linux-musl.tar.gz", x64.url)
        assertEquals("9af4d751fce183faedea629437d5252dbf458eeb04130cf4f97be844621ea985", x64.sha256)
        assertEquals(117805733L, x64.downloadBytes)
        assertEquals(294277432L, x64.installedBytes)
        assertEquals("codex-x86_64-unknown-linux-musl", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Goose pins equal the downloads checked on 2026-11-14`() {
        // Moved after the freeze. Each SHA-256 equals GitHub's digest for the release asset; installedBytes is the member's size.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("goose"))!!.recipe!!
        assertEquals("1.59.1", recipe.version)
        assertEquals("goose", recipe.executable)
        assertEquals(listOf("acp"), recipe.launchArgs)
        assertEquals(listOf("configure"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://github.com/aaif-goose/goose/releases/download/v1.59.1/" +
                "goose-aarch64-unknown-linux-gnu.tar.gz",
            arm.url,
        )
        assertEquals("e3619b2ebf76dbbe8924561fd748220f289a34671e2757cda1cd833e44194f8e", arm.sha256)
        assertEquals(63280232L, arm.downloadBytes)
        assertEquals(195103264L, arm.installedBytes)
        assertEquals("./goose", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://github.com/aaif-goose/goose/releases/download/v1.59.1/" +
                "goose-x86_64-unknown-linux-gnu.tar.gz",
            x64.url,
        )
        assertEquals("04c08900ab2ae78b8e2f556f1ed535a91b61c080a3ff62e9fd8b280cceb544ef", x64.sha256)
        assertEquals(62448338L, x64.downloadBytes)
        assertEquals(208579376L, x64.installedBytes)
        assertEquals("./goose", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Oh My Pi pins equal the downloads checked on 2026-11-14`() {
        // Moved after the freeze. Each SHA-256 equals GitHub's digest for the release asset; the executable's size is both sizes.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("omp-acp"))!!.recipe!!
        assertEquals("18.20.0", recipe.version)
        assertEquals("omp", recipe.executable)
        assertEquals(listOf("acp"), recipe.launchArgs)
        assertEquals(listOf("login"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://github.com/can1357/oh-my-pi/releases/download/v18.20.0/" +
                "omp-linux-arm64",
            arm.url,
        )
        assertEquals("8b5e197db7c58b7ebabf83c6f03597f89bae3a766a84c6481b3a942a125f5e22", arm.sha256)
        assertEquals(244435960L, arm.downloadBytes)
        assertEquals(244435960L, arm.installedBytes)
        assertEquals(null, arm.archiveMember)
        assertEquals(AgentArtifactFormat.Executable, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://github.com/can1357/oh-my-pi/releases/download/v18.20.0/" +
                "omp-linux-x64",
            x64.url,
        )
        assertEquals("c8694be8c6e6a1f45e3636dd8a8e9eaa78878ad629a89000b403ec5e456d3f89", x64.sha256)
        assertEquals(287484080L, x64.downloadBytes)
        assertEquals(287484080L, x64.installedBytes)
        assertEquals(null, x64.archiveMember)
        assertEquals(AgentArtifactFormat.Executable, x64.format)
    }

    @Test
    fun `B-AGENT-008 fx pins equal the downloads checked on 2026-11-14`() {
        // Moved after the freeze. Each SHA-256 equals GitHub's digest for the release asset; installedBytes is the member's size.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("fx"))!!.recipe!!
        assertEquals("0.0.16", recipe.version)
        assertEquals("fx", recipe.executable)
        assertEquals(listOf("acp"), recipe.launchArgs)
        assertEquals(listOf("login"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://github.com/vercel-labs/fx/releases/download/v0.0.16/" +
                "fx-linux-aarch64.tar.gz",
            arm.url,
        )
        assertEquals("ca9441b104357e2105039dbfd4c2eaf0d6ee65a84085f23f51e880c95cbf46af", arm.sha256)
        assertEquals(5562847L, arm.downloadBytes)
        assertEquals(10832712L, arm.installedBytes)
        assertEquals("fx", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://github.com/vercel-labs/fx/releases/download/v0.0.16/" +
                "fx-linux-x86_64.tar.gz",
            x64.url,
        )
        assertEquals("f0fb822c7be546c80bcc89d50521bc1454435e0c6f586e8420c4e2b84fa80599", x64.sha256)
        assertEquals(5680318L, x64.downloadBytes)
        assertEquals(12814000L, x64.installedBytes)
        assertEquals("fx", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
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
        // The Node pins as the Flutter app's paseo_scripts.dart last had them (v24.22.2); native-owned since 2026-11-13.
        val node = mapOf(
            "nodeVersion" to "v24.22.2",
            "nodeArm64Sha256" to "95c264150aceb7f3437a29fd6984924f5f8d1511ea3cbd6c508b83f6c555dae2",
            "nodeX64Sha256" to "361718c444e49af2ca5a110c83ba6a8901143f23ab285ebbef0341f391f482a9",
        )
        fun dartConst(name: String) = node.getValue(name)
        // Paseo moved to 0.15.4 after the Flutter freeze (B-193): the pins equal the install script's own checks.
        assertEquals(PaseoPhoneScripts.VERSION, pins.paseo.version)
        assertEquals(PaseoPhoneScripts.PACKAGE_LOCK_SHA256, pins.paseo.packageLockSha256)
        assertEquals(PaseoPhoneScripts.CLI_SHA256, pins.paseo.cliSha256)
        assertEquals(PaseoPhoneScripts.PAYLOAD_SHA256, pins.paseo.payloadSha256)
        assertEquals(PaseoPhoneScripts.CODE_TREE_SHA256, pins.paseo.codeTreeSha256)
        assertEquals(dartConst("nodeVersion"), pins.node.version)
        assertEquals(dartConst("nodeArm64Sha256"), pins.node.sha256[AgentArchitecture.Arm64])
        assertEquals(dartConst("nodeX64Sha256"), pins.node.sha256[AgentArchitecture.X64])
        assertEquals("/home/oc/.local/share/oc-paseo/0.15.4-b61e82dd777f", PaseoPhoneScripts.INSTALL_DIRECTORY)
        val tested = Regex("claudeCodeTestedVersions = \\{([^}]*)\\}").find(testedVersions)!!.groupValues[1]
        // 2.1.321 moved after the freeze (Fable 5.2 needs Claude Code 2.1.321 or newer): the freeze set plus that pin.
        assertEquals(Regex("'([^']+)'").findAll(tested).map { it.groupValues[1] }.toList() + "2.1.321", pins.claudeCodeTestedVersions)
        val paseoTested = Regex("paseoTestedVersions = \\{([^}]*)\\}").find(testedVersions)!!.groupValues[1]
        assertEquals(Regex("'([^']+)'").findAll(paseoTested).map { it.groupValues[1] }.toList() + listOf("0.15.3", "0.15.4"), pins.paseoTestedVersions)
    }
}
