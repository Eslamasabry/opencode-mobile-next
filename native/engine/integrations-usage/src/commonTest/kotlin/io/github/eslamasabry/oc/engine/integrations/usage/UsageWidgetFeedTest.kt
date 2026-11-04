package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Ported from Flutter test/usage_widget_snapshot_test.dart, test/usage_signin_destination_test.dart (Claude through
// Paseo), test/usage_widget_screen_test.dart (the phone source) and test/paseo_usage_test.dart (switching off).

private val WIDGET_NOW = Instant.parse("2026-10-12T12:00:00Z")

/** A collector snapshot as the quota module hands it over; account, plan and URL never enter this type. */
private fun quota(provider: String = "codex", used: Double = 38.0, usable: Boolean = true) =
    QuotaWidgetInput(
        providerId = provider,
        canShowWindows = usable,
        fetchedAt = WIDGET_NOW,
        windows = if (usable) listOf(QuotaWidgetWindow(100 - used, 18_000, WIDGET_NOW + 3.hours)) else emptyList(),
    )

class UsageWidgetFeedTest {
    private val kv = FakeKv()
    private var redraws = 0
    private val widget = UsageWidgetFeed(kv, LANE, isAndroid = true, refreshNative = { redraws++ }, clock = { WIDGET_NOW })

    private fun stored(profile: String): Map<*, *> = DartJson.decode(kv.str(UsageWidgetFeed.key(profile))!!) as Map<*, *>

    private fun rows(profile: String): Map<*, *> = stored(profile)["rows"] as Map<*, *>

    @Test
    fun `B-SEC-179 stores no account plan address credential or error text`() =
        runTest {
            widget.record("p1", "Home", quota())
            val raw = assertNotNull(kv.str(UsageWidgetFeed.key("p1")))
            assertFalse(raw.contains("a".repeat(64)))
            assertFalse(raw.contains("plus"))
            assertFalse(raw.contains("http"))
            assertFalse(raw.contains("ref"))
            assertFalse(raw.contains("primary"))
            val row = rows("p1")["codex"] as Map<*, *>
            assertEquals(62L, ((row["windows"] as List<*>).single() as Map<*, *>)["left"])
        }

    @Test
    fun `B-SEC-179 Claude and unusable snapshots never appear`() =
        runTest {
            widget.record("p1", "Home", quota(provider = "claude"))
            widget.record("p1", "Home", quota(usable = false))
            assertNull(kv.str(UsageWidgetFeed.key("p1")))
            assertEquals(0, redraws)
            widget.markAuthNeeded("p1", "Home", "claude")
            assertNull(kv.str(UsageWidgetFeed.key("p1")))
        }

    @Test
    fun `B-SEC-179 auth needed keeps the numbers and flags the row until a fresh measurement`() =
        runTest {
            widget.record("p1", "Home", quota())
            widget.markAuthNeeded("p1", "Home", "codex")
            val row = rows("p1")["codex"] as Map<*, *>
            assertEquals("auth", row["state"])
            assertEquals(WIDGET_NOW.toEpochMilliseconds(), row["fetchedAt"])
            assertEquals(62L, ((row["windows"] as List<*>).single() as Map<*, *>)["left"])
            widget.record("p1", "Home", quota(used = 40.0))
            assertEquals("ok", (rows("p1")["codex"] as Map<*, *>)["state"])
        }

    @Test
    fun `B-SEC-179 is inert off Android`() =
        runTest {
            val other = UsageWidgetFeed(kv, LANE, isAndroid = false, clock = { WIDGET_NOW })
            other.record("p1", "Home", quota())
            other.recordReadings("p1", "Home", listOf(ProviderUsageReading("codex", "Codex", WIDGET_NOW, listOf(ProviderUsageWindowReading(18_000, "", 60, null)))))
            assertTrue(kv.values.isEmpty())
        }

    @Test
    fun `B-SEC-179 the profile deletion sweep and clearForProfile remove it`() =
        runTest {
            widget.record("p1", "Home", quota())
            widget.record("p2", "Other", quota())
            assertEquals(setOf(UsageWidgetFeed.key("p1")), kv.scopedKeys("p1"))
            kv.sweep("p1")
            assertNull(kv.str(UsageWidgetFeed.key("p1")))
            assertNotNull(kv.str(UsageWidgetFeed.key("p2")))
            redraws = 0
            assertTrue(widget.clearForProfile("p2"))
            assertNull(kv.str(UsageWidgetFeed.key("p2")))
            assertEquals(1, redraws)
        }

    @Test
    fun `B-SEC-179 removing the last provider removes the entry`() =
        runTest {
            widget.record("p1", "Home", quota())
            widget.removeProvider("p1", "codex")
            assertNull(kv.str(UsageWidgetFeed.key("p1")))
        }

    @Test
    fun `B-STATE-148 a signed-out Claude via Paseo becomes a reading with no numbers`() {
        val readings =
            parseProviderUsageList(
                mapOf(
                    "providers" to
                        listOf(
                            mapOf("providerId" to "claude", "displayName" to "Claude", "status" to "unavailable", "windows" to emptyList<Any>(), "error" to null),
                            // A provider the person never set up is not a sign-in prompt.
                            mapOf("providerId" to "kimi", "displayName" to "Kimi", "status" to "unavailable", "windows" to emptyList<Any>()),
                            // A failure is not "sign in".
                            mapOf("providerId" to "codex", "displayName" to "Codex", "status" to "error", "error" to "Codex usage API returned 500"),
                        ),
                ),
                now = NOW,
            )
        assertEquals("claude", readings.single().providerId)
        assertTrue(readings.single().needsSignIn)
        assertTrue(readings.single().windows.isEmpty())
    }

    @Test
    fun `B-STATE-148 the widget stores it as a row and numbers replace it`() =
        runTest {
            widget.recordReadings("phone", "", listOf(ProviderUsageReading("claude", "Claude", NOW, emptyList(), needsSignIn = true)), thisPhone = true)
            assertTrue(kv.str(UsageWidgetFeed.key("phone"))!!.contains("\"state\":\"signin\""))
            widget.recordReadings(
                "phone",
                "",
                listOf(ProviderUsageReading("claude", "Claude", NOW, listOf(ProviderUsageWindowReading(18_000, "", 60, null)))),
                thisPhone = true,
            )
            assertTrue(kv.str(UsageWidgetFeed.key("phone"))!!.contains("\"ok\""))
            assertFalse(kv.str(UsageWidgetFeed.key("phone"))!!.contains("signin"))
        }

    @Test
    fun `B-STATE-148 not-signed-in writes a row with the way in and no numbers`() =
        runTest {
            widget.markNotSignedIn("p1", "Home", "glm", displayName = "GLM")
            val row = rows("p1")["glm"] as Map<*, *>
            assertEquals("signin", row["state"])
            assertEquals(UsageWidgetFeed.ADD_KEY, row["action"])
            assertTrue((row["windows"] as List<*>).isEmpty())
            widget.markNotSignedIn("p1", "Home", "Bad Id")
            assertEquals(setOf("glm"), rows("p1").keys)
        }
}

private class PhoneAndPaseo(var reply: List<ProviderUsageReading>) : PaseoUsagePorts {
    var serverReads = 0
    var phone = true
    var readable = true

    override fun paseoProfiles(): List<PaseoUsageProfile> = listOf(PaseoUsageProfile("p1", "Desk"))

    override fun isReadable(profileId: String): Boolean = readable

    override suspend fun readServer(profileId: String): Outcome<List<ProviderUsageReading>> {
        serverReads++
        return Outcome.Ok(reply)
    }

    override fun phoneSupported(): Boolean = phone

    override suspend fun readPhone(): Outcome<List<ProviderUsageReading>?> = Outcome.Ok(reply)
}

class PaseoUsageMonitorTest {
    private val kv = FakeKv()
    private val widget = UsageWidgetFeed(kv, LANE, isAndroid = true, clock = { NOW })
    private val claude = ProviderUsageReading("claude", "Claude", NOW, listOf(ProviderUsageWindowReading(18_000, "", 60, null)))

    @Test
    fun `B-NAV-080 rows from the phone source are labelled for This phone`() =
        runTest {
            val ports = PhoneAndPaseo(listOf(claude))
            val monitor = PaseoUsageMonitor(kv, LANE, ports, widget, clock = { NOW })
            assertTrue(monitor.setEnabled(PaseoUsageMonitor.PHONE_ID, true))
            monitor.refresh()
            val doc = DartJson.decode(kv.str(UsageWidgetFeed.key("phone"))!!) as Map<*, *>
            assertEquals(true, doc["local"])
            assertEquals("Claude", ((doc["rows"] as Map<*, *>)["claude"] as Map<*, *>)["name"])
            // Deleting the phone source's opt-in clears its numbers.
            assertTrue(monitor.setEnabled(PaseoUsageMonitor.PHONE_ID, false))
            assertNull(kv.str(UsageWidgetFeed.key("phone")))
        }

    @Test
    fun `B-SEC-116 switching off runtime gating and deletion clear or stop it`() =
        runTest {
            val ports = PhoneAndPaseo(listOf(claude)).also { it.phone = false }
            val monitor = PaseoUsageMonitor(kv, LANE, ports, widget, clock = { NOW })
            monitor.setEnabled("p1", true)
            monitor.refresh()
            monitor.setRuntime(foreground = false, backgroundAllowed = false)
            val reads = ports.serverReads
            monitor.refresh()
            assertEquals(reads, ports.serverReads)
            assertEquals(PaseoUsageStatus.Paused, monitor.statusFor("p1"))
            monitor.setRuntime(foreground = true, backgroundAllowed = false)
            monitor.refresh()
            assertTrue(monitor.setEnabled("p1", false))
            assertNull(kv.str(UsageWidgetFeed.key("p1")))
            assertNull(kv.get(PaseoUsageMonitor.key("p1")))
        }

    @Test
    fun `B-SEC-116 profile deletion sweeps the opt-in and the snapshot`() =
        runTest {
            val ports = PhoneAndPaseo(listOf(claude)).also { it.phone = false }
            val monitor = PaseoUsageMonitor(kv, LANE, ports, widget, clock = { NOW })
            monitor.setEnabled("p1", true)
            monitor.refresh()
            assertEquals(setOf(PaseoUsageMonitor.key("p1"), UsageWidgetFeed.key("p1")), kv.scopedKeys("p1"))
            assertEquals(PrefValue.Bool(true), kv.get(PaseoUsageMonitor.key("p1")))
            monitor.removeProfile("p1")
            assertNull(kv.str(UsageWidgetFeed.key("p1")))
            assertFalse(monitor.enabled("p1"))
            kv.sweep("p1")
            assertTrue(kv.values.isEmpty())
        }
}
