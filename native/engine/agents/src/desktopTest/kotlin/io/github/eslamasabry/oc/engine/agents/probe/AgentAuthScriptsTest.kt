package io.github.eslamasabry.oc.engine.agents.probe

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.auth.AuthProjectionJson
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Port of test/agent_auth_probe_test.dart: the authored scripts run with bash against fake CLIs, exactly as the Dart
// test does (the guest's /home/oc becomes a temp dir and the uid check reads 1000).

private val repoRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "pubspec.yaml").isFile && File(it, "native").isDirectory }
}

class AgentAuthScriptsTest {
    private lateinit var root: File
    private lateinit var base: String
    private lateinit var home: String

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("oc-auth-probe-").toFile()
        base = "${root.path}/oc"
        home = "$base/.oc-profiles/fixture"
        File("$base/.local/bin").mkdirs()
        File(home).mkdirs()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun cli(id: String, body: String) {
        val file = File("$base/.local/bin/$id")
        file.writeText("#!/bin/sh\n$body\n")
        file.setExecutable(true, true)
    }

    private fun run(id: String, logout: Boolean = false): AuthProjection {
        val agent = AgentCatalog.builtIn.byId(AgentId(id))!!
        val script = AgentAuthScripts.script(agent, if (logout) AuthScriptAction.SignOut else AuthScriptAction.Probe)!!
        val process = ProcessBuilder("bash", "-c", script.replace("/home/oc", base).replace("os.getuid()", "1000"))
            .directory(File(home))
            .also {
                it.environment().clear()
                it.environment() += mapOf(
                    "HOME" to home,
                    "CLAUDE_CONFIG_DIR" to "$home/claude",
                    "CODEX_HOME" to "$home/codex",
                    "PATH" to "/usr/bin:/bin",
                )
            }
            .start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        val stdout = process.inputStream.readBytes().decodeToString()
        val stderr = process.errorStream.readBytes().decodeToString()
        assertEquals(0, process.exitValue())
        assertEquals("", stderr)
        assertFalse(stdout.contains("synthetic-secret"))
        return AuthProjectionJson.decode(Json.parseToJsonElement(stdout).jsonObject)
    }

    @Test
    fun `B-AGENT-005 authored scripts are byte-identical to the Dart scripts the native side admits`() {
        val fixtures = File(repoRoot, "native/android/agenthost/src/test/resources/auth-scripts")
        val profile = ProfileId("synthetic-profile")
        for (agent in AgentCatalog.builtIn.agents) {
            val id = agent.id.raw
            for (action in AuthScriptAction.entries) {
                val fixture = File(fixtures, "$id-${action.wire}.txt")
                if (!fixture.isFile) continue
                val script = AgentAuthScripts.forProfile(profile, AgentAuthScripts.script(agent, action)!!)
                assertEquals(fixture.readText(), script, fixture.name)
                // The digest PhoneAgentAuthRequest checks, over the header-normalized body.
                val executable = agent.recipe!!.executable
                val prefix = script.substringBefore('\n') + "\n"
                val normalized = script.removePrefix(prefix).replace(
                    "python3 - '$id' '$executable' ${action.wire} <<'OC_AUTH_PROBE'\n",
                    "python3 - 'AGENT' 'EXECUTABLE' ACTION <<'OC_AUTH_PROBE'\n",
                )
                val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                assertEquals("d06e0af7b4992e58d93ad4869e4e1b96a39117b06197a6675f3ecfa4c002399c", digest, fixture.name)
            }
        }
        assertTrue(AgentAuthScripts.supportsSignOut(AgentId.CLAUDE))
        assertTrue(AgentAuthScripts.supportsSignOut(AgentId("fx")))
        assertFalse(AgentAuthScripts.supportsSignOut(AgentId("codex")))
    }

    @Test
    fun `B-AGENT-005 captured installed agents have final explicit account states`() {
        for ((name, state) in mapOf("claude-signedIn" to AuthState.SignedIn, "fx-signedOut" to AuthState.SignedOut)) {
            val fixture = Json.parseToJsonElement(File(repoRoot, "test/fixtures/agent_auth/$name-device.json").readText()).jsonObject
            assertEquals(state, AuthProjectionJson.decode(fixture["probe"]!!.jsonObject).state)
            assertTrue(fixture["capture"]!!.jsonObject["elapsedMs"]!!.jsonPrimitive.int < 10000)
        }
    }

    @Test
    fun `B-AGENT-005 malformed and contradictory account replies remain errors`() {
        for (data in listOf(
            "{}",
            """{"state":"loading"}""",
            """{"state":"signedOut","accountDisplayName":"account"}""",
            """{"state":"signedIn","accountDisplayName":"bad\naccount"}""",
            """{"state":"signedIn","error":"synthetic-secret"}""",
            """{"state":"error","error":"synthetic-secret"}""",
        )) {
            val result = AuthProjectionJson.decode(Json.parseToJsonElement(data).jsonObject)
            assertEquals(AuthState.Error, result.state, data)
            assertEquals(AuthProbeError.InvalidResponse, result.error, data)
            assertNull(result.account, data)
        }
    }

    @Test
    fun `B-AGENT-005 Claude explicit signed-out status overrides successful exit`() {
        cli("claude", """
            printf '%s\n' '{"loggedIn":false,"authMethod":"none","apiProvider":"firstParty"}'
            exit 0
        """.trimIndent())
        assertEquals(AuthState.SignedOut, run("claude").state)
    }

    @Test
    fun `B-AGENT-005 Claude status exposes only its dedicated account field`() {
        cli("claude", """
            printf '%s\n' '{"loggedIn":true,"authMethod":"claude.ai","apiProvider":"firstParty","email":"test@example.invalid","accessToken":"synthetic-secret"}'
            printf '%s\n' 'synthetic-secret' >&2
        """.trimIndent())
        val result = run("claude")
        assertEquals(AuthState.SignedIn, result.state)
        assertEquals("test@example.invalid", assertNotNull(result.account).value)
    }

    @Test
    fun `B-AGENT-005 CLI failures never become a signed-out account`() {
        cli("claude", """
            printf '%s\n' 'Run claude login synthetic-secret'
            exit 1
        """.trimIndent())
        assertEquals(AuthProbeError.InvalidResponse, run("claude").error)
    }

    @Test
    fun `B-AGENT-005 fx explicit source is independent of provider error text`() {
        cli("fx", """printf '%s\n' '{"kind":"status","auth":"fx login","error":"Run fx login synthetic-secret"}'""")
        assertEquals(AuthState.SignedIn, run("fx").state)
        cli("fx", """printf '%s\n' '{"kind":"status","auth":"missing"}'""")
        assertEquals(AuthState.SignedOut, run("fx").state)
    }

    @Test
    fun `B-AGENT-005 fx expired and future unknown credential sources are errors`() {
        cli("fx", """printf '%s\n' '{"kind":"status","auth":"fx login","auth_expired":true}'""")
        assertEquals(AuthProbeError.SignInExpired, run("fx").error)
        cli("fx", """printf '%s\n' '{"kind":"status","auth":"future source"}'""")
        assertEquals(AuthProbeError.InvalidResponse, run("fx").error)
    }

    @Test
    fun `B-AGENT-005 unsupported agents reach a typed final error without launching`() {
        for (id in listOf("gemini", "qwen", "goose", "omp-acp")) {
            assertEquals(AuthProbeError.ProbeUnsupported, run(id).error, id)
        }
    }

    @Test
    fun `B-AGENT-005 Codex login status on stderr decides the account state`() {
        File("$home/codex").mkdirs()
        cli("codex", """
            [ "${'$'}1 ${'$'}2" = "login status" ] || exit 9
            printf '%s\n' 'Logged in using an API key - sk-synthetic-secret' >&2
        """.trimIndent())
        assertEquals(AuthState.SignedIn, run("codex").state)
        cli("codex", """printf '%s\n' 'Logged in using ChatGPT' >&2""")
        assertEquals(AuthState.SignedIn, run("codex").state)
        cli("codex", "printf '%s\\n' 'Not logged in' >&2\nexit 1")
        assertEquals(AuthState.SignedOut, run("codex").state)
        // A configuration error is not a signed-out account.
        cli("codex", "printf '%s\\n' 'Error loading configuration: synthetic-secret' >&2\nexit 1")
        assertEquals(AuthProbeError.InvalidResponse, run("codex").error)
    }

    @Test
    fun `B-AGENT-005 Kilo reads only whether its credential store has one`() {
        cli("kilo", "exit 9")
        assertEquals(AuthState.SignedOut, run("kilo").state)
        val store = File("$home/.local/share/kilo/auth.json")
        store.parentFile.mkdirs()
        store.writeText("{}")
        assertEquals(AuthState.SignedOut, run("kilo").state)
        store.writeText("""{"kilo":{"type":"oauth","access":"synthetic-secret","refresh":"r","expires":1}}""")
        assertEquals(AuthState.SignedIn, run("kilo").state)
        store.writeText("""{"anthropic":{"type":"api","key":"synthetic-secret"}}""")
        assertEquals(AuthState.SignedIn, run("kilo").state)
        store.writeText("[1]")
        assertEquals(AuthProbeError.InvalidResponse, run("kilo").error)
    }

    @Test
    fun `B-AGENT-005 Cline reads its chosen provider's key or sign-in`() {
        cli("cline", "exit 9")
        assertEquals(AuthState.SignedOut, run("cline").state)
        val store = File("$home/.cline/data/settings/providers.json")
        store.parentFile.mkdirs()
        // What a first run leaves: the default provider with no credential.
        store.writeText(
            """{"version":1,"lastUsedProvider":"cline","providers":{"cline":{"settings":{"provider":"cline","model":"m"},"tokenSource":"manual"}}}""",
        )
        assertEquals(AuthState.SignedOut, run("cline").state)
        store.writeText(
            """{"version":1,"lastUsedProvider":"cline","providers":{"cline":{"settings":{"provider":"cline","auth":{"accessToken":"synthetic-secret"}},"tokenSource":"oauth"}}}""",
        )
        assertEquals(AuthState.SignedIn, run("cline").state)
        store.writeText(
            """{"version":1,"lastUsedProvider":"anthropic","providers":{"anthropic":{"settings":{"provider":"anthropic","apiKey":"synthetic-secret"}}}}""",
        )
        assertEquals(AuthState.SignedIn, run("cline").state)
    }

    @Test
    fun `B-AGENT-005 missing installation is distinct from signed out`() {
        assertEquals(AuthProbeError.NotInstalled, run("fx").error)
    }

    @Test
    fun `B-AGENT-005 a CLI that hangs reaches timedOut within ten seconds`() {
        cli("fx", "sleep 60")
        val started = System.nanoTime()
        assertEquals(AuthProbeError.TimedOut, run("fx").error)
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(10))
    }

    @Test
    fun `B-AGENT-005 successful logout exit requires a signed-out probe`() {
        cli("claude", """
            if [ "${'$'}2" = logout ]; then printf '%s\n' 'synthetic-secret'; exit 0; fi
            printf '%s\n' '{"loggedIn":true,"authMethod":"claude.ai","apiProvider":"firstParty"}'
        """.trimIndent())
        assertEquals(AuthProbeError.SignOutFailed, run("claude", logout = true).error)
    }

    @Test
    fun `B-AGENT-005 logout runs the agent command and confirms credentials are gone`() {
        cli("fx", """
            if [ "${'$'}1" = logout ]; then touch "${'$'}HOME/logged-out"; exit 0; fi
            if [ -f "${'$'}HOME/logged-out" ]; then
              printf '%s\n' '{"kind":"status","auth":"missing"}'
            else
              printf '%s\n' '{"kind":"status","auth":"fx login"}'
            fi
        """.trimIndent())
        assertEquals(AuthState.SignedOut, run("fx", logout = true).state)
        assertTrue(File("$home/logged-out").exists())
    }
}
