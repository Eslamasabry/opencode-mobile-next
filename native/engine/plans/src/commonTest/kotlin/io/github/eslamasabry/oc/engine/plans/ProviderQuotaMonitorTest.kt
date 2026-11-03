package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderQuotaMonitorTest {
    private val start = utc(2026, 9, 7, 12)
    private val codex = QuotaProvider.Codex

    private fun TestScope.rig(): Pair<MonitorRig, ProviderQuotaMonitor> {
        val rig = MonitorRig(start)
        return rig to rig.make(backgroundScope)
    }

    private fun MonitorRig.stored(): String? = prefs.getString(ProviderQuotaMonitor.key("profile"))

    @Test
    fun `B-ANDR-065 no reads before independent enrollment and future evidence is refused`() =
        runTest {
            val (rig, m) = rig()
            m.start()
            m.refresh()
            assertTrue(rig.reads.isEmpty())
            assertFalse(m.enroll("profile", quotaSample(start, fetched = start + 1), notifications = true))
            assertTrue(m.enroll("profile", rig.response, notifications = false))
            m.refresh()
            assertEquals(listOf("profile"), rig.reads)
            assertTrue(rig.alerts.isEmpty())
            assertFalse(rig.stored()!!.contains("usedPercent"))
        }

    @Test
    fun `B-ANDR-065 background polling needs active runtime and unknown Wi-Fi fails closed`() =
        runTest {
            val (rig, m) = rig()
            m.enroll("profile", rig.response, notifications = true, wifiOnly = true)
            m.setRuntime(foreground = false, backgroundAllowed = false)
            m.refresh()
            assertTrue(rig.reads.isEmpty())
            rig.wifi = null
            m.setRuntime(foreground = false, backgroundAllowed = true)
            m.refresh()
            assertTrue(rig.reads.isEmpty())
            assertEquals(QuotaMonitorStatus.WifiRequired, m.observationFor("profile", codex).status)
            rig.wifiThrows = true
            m.refresh()
            assertTrue(rig.reads.isEmpty())
        }

    @Test
    fun `B-ANDR-065 once migrated, the shared rules decide Wi-Fi, quiet hours and alerts`() =
        runTest {
            val (rig, m) = rig()
            // The record says: alerts on, any network, no quiet hours. Until migration, the record answers.
            m.enroll("profile", rig.response, notifications = true)
            assertNull(rig.shared)

            // Shared Wi-Fi only pauses the read although the record allows it.
            rig.shared = SharedNotifyRules(wifiOnly = true, quotaAlerts = true)
            rig.wifi = false
            m.setRuntime(foreground = false, backgroundAllowed = true)
            m.refresh()
            assertTrue(rig.reads.isEmpty())
            assertEquals(QuotaMonitorStatus.WifiRequired, m.observationFor("profile", codex).status)

            // Shared quiet hours (all day) silence the alert the record allows.
            rig.shared = SharedNotifyRules(quietStart = 0, quietEnd = 0, quotaAlerts = true)
            m.refresh()
            assertTrue(rig.reads.isNotEmpty())
            assertTrue(rig.alerts.isEmpty())

            // Shared alerts off wins over the record's "on".
            rig.shared = SharedNotifyRules()
            m.refresh()
            assertTrue(rig.alerts.isEmpty())

            rig.shared = SharedNotifyRules(quotaAlerts = true)
            m.refresh()
            assertEquals(1, rig.alerts.size)
        }

    @Test
    fun `B-ANDR-065 native opt-in and quiet hours are separate from fresh reads`() =
        runTest {
            val (rig, m) = rig()
            m.enroll("profile", rig.response, notifications = true, quietStart = 0, quietEnd = 0)
            m.setRuntime(foreground = false, backgroundAllowed = true)
            m.refresh()
            assertTrue(rig.reads.isNotEmpty())
            assertTrue(rig.alerts.isEmpty())
            m.setPolicy("profile", codex, notifications = false, wifiOnly = false)
            m.refresh()
            assertTrue(rig.alerts.isEmpty())
            m.setPolicy("profile", codex, notifications = true, wifiOnly = false)
            m.refresh()
            assertEquals(1, rig.alerts.size)
            assertEquals(setOf("profileID", "key", "token"), rig.alerts.single().keys)
            assertFalse(rig.alerts.single().toString().contains("private"))
        }

    @Test
    fun `B-ANDR-066 unknown resets dedupe across restart and account rotation cannot publish or route`() =
        runTest {
            val (rig, m) = rig()
            rig.response = quotaSample(start, noReset = true)
            m.enroll("profile", rig.response, notifications = true)
            m.setRuntime(foreground = false, backgroundAllowed = true)
            m.refresh()
            assertEquals(1, rig.alerts.size)
            val token = rig.alerts.single().getValue("token")
            val restored = rig.make(backgroundScope)
            restored.setRuntime(foreground = false, backgroundAllowed = true)
            restored.refresh()
            assertEquals(1, rig.alerts.size)
            rig.response = quotaSample(start, account = "b", noReset = true)
            restored.refresh()
            assertEquals(1, rig.alerts.size)
            assertEquals(QuotaMonitorStatus.SourceChanged, restored.observationFor("profile", codex).status)
            assertNull(restored.resolveRoute("profile", token))
        }

    @Test
    fun `B-ANDR-066 password and re-entry changes retire old observations and preserve markers`() =
        runTest {
            val (rig, m) = rig()
            rig.response = quotaSample(start, noReset = true)
            m.enroll("profile", rig.response, notifications = true)
            m.setRuntime(foreground = false, backgroundAllowed = true)
            m.refresh()
            assertEquals(1, rig.alerts.size)
            val token = rig.alerts.single().getValue("token")
            val rules = m.rulesFor("profile", codex)!!
            assertTrue(rules.alerted.isNotEmpty())
            val persisted = rig.stored()!!
            assertTrue(persisted.contains(rules.source))
            assertFalse(persisted.contains("private-fixture-password"))

            rig.rotate("rotated-password")
            assertEquals(QuotaMonitorStatus.SourceChanged, m.observationFor("profile", codex).status)
            assertNull(m.routeForToken("profile", token))
            m.refresh()
            assertEquals(1, rig.alerts.size)
            assertTrue(m.rulesFor("profile", codex)!!.alerted.isNotEmpty())
            assertEquals(rules.source, m.rulesFor("profile", codex)!!.source)
            assertEquals(persisted, rig.stored())
            assertFalse(persisted.contains("rotated-password"))

            rig.rotate("rotated-password+reentry-required")
            assertEquals(QuotaMonitorStatus.SourceChanged, m.observationFor("profile", codex).status)
            assertNull(m.routeForToken("profile", token))
        }

    @Test
    fun `B-ANDR-066 three-source cycles rotate fairly`() =
        runTest {
            val (rig, m) = rig()
            repeat(4) { rig.add("extra$it") }
            for (p in rig.profiles.toList()) m.enroll(p.id, rig.response, notifications = false)
            m.refresh()
            assertEquals(3, rig.reads.size)
            m.refresh()
            assertEquals(6, rig.reads.size)
            assertEquals(5, rig.reads.toSet().size)
        }

    @Test
    fun `B-ANDR-066 alert sink failures roll back the claim for a later truthful retry`() =
        runTest {
            val (rig, m) = rig()
            rig.response = quotaSample(start, noReset = true)
            m.enroll("profile", rig.response, notifications = true)
            m.setRuntime(foreground = false, backgroundAllowed = true)
            rig.alertFails = true
            m.refresh()
            assertEquals(QuotaMonitorStatus.Unavailable, m.observationFor("profile", codex).status)
            assertTrue(m.rulesFor("profile", codex)!!.alerted.isEmpty())
            assertTrue(rig.alerts.isEmpty())

            rig.alertFails = false
            m.refresh()
            assertEquals(1, rig.alerts.size)
            assertTrue(m.rulesFor("profile", codex)!!.alerted.isNotEmpty())
        }

    @Test
    fun `B-ANDR-066 rotating credentials retires providers independently through disable and reenroll`() =
        runTest {
            val (rig, m) = rig()
            m.enroll("profile", rig.response, notifications = true)
            m.enroll("profile", quotaSample(start, provider = QuotaProvider.MiniMax), notifications = true)
            val codexToken = m.rulesFor("profile", codex)!!.token
            val minimaxToken = m.rulesFor("profile", QuotaProvider.MiniMax)!!.token

            rig.rotate("rotated-password")
            assertEquals(QuotaMonitorStatus.SourceChanged, m.observationFor("profile", codex).status)
            assertEquals(QuotaMonitorStatus.SourceChanged, m.observationFor("profile", QuotaProvider.MiniMax).status)

            assertTrue(m.disable("profile", codex))
            assertEquals(QuotaMonitorStatus.Disabled, m.observationFor("profile", codex).status)
            assertNull(m.routeForToken("profile", codexToken))
            assertEquals(QuotaMonitorStatus.SourceChanged, m.observationFor("profile", QuotaProvider.MiniMax).status)
            assertNull(m.routeForToken("profile", minimaxToken))

            rig.response = quotaSample(start, noReset = true)
            assertTrue(m.enroll("profile", rig.response, notifications = true))
            val reauthorized = m.rulesFor("profile", codex)!!.token
            assertNull(m.routeForToken("profile", codexToken))
            assertNotNull(m.routeForToken("profile", reauthorized))
            assertEquals(QuotaMonitorStatus.SourceChanged, m.observationFor("profile", QuotaProvider.MiniMax).status)
            assertNull(m.routeForToken("profile", minimaxToken))
        }

    @Test
    fun `B-ANDR-066 recovery dismisses old alerts and changing threshold rearms its own marker`() =
        runTest {
            val (rig, m) = rig()
            rig.response = quotaSample(start, percent = 60.0, noReset = true)
            m.enroll("profile", rig.response, notifications = true, threshold = 50.0)
            m.setRuntime(foreground = false, backgroundAllowed = true)
            m.refresh()
            assertEquals(1, rig.alerts.size)
            rig.dismissed.clear()
            rig.response = quotaSample(start, missingFirst = true)
            m.refresh()
            assertTrue(rig.dismissed.isEmpty(), "a missing window is not recovery")
            rig.response = quotaSample(start, percent = 20.0, noReset = true)
            m.refresh()
            assertTrue("quota:profile:codex" in rig.dismissed)
            assertEquals(1, rig.alerts.size)
            m.setPolicy("profile", codex, notifications = true, wifiOnly = false, threshold = 90.0)
            rig.response = quotaSample(start, percent = 95.0, noReset = true)
            m.refresh()
            assertEquals(2, rig.alerts.size)
            m.refresh()
            assertEquals(2, rig.alerts.size)
        }

    @Test
    fun `B-ANDR-067 aborted deletion retains rotated credential retirement until explicit reenrollment`() =
        runTest {
            val (rig, m) = rig()
            assertTrue(m.enroll("profile", rig.response, notifications = false))
            m.refresh()
            val originalToken = m.rulesFor("profile", codex)!!.token
            rig.rotate("rotated-password")
            assertEquals(QuotaMonitorStatus.SourceChanged, m.observationFor("profile", codex).status)
            m.refresh()
            rig.reads.clear()

            m.removeProfile("profile", retainIdentity = true)
            m.drain("profile")
            m.cancelDeletion("profile")
            m.refresh()
            assertTrue(rig.reads.isEmpty())
            assertEquals(QuotaMonitorStatus.SourceChanged, m.observationFor("profile", codex).status)
            assertNull(m.routeForToken("profile", originalToken))
            // Editing notification settings does not renew collection consent.
            assertFalse(m.setPolicy("profile", codex, notifications = true, wifiOnly = false))

            assertTrue(m.enroll("profile", rig.response, notifications = true))
            val renewed = m.rulesFor("profile", codex)!!.token
            assertNotEquals(originalToken, renewed)
            m.refresh()
            assertTrue(rig.reads.isNotEmpty())
            assertNull(m.routeForToken("profile", originalToken))
            assertNotNull(m.routeForToken("profile", renewed))
        }

    @Test
    fun `B-ANDR-067 background cancellation and deletion reject late provider completions`() =
        runTest {
            val (rig, m) = rig()
            m.enroll("profile", rig.response, notifications = true)
            val pending = CompletableDeferred<Outcome<ProviderQuotaSnapshot>>()
            rig.pending = pending
            val read = m.startRefresh()!!
            testScheduler.runCurrent()
            m.setRuntime(foreground = false, backgroundAllowed = false)
            rig.deleted += "profile"
            m.removeProfile("profile")
            rig.prefs.values.remove(ProviderQuotaMonitor.key("profile"))
            pending.complete(rig.response.ok())
            read.await()
            m.drain("profile")
            assertTrue(rig.gateways.single().closed > 0)
            assertTrue(rig.alerts.isEmpty())
            assertNull(rig.stored())
            assertNull(m.routeForToken("profile", REF_A))
        }

    @Test
    fun `B-ANDR-067 failed disable pauses reads until cleanup can be retried`() =
        runTest {
            val (rig, m) = rig()
            m.enroll("profile", rig.response, notifications = true)
            rig.prefs.refuse = true
            assertFalse(m.disable("profile", codex))
            m.refresh()
            assertTrue(rig.reads.isEmpty())
            rig.prefs.refuse = false
            assertTrue(m.disable("profile", codex))
            assertTrue(m.sources.isEmpty())
        }

    @Test
    fun `B-ANDR-067 valid historical route survives unavailable refresh but disable rejects pending route`() =
        runTest {
            val (rig, m) = rig()
            m.enroll("profile", rig.response, notifications = true)
            val token = m.rulesFor("profile", codex)!!.token
            val first = CompletableDeferred<Outcome<ProviderQuotaSnapshot>>()
            rig.pending = first
            val route = async { m.resolveRoute("profile", token) }
            testScheduler.runCurrent()
            assertEquals(QuotaMonitorStatus.Checking, m.observationFor("profile", codex).status)
            first.complete(Outcome.Failed(Failure.Network()))
            assertEquals("profile", route.await()?.profileId)
            assertEquals(QuotaMonitorStatus.Unavailable, m.observationFor("profile", codex).status)
            assertNull(m.observationFor("profile", codex).snapshot)

            val second = CompletableDeferred<Outcome<ProviderQuotaSnapshot>>()
            rig.pending = second
            val revoked = async { m.resolveRoute("profile", token) }
            testScheduler.runCurrent()
            m.disable("profile", codex)
            second.complete(rig.response.ok())
            assertNull(revoked.await())
        }

    @Test
    fun `B-ANDR-067 quota native routes require opaque scope and cannot carry quick actions`() {
        val token = "a".repeat(64)
        val open =
            QuotaAlertRoutes.openFromPlatform(
                mapOf("kind" to "quota", "sessionID" to "quota", "profileID" to "profile", "monitorToken" to token),
            )
        assertEquals(QuotaAlertOpen(reset = false, profileId = "profile", monitorToken = token), open)
        assertNull(
            QuotaAlertRoutes.openFromPlatform(
                mapOf("kind" to "quota", "sessionID" to "session", "profileID" to "profile", "monitorToken" to token),
            ),
        )
        assertNull(
            QuotaAlertRoutes.openFromPlatform(
                mapOf("kind" to "quota", "sessionID" to "quota", "profileID" to " ", "monitorToken" to token),
            ),
        )
        assertNull(
            QuotaAlertRoutes.openFromPlatform(
                mapOf(
                    "kind" to "quota",
                    "sessionID" to "quota",
                    "profileID" to "profile",
                    "monitorToken" to "A".repeat(64),
                ),
            ),
        )
        assertFalse(QuotaAlertRoutes.actionAllowed("quota"))
        assertTrue(QuotaAlertRoutes.actionAllowed("permission"))
    }
}
