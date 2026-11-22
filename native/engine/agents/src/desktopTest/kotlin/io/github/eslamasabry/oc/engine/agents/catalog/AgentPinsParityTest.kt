package io.github.eslamasabry.oc.engine.agents.catalog

import io.github.eslamasabry.oc.engine.agents.AgentId
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
    private val postFreeze =
        setOf("antigravity", "opencode2", "claude", "codex", "goose", "omp-acp", "fx", "qwen", "kilo", "cline")

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
        // An agent's artifact can also be a `final <id> = archive(...);` before the entries (qwen's is): that
        // definition belongs to the agent too, so a post-freeze agent's old copy of it is left out as well.
        val postFreezeDefinition = Regex("""final (\w+) = (?:archive|binary)\(.*?\n  \);\n""", RegexOption.DOT_MATCHES_ALL)
        val frozenDart = entries.filterNot { e -> postFreeze.any { id -> e.trimStart().startsWith("'$id'") } }
            .joinToString("\n      entry(")
            .let { text -> postFreezeDefinition.replace(text) { m -> if (m.groupValues[1] in postFreeze) "" else m.value } }
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
    fun `B-AGENT-008 Antigravity keeps its route, sign-in and limitation and its pins equal the 1-5-9 download`() {
        val agent = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("antigravity"))!!
        val recipe = agent.recipe!!
        assertEquals("Antigravity", agent.name)
        assertEquals(AgentRoute.AcpPaseo, agent.route)
        assertEquals("antigravity", agent.providerId)
        assertEquals(AgentSignInMethod.BrowserOAuthHost, agent.signInMethod)
        assertEquals(AgentLimitation.AntigravityGoogleSignIn, agent.limitation)
        assertEquals("1.5.14", recipe.version)
        assertEquals("agy", recipe.executable)
        assertEquals(listOf("acp", "--disable-telemetry"), recipe.launchArgs)
        assertEquals(listOf("acp", "--login"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.14-5302690847260672/" +
                "linux-arm/cli_linux_arm64.tar.gz",
            arm.url,
        )
        assertEquals("e5f69a3123fac66974b303d9a201f381a0fb32b3ef869d9139da85d5caf67a2a", arm.sha256)
        assertEquals(92116379L, arm.downloadBytes)
        assertEquals(256070320L, arm.installedBytes)
        assertEquals("antigravity", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.14-5302690847260672/" +
                "linux-x64/cli_linux_x64.tar.gz",
            x64.url,
        )
        assertEquals("d58386322d2073e96b33f7cc32b4e9ae2db6228f58440369725967b2de48c7b5", x64.sha256)
        assertEquals(101195950L, x64.downloadBytes)
        assertEquals(273240776L, x64.installedBytes)
        // Gemini stays: the catalog only gains Antigravity.
        assertNotNull(AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("gemini")))
    }

    @Test
    fun `B-AGENT-008 Claude Code pins equal the release manifest values and the install script`() {
        // Moved after the freeze (2.1.319 -> 2.1.325 -> 2.1.327 (Paseo 0.15.4 lists Fable 5.2 only for Claude Code 2.1.321 or
        // newer).
        // Each SHA-256 and size is the release manifest's, and equals the bytes of the downloaded binary.
        val recipe = AgentPins.builtIn.catalog().byId(AgentId("claude"))!!.recipe!!
        assertEquals("2.1.330", recipe.version)
        assertEquals(ClaudeScripts.VERSION, recipe.version)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals("https://downloads.claude.ai/claude-code-releases/2.1.330/linux-arm64/claude", arm.url)
        assertEquals("6016f90cac783e367fb1aa97c768a2efb94a0128a5b46b70fb3d8fcbbd9dd444", arm.sha256)
        assertEquals(ClaudeScripts.ARM64_SHA256, arm.sha256)
        assertEquals(379971000L, arm.downloadBytes)
        assertEquals(379971000L, arm.installedBytes)
        assertEquals(AgentArtifactFormat.Executable, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals("https://downloads.claude.ai/claude-code-releases/2.1.330/linux-x64/claude", x64.url)
        assertEquals("f390eaa0814e6a1bff74cbb47acb74cb9c89d30ced809fe0af5af00c7bfe8ebb", x64.sha256)
        assertEquals(ClaudeScripts.X64_SHA256, x64.sha256)
        assertEquals(381121040L, x64.downloadBytes)
        assertEquals(381121040L, x64.installedBytes)
        assertEquals(AgentArtifactFormat.Executable, x64.format)
    }

    @Test
    fun `B-AGENT-008 Codex pins equal the release assets checked on 2026-11-14`() {
        // Moved after the freeze (0.167.0 -> 0.170.0). Issue 147: the pin is the official package (bin/codex beside
        // bin/codex-code-mode-host, codex-exec-server, codex-path/rg, codex-resources), not the bare binary: Codex
        // starts its code-mode host from its own folder and failed with "host executable was not found" without it.
        // Each SHA-256 equals GitHub's own asset digest and the bytes fetched on 2026-11-15; installedBytes is the
        // whole extracted tree (the installer keeps it).
        val recipe = AgentPins.builtIn.catalog().byId(AgentId("codex"))!!.recipe!!
        assertEquals("0.171.0", recipe.version)
        assertEquals("codex", recipe.executable)
        assertEquals(listOf("app-server"), recipe.launchArgs)
        assertEquals(listOf("login", "--device-auth"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://github.com/openai/codex/releases/download/rust-v0.171.0/" +
                "codex-package-aarch64-unknown-linux-musl.tar.gz",
            arm.url,
        )
        assertEquals("d8c43bee6cf11b01eb18b95f4d7961092bc40228180808859e70dabb7784c1c9", arm.sha256)
        assertEquals(199315347L, arm.downloadBytes)
        assertEquals(516528332L, arm.installedBytes)
        assertEquals("bin/codex", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://github.com/openai/codex/releases/download/rust-v0.171.0/" +
                "codex-package-x86_64-unknown-linux-musl.tar.gz",
            x64.url,
        )
        assertEquals("8391d67757496bfd14893eca6cbf0c96994bf2627566ff1f018f70600c9ef6b3", x64.sha256)
        assertEquals(197763180L, x64.downloadBytes)
        assertEquals(521571182L, x64.installedBytes)
        assertEquals("bin/codex", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Goose pins equal the downloads checked on 2026-11-14`() {
        // Moved after the freeze. Each SHA-256 equals GitHub's digest for the release asset; installedBytes is the
        // member's size.
        val recipe = AgentPins.builtIn.catalog().byId(AgentId("goose"))!!.recipe!!
        assertEquals("1.60.2", recipe.version)
        assertEquals("goose", recipe.executable)
        assertEquals(listOf("acp"), recipe.launchArgs)
        assertEquals(listOf("configure"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://github.com/aaif-goose/goose/releases/download/v1.60.2/" +
                "goose-aarch64-unknown-linux-gnu.tar.gz",
            arm.url,
        )
        assertEquals("7e74d796e93b89df374715dc669149cb634f74d778de0b87bb7c031b5b093731", arm.sha256)
        assertEquals(64482004L, arm.downloadBytes)
        assertEquals(199216392L, arm.installedBytes)
        assertEquals("./goose", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://github.com/aaif-goose/goose/releases/download/v1.60.2/" +
                "goose-x86_64-unknown-linux-gnu.tar.gz",
            x64.url,
        )
        assertEquals("5f6e463621a8428617ae4870eecee7a00a253e9bbfccb6340dfaa4f0ec2aaf32", x64.sha256)
        assertEquals(63566822L, x64.downloadBytes)
        assertEquals(213003128L, x64.installedBytes)
        assertEquals("./goose", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Oh My Pi pins equal the downloads checked on 2026-11-14`() {
        // Moved after the freeze. Each SHA-256 equals GitHub's digest for the release asset; the executable's size is
        // both sizes.
        val recipe = AgentPins.builtIn.catalog().byId(AgentId("omp-acp"))!!.recipe!!
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
        // Moved after the freeze. Each SHA-256 equals GitHub's digest for the release asset; installedBytes is the
        // member's size.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("fx"))!!.recipe!!
        assertEquals("0.0.17", recipe.version)
        assertEquals("fx", recipe.executable)
        assertEquals(listOf("acp"), recipe.launchArgs)
        assertEquals(listOf("login"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://github.com/vercel-labs/fx/releases/download/v0.0.17/" +
                "fx-linux-aarch64.tar.gz",
            arm.url,
        )
        assertEquals("b4633b8987ec43f1b9454e4629f3b5a4f1e086aae36b84ae4fefeacd6a53761f", arm.sha256)
        assertEquals(4550277L, arm.downloadBytes)
        assertEquals(6869488L, arm.installedBytes)
        assertEquals("fx", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://github.com/vercel-labs/fx/releases/download/v0.0.17/" +
                "fx-linux-x86_64.tar.gz",
            x64.url,
        )
        assertEquals("78c155d59fa59e42fd56d93037fd75041868c9c67865e8c7f0ca6dcdfac23e75", x64.sha256)
        assertEquals(4769247L, x64.downloadBytes)
        assertEquals(8833664L, x64.installedBytes)
        assertEquals("fx", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Cline 3-0-75 pins equal the downloads checked on 2026-11-22`() {
        // Moved after the freeze. Each SHA-256 is of the registry tarball, whose sha512 equals dist.integrity;
        // installedBytes is the executable member's size.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("cline"))!!.recipe!!
        assertEquals("3.0.75", recipe.version)
        assertEquals("cline", recipe.executable)
        assertEquals(listOf("--acp"), recipe.launchArgs)
        assertEquals(listOf("auth"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals("https://registry.npmjs.org/@cline/cli-linux-arm64/-/cli-linux-arm64-3.0.75.tgz", arm.url)
        assertEquals("8f20880bccb5c626d44c157c3dfa3916147be09cf3ebf6066fc7288f58288743", arm.sha256)
        assertEquals(55316566L, arm.downloadBytes)
        assertEquals(165464360L, arm.installedBytes)
        assertEquals("package/bin/cline", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals("https://registry.npmjs.org/@cline/cli-linux-x64/-/cli-linux-x64-3.0.75.tgz", x64.url)
        assertEquals("e2fee40340a2bb248830f8e2a361e4b93b148a9ed90d246e69af3627176ea96d", x64.sha256)
        assertEquals(55550729L, x64.downloadBytes)
        assertEquals(165025248L, x64.installedBytes)
        assertEquals("package/bin/cline", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Qwen Code pins equal the downloads checked on 2026-11-14`() {
        // Moved after the freeze. The SHA-256 is of the registry tarball, whose sha512 equals dist.integrity;
        // installedBytes is dist.unpackedSize.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("qwen"))!!.recipe!!
        assertEquals("0.28.4", recipe.version)
        assertEquals("qwen", recipe.executable)
        assertEquals(listOf("--acp"), recipe.launchArgs)
        assertEquals(listOf(), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://registry.npmjs.org/@qwen-code/qwen-code/-/" +
                "qwen-code-0.28.4.tgz",
            arm.url,
        )
        assertEquals("bd9daa7bef7ab4cccea8f74372d1924f54637412f73bd21242e6740c145fdfc5", arm.sha256)
        assertEquals(44773289L, arm.downloadBytes)
        assertEquals(162973081L, arm.installedBytes)
        assertEquals("package/cli-entry.js", arm.archiveMember)
        assertEquals(AgentArtifactFormat.NpmTarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://registry.npmjs.org/@qwen-code/qwen-code/-/" +
                "qwen-code-0.28.4.tgz",
            x64.url,
        )
        assertEquals("bd9daa7bef7ab4cccea8f74372d1924f54637412f73bd21242e6740c145fdfc5", x64.sha256)
        assertEquals(44773289L, x64.downloadBytes)
        assertEquals(162973081L, x64.installedBytes)
        assertEquals("package/cli-entry.js", x64.archiveMember)
        assertEquals(AgentArtifactFormat.NpmTarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Kilo pins equal the downloads checked on 2026-11-14`() {
        // Moved after the freeze. Each SHA-256 is of the registry tarball, whose sha512 equals dist.integrity;
        // installedBytes is the member's size.
        val recipe = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("kilo"))!!.recipe!!
        assertEquals("7.8.32", recipe.version)
        assertEquals("kilo", recipe.executable)
        assertEquals(listOf("acp"), recipe.launchArgs)
        assertEquals(listOf("auth", "login"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://registry.npmjs.org/@kilocode/cli-linux-arm64/-/" +
                "cli-linux-arm64-7.8.32.tgz",
            arm.url,
        )
        assertEquals("ef6c6384d319fd19bf2d7c731c99f32d83e15ed5732955358ca6420292cde447", arm.sha256)
        assertEquals(83986745L, arm.downloadBytes)
        assertEquals(264227832L, arm.installedBytes)
        assertEquals("package/bin/kilo", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://registry.npmjs.org/@kilocode/cli-linux-x64/-/" +
                "cli-linux-x64-7.8.32.tgz",
            x64.url,
        )
        assertEquals("d37d2822060210cf9069ec7548f8866c737b391f46f0d24653c3049064b4c9dd", x64.sha256)
        assertEquals(84585073L, x64.downloadBytes)
        assertEquals(265234608L, x64.installedBytes)
        assertEquals("package/bin/kilo", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Antigravity 1-5-9 pins equal the downloads checked on 2026-11-14`() {
        // Moved after the freeze. Each SHA-256 is of the downloaded bytes, whose sha512 equals the release manifest's
        // value for that platform.
        val recipe = AgentPins.builtIn.catalog().byId(AgentId("antigravity"))!!.recipe!!
        assertEquals("1.5.14", recipe.version)
        assertEquals("agy", recipe.executable)
        assertEquals(listOf("acp", "--disable-telemetry"), recipe.launchArgs)
        assertEquals(listOf("acp", "--login"), recipe.signInArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals(
            "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.14-5302690847260672/linux-arm/" +
                "cli_linux_arm64.tar.gz",
            arm.url,
        )
        assertEquals("e5f69a3123fac66974b303d9a201f381a0fb32b3ef869d9139da85d5caf67a2a", arm.sha256)
        assertEquals(92116379L, arm.downloadBytes)
        assertEquals(256070320L, arm.installedBytes)
        assertEquals("antigravity", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.14-5302690847260672/linux-x64/" +
                "cli_linux_x64.tar.gz",
            x64.url,
        )
        assertEquals("d58386322d2073e96b33f7cc32b4e9ae2db6228f58440369725967b2de48c7b5", x64.sha256)
        assertEquals(101195950L, x64.downloadBytes)
        assertEquals(273240776L, x64.installedBytes)
        assertEquals("antigravity", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 OpenCode 2 pins equal the native pin of commit 93fcf4049`() {
        // Moved after the freeze (2.2.5 -> 2.3.2). Each SHA-256 is the registry tarball's, whose sha512 matches npm's
        // published integrity; the same values pin the in-app Ubuntu install (OpenCodePins).
        val agent = AgentPins.builtIn.catalog().byId(io.github.eslamasabry.oc.engine.agents.AgentId("opencode2"))!!
        val recipe = agent.recipe!!
        assertEquals("2.3.2", recipe.version)
        assertEquals("opencode", recipe.executable)
        assertEquals(listOf("serve", "--hostname", "127.0.0.1"), recipe.launchArgs)
        val arm = recipe.artifacts.getValue(AgentArchitecture.Arm64)
        assertEquals("https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.3.2.tgz", arm.url)
        assertEquals("91a937129efdae8eb8c0c5018ae533f9f35117f26ffd9a4d1f7a5f9da1e69306", arm.sha256)
        assertEquals("package/bin/opencode", arm.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, arm.format)
        val x64 = recipe.artifacts.getValue(AgentArchitecture.X64)
        assertEquals(
            "https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.3.2.tgz",
            x64.url,
        )
        assertEquals("e567dcdb1310dcae4e442077e0d508e10a80c1b996131a04dc9b978cdd26e5cd", x64.sha256)
        assertEquals("package/bin/opencode", x64.archiveMember)
        assertEquals(AgentArtifactFormat.TarGz, x64.format)
    }

    @Test
    fun `B-AGENT-008 Paseo, lock, Node and tested Claude versions equal the freeze tip`() {
        val pins = AgentPins.builtIn
        // The Node pins as the Flutter app's paseo_scripts.dart last had them (v24.22.3); native-owned since 2026-11-13.
        val node = mapOf(
            "nodeVersion" to "v24.22.3",
            "nodeArm64Sha256" to "261af2af250dfd7e9f25cf151d1b2713a0adf8f10a9ad116c4947f68627affe8",
            "nodeX64Sha256" to "061ec3e3062115705b56b51fa9cc330ab50d34b6969387d4e4b1a50c6809acc2",
        )
        fun dartConst(name: String) = node.getValue(name)
        // Paseo moved to 0.17.1 (via 0.16.1, 0.15.4) after the Flutter freeze (B-193): the pins equal the install script's own checks.
        assertEquals(PaseoPhoneScripts.VERSION, pins.paseo.version)
        assertEquals(PaseoPhoneScripts.PACKAGE_LOCK_SHA256, pins.paseo.packageLockSha256)
        assertEquals(PaseoPhoneScripts.CLI_SHA256, pins.paseo.cliSha256)
        assertEquals(PaseoPhoneScripts.PAYLOAD_SHA256, pins.paseo.payloadSha256)
        assertEquals(PaseoPhoneScripts.CODE_TREE_SHA256, pins.paseo.codeTreeSha256)
        assertEquals(dartConst("nodeVersion"), pins.node.version)
        assertEquals(dartConst("nodeArm64Sha256"), pins.node.sha256[AgentArchitecture.Arm64])
        assertEquals(dartConst("nodeX64Sha256"), pins.node.sha256[AgentArchitecture.X64])
        assertEquals("/home/oc/.local/share/oc-paseo/0.17.1-e642771add42", PaseoPhoneScripts.INSTALL_DIRECTORY)
        val tested = Regex("claudeCodeTestedVersions = \\{([^}]*)\\}").find(testedVersions)!!.groupValues[1]
        // 2.1.321, 2.1.325 and 2.1.327 moved after the freeze (Fable 5.2 needs 2.1.321 or newer), and 2.1.330 was
        // proven on a phone on 2026-11-21: the freeze set plus all four.
        assertEquals(
            Regex("'([^']+)'").findAll(tested).map { it.groupValues[1] }.toList() +
                listOf("2.1.321", "2.1.325", "2.1.327", "2.1.330"),
            pins.claudeCodeTestedVersions,
        )
        val paseoTested = Regex("paseoTestedVersions = \\{([^}]*)\\}").find(testedVersions)!!.groupValues[1]
        assertEquals(
            Regex("'([^']+)'").findAll(paseoTested).map { it.groupValues[1] }.toList() + listOf("0.15.3", "0.15.4", "0.16.1"),
            pins.paseoTestedVersions,
        )
    }
}
