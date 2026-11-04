package io.github.eslamasabry.oc.engine.agents.scripts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun sha256Hex(bytes: ByteArray): String = hexSha256(bytes)

private fun hexSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** The JVM binding of [PaseoLockCodec] (what the app wires on Android). */
internal object JvmPaseoLockCodec : PaseoLockCodec {
    override fun sha256Hex(bytes: ByteArray): String = hexSha256(bytes)

    override fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
}

/** Port of test/paseo_phone_install_test.dart against [PaseoPhoneScripts]. */
class PaseoPhoneInstallTest {
    private val lock = File(repoRoot, PaseoPhoneScripts.PACKAGE_LOCK_ASSET).readText()
    private lateinit var temp: File
    private val fixturePayload = "export const pinnedFixture = true;\n"
    private var fixtureCliSha = ""
    private var fixtureCodeTreeSha = ""

    @BeforeTest
    fun setUp() {
        temp = tempDir("paseo-phone-")
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    private fun isolated(script: String) = script.replace("/home/oc", "${temp.path}/oc")

    private fun fixtureScript(script: String) = isolated(script)
        .replace(PaseoPhoneScripts.CODE_TREE_SHA256, fixtureCodeTreeSha)
        .replace(PaseoPhoneScripts.CLI_SHA256, fixtureCliSha)
        .replace(PaseoPhoneScripts.PAYLOAD_SHA256, sha256Hex(fixturePayload.toByteArray()))

    private val directory get() = isolated(PaseoPhoneScripts.INSTALL_DIRECTORY)
    private val parent get() = File(directory).parent
    private val launcher get() = "${temp.path}/oc/.local/bin/paseo"
    private val launcherParent get() = File(launcher).parent

    /** Accept only the install's exact sync paths and record each receipt (GNU sync -f would flush the host disk). */
    private fun fixtureSync(): String = """
sync() {
  [ "${'$'}#" = 2 ] && [ "${'$'}1" = -f ] || return 90
  case "${'$'}2" in
    '$directory.oc-pending.new'|'$launcher.oc-pending.new')
      [ -f "${'$'}2" ] && [ ! -L "${'$'}2" ] || return 91
      fixture_sync_receipt="${'$'}2" ;;
    '$parent'|'$launcherParent')
      [ -d "${'$'}2" ] && [ ! -L "${'$'}2" ] || return 92
      case "${'$'}2" in
        '$parent') fixture_sync_receipt='$directory.oc-pending' ;;
        *) fixture_sync_receipt='$launcher.oc-pending' ;;
      esac ;;
    '$directory'|'$launcher')
      [ -e "${'$'}2" ] || [ -L "${'$'}2" ] || return 93
      fixture_sync_receipt="${'$'}2.oc-pending" ;;
    *) return 94 ;;
  esac
  fixture_sync_state=none
  if [ -e "${'$'}fixture_sync_receipt" ] || [ -L "${'$'}fixture_sync_receipt" ]; then
    [ -f "${'$'}fixture_sync_receipt" ] && [ ! -L "${'$'}fixture_sync_receipt" ] || return 95
    fixture_sync_state=${'$'}(cat "${'$'}fixture_sync_receipt") || return 96
    case "${'$'}fixture_sync_state" in new|existing) ;; *) return 97 ;; esac
  fi
  printf '%s\t%s\n' "${'$'}2" "${'$'}fixture_sync_state" >> '${temp.path}/sync-calls'
}
""".removePrefix("\n")

    private fun recordedSyncCalls() = File("${temp.path}/sync-calls").readLines()

    private fun expectedSyncCalls(receipt: String = "new", rollback: Boolean = false) = listOfNotNull(
        "$directory.oc-pending.new\t$receipt",
        "$parent\t$receipt",
        "$launcher.oc-pending.new\t$receipt",
        "$launcherParent\t$receipt",
        if (!rollback) "$directory\t$receipt" else null,
        "$parent\t$receipt",
        "$parent\tnone",
        if (!rollback) "$launcher\t$receipt" else null,
        "$launcherParent\t$receipt",
        "$launcherParent\tnone",
    )

    private fun runInstall(
        failNpm: Boolean = false,
        tamperCli: Boolean = false,
        tamperPayload: Boolean = false,
        wrongLink: Boolean = false,
        failLaunch: Boolean = false,
        wrongVersion: Boolean = false,
        activeFails: Boolean = false,
    ): ShResult {
        val bin = File("${temp.path}/oc/.local/node/bin").also { it.mkdirs() }
        val node = File("${bin.path}/node")
        node.writeText("#!/bin/sh\nif [ \"\$1\" = --version ]; then echo ${PaseoPhoneScripts.NODE_VERSION}; fi\n")
        val active = if (activeFails) "case \"\$0\" in\n  *.new/*) ;;\n  *) exit 44 ;;\nesac" else ""
        val fixtureCli = "#!/bin/sh\n" +
            "$active\n" +
            "printf executed >> '${temp.path}/cli-executed'\n" +
            "[ -z \"\${BC3_PRIVATE_TOKEN:-}\" ] || exit 91\n" +
            "case \"\$*\" in\n" +
            "  --version) echo ${if (wrongVersion) "0.0.0" else PaseoPhoneScripts.VERSION} ;;\n" +
            "  'daemon run --help')\n" +
            "    ${if (failLaunch) "echo private-cli-output >&2; exit 42" else "echo \"--home Local daemon home\""} ;;\n" +
            "  *) exit 92 ;;\n" +
            "esac\n"
        fixtureCliSha = sha256Hex(fixtureCli.toByteArray())
        val manifest = "$fixtureCliSha  ${PaseoPhoneScripts.CLI_RELATIVE_PATH}\n" +
            "${sha256Hex(fixturePayload.toByteArray())}  ${PaseoPhoneScripts.PAYLOAD_RELATIVE_PATH}\n"
        fixtureCodeTreeSha = sha256Hex(manifest.toByteArray())
        val cliBase64 = Base64.getEncoder().encodeToString((fixtureCli + if (tamperCli) "# changed\n" else "").toByteArray())
        val payloadBase64 = Base64.getEncoder().encodeToString((fixturePayload + if (tamperPayload) "// changed\n" else "").toByteArray())
        val link = if (wrongLink) "../@getpaseo/cli/bin/other" else "../@getpaseo/cli/bin/paseo"
        val npm = File("${bin.path}/npm")
        npm.writeText(
            "#!/bin/sh\n" +
                "${if (failNpm) "echo private-npm-output >&2; exit 42" else ""}\n" +
                "while [ \"\$1\" != --prefix ]; do shift; done\n" +
                "shift\n" +
                "mkdir -p \"\$1/node_modules/.bin\" \"\$1/node_modules/@getpaseo/cli/bin\" \"\$1/node_modules/@getpaseo/cli/dist\"\n" +
                "printf '%s' '$cliBase64' | base64 -d > \"\$1/${PaseoPhoneScripts.CLI_RELATIVE_PATH}\"\n" +
                "printf '%s' '$payloadBase64' | base64 -d > \"\$1/${PaseoPhoneScripts.PAYLOAD_RELATIVE_PATH}\"\n" +
                "chmod 755 \"\$1/${PaseoPhoneScripts.CLI_RELATIVE_PATH}\"\n" +
                "ln -s '$link' \"\$1/node_modules/.bin/paseo\"\n",
        )
        run(listOf("chmod", "755", node.path, npm.path))
        return run(
            listOf(
                "/bin/sh",
                "-c",
                "id() { echo 1000; }\n" +
                    "uname() { echo aarch64; }\n" +
                    "oc_stage() { printf '::oc stage %s\\n' \"\$1\"; }\n" +
                    "oc_version() { printf '%s\\n' \"\$1\"; }\n" +
                    "${fixtureSync()}\n" +
                    "${fixtureScript(PaseoPhoneScripts.install(lock, JvmPaseoLockCodec))}\n",
            ),
            environment = mapOf("HOME" to "${temp.path}/oc", "BC3_PRIVATE_TOKEN" to "private-environment-sentinel"),
            timeoutSeconds = 120,
        )
    }

    private fun runCheck() = run(listOf("/bin/sh", "-c", "${fixtureSync()}\n${fixtureScript(PaseoPhoneScripts.check)}"))

    private val packages: JsonObject get() = Json.parseToJsonElement(lock).jsonObject.getValue("packages").jsonObject

    @Test
    fun `B-LINUX-114 shipped lock freezes every HTTPS tarball with SRI`() {
        assertEquals(PaseoPhoneScripts.PACKAGE_LOCK_SHA256, sha256Hex(lock.toByteArray()))
        assertEquals("0.11.2", packages.getValue("node_modules/@getpaseo/cli").jsonObject.getValue("version").jsonPrimitive.content)
        for ((key, value) in packages) {
            if (key.isEmpty()) continue
            assertTrue(value.jsonObject.getValue("resolved").jsonPrimitive.content.startsWith("https://registry.npmjs.org/"), key)
            assertTrue(value.jsonObject.getValue("integrity").jsonPrimitive.content.startsWith("sha512-"), key)
        }
        for (arch in listOf("arm64", "x64")) {
            val entry = packages.entries.single { it.key.endsWith("/sherpa-onnx-linux-$arch") }
            assertEquals("1.12.28", entry.value.jsonObject.getValue("version").jsonPrimitive.content)
        }
    }

    @Test
    fun `B-LINUX-114 CLI pins match the independently verified registry artifact`() {
        val pkg = packages.getValue("node_modules/@getpaseo/cli").jsonObject
        assertEquals("bin/paseo", pkg.getValue("bin").jsonObject.getValue("paseo").jsonPrimitive.content)
        assertEquals("node_modules/@getpaseo/cli/bin/paseo", PaseoPhoneScripts.CLI_RELATIVE_PATH)
        assertEquals("2b761f40e5a6416e4aaa733f38a47d5a4639b42cc1f88fc060256d2c4ed8cf94", PaseoPhoneScripts.CLI_SHA256)
        assertEquals("8620d03c3e7e6776c3bfc4d22a8875f92209a563612fe7cda63dea7512f83c09", PaseoPhoneScripts.PAYLOAD_SHA256)
        assertEquals("aa0467db317397425785bafc5999e9b5287b0ef4e8d799718cf509cce82a7f04", PaseoPhoneScripts.CODE_TREE_SHA256)
    }

    @Test
    fun `B-LINUX-114 modified lock is rejected before constructing executable script`() {
        assertFailsWith<PaseoPinUnavailable> { PaseoPhoneScripts.install("$lock ", JvmPaseoLockCodec) }
    }

    @Test
    fun `B-LINUX-114 lock transport stays below Linux per-argument limit`() {
        assertTrue(PaseoPhoneScripts.install(lock, JvmPaseoLockCodec).toByteArray().size < 64 * 1024)
    }

    @Test
    fun `B-LINUX-114 all install and check scripts parse as POSIX shell`() {
        for (script in listOf(
            PaseoPhoneScripts.nodeInstall,
            PaseoPhoneScripts.nodeCheck,
            PaseoPhoneScripts.install(lock, JvmPaseoLockCodec),
            PaseoPhoneScripts.check,
        )) {
            val file = File("${temp.path}/script.sh").also { it.writeText(script) }
            val result = run(listOf("/bin/sh", "-n", file.path))
            assertEquals(0, result.exitCode, result.stderr)
        }
    }

    @Test
    fun `B-LINUX-114 fake installer completes under oc and replaces the launcher`() {
        val result = runInstall()
        assertEquals(0, result.exitCode, result.stderr)
        assertTrue(result.stdout.endsWith("${PaseoPhoneScripts.VERSION}\n"))
        assertTrue("::oc stage Checking Paseo checksum" in result.stdout)
        assertTrue("::oc stage Checking Paseo launch command" in result.stdout)
        assertTrue("::oc stage Checking Paseo version" in result.stdout)
        val checked = runCheck()
        assertEquals(0, checked.exitCode, checked.stderr)
        assertEquals(PaseoPhoneScripts.PACKAGE_LOCK_SHA256, File("$directory/.oc-package-lock-sha256").readText())
        assertTrue(isLink(launcher))
        assertEquals(expectedSyncCalls(), recordedSyncCalls())
    }

    private fun changedFails(failure: String) {
        val old = File("$directory/previous").also { it.parentFile.mkdirs(); it.writeText("previous") }
        val result = runInstall(
            tamperCli = failure == "entrypoint",
            tamperPayload = failure == "payload",
            wrongLink = failure == "launcher",
        )
        assertNotEquals(0, result.exitCode)
        assertTrue("::oc stage Checking Paseo checksum" in result.stdout)
        assertTrue("checksum check. Run setup again." in result.stderr, result.stderr)
        assertFalse(exists("${temp.path}/cli-executed"))
        assertEquals("previous", old.readText())
    }

    @Test
    fun `B-LINUX-114 changed entrypoint fails before execution or replacing good tree`() = changedFails("entrypoint")

    @Test
    fun `B-LINUX-114 changed payload fails before execution or replacing good tree`() = changedFails("payload")

    @Test
    fun `B-LINUX-114 changed launcher fails before execution or replacing good tree`() = changedFails("launcher")

    @Test
    fun `B-LINUX-114 launch command failure names its step and hides private output`() {
        val result = runInstall(failLaunch = true)
        assertNotEquals(0, result.exitCode)
        assertTrue("::oc stage Checking Paseo launch command" in result.stdout)
        assertTrue("launch command check. Run setup again." in result.stderr)
        assertFalse("private-cli-output" in result.stdout + result.stderr)
        assertFalse(File(directory).isDirectory)
    }

    @Test
    fun `B-LINUX-114 version failure names its step before replacing the tree`() {
        val result = runInstall(wrongVersion = true)
        assertNotEquals(0, result.exitCode)
        assertTrue("::oc stage Checking Paseo version" in result.stdout)
        assertTrue("version check. Run setup again." in result.stderr)
        assertFalse(File(directory).isDirectory)
    }

    @Test
    fun `B-LINUX-114 installed marker cannot hide modified CLI implementation`() {
        assertEquals(0, runInstall().exitCode)
        val execution = File("${temp.path}/cli-executed").also { assertTrue(it.delete()) }
        File("$directory/node_modules/@getpaseo/cli/dist/daemon.js").writeText("throw new Error(\"changed\");")
        assertNotEquals(0, runCheck().exitCode)
        assertFalse(execution.exists())
    }

    @Test
    fun `B-LINUX-114 installed marker cannot hide a changed outer launcher`() {
        assertEquals(0, runInstall().exitCode)
        val execution = File("${temp.path}/cli-executed").also { assertTrue(it.delete()) }
        Files.delete(Paths.get(launcher))
        link(launcher, "$directory/${PaseoPhoneScripts.CLI_RELATIVE_PATH}")
        assertNotEquals(0, runCheck().exitCode)
        assertFalse(execution.exists())
    }

    @Test
    fun `B-LINUX-114 failed npm retains previous install and hides npm output`() {
        val old = File("$directory/previous").also { it.parentFile.mkdirs(); it.writeText("previous") }
        val result = runInstall(failNpm = true)
        assertNotEquals(0, result.exitCode)
        assertTrue("Check the connection and retry" in result.stderr)
        assertFalse("private-npm-output" in result.stdout + result.stderr)
        assertEquals("previous", old.readText())
        assertFalse(File("${temp.path}/oc/.cache/oc-paseo-install").isDirectory)
    }

    private fun update(activeFails: Boolean) {
        assertEquals(0, runInstall().exitCode)
        val cli = File("$directory/${PaseoPhoneScripts.CLI_RELATIVE_PATH}")
        val previousCode = cli.readText()
        val previousLink = linkTarget(launcher)
        assertTrue(File("${temp.path}/sync-calls").delete())
        val result = runInstall(activeFails = activeFails)
        if (activeFails) {
            assertNotEquals(0, result.exitCode)
            assertTrue("Paseo could not finish updating. Run setup again." in result.stderr, result.stderr)
            assertEquals(previousCode, cli.readText())
        } else {
            assertEquals(0, result.exitCode, result.stderr)
            assertEquals(previousCode, File("$directory.oc-good/${PaseoPhoneScripts.CLI_RELATIVE_PATH}").readText())
            assertEquals(previousLink, linkTarget("$launcher.oc-good"))
        }
        assertEquals(previousLink, linkTarget(launcher))
        assertFalse(exists("$directory.oc-pending"))
        assertFalse(exists("$launcher.oc-pending"))
        assertEquals(expectedSyncCalls(receipt = "existing", rollback = activeFails), recordedSyncCalls())
    }

    @Test
    fun `B-LINUX-114 successful Paseo update retains the previous good generation`() = update(activeFails = false)

    @Test
    fun `B-LINUX-114 activated Paseo failure restores the previous tree and launcher`() = update(activeFails = true)

    @Test
    fun `B-LINUX-114 root is refused before installing`() {
        val result = run(
            listOf("/bin/sh", "-c", "id() { echo 0; }; ${isolated(PaseoPhoneScripts.nodeInstall)}"),
            environment = mapOf("HOME" to "${temp.path}/oc"),
        )
        assertNotEquals(0, result.exitCode)
        assertTrue("private Linux user" in result.stderr)
        assertFalse(File("${temp.path}/oc/.local").exists())
    }

    @Test
    fun `B-LINUX-114 Node downloads exactly the pinned artifact for each ABI`() {
        for ((uname, expected) in mapOf(
            "aarch64" to ("arm64" to PaseoPhoneScripts.NODE_ARM64_SHA256),
            "x86_64" to ("x64" to PaseoPhoneScripts.NODE_X64_SHA256),
        )) {
            val result = run(
                listOf(
                    "/bin/sh",
                    "-c",
                    "id() { echo 1000; }\n" +
                        "uname() { echo $uname; }\n" +
                        "oc_stage() { :; }\n" +
                        "oc_download() { printf '%s\\n%s\\n' \"\$1\" \"\$3\"; return 23; }\n" +
                        "${isolated(PaseoPhoneScripts.nodeInstall)}\n",
                ),
                environment = mapOf("HOME" to "${temp.path}/oc"),
            )
            assertEquals(23, result.exitCode, result.stderr)
            assertTrue("linux-${expected.first}.tar.gz" in result.stdout)
            assertTrue(expected.second in result.stdout)
        }
    }

    @Test
    fun `B-LINUX-114 every literal line of the Flutter scripts appears in the port`() {
        val source = File(repoRoot, "lib/builtin/agents/paseo_scripts.dart").readText()
        assertEquals(dartRawStrings("lib/builtin/agents/paseo_scripts.dart").getValue("_requireAgentUser"), PaseoPhoneScripts.REQUIRE_AGENT_USER)
        val sections = Regex("'''(.*?)'''", RegexOption.DOT_MATCHES_ALL).findAll(source).map { it.groupValues[1] }.toList()
        val generated = listOf(
            PaseoPhoneScripts.nodeInstall, PaseoPhoneScripts.nodeCheck,
            PaseoPhoneScripts.install(lock, JvmPaseoLockCodec), PaseoPhoneScripts.check,
        ).joinToString("\n")
        val lines = sections.flatMap { it.split('\n') }
            .map { it.replace("\\$", "\u0001").replace("\\\\", "\\") }
            .filter { it.isNotBlank() && '$' !in it && "\"name\"" !in it && "sherpa" !in it && "\"version\"" !in it && "\"private\"" !in it && it.trim() !in setOf("{", "}", "},") && "overrides" !in it }
            .map { it.replace('\u0001', '$') }
        assertTrue(lines.size > 80, "${lines.size}")
        for (line in lines) assertTrue(line in generated, line)
    }
}
