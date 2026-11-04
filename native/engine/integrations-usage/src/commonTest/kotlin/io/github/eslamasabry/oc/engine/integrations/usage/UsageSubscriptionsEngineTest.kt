package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// Ported from Flutter test/usage_subscriptions_controller_test.dart.

private class ScriptedSource(var results: List<UsageSourceResult>) : UsageSource {
    var calls = 0
    var throwNext = false

    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> {
        calls++
        if (throwNext) error("private-source-failure")
        return Outcome.Ok(results[(calls - 1).coerceIn(0, results.size - 1)])
    }
}

private class RecordingBridge : UsageAlertBridge {
    var legacy: List<LegacyQuotaRule> = emptyList()
    var lastWanted: List<QuotaWatch> = emptyList()
    var lastAlertsOn: Boolean? = null

    override fun legacyRules(): List<LegacyQuotaRule> = legacy

    override suspend fun reconcile(wanted: List<QuotaWatch>, alertsOn: Boolean) {
        lastWanted = wanted
        lastAlertsOn = alertsOn
    }
}

private fun r(
    provider: String,
    connection: String,
    account: String? = null,
    five: Double = 20.0,
    week: Double = 40.0,
    age: Duration = 1.minutes,
    status: UsageReadingStatus = UsageReadingStatus.Ok,
) = reading(
    provider,
    connection,
    account = account,
    status = status,
    windows = if (status == UsageReadingStatus.Ok) both(five, week) else emptyList(),
    age = age,
    method = SignInMethod.Unknown,
)

private fun ok(vararg readings: UsageReading) = UsageSourceResult(readings = readings.toList())

class UsageSubscriptionsEngineTest {
    private val kv = FakeKv()
    private var connections = listOf(IN_APP, PC)
    private var quotaAlerts = false
    private val bridge = RecordingBridge()
    private var redraws = 0
    private val widget = UsageWidgetFeed(kv, LANE, isAndroid = true, refreshNative = { redraws++ }, clock = { NOW })

    private fun make(sources: List<UsageSource>, bridged: Boolean = true) =
        UsageSubscriptionsEngine(
            store = kv,
            lane = LANE,
            connections = { connections },
            sources = sources,
            alertsOnRule = { quotaAlerts },
            setQuotaAlerts = { quotaAlerts = it },
            alertBridge = if (bridged) bridge else null,
            widget = widget,
            clock = { NOW },
        )

    // ---- B-STATE-149

    @Test
    fun `B-STATE-149 one refresh gathers every source into a single list`() =
        runTest {
            val a = ScriptedSource(listOf(ok(r("codex", "inapp", account = "a"))))
            val b = ScriptedSource(listOf(ok(r("claude", "pc"), r("codex", "pc", account = "a", age = 30.minutes))))
            val c = make(listOf(a, b))
            assertFalse(c.snapshot.value.loaded)
            c.refresh()
            assertTrue(c.snapshot.value.loaded)
            assertEquals(setOf("codex", "claude"), c.snapshot.value.subscriptions.map { it.providerId }.toSet())
            val codex = c.snapshot.value.subscriptions.first { it.providerId == "codex" }
            assertEquals(2, codex.signedInOn.size)
            assertEquals(IN_APP, codex.readFrom)
        }

    @Test
    fun `B-STATE-149 a source that throws is ignored and the others still show`() =
        runTest {
            val bad = ScriptedSource(listOf(UsageSourceResult())).also { it.throwNext = true }
            val good = ScriptedSource(listOf(ok(r("codex", "inapp", account = "a"))))
            val c = make(listOf(bad, good))
            c.refresh()
            assertEquals(1, c.snapshot.value.subscriptions.size)
        }

    @Test
    fun `B-STATE-149 numbers survive a restart and show before the first refresh`() =
        runTest {
            val first = make(listOf(ScriptedSource(listOf(ok(r("codex", "pc", account = "a"))))))
            first.refresh()
            val stored = assertNotNull(kv.str("oc.usageLast.pc"))
            assertFalse(stored.contains("token"))

            val second = make(listOf(ScriptedSource(listOf(UsageSourceResult()))))
            assertFalse(second.snapshot.value.loaded)
            assertEquals("codex", second.snapshot.value.subscriptions.single().providerId)
            assertEquals(2, second.snapshot.value.subscriptions.single().windows.size)
        }

    @Test
    fun `B-STATE-149 an older reading never replaces a newer one`() =
        runTest {
            val source =
                ScriptedSource(
                    listOf(
                        ok(r("codex", "inapp", account = "a", five = 10.0)),
                        ok(r("codex", "inapp", account = "a", five = 90.0, age = 2.hours)),
                    ),
                )
            val c = make(listOf(source))
            c.refresh()
            c.refresh()
            assertEquals(10.0, c.snapshot.value.subscriptions.single().fiveHour!!.usedPercent)
        }

    // ---- B-STATE-150

    @Test
    fun `B-STATE-150 an unreachable connection keeps its numbers as offlineStale then recovers`() =
        runTest {
            val source =
                ScriptedSource(
                    listOf(
                        ok(r("codex", "pc", account = "a")),
                        UsageSourceResult(unreached = setOf("pc")),
                        ok(r("codex", "pc", account = "a")),
                    ),
                )
            val c = make(listOf(source))
            c.refresh()
            assertEquals(SubscriptionState.Ok, c.snapshot.value.subscriptions.single().state)
            c.refresh()
            var s = c.snapshot.value.subscriptions.single()
            assertEquals(SubscriptionState.OfflineStale, s.state)
            assertTrue(s.windows.isNotEmpty())
            assertFalse(s.signedInOn.single().online)
            assertTrue(c.snapshot.value.isOffline(PC))
            assertFalse(c.snapshot.value.hasAnswered(PC))
            c.refresh()
            assertFalse(c.snapshot.value.isOffline(PC))
            s = c.snapshot.value.subscriptions.single()
            assertEquals(SubscriptionState.Ok, s.state)
        }

    @Test
    fun `B-STATE-150 rate limited keeps the last numbers and a broken reader says so`() =
        runTest {
            val source =
                ScriptedSource(
                    listOf(
                        ok(r("codex", "inapp", account = "a")),
                        UsageSourceResult(failures = listOf(UsageFailure("inapp", "codex", UsageProblem.RateLimited))),
                        UsageSourceResult(failures = listOf(UsageFailure("inapp", "codex", UsageProblem.Broken))),
                    ),
                )
            val c = make(listOf(source))
            c.refresh()
            c.refresh()
            assertEquals(SubscriptionState.Ok, c.snapshot.value.subscriptions.single().state)
            assertTrue(c.snapshot.value.subscriptions.single().windows.isNotEmpty())
            c.refresh()
            assertEquals(SubscriptionState.ReaderBroken, c.snapshot.value.subscriptions.single().state)
            assertEquals(UsageFix.TryAgain, c.snapshot.value.subscriptions.single().fix)
        }

    @Test
    fun `B-STATE-150 sign-ins with no reader show cantReadYet and a vanished sign-in leaves nothing`() =
        runTest {
            val source =
                ScriptedSource(
                    listOf(
                        UsageSourceResult(
                            signIns = listOf(UsageSignIn("codex", "pc", displayName = "Codex")),
                            authoritativeSignIns = setOf("pc"),
                        ),
                        UsageSourceResult(signIns = emptyList(), authoritativeSignIns = setOf("pc")),
                    ),
                )
            val c = make(listOf(source))
            c.refresh()
            assertEquals(SubscriptionState.CantReadYet, c.snapshot.value.subscriptions.single().state)
            assertEquals(PC, c.snapshot.value.subscriptions.single().fixConnection)
            c.refresh()
            assertTrue(c.snapshot.value.subscriptions.isEmpty())
            assertTrue("codex" in c.snapshot.value.addable)
        }

    @Test
    fun `B-STATE-150 a plan that was read and then disappears from the server stays as not signed in`() =
        runTest {
            val source =
                ScriptedSource(
                    listOf(
                        ok(r("codex", "pc", account = "a")),
                        UsageSourceResult(signIns = emptyList(), authoritativeSignIns = setOf("pc")),
                    ),
                )
            val c = make(listOf(source))
            c.refresh()
            c.refresh()
            val s = c.snapshot.value.subscriptions.single()
            assertEquals(SubscriptionState.NotSignedIn, s.state)
            assertEquals(UsageFix.SignIn, s.fix)
        }

    // ---- B-SEC-178

    @Test
    fun `B-SEC-178 a removed profile loses its cache its overrides and its widget rows`() =
        runTest {
            val c = make(listOf(ScriptedSource(listOf(ok(r("codex", "pc", account = "a"), r("codex", "inapp", account = "b"))))))
            c.refresh()
            val pcKey = usageSubscriptionKey("codex", "a", "pc")
            val keepKey = usageSubscriptionKey("codex", "b", "inapp")
            c.setThresholdFor(pcKey, 95.0)
            c.setThresholdFor(keepKey, 90.0)
            connections = listOf(IN_APP)
            c.removeProfile("pc")
            assertNull(kv.str("oc.usageLast.pc"))
            assertEquals("b", c.snapshot.value.subscriptions.single().accountRef)
            val blob = DartJson.decode(kv.str(UsageSubscriptionsEngine.ALERTS_KEY)!!) as Map<*, *>
            assertEquals(listOf(keepKey), (blob["overrides"] as Map<*, *>).keys.toList())
            val feed = DartJson.decode(kv.str(UsageWidgetFeed.SUBSCRIPTIONS_KEY)!!) as Map<*, *>
            assertEquals(keepKey, ((feed["entries"] as List<*>).single() as Map<*, *>)["key"])
        }

    @Test
    fun `B-SEC-178 the profile deletion sweep finds the cached numbers by their key`() =
        runTest {
            val c = make(listOf(ScriptedSource(listOf(ok(r("codex", "pc", account = "a"))))))
            c.refresh()
            assertTrue(UsageSubscriptionsEngine.lastKey("pc") in kv.scopedKeys("pc"))
            assertTrue(kv.scopedKeys("inapp").isEmpty())
            // The app-wide alert levels and widget feed are not any one profile's.
            c.setThresholdFor(usageSubscriptionKey("codex", "a", "pc"), 90.0)
            assertFalse(UsageSubscriptionsEngine.ALERTS_KEY in kv.scopedKeys("pc"))
            assertFalse(UsageWidgetFeed.SUBSCRIPTIONS_KEY in kv.scopedKeys("pc"))
        }

    // ---- B-STATE-151 alerts

    @Test
    fun `B-STATE-151 every readable subscription is watched at 80 with no enrolment step`() =
        runTest {
            val c = make(listOf(ScriptedSource(listOf(ok(r("codex", "inapp", account = "a"), r("glm", "inapp", account = "g"))))))
            c.refresh()
            assertEquals(80.0, c.defaultThreshold)
            assertEquals(setOf(80.0), bridge.lastWanted.map { it.threshold }.toSet())
            assertEquals(setOf("codex", "glm"), bridge.lastWanted.map { it.providerId }.toSet())
            assertEquals(false, bridge.lastAlertsOn)
            c.setAlertsOn(true)
            assertTrue(c.alertsOn)
            assertTrue(quotaAlerts)
            assertEquals(true, bridge.lastAlertsOn)
        }

    @Test
    fun `B-STATE-151 a per-subscription override changes only that watch and persists`() =
        runTest {
            val source = ScriptedSource(listOf(ok(r("codex", "inapp", account = "a"), r("glm", "inapp", account = "g"))))
            val c = make(listOf(source))
            c.refresh()
            val key = usageSubscriptionKey("codex", "a", "inapp")
            assertTrue(c.setThresholdFor(key, 90.0))
            assertEquals(90.0, bridge.lastWanted.first { it.providerId == "codex" }.threshold)
            assertEquals(80.0, bridge.lastWanted.first { it.providerId == "glm" }.threshold)
            assertEquals("codex", c.alertOverrides.single().providerId)

            val again = make(listOf(source))
            assertEquals(90.0, again.subscription(key)!!.alertThreshold)
            assertTrue(c.setThresholdFor(key, null))
            assertTrue(c.alertOverrides.isEmpty())
        }

    @Test
    fun `B-STATE-151 bad levels are refused and change nothing`() =
        runTest {
            val c = make(listOf(ScriptedSource(listOf(UsageSourceResult()))))
            assertFalse(c.setDefaultThreshold(0.0))
            assertFalse(c.setDefaultThreshold(101.0))
            assertFalse(c.setDefaultThreshold(Double.NaN))
            assertFalse(c.setThresholdFor("codex|a", 150.0))
            assertEquals(80.0, c.defaultThreshold)
        }

    @Test
    fun `B-STATE-151 the default level moves every subscription without an override`() =
        runTest {
            val c = make(listOf(ScriptedSource(listOf(ok(r("codex", "inapp", account = "a", five = 70.0))))))
            c.refresh()
            assertEquals(SubscriptionState.Ok, c.snapshot.value.subscriptions.single().state)
            c.setDefaultThreshold(60.0)
            assertEquals(SubscriptionState.NearLimit, c.snapshot.value.subscriptions.single().state)
            assertEquals(60.0, bridge.lastWanted.single().threshold)
        }

    @Test
    fun `B-STATE-151 tell me when it resets is off by default and per subscription`() =
        runTest {
            val c = make(listOf(ScriptedSource(listOf(ok(r("codex", "inapp", account = "a"))))))
            c.refresh()
            val key = usageSubscriptionKey("codex", "a", "inapp")
            assertFalse(c.notifyOnResetDefault)
            assertFalse(c.subscription(key)!!.notifyOnReset)
            c.setNotifyOnReset(key, true)
            assertTrue(c.subscription(key)!!.notifyOnReset)
            assertFalse(c.subscription(key)!!.hasAlertOverride)
        }

    @Test
    fun `B-STATE-151 migration keeps a chosen threshold from the old per-source rules once`() =
        runTest {
            bridge.legacy =
                listOf(
                    LegacyQuotaRule("inapp", "codex", "a", 90.0),
                    LegacyQuotaRule("inapp", "glm", "g", 80.0),
                    LegacyQuotaRule("pc", "codex", "z", 100.0),
                )
            val source = ScriptedSource(listOf(ok(r("codex", "inapp", account = "a"), r("glm", "inapp", account = "g"), r("codex", "pc", account = "z"))))
            val c = make(listOf(source))
            c.refresh()
            assertEquals(90.0, c.subscription(usageSubscriptionKey("codex", "a", "inapp"))!!.alertThreshold)
            assertFalse(c.subscription(usageSubscriptionKey("glm", "g", "inapp"))!!.hasAlertOverride)
            assertEquals(100.0, c.subscription(usageSubscriptionKey("codex", "z", "pc"))!!.alertThreshold)
            // A rule that appears later is not migrated again.
            bridge.legacy = listOf(LegacyQuotaRule("inapp", "glm", "g", 50.0))
            c.refresh()
            assertFalse(c.subscription(usageSubscriptionKey("glm", "g", "inapp"))!!.hasAlertOverride)
            val restarted = make(listOf(source))
            restarted.refresh()
            assertFalse(restarted.subscription(usageSubscriptionKey("glm", "g", "inapp"))!!.hasAlertOverride)
        }
}
