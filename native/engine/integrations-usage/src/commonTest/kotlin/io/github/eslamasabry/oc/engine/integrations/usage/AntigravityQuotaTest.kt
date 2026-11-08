package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

// Ported from Flutter test/antigravity_quota_client_test.dart (PR #125), plus the _paReadUsage branches.

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
        assertFalse(".oc-profiles" in script)
        assertTrue("/home/oc/.local/bin/agy" in script)
        assertFalse(".oc-profiles" in AntigravityQuotaClient.cliScript("a".repeat(65)))
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
}
