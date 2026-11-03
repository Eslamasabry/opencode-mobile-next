package io.github.eslamasabry.oc.engine.plans

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val PASEO = UsageConnection("paseo1", "Studio", UsageConnectionKind.PaseoServer)

/** The rig of usage_subscription_alerts_test.dart: a fake notification sink stands in for Android. */
private class AlertRig(private val scope: CoroutineScope, val prefs: MemoryPrefs = MemoryPrefs()) {
    val shown = mutableListOf<UsageAlertNote>()
    val dismissed = mutableListOf<String>()
    var sinkWorks = true
    var onWifi = true
    var rules = SharedNotifyRules(quotaAlerts = true)
    val clock = FakeClock(utc(2026, 10, 13, 14))
    var readings: List<UsageReading> = emptyList()
    var covered: Set<String> = emptySet()
    lateinit var controller: UsageSubscriptionsController

    init {
        build()
    }

    private fun build() {
        val delivery =
            SubscriptionAlertDelivery(
                prefs = prefs,
                sharedRules = { rules },
                notifier =
                    object : PlanAlertNotifier {
                        override suspend fun show(note: UsageAlertNote): Boolean {
                            if (sinkWorks) shown += note
                            return sinkWorks
                        }

                        override suspend fun dismiss(key: String) {
                            dismissed += key
                        }
                    },
                wifi = { onWifi },
                clock = clock,
            )
        val bridge =
            object : UsageAlertBridge {
                override fun legacyRules(): List<LegacyQuotaRule> = emptyList()

                override suspend fun reconcile(wanted: List<QuotaWatch>, alertsOn: Boolean) {}

                override fun covers(connectionId: String, providerId: String) = "$connectionId|$providerId" in covered
            }
        controller =
            UsageSubscriptionsController(
                prefs = prefs,
                connections = { listOf(PASEO, PC) },
                sources = listOf(UsageSource { UsageSourceResult(readings = readings) }),
                sharedRules = { rules },
                setQuotaAlerts = {},
                clock = clock,
                scope = scope,
                alertBridge = bridge,
                subscriptionAlerts = delivery,
                foregroundIntervalMs = NEVER,
                backgroundIntervalMs = NEVER,
            )
        controller.setRuntime(foreground = false, backgroundAllowed = true)
    }

    /** A new delivery and controller over the same stored records (an app restart). */
    fun restart() {
        controller.dispose()
        build()
    }

    fun reading(
        connection: String,
        provider: String = "claude",
        used: Double = 85.0,
        resetHours: Int = 2,
        age: Long = MIN,
    ) = UsageReading(
        providerId = provider,
        displayName = if (provider == "claude") "Claude" else "Codex",
        connectionId = connection,
        status = UsageReadingStatus.Ok,
        fetchedAt = clock.now - age,
        method = SignInMethod.Plan,
        windows = listOf(UsageWindow("primary", used, durationSeconds = 18000, resetsAt = clock.now + resetHours * HOUR)),
    )

    suspend fun read(list: List<UsageReading>, foreground: Boolean = false) {
        readings = list
        controller.setRuntime(foreground = foreground, backgroundAllowed = true)
        controller.refresh(force = true)
    }
}

class SubscriptionAlertsTest {
    @Test
    fun `B-ANDR-096 a Paseo plan past its level alerts once, with the plan's token`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("paseo1")))
            assertEquals(1, rig.shown.size)
            val note = rig.shown.single()
            assertFalse(note.reset)
            assertEquals("paseo1", note.profileId)
            assertEquals("Claude", note.providerName)
            assertTrue(Regex("^[a-f0-9]{64}$").matches(note.token))
            val key = rig.controller.snapshot.subscriptions.single().key
            assertEquals(SubscriptionAlertDelivery.tokenFor(key), note.token)
            assertTrue(rig.controller.ownsAlertToken("paseo1", note.token))
            assertFalse(rig.controller.ownsAlertToken("pc", note.token))
            assertFalse(rig.controller.ownsAlertToken("paseo1", "f".repeat(64)))
            // The same crossing again, and again: still one notification.
            rig.read(listOf(rig.reading("paseo1", used = 88.0)))
            rig.read(listOf(rig.reading("paseo1", used = 90.0)))
            assertEquals(1, rig.shown.size)
        }

    @Test
    fun `B-ANDR-096 a computer plan alerts the same way, and again after its reset`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("pc", provider = "codex")))
            assertEquals(1, rig.shown.size)
            assertEquals("pc", rig.shown.single().profileId)
            assertEquals("Codex", rig.shown.single().providerName)
            // A new reset time is a new crossing, so it alerts again.
            rig.read(listOf(rig.reading("pc", provider = "codex", used = 20.0)))
            rig.read(listOf(rig.reading("pc", provider = "codex", used = 83.0, resetHours = 5)))
            assertEquals(2, rig.shown.size)
            assertTrue(rig.shown.none { it.reset })
        }

    @Test
    fun `B-ANDR-096 below the level nothing is shown and a stale alert is dismissed`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("paseo1", used = 40.0)))
            assertTrue(rig.shown.isEmpty())
            assertTrue(rig.dismissed.isNotEmpty())
        }

    @Test
    fun `B-ANDR-096 the plan's own level decides, not the default`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("paseo1", used = 60.0)))
            val key = rig.controller.snapshot.subscriptions.single().key
            assertTrue(rig.shown.isEmpty())
            rig.controller.setThresholdFor(key, 50.0)
            rig.read(listOf(rig.reading("paseo1", used = 60.0)))
            assertEquals(1, rig.shown.size)
        }

    @Test
    fun `B-ANDR-097 quiet hours hold it back and it is announced afterwards`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.rules = SharedNotifyRules(quotaAlerts = true, quietStart = 22 * 60, quietEnd = 8 * 60)
            rig.clock.now = utc(2026, 10, 13, 23)
            rig.read(listOf(rig.reading("paseo1")))
            assertTrue(rig.shown.isEmpty())
            rig.clock.now = utc(2026, 10, 14, 9)
            rig.read(listOf(rig.reading("paseo1")))
            assertEquals(1, rig.shown.size)
        }

    @Test
    fun `B-ANDR-097 with the app open, or alerts off, nothing is shown`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("paseo1")), foreground = true)
            assertTrue(rig.shown.isEmpty())
            rig.rules = SharedNotifyRules()
            rig.read(listOf(rig.reading("paseo1")))
            assertTrue(rig.shown.isEmpty())
            rig.rules = SharedNotifyRules(quotaAlerts = true)
            rig.read(listOf(rig.reading("paseo1")))
            assertEquals(1, rig.shown.size)
        }

    @Test
    fun `B-ANDR-097 Wi-Fi only waits for Wi-Fi without losing the crossing`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.rules = SharedNotifyRules(quotaAlerts = true, wifiOnly = true)
            rig.onWifi = false
            rig.read(listOf(rig.reading("paseo1")))
            assertTrue(rig.shown.isEmpty())
            rig.onWifi = true
            rig.read(listOf(rig.reading("paseo1")))
            assertEquals(1, rig.shown.size)
        }

    @Test
    fun `B-ANDR-097 a notification the system refused is tried again, not lost`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.sinkWorks = false
            rig.read(listOf(rig.reading("paseo1")))
            assertTrue(rig.shown.isEmpty())
            rig.sinkWorks = true
            rig.read(listOf(rig.reading("paseo1")))
            assertEquals(1, rig.shown.size)
        }

    @Test
    fun `B-ANDR-097 the crossing stays alerted across an app restart`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("paseo1")))
            assertEquals(1, rig.shown.size)
            rig.restart()
            rig.read(listOf(rig.reading("paseo1")))
            assertEquals(1, rig.shown.size)
        }

    @Test
    fun `B-ANDR-097 numbers older than the limit never alert`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("paseo1", age = 3 * HOUR)))
            assertTrue(rig.shown.isEmpty())
        }

    @Test
    fun `B-ANDR-097 a plan the quota monitor already alerts for is not alerted twice`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.covered = setOf("pc|codex")
            rig.read(listOf(rig.reading("pc", provider = "codex")))
            assertTrue(rig.shown.isEmpty())
        }

    private suspend fun AlertRig.highThenLow(covered: Boolean, switchOn: Boolean): AlertRig {
        if (covered) this.covered = setOf("pc|codex")
        read(listOf(reading("pc", provider = "codex", used = 90.0)))
        val key = controller.snapshot.subscriptions.single().key
        if (switchOn) controller.setNotifyOnReset(key, true)
        shown.clear()
        clock.now += 3 * HOUR
        read(listOf(reading("pc", provider = "codex", used = 2.0, resetHours = 5)))
        return this
    }

    @Test
    fun `B-ANDR-098 the reset message says the plan is back, once, when asked to`() =
        runTest {
            val rig = AlertRig(backgroundScope).highThenLow(covered = false, switchOn = true)
            assertEquals(1, rig.shown.size)
            assertTrue(rig.shown.single().reset)
            assertEquals("Codex", rig.shown.single().providerName)
            assertTrue(rig.shown.single().key.startsWith("quota-reset|"))
            rig.read(listOf(rig.reading("pc", provider = "codex", used = 3.0, resetHours = 5)))
            assertEquals(1, rig.shown.size)
        }

    @Test
    fun `B-ANDR-098 the reset message is not sent when the switch is off`() =
        runTest {
            val rig = AlertRig(backgroundScope).highThenLow(covered = false, switchOn = false)
            assertTrue(rig.shown.isEmpty())
        }

    @Test
    fun `B-ANDR-098 the reset message is sent for a monitored plan without a second level alert`() =
        runTest {
            val rig = AlertRig(backgroundScope).highThenLow(covered = true, switchOn = true)
            assertEquals(1, rig.shown.size)
            assertTrue(rig.shown.single().reset)
        }

    @Test
    fun `B-ANDR-098 the reset message is not sent after a long silence`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("pc", provider = "codex", used = 90.0)))
            val key = rig.controller.snapshot.subscriptions.single().key
            rig.controller.setNotifyOnReset(key, true)
            rig.shown.clear()
            rig.clock.now += 2 * DAY
            rig.read(listOf(rig.reading("pc", provider = "codex", used = 2.0, resetHours = 5)))
            assertTrue(rig.shown.isEmpty())
        }

    @Test
    fun `B-ANDR-098 records of a removed server go with it`() =
        runTest {
            val rig = AlertRig(backgroundScope)
            rig.read(listOf(rig.reading("paseo1")))
            assertNotNull(rig.prefs.getString(SubscriptionAlertDelivery.STORE_KEY))
            rig.controller.removeProfile("paseo1")
            testScheduler.runCurrent()
            val left = rig.prefs.getString(SubscriptionAlertDelivery.STORE_KEY)
            assertTrue(left == null || !left.contains("claude"))
        }
}
