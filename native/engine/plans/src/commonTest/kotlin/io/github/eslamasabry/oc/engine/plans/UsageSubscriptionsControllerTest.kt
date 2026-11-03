package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsageSubscriptionsControllerTest {
    private fun ctlReading(
        provider: String,
        connection: String,
        account: String? = null,
        five: Double = 20.0,
        week: Double = 40.0,
        status: UsageReadingStatus = UsageReadingStatus.Ok,
    ) = UsageReading(
        providerId = provider,
        displayName = provider,
        connectionId = connection,
        status = status,
        fetchedAt = NOW - MIN,
        accountRef = account,
        windows =
            if (status == UsageReadingStatus.Ok) {
                listOf(
                    UsageWindow("primary", five, durationSeconds = 18000, resetsAt = NOW + 2 * HOUR),
                    UsageWindow("secondary", week, durationSeconds = 604800, resetsAt = NOW + 2 * HOUR),
                )
            } else {
                emptyList()
            },
    )

    private class SeqSource(val results: List<UsageSourceResult>) : UsageSource {
        var calls = 0

        override suspend fun read(context: UsageReadContext): UsageSourceResult {
            calls++
            return results[(calls - 1).coerceIn(0, results.size - 1)]
        }
    }

    private fun make(
        scope: CoroutineScope,
        prefs: MemoryPrefs,
        vararg sources: UsageSource,
    ): UsageSubscriptionsController {
        val clock = FakeClock(NOW)
        return UsageSubscriptionsController(
            prefs = prefs,
            connections = { listOf(IN_APP, PC) },
            sources = sources.toList(),
            sharedRules = { SharedNotifyRules() },
            setQuotaAlerts = {},
            clock = clock,
            scope = scope,
            widget = UsageWidgetSnapshot(prefs, FakeWidgetHost(), clock),
            foregroundIntervalMs = NEVER,
            backgroundIntervalMs = NEVER,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun entries(prefs: MemoryPrefs): List<Map<String, Any?>> =
        ((DartJson.decode(prefs.getString(UsageWidgetSnapshot.SUBSCRIPTIONS_KEY)!!) as Map<String, Any?>)["entries"]
            as List<Map<String, Any?>>)

    private fun ok(vararg readings: UsageReading) = UsageSourceResult(readings = readings.toList())

    // ------------------------------------------------------------------------------------------- widget feed

    @Test
    fun `B-ANDR-099 one entry per subscription in page order, each with both windows`() =
        runTest {
            val prefs = MemoryPrefs()
            val c =
                make(
                    backgroundScope,
                    prefs,
                    SeqSource(
                        listOf(
                            ok(
                                ctlReading("codex", "inapp", account = "a", five = 82.0, week = 41.0),
                                ctlReading("claude", "pc", five = 22.0, week = 35.0),
                            ),
                        ),
                    ),
                )
            c.refresh()
            val list = entries(prefs)
            assertEquals(listOf("codex", "claude"), list.map { it["provider"] })
            assertEquals("near", list.first()["state"])
            @Suppress("UNCHECKED_CAST")
            val windows = list.first()["windows"] as List<Map<String, Any?>>
            assertEquals(listOf<Any?>(18000L, 604800L), windows.map { it["seconds"] })
            assertEquals(listOf<Any?>(18L, 59L), windows.map { it["left"] })
            assertFalse(prefs.getString(UsageWidgetSnapshot.SUBSCRIPTIONS_KEY)!!.contains("\"a\""))
        }

    @Test
    fun `B-ANDR-099 rows that need setup show by default and can be hidden`() =
        runTest {
            val prefs = MemoryPrefs()
            val source =
                SeqSource(
                    listOf(
                        UsageSourceResult(
                            readings = listOf(ctlReading("codex", "inapp", account = "a")),
                            signIns = listOf(UsageSignIn("glm", "pc", displayName = "GLM")),
                            authoritativeSignIns = setOf("pc"),
                        ),
                    ),
                )
            val c = make(backgroundScope, prefs, source)
            c.refresh()
            val setup = entries(prefs).single { it["provider"] == "glm" }
            assertEquals("setup", setup["state"])
            assertEquals("set_up", setup["action"])
            assertEquals("pc", setup["profileId"])
            assertEquals(emptyList<Any?>(), setup["windows"])

            c.setWidgetSettings(UsageWidgetSettings(showSetupRows = false))
            assertEquals(listOf<Any?>("codex"), entries(prefs).map { it["provider"] })
            // The choice survives a restart.
            assertFalse(make(backgroundScope, prefs, source).widgetSettings.showSetupRows)
        }

    @Test
    fun `B-ANDR-099 hidden subscriptions and the window choice apply`() =
        runTest {
            val prefs = MemoryPrefs()
            val c =
                make(
                    backgroundScope,
                    prefs,
                    SeqSource(listOf(ok(ctlReading("codex", "inapp", account = "a"), ctlReading("claude", "pc")))),
                )
            c.refresh()
            c.setWidgetSettings(
                UsageWidgetSettings(
                    hiddenKeys = setOf(usageSubscriptionKey("claude", null, "pc")),
                    windows = UsageWidgetWindows.Weekly,
                ),
            )
            val list = entries(prefs)
            assertEquals(listOf<Any?>("codex"), list.map { it["provider"] })
            @Suppress("UNCHECKED_CAST")
            assertEquals(604800L, (list.single()["windows"] as List<Map<String, Any?>>).single()["seconds"])
        }

    @Test
    fun `B-ANDR-099 sign-in rows carry the key or sign-in action and the fixing connection`() =
        runTest {
            val prefs = MemoryPrefs()
            val c =
                make(
                    backgroundScope,
                    prefs,
                    SeqSource(
                        listOf(
                            ok(
                                ctlReading("glm", "pc", status = UsageReadingStatus.NotSignedIn),
                                ctlReading("claude", "pc", status = UsageReadingStatus.NotSignedIn),
                            ),
                        ),
                    ),
                )
            c.refresh()
            val byProvider = entries(prefs).associateBy { it["provider"] }
            assertEquals("add_key", byProvider.getValue("glm")["action"])
            assertEquals("sign_in", byProvider.getValue("claude")["action"])
            assertEquals("signin", byProvider.getValue("claude")["state"])
            assertEquals("pc", byProvider.getValue("claude")["profileId"])
        }

    @Test
    fun `B-ANDR-099 an offline plan is labelled stale in the feed`() =
        runTest {
            val prefs = MemoryPrefs()
            val c =
                make(
                    backgroundScope,
                    prefs,
                    SeqSource(
                        listOf(
                            ok(ctlReading("codex", "pc", account = "a")),
                            UsageSourceResult(unreached = setOf("pc")),
                        ),
                    ),
                )
            c.refresh()
            c.refresh()
            assertEquals("stale", entries(prefs).single()["state"])
        }

    // ------------------------------------------------------------------------- collector source and bridge

    private class CollectorRig(scope: CoroutineScope) {
        val prefs = MemoryPrefs()
        val clock = FakeClock(NOW)
        val profiles = listOf(profile("inapp"), profile("pc"))
        var reply: Map<String, Outcome<ProviderQuotaSnapshot>> = emptyMap()
        val reads = mutableListOf<String>()
        var rules = SharedNotifyRules()
        val monitor =
            ProviderQuotaMonitor(
                prefs = prefs,
                profiles = { profiles },
                isReadable = { id -> profiles.any { it.id == id } },
                createGateway = { _, _ -> error("the monitor must not read") },
                wifi = { true },
                sink =
                    object : QuotaAlertSink {
                        override suspend fun alert(profileId: String, key: String, token: String) = true.ok()

                        override suspend fun dismiss(key: String) {}
                    },
                sharedRules = { null },
                clock = clock,
                secureRandom = { ByteArray(it) { 7 } },
                scope = scope,
                foregroundIntervalMs = NEVER,
                backgroundIntervalMs = NEVER,
            )

        fun snap(provider: QuotaProvider = QuotaProvider.Codex, account: String = "a", used: Double = 25.0) =
            quotaSample(NOW, account = account, percent = used, provider = provider)

        fun source(inApp: Set<String> = setOf("inapp")) =
            QuotaCollectorSource(
                profiles = { profiles },
                isReadable = { id -> profiles.any { it.id == id } },
                isInApp = { it.id == "inapp" },
                inAppEnabled = { it in inApp },
                createGateway = { p, provider ->
                    reads += "${p.id}:${provider.id}"
                    FakeQuotaGateway {
                        reply["${p.id}:${provider.id}"] ?: Outcome.Failed(Failure.Network())
                    }
                },
                monitor = monitor,
                sharedRules = { rules },
            )

        fun context(lastReading: ((String, String) -> UsageReading?)? = null) =
            UsageReadContext(connections = listOf(IN_APP, PC), now = NOW, force = true, lastReading = lastReading)
    }

    private fun TestScope.collector() = CollectorRig(backgroundScope)

    @Test
    fun `B-AGENT-314 In-app Ubuntu is read for every provider, a remote one only when consented`() =
        runTest {
            val rig = collector()
            rig.reply =
                mapOf(
                    "inapp:codex" to rig.snap().ok(),
                    "inapp:glm" to rig.snap(provider = QuotaProvider.Glm, account = "c").ok(),
                )
            val result = rig.source().read(rig.context())
            assertEquals(listOf("inapp:codex", "inapp:minimax", "inapp:glm"), rig.reads)
            assertTrue(rig.reads.none { it.startsWith("pc") })
            val codex = result.readings.first { it.providerId == "codex" }
            assertEquals(UsageReadingStatus.Ok, codex.status)
            assertEquals("a".repeat(64), codex.accountRef)
            assertEquals("plus", codex.plan)
            assertEquals(25.0, codex.windows.single().usedPercent)
            // MiniMax answered "unavailable" on an In-app reader: broken, not offline.
            assertEquals(listOf("minimax:Broken"), result.failures.map { "${it.providerId}:${it.problem}" })
            assertTrue(result.unreached.isEmpty())
        }

    @Test
    fun `B-AGENT-314 a remote collector that cannot be reached marks the connection offline`() =
        runTest {
            val rig = collector()
            assertTrue(rig.monitor.enroll("pc", rig.snap(), notifications = false, threshold = 80.0))
            val result = rig.source().read(rig.context())
            assertTrue("pc:codex" in rig.reads)
            assertEquals(setOf("pc"), result.unreached)
        }

    @Test
    fun `B-AGENT-314 an expired collector sign-in is authRequired, signed out is kept only if used before`() =
        runTest {
            val rig = collector()
            rig.reply = mapOf("inapp:codex" to Outcome.Failed(Failure.SignIn(SignInWhy.TokenRejected)))
            var result = rig.source().read(rig.context())
            assertEquals(UsageReadingStatus.AuthRequired, result.readings.single().status)
            rig.reply = emptyMap()
            result =
                rig.source().read(
                    rig.context { _, p -> if (p == "glm") reading("glm", "inapp", account = "g") else null },
                )
            assertTrue(result.readings.isEmpty())
        }

    @Test
    fun `B-AGENT-314 rate limited by the provider is a problem, not an error state`() =
        runTest {
            val rig = collector()
            val limited =
                rig.snap().copy(
                    status = ProviderQuotaStatus.RateLimited,
                    windows = emptyList(),
                    ordinaryUsageAllowed = null,
                    account = ProviderQuotaAccount(ref = null, status = QuotaAccountStatus.Unverified),
                )
            rig.reply = mapOf("inapp:codex" to limited.ok())
            val result = rig.source().read(rig.context())
            assertTrue(result.failures.any { it.providerId == "codex" && it.problem == UsageProblem.RateLimited })
        }

    @Test
    fun `B-AGENT-314 alerts enrol In-app readings at the effective level without a step`() =
        runTest {
            val rig = collector()
            rig.reply = mapOf("inapp:codex" to rig.snap(account = "a").ok())
            val source = rig.source()
            source.read(rig.context())
            rig.rules = rig.rules.copy(quotaAlerts = true)
            source.reconcile(listOf(QuotaWatch("inapp", "codex", 80.0)), alertsOn = true)
            val enrolled = rig.monitor.rulesFor("inapp", QuotaProvider.Codex)!!
            assertEquals(80.0, enrolled.threshold)
            assertTrue(enrolled.notifications)
            assertEquals("a".repeat(64), enrolled.account)

            // An override changes the monitor's per-source level.
            source.reconcile(listOf(QuotaWatch("inapp", "codex", 92.0)), alertsOn = true)
            assertEquals(92.0, rig.monitor.rulesFor("inapp", QuotaProvider.Codex)!!.threshold)
            assertEquals(92.0, source.legacyRules().single().threshold)
        }

    @Test
    fun `B-AGENT-314 alerts off enrol nothing and remote collectors are never auto-enrolled`() =
        runTest {
            val rig = collector()
            rig.reply = mapOf("inapp:codex" to rig.snap().ok(), "pc:codex" to rig.snap(account = "b").ok())
            val source = rig.source()
            source.read(rig.context())
            source.reconcile(listOf(QuotaWatch("inapp", "codex", 80.0)), alertsOn = false)
            assertNull(rig.monitor.rulesFor("inapp", QuotaProvider.Codex))
            source.reconcile(listOf(QuotaWatch("pc", "codex", 80.0)), alertsOn = true)
            assertNull(rig.monitor.rulesFor("pc", QuotaProvider.Codex))
        }

    // --------------------------------------------------------------------------------------- the tap target

    @Test
    fun `B-ANDR-095 the tap target round trips and rejects anything malformed`() {
        val target = UsageSignInTarget("1790000000000000", "glm", UsageWidgetSnapshot.actionFor("glm"))
        assertEquals(target, UsageSignInTarget.fromWire(target.wire))
        for (bad in listOf(
            "usage_target:sign_in:codex",
            "usage_target:launch:codex:p1",
            "usage_target:sign_in:Codex:p1",
            "usage_target:sign_in:codex:p 1",
            "usage_target:sign_in:codex:p1:extra",
            "usage",
            null,
        )) {
            assertNull(UsageSignInTarget.fromWire(bad), "$bad")
        }
        val setUp = UsageSignInTarget.fromWire("usage_target:set_up:codex:pc1")
        assertEquals("set_up", setUp?.action)
        assertEquals("pc1", setUp?.profileId)
        assertEquals(setUp, UsageSignInTarget.fromWire(setUp!!.wire))
    }

    @Test
    fun `B-ANDR-095 the channel hands the target over once`() {
        val inbox = UsageTargetInbox()
        val target = UsageSignInTarget("p1", "codex", UsageWidgetSnapshot.actionFor("codex"))
        repeat(2) { assertTrue(inbox.accept(target.wire)) }
        assertFalse(inbox.accept("usage_target:bad"))
        assertEquals(2L, inbox.arrivals.value)
        assertEquals(target, inbox.take())
        assertNull(inbox.take())
    }
}
