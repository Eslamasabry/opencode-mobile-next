package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// Ported from Flutter test/antigravity_quota_client_test.dart (PR #125 and the account lines of PR #136), plus the
// _paReadUsage branches.

private val AGY_NOW = Instant.parse("2026-11-07T20:00:00Z")

private const val BOTH_GROUPS = """
{"command":{"name":"usage","data":{"groups":[
  {"name":"Gemini Models","description":"Models within this group: Gemini Flash, Gemini Pro","buckets":[
    {"id":"gemini-weekly","name":"Weekly Limit Remaining","window":"weekly","remaining_fraction":0.6815,"reset_time":"2026-11-14T07:25:34Z"},
    {"id":"gemini-5h","name":"Five Hour Limit Remaining","window":"5h","remaining_fraction":0.4579,"reset_time":"2026-11-07T21:48:21Z"}]},
  {"name":"Claude and GPT models","description":"Models within this group: Claude Opus, Claude Sonnet, GPT-OSS","buckets":[
    {"id":"3p-weekly","name":"Weekly Limit Remaining","window":"weekly","remaining_fraction":0.0,"reset_time":"2026-11-11T10:05:57Z"},
    {"id":"3p-5h","name":"Five Hour Limit Remaining","window":"5h","disabled":true,"remaining_fraction":1.0}]}
]}}}
"""

private const val ONE_GROUP =
    """{"command":{"name":"usage","data":{"groups":[{"name":"Gemini Models","buckets":[""" +
        """{"id":"gemini-5h","window":"5h","remaining_fraction":0.45,"reset_time":"2026-11-07T21:48:21Z"}]}]}}}"""

class AntigravityQuotaTest {
    @Test
    fun `B-AGENT-430 the CLI's Gemini and Claude and GPT groups become two readings with 5-hour and weekly limits`() {
        val readings = AntigravityQuotaClient.parseCliJson(DartJson.decode(BOTH_GROUPS) as Map<*, *>, AGY_NOW)
        assertEquals(2, readings.size)

        val gemini = readings[0]
        assertEquals("antigravity", gemini.providerId)
        assertEquals("Antigravity (Gemini Models)", gemini.displayName)
        assertEquals(2, gemini.windows.size)
        assertEquals(18_000L, gemini.windows[0].durationSeconds)
        assertEquals("5-hour", gemini.windows[0].label)
        assertEquals(46, gemini.windows[0].remainingPercent)
        assertEquals(Instant.parse("2026-11-07T21:48:21Z"), gemini.windows[0].resetsAt)
        assertEquals(604_800L, gemini.windows[1].durationSeconds)
        assertEquals(68, gemini.windows[1].remainingPercent)
        assertEquals(Instant.parse("2026-11-14T07:25:34Z"), gemini.windows[1].resetsAt)

        val claude = readings[1]
        assertEquals("antigravity_claude", claude.providerId)
        assertEquals("Antigravity (Claude and GPT models)", claude.displayName)
        assertEquals(18_000L, claude.windows[0].durationSeconds)
        assertEquals(0, claude.windows[0].remainingPercent, "a disabled 5-hour bucket has nothing left, not 100%")
        assertEquals(0, claude.windows[1].remainingPercent)
        assertEquals(Instant.parse("2026-11-11T10:05:57Z"), claude.windows[1].resetsAt)
        assertFalse(gemini.needsSignIn || claude.needsSignIn)
    }

    @Test
    fun `B-AGENT-430 the read runs the CLI inside the built-in Linux and parses it past a banner`() = runTest {
        val scripts = mutableListOf<String>()
        val client =
            AntigravityQuotaClient({ script, _ ->
                scripts += script
                LinuxRunResult(0, "agy 1.5.3\n$ONE_GROUP")
            }, { AGY_NOW })

        val readings = client.fetchAllUsage(profileId = "p1")
        assertEquals(1, readings.size)
        assertEquals("antigravity", readings[0].providerId)
        assertEquals("Antigravity", readings[0].displayName)
        assertEquals(45, readings[0].windows[0].remainingPercent)
        assertTrue("/home/oc/.oc-profiles/p1/.local/bin/agy" in scripts.single())
        assertTrue("/home/oc/.local/bin/agy" in scripts.single())
        assertTrue("-p /usage --output-format json" in scripts.single())
        assertTrue("HOME=" in scripts.single())
        assertEquals(readings, client.cachedReadings)
    }

    @Test
    fun `B-AGENT-431 no CLI, not signed in or unreadable output reads as no readings`() = runTest {
        val answers =
            listOf(LinuxRunResult(127, ""), LinuxRunResult(1, "Please sign in"), LinuxRunResult(0, "not json"))
        for (res in answers) {
            val client = AntigravityQuotaClient({ _, _ -> res }, { AGY_NOW })
            assertEquals(emptyList(), client.fetchAllUsage())
        }
        val failing = AntigravityQuotaClient({ _, _ -> error("linux gone") }, { AGY_NOW })
        assertEquals(emptyList(), failing.fetchAllUsage())
        assertEquals(emptyList(), AntigravityQuotaClient(null) { AGY_NOW }.fetchAllUsage())
    }

    @Test
    fun `B-SEC-230 a profile id that is not a plain id never reaches the script`() {
        val script = AntigravityQuotaClient.cliScript(profileId = "x; rm -rf /")
        assertFalse("rm -rf" in script)
        // Only the fixed sign-in search (`.oc-profiles/*/`) remains; no profile's own path is named (PR #136).
        assertFalse(Regex("\\.oc-profiles/[^*]").containsMatchIn(script))
        assertTrue("/home/oc/.local/bin/agy" in script)
        assertFalse(Regex("\\.oc-profiles/[^*]").containsMatchIn(AntigravityQuotaClient.cliScript("a".repeat(65))))
        assertTrue("/home/oc/.oc-profiles/a_B-9/" in AntigravityQuotaClient.cliScript("a_B-9"))
    }

    @Test
    fun `B-AGENT-432 Antigravity comes only from its CLI, needs sign-in when signed out, never invented`() =
        runTest {
            val window = ProviderUsageWindowReading(18_000, "", 50, null)
            val claude = ProviderUsageReading("claude", "Claude", AGY_NOW, listOf(window))
            val ready = AntigravityAgentState.Ready
            val agy = AntigravityQuotaClient.parseCliJson(DartJson.decode(ONE_GROUP) as Map<*, *>, AGY_NOW)
            var asked = 0
            val cli: suspend () -> List<ProviderUsageReading>? = {
                asked++
                agy
            }

            assertEquals(listOf(claude) + agy, withAntigravityUsage(listOf(claude), ready, AGY_NOW, cli))
            // A CLI that reports nothing adds nothing.
            assertEquals(listOf(claude), withAntigravityUsage(listOf(claude), ready, AGY_NOW) { emptyList() })
            assertEquals(listOf(claude), withAntigravityUsage(listOf(claude), ready, AGY_NOW) { null })

            asked = 0
            val signedOut = withAntigravityUsage(listOf(claude), AntigravityAgentState.SignedOut, AGY_NOW, cli)
            assertEquals(0, asked, "a signed-out agent's CLI is not asked")
            val needs = signedOut.single { it.providerId == "antigravity" }
            assertTrue(needs.needsSignIn)
            assertTrue(needs.windows.isEmpty())

            val other = AntigravityAgentState.Other
            assertEquals(listOf(claude), withAntigravityUsage(listOf(claude), other, AGY_NOW, cli))
            assertEquals(0, asked)

            val noRow = withAntigravityUsage(emptyList(), AntigravityAgentState.Absent, AGY_NOW) {
                agy + ProviderUsageReading("antigravity_x", "x", AGY_NOW, emptyList(), needsSignIn = true)
            }
            assertEquals(agy, noRow)

            // The helper's own Antigravity reading wins: the CLI is not asked.
            val own = ProviderUsageReading("antigravity", "Antigravity", AGY_NOW, emptyList())
            assertEquals(listOf(own), withAntigravityUsage(listOf(own), AntigravityAgentState.Ready, AGY_NOW, cli))
            assertEquals(0, asked)
        }

    @Test
    fun `B-183 an ACCOUNT_EMAIL line in the CLI output names the account on every reading`() = runTest {
        val client = AntigravityQuotaClient({ _, _ -> LinuxRunResult(0, "ACCOUNT_EMAIL:altaf.alu@gmail.com\n$ONE_GROUP") }, { AGY_NOW })
        val readings = client.fetchAllUsage(profileId = "p1")
        assertEquals("antigravity", readings.single().providerId)
        assertEquals("altaf.alu@gmail.com", readings.single().accountRef)
        assertEquals("Google AI Pro", readings.single().planLabel)

        val both = AntigravityQuotaClient({ _, _ -> LinuxRunResult(0, "ACCOUNT_EMAIL:a@b.co\n$BOTH_GROUPS") }, { AGY_NOW })
        assertEquals(listOf("a@b.co", "a@b.co"), both.fetchAllUsage().map { it.accountRef })

        // Without the line, no account is guessed.
        val none = AntigravityQuotaClient({ _, _ -> LinuxRunResult(0, ONE_GROUP) }, { AGY_NOW })
        assertNull(none.fetchAllUsage().single().accountRef)
    }

    @Test
    fun `B-183 the probe picks only the email field and prints only a checked value`() {
        val script = AntigravityQuotaClient.cliScript(profileId = "p1")
        assertTrue("antigravity-oauth-token" in script)
        assertTrue("id_token_email" in script)
        assertTrue("ACCOUNT_EMAIL" in script)
        assertTrue("export HOME=" in script)
        assertTrue("-gt ${AntigravityQuotaClient.MAX_ACCOUNT_LENGTH}" in script)
        assertTrue("*[!A-Za-z0-9.@_+-]*" in script)
        // Emits only a validated email, never the token file itself.
        assertFalse("cat " in script)
        assertFalse("access_token" in script)
        assertFalse("refresh_token" in script)
        assertTrue(script.endsWith("done; exit 127"))
    }

    @Test
    fun `B-183 the probe output parser accepts only an email-shaped value`() {
        assertEquals(
            "user@example.com",
            AntigravityQuotaClient.parseProbeAccount("noise\nACCOUNT_EMAIL:user@example.com\n{}"),
        )
        assertEquals("user@example.com", AntigravityQuotaClient.parseProbeAccount("ACCOUNT_EMAIL: user@example.com \r\n{}"))
        val bad =
            listOf(
                "ACCOUNT_EMAIL:not-an-email",
                "ACCOUNT_EMAIL:user@example.com extra",
                "ACCOUNT_EMAIL:{\"k\":\"v\"}",
                "ACCOUNT_EMAIL:${"a".repeat(70)}@example.com",
                "ACCOUNT_EMAIL:${"a".repeat(50)}@${"b".repeat(10)}.com",
                "ACCOUNT_EMAIL:",
                "ACCOUNT_EMAIL:user@localhost",
                "ACCOUNT_EMAIL:user@exa mple.com",
                "ACCOUNT_EMAIL:ya29.a0AfH6SMBx-token-like-value",
                "ACCOUNT_EMAIL:eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.sig",
                "ACCOUNT_EMAIL:<script>@x.com",
                "say ACCOUNT_EMAIL:user@example.com",
                "user@example.com",
                "no marker at all",
            )
        for (value in bad) assertNull(AntigravityQuotaClient.parseProbeAccount(value), value)
    }

    @Test
    fun `B-183 an account field in the CLI's own JSON is used when the probe printed none`() {
        val json = DartJson.decode(ONE_GROUP.replace("\"data\":{", "\"data\":{\"email\":\"me@corp.io\",")) as Map<*, *>
        assertEquals("me@corp.io", AntigravityQuotaClient.parseCliJson(json, AGY_NOW).single().accountRef)
        val probe = AntigravityQuotaClient.parseCliJson(json, AGY_NOW, accountEmail = "probe@corp.io")
        assertEquals("probe@corp.io", probe.single().accountRef, "the probe's checked email wins")
    }
}
