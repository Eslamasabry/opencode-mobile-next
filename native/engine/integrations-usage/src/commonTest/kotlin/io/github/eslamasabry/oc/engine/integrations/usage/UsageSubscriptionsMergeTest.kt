package io.github.eslamasabry.oc.engine.integrations.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Ported from Flutter test/usage_subscriptions_test.dart (rows of docs/design/usage-subscriptions-contract-2026-10-13.md).

internal val NOW: Instant = Instant.parse("2026-10-13T12:00:00Z")
internal val PC = UsageConnection("pc", "PC", UsageConnectionKind.OpenCodeServer)
internal val IN_APP = UsageConnection("inapp", "In-app Ubuntu", UsageConnectionKind.InAppUbuntu)
internal val LAPTOP = UsageConnection("laptop", "Laptop", UsageConnectionKind.OpenCodeServer)
internal val PHONE = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)

internal fun both(five: Double = 20.0, week: Double = 40.0, now: Instant = NOW): List<UsageWindow> =
    listOf(
        UsageWindow("primary", five, durationSeconds = 18_000, resetsAt = now + 2.hours),
        UsageWindow("secondary", week, durationSeconds = 604_800, resetsAt = now + 3.days),
    )

internal fun reading(
    provider: String,
    connection: String,
    account: String? = null,
    status: UsageReadingStatus = UsageReadingStatus.Ok,
    windows: List<UsageWindow>? = null,
    age: Duration = 1.minutes,
    problem: UsageProblem? = null,
    plan: String? = null,
    method: SignInMethod = SignInMethod.Plan,
    now: Instant = NOW,
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

class UsageSubscriptionsMergeTest {
    private fun merge(
        readings: List<UsageReading>,
        connections: List<UsageConnection> = listOf(IN_APP),
        signIns: List<UsageSignIn> = emptyList(),
        authoritative: Set<String> = emptySet(),
        threshold: Double = 80.0,
        overrides: Map<String, UsageAlertOverride> = emptyMap(),
    ): UsageSnapshot =
        mergeUsageSubscriptions(
            now = NOW,
            connections = connections,
            readings = readings,
            signIns = signIns,
            authoritativeSignIns = authoritative,
            defaultThreshold = threshold,
            overrides = overrides,
        )

    // ---- B-STATE-152: one card per account, freshest wins, connection names only when needed

    @Test
    fun `B-STATE-152 row 6 the same account on two connections is one card and freshest wins`() {
        val s =
            merge(
                listOf(
                    reading("codex", "pc", account = "a", age = 40.minutes),
                    reading("codex", "inapp", account = "a", windows = both(five = 55.0), age = 2.minutes),
                ),
                connections = listOf(IN_APP, PC),
            )
        assertEquals(1, s.subscriptions.size)
        val c = s.subscriptions.single()
        assertEquals(IN_APP, c.readFrom)
        assertEquals(55.0, c.fiveHour!!.usedPercent)
        assertEquals(listOf("inapp", "pc"), c.signedInOn.map { it.connection.id })
        assertTrue(c.signedInOn.first().readsFromHere)
        assertFalse(c.signedInOn.last().readsFromHere)
    }

    @Test
    fun `B-STATE-152 row 6 freshest wins whichever connection holds it`() {
        val s =
            merge(
                listOf(
                    reading("codex", "inapp", account = "a", age = 1.hours),
                    reading("codex", "pc", account = "a", windows = both(five = 10.0), age = 1.minutes),
                ),
                connections = listOf(IN_APP, PC),
            )
        assertEquals(PC, s.subscriptions.single().readFrom)
        assertEquals(10.0, s.subscriptions.single().fiveHour!!.usedPercent)
    }

    @Test
    fun `B-STATE-152 row 7 two accounts stay two cards and ask for the connection name`() {
        val s =
            merge(
                listOf(
                    reading("codex", "pc", account = "personal"),
                    reading("codex", "laptop", account = "work", windows = both(five = 70.0)),
                ),
                connections = listOf(PC, LAPTOP),
            )
        assertEquals(2, s.subscriptions.size)
        assertTrue(s.subscriptions.all { it.showConnectionName })
        assertEquals(setOf("personal", "work"), s.subscriptions.map { it.accountRef }.toSet())
    }

    @Test
    fun `B-STATE-152 row 7 a lone card does not ask for the connection name`() {
        val s = merge(listOf(reading("codex", "pc", account = "a")), connections = listOf(PC))
        assertFalse(s.subscriptions.single().showConnectionName)
    }

    @Test
    fun `B-STATE-152 row 9 a removed connection takes only its own plans along`() {
        val readings =
            listOf(
                reading("codex", "pc", account = "a"),
                reading("glm", "pc", account = "g"),
                reading("codex", "inapp", account = "a"),
            )
        val s = merge(readings, connections = listOf(IN_APP))
        assertEquals(listOf("codex"), s.subscriptions.map { it.providerId })
        assertEquals(listOf("inapp"), s.subscriptions.single().signedInOn.map { it.connection.id })
    }

    @Test
    fun `B-STATE-152 row 13 another connection with the same account takes over reading`() {
        val s =
            merge(
                listOf(
                    reading("codex", "pc", account = "a", status = UsageReadingStatus.AuthRequired, age = 1.minutes),
                    reading("codex", "inapp", account = "a", age = 1.hours),
                ),
                connections = listOf(IN_APP, PC),
            )
        assertEquals(1, s.subscriptions.size)
        val c = s.subscriptions.single()
        assertEquals(SubscriptionState.Ok, c.state)
        assertEquals(IN_APP, c.readFrom)
        assertEquals(SignInState.Expired, c.signedInOn.first { it.connection.id == "pc" }.state)
    }

    @Test
    fun `B-STATE-152 rows 28 and 29 server names only matter with several connections`() {
        assertFalse(merge(emptyList()).hasSeveralConnections)
        assertTrue(merge(emptyList(), connections = listOf(IN_APP, PC)).hasSeveralConnections)
    }

    @Test
    fun `B-STATE-152 U22 not-signed-in leftovers fold into the signed-in card`() {
        val s =
            merge(
                listOf(reading("codex", "inapp", status = UsageReadingStatus.NotSignedIn)),
                connections = listOf(IN_APP, PC),
                signIns = listOf(UsageSignIn("codex", "pc", displayName = "Codex")),
                authoritative = setOf("pc"),
            )
        val codex = s.subscriptions.filter { it.providerId == "codex" }
        assertEquals(1, codex.size)
        assertEquals(SubscriptionState.CantReadYet, codex.single().state)
        assertFalse(codex.single().showConnectionName)
        val pairs = codex.single().signedInOn.map { it.connection.id to it.state }
        assertTrue(pairs.containsAll(listOf("pc" to SignInState.SignedIn, "inapp" to SignInState.NotSignedIn)))
    }

    @Test
    fun `B-STATE-152 U22 several not-signed-in records are one card`() {
        val s =
            merge(
                listOf(
                    reading("glm", "inapp", status = UsageReadingStatus.NotSignedIn),
                    reading("glm", "pc", status = UsageReadingStatus.NotSignedIn),
                ),
                connections = listOf(IN_APP, PC),
            )
        assertEquals(1, s.subscriptions.size)
        assertEquals(2, s.subscriptions.single().signedInOn.size)
    }

    @Test
    fun `B-STATE-152 U22 two different accounts stay two cards`() {
        val s =
            merge(
                listOf(reading("codex", "inapp", account = "a"), reading("codex", "pc", account = "b")),
                connections = listOf(IN_APP, PC),
            )
        assertEquals(2, s.subscriptions.size)
    }

    // ---- B-STATE-153: signed-out handling

    @Test
    fun `B-STATE-153 row 11 signed out where it was used stays listed with a fix`() {
        val s =
            merge(
                listOf(
                    reading("codex", "inapp", status = UsageReadingStatus.NotSignedIn),
                    reading("glm", "inapp", status = UsageReadingStatus.NotSignedIn),
                ),
            )
        val codex = s.subscriptions.first { it.providerId == "codex" }
        val glm = s.subscriptions.first { it.providerId == "glm" }
        assertEquals(SubscriptionState.NotSignedIn, codex.state)
        assertEquals(UsageFix.SignIn, codex.fix)
        assertEquals(UsageFix.AddKey, glm.fix)
        assertTrue(codex.windows.isEmpty())
    }

    @Test
    fun `B-STATE-153 row 11 a cached provider the server no longer lists is signed out`() {
        val s = merge(listOf(reading("codex", "pc", account = "a")), connections = listOf(PC), authoritative = setOf("pc"))
        assertEquals(SubscriptionState.NotSignedIn, s.subscriptions.single().state)
        assertEquals(PC, s.subscriptions.single().fixConnection)
    }

    @Test
    fun `B-STATE-153 row 11 but only when the server actually answered`() {
        val s = merge(listOf(reading("codex", "pc", account = "a")), connections = listOf(PC))
        assertEquals(SubscriptionState.Ok, s.subscriptions.single().state)
    }

    @Test
    fun `B-STATE-153 row 12 a never-used provider is only in the add list`() {
        val s = merge(listOf(reading("codex", "inapp", account = "a")))
        assertEquals(listOf("codex"), s.subscriptions.map { it.providerId })
        assertEquals(listOf("claude", "glm", "minimax"), s.addable)
    }

    @Test
    fun `B-STATE-153 U8 an unreachable connection is offline never not signed in`() {
        val s =
            mergeUsageSubscriptions(
                now = NOW,
                connections = listOf(IN_APP, PC),
                readings = emptyList(),
                signIns = listOf(UsageSignIn("codex", "pc")),
                offlineConnections = setOf("pc"),
            )
        assertTrue(s.isOffline(PC))
        assertFalse(s.hasAnswered(PC))
        assertFalse(s.subscriptions.single().signedInOn.single().online)
        assertEquals(SignInState.SignedIn, s.subscriptions.single().signedInOn.single().state)
    }

    @Test
    fun `B-STATE-153 U8 only a connection that answered can be not signed in`() {
        val s = merge(emptyList(), connections = listOf(IN_APP, PC), authoritative = setOf("pc"))
        assertTrue(s.hasAnswered(PC))
        assertFalse(s.hasAnswered(IN_APP))
    }

    @Test
    fun `B-STATE-153 U10 a record with no reading carries no read time`() {
        val s = merge(listOf(reading("codex", "inapp", status = UsageReadingStatus.NotSignedIn)))
        assertNull(s.subscriptions.single().fetchedAt)
        assertNull(s.subscriptions.single().checkedAt)
    }

    // ---- B-STATE-154: plan status levels and readings

    @Test
    fun `B-STATE-154 rows 19 to 21 ok near the alert level and exhausted`() {
        val s =
            merge(
                listOf(
                    reading("codex", "inapp", account = "a", windows = both(five = 20.0, week = 79.0)),
                    reading("glm", "inapp", account = "b", windows = both(five = 80.0, week = 10.0)),
                    reading("minimax", "inapp", account = "c", windows = both(five = 100.0, week = 10.0)),
                ),
            )
        fun of(p: String) = s.subscriptions.first { it.providerId == p }.state
        assertEquals(SubscriptionState.Ok, of("codex"))
        assertEquals(SubscriptionState.NearLimit, of("glm"))
        assertEquals(SubscriptionState.Exhausted, of("minimax"))
        assertEquals(listOf("minimax", "glm", "codex"), s.subscriptions.map { it.providerId })
    }

    @Test
    fun `B-STATE-154 row 20 a per-plan override moves the alert level for that plan only`() {
        val s =
            merge(
                listOf(
                    reading("codex", "inapp", account = "a", windows = both(five = 85.0, week = 0.0)),
                    reading("glm", "inapp", account = "b", windows = both(five = 85.0, week = 0.0)),
                ),
                overrides = mapOf(usageSubscriptionKey("codex", "a", "inapp") to UsageAlertOverride(threshold = 90.0)),
            )
        val codex = s.subscriptions.first { it.providerId == "codex" }
        val glm = s.subscriptions.first { it.providerId == "glm" }
        assertEquals(SubscriptionState.Ok, codex.state)
        assertEquals(90.0, codex.alertThreshold)
        assertTrue(codex.hasAlertOverride)
        assertEquals(SubscriptionState.NearLimit, glm.state)
        assertEquals(80.0, glm.alertThreshold)
        assertFalse(glm.hasAlertOverride)
    }

    @Test
    fun `B-STATE-154 row 22 GLM keeps all three windows with 5 hours and the week first`() {
        val s =
            merge(
                listOf(
                    reading(
                        "glm",
                        "inapp",
                        account = "g",
                        windows =
                            listOf(
                                UsageWindow("tools", 5.0, durationSeconds = 2_592_000),
                                UsageWindow("week", 30.0, durationSeconds = 604_800),
                                UsageWindow("5h", 10.0, durationSeconds = 18_000),
                            ),
                    ),
                ),
            )
        val c = s.subscriptions.single()
        assertEquals(listOf(UsageWindowKind.FiveHour, UsageWindowKind.Weekly, UsageWindowKind.Monthly), c.windows.map { it.kind })
        assertEquals(10.0, c.fiveHour!!.usedPercent)
        assertEquals(30.0, c.weekly!!.usedPercent)
    }

    @Test
    fun `B-STATE-154 row 23 a working reader with no limits is noLimits`() {
        val s = merge(listOf(reading("copilot", "inapp", status = UsageReadingStatus.NoLimits)))
        assertEquals(SubscriptionState.NoLimits, s.subscriptions.single().state)
    }

    @Test
    fun `B-STATE-154 row 24 a rate-limited provider keeps the last numbers without an error`() {
        val s = merge(listOf(reading("codex", "inapp", account = "a", age = 20.minutes, problem = UsageProblem.RateLimited)))
        val c = s.subscriptions.single()
        assertEquals(SubscriptionState.Ok, c.state)
        assertTrue(c.windows.isNotEmpty())
        assertEquals(UsageFix.None, c.fix)
    }

    @Test
    fun `B-STATE-154 row 25 a broken reader keeps the numbers and offers Try again`() {
        val s = merge(listOf(reading("codex", "inapp", account = "a", problem = UsageProblem.Broken)))
        val c = s.subscriptions.single()
        assertEquals(SubscriptionState.ReaderBroken, c.state)
        assertEquals(UsageFix.TryAgain, c.fix)
        assertTrue(c.windows.isNotEmpty())
    }

    @Test
    fun `B-STATE-154 row 25 a reader that never worked shows broken without numbers`() {
        val s = merge(listOf(reading("codex", "inapp", status = UsageReadingStatus.NoLimits, problem = UsageProblem.Broken)))
        assertEquals(SubscriptionState.ReaderBroken, s.subscriptions.single().state)
        assertTrue(s.subscriptions.single().windows.isEmpty())
    }

    @Test
    fun `B-STATE-154 row 27 Paseo-reported providers become cards with the same rules`() {
        val s =
            merge(
                listOf(
                    reading("copilot", UsageConnection.PHONE_ID, windows = both(five = 85.0)),
                    reading("kimi", UsageConnection.PHONE_ID),
                ),
                connections = listOf(PHONE),
            )
        assertEquals(setOf("copilot", "kimi"), s.subscriptions.map { it.providerId }.toSet())
        assertEquals(SubscriptionState.NearLimit, s.subscriptions.first { it.providerId == "copilot" }.state)
        assertEquals(usagePlanProviders, s.addable)
    }

    @Test
    fun `B-STATE-154 provider ids from OpenCode Paseo and the collector all map`() {
        assertEquals("codex", canonicalUsageProviderId("openai"))
        assertEquals("codex", canonicalUsageProviderId("codex"))
        assertEquals("glm", canonicalUsageProviderId("zai-coding-plan"))
        assertEquals("minimax", canonicalUsageProviderId("minimax-coding-plan"))
        assertEquals("claude", canonicalUsageProviderId("anthropic"))
        assertNull(canonicalUsageProviderId("openrouter"))
    }

    @Test
    fun `B-STATE-154 readings survive a JSON round trip without account text`() {
        val r = reading("codex", "pc", account = "a".repeat(64), plan = "pro")
        val back = assertNotNull(UsageReading.fromJson(r.toJson(), "pc"))
        assertEquals(r.accountRef, back.accountRef)
        assertEquals(2, back.windows.size)
        assertEquals(r.fetchedAt, back.fetchedAt)
        assertFalse(r.toJson().toString().contains("token"))
        assertNull(UsageReading.fromJson(mapOf("provider" to "Bad Id"), "pc"))
    }

    // ---- Earlier rows of the same table (kept for completeness; they guard the same merge)

    @Test
    fun `B-STATE-152 rows 1 to 5 add list readable card set up and stale offline numbers`() {
        val empty = merge(emptyList())
        assertTrue(empty.subscriptions.isEmpty())
        assertEquals(usagePlanProviders, empty.addable)

        val one = merge(listOf(reading("codex", "inapp", account = "a", plan = "plus"))).subscriptions.single()
        assertEquals(SubscriptionState.Ok, one.state)
        assertEquals(IN_APP, one.readFrom)
        assertEquals("plus", one.planLabel)
        assertEquals(80.0, one.fiveHour!!.leftPercent)
        assertEquals(60.0, one.weekly!!.leftPercent)

        val setUp =
            merge(
                emptyList(),
                connections = listOf(IN_APP, PC),
                signIns = listOf(UsageSignIn("codex", "pc", displayName = "Codex")),
                authoritative = setOf("pc"),
            ).subscriptions.single()
        assertEquals(SubscriptionState.CantReadYet, setUp.state)
        assertEquals(UsageFix.SetUp, setUp.fix)
        assertEquals(PC, setUp.fixConnection)

        val stale =
            merge(
                listOf(
                    reading(
                        "codex",
                        "pc",
                        account = "a",
                        windows = listOf(UsageWindow("primary", 90.0, durationSeconds = 18_000, resetsAt = NOW - 30.minutes)),
                        age = 3.hours,
                        problem = UsageProblem.Offline,
                    ),
                ),
                connections = listOf(PC),
            ).subscriptions.single()
        assertEquals(SubscriptionState.OfflineStale, stale.state)
        assertTrue(stale.resetSinceLastReading)
        assertFalse(stale.signedInOn.single().online)
        assertEquals(PC, stale.readFrom)
    }
}
