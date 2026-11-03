package io.github.eslamasabry.oc.engine.plans

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal val NOW = utc(2026, 10, 13, 12)
internal val PC = UsageConnection("pc", "PC", UsageConnectionKind.OpenCodeServer)
internal val IN_APP = UsageConnection("inapp", "In-app Ubuntu", UsageConnectionKind.InAppUbuntu)
internal val PHONE = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)

internal fun both(five: Double = 20.0, week: Double = 40.0, now: Long = NOW): List<UsageWindow> =
    listOf(
        UsageWindow("primary", five, durationSeconds = 18000, resetsAt = now + 2 * HOUR),
        UsageWindow("secondary", week, durationSeconds = 604800, resetsAt = now + 3 * DAY),
    )

internal fun reading(
    provider: String,
    connection: String,
    account: String? = null,
    status: UsageReadingStatus = UsageReadingStatus.Ok,
    windows: List<UsageWindow>? = null,
    age: Long = MIN,
    problem: UsageProblem? = null,
    plan: String? = null,
    method: SignInMethod = SignInMethod.Plan,
    now: Long = NOW,
): UsageReading =
    UsageReading(
        providerId = provider,
        displayName = provider,
        connectionId = connection,
        status = status,
        fetchedAt = now - age,
        windows = windows ?: if (status == UsageReadingStatus.Ok) both(now = now) else emptyList(),
        accountRef = account,
        plan = plan,
        problem = problem,
        method = method,
    )

class UsageSubscriptionsTest {
    private fun merge(
        readings: List<UsageReading>,
        connections: List<UsageConnection> = listOf(IN_APP),
        signIns: List<UsageSignIn> = emptyList(),
        authoritative: Set<String> = emptySet(),
    ) = mergeUsageSubscriptions(
        now = NOW,
        connections = connections,
        readings = readings,
        signIns = signIns,
        authoritativeSignIns = authoritative,
    )

    @Test
    fun `B-AGENT-315 an API key is not a plan card`() {
        val s =
            merge(
                emptyList(),
                connections = listOf(PC),
                signIns =
                    listOf(
                        UsageSignIn("codex", "pc", method = SignInMethod.ApiKey, displayName = "OpenAI"),
                        UsageSignIn("claude", "pc", method = SignInMethod.ApiKey, displayName = "Claude"),
                    ),
                authoritative = setOf("pc"),
            )
        assertTrue(s.subscriptions.isEmpty())
        assertEquals(listOf("codex", "claude"), s.apiKeyAccounts.map { it.providerId })
        // Claude stays on the add list: its plan limits come from Claude Code.
        assertEquals(listOf("claude", "glm", "minimax"), s.addable)
        assertTrue(s.apiKeyAccounts.last().viaOpenCode)
        assertFalse(s.apiKeyAccounts.first().viaOpenCode)
    }

    @Test
    fun `B-AGENT-315 a key plus a plan reading is still a plan`() {
        val s =
            merge(
                listOf(reading("codex", "pc", account = "a")),
                connections = listOf(PC),
                signIns = listOf(UsageSignIn("codex", "pc", method = SignInMethod.ApiKey)),
            )
        assertEquals(1, s.subscriptions.size)
        assertTrue(s.apiKeyAccounts.isEmpty())
    }

    @Test
    fun `B-AGENT-315 Claude via the phone host or a PC is one card each`() {
        val s =
            merge(
                listOf(
                    reading("claude", UsageConnection.PHONE_ID),
                    reading("claude", "pc", windows = both(five = 30.0)),
                ),
                connections = listOf(PHONE, PC),
            )
        assertEquals(2, s.subscriptions.size)
        assertEquals(setOf(UsageConnection.PHONE_ID, "pc"), s.subscriptions.map { it.readFrom!!.id }.toSet())
    }

    @Test
    fun `B-AGENT-315 Claude Code signed out offers Sign in on the phone`() {
        val s =
            merge(
                listOf(reading("claude", UsageConnection.PHONE_ID, status = UsageReadingStatus.NotSignedIn)),
                connections = listOf(PHONE),
            )
        val c = s.subscriptions.single()
        assertEquals(SubscriptionState.NotSignedIn, c.state)
        assertEquals(UsageFix.SignIn, c.fix)
        assertEquals(PHONE, c.fixConnection)
    }

    @Test
    fun `B-AGENT-315 Claude on an OpenCode server is a Details row, not a card`() {
        val s =
            merge(
                emptyList(),
                connections = listOf(PC),
                signIns = listOf(UsageSignIn("claude", "pc", displayName = "Claude")),
                authoritative = setOf("pc"),
            )
        assertTrue(s.subscriptions.isEmpty())
        assertTrue(s.apiKeyAccounts.single().viaOpenCode)
        assertTrue("claude" in s.addable)
    }

    @Test
    fun `B-AGENT-315 a Paseo sign-in-needed status is still a Claude card`() {
        val s =
            merge(
                listOf(reading("claude", UsageConnection.PHONE_ID, status = UsageReadingStatus.NotSignedIn)),
                connections = listOf(PC, PHONE),
                signIns = listOf(UsageSignIn("claude", "pc")),
                authoritative = setOf("pc"),
            )
        assertEquals("claude", s.subscriptions.single().providerId)
        assertEquals(PHONE, s.subscriptions.single().fixConnection)
    }

    @Test
    fun `B-AGENT-315 providers the reader cannot read are not plan cards`() {
        val s =
            merge(
                emptyList(),
                connections = listOf(PC),
                signIns = listOf("copilot", "kimi", "minimax", "glm", "codex").map { UsageSignIn(it, "pc") },
                authoritative = setOf("pc"),
            )
        assertEquals(setOf("codex", "glm", "minimax"), s.subscriptions.map { it.providerId }.toSet())
        assertTrue(s.subscriptions.all { it.fix == UsageFix.SetUp })
    }

    @Test
    fun `B-AGENT-315 Copilot from a Paseo reading is still a card`() {
        val s = merge(listOf(reading("copilot", UsageConnection.PHONE_ID)), connections = listOf(PHONE))
        assertEquals("copilot", s.subscriptions.single().providerId)
    }
}
